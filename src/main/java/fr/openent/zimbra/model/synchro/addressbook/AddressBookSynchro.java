package fr.openent.zimbra.model.synchro.addressbook;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.model.constant.I18nConstants;
import fr.openent.zimbra.model.synchro.Structure;
import fr.openent.zimbra.model.synchro.addressbook.contacts.*;
import fr.openent.zimbra.service.data.Neo4jAddrbookService;
import fr.wseduc.webutils.I18n;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.user.UserUtils;


import java.util.HashMap;
import java.util.Map;

import static fr.openent.zimbra.service.data.Neo4jAddrbookService.*;

/**
 * Synchronize address books with Zimbra.
 * 1- load() to load neo4j data
 * 2- sync() to sync data in user's address book
 */
public class AddressBookSynchro {

    protected String uai;
    protected String name;
    protected Map<String,AddressBookFolder> folders = new HashMap<>();

    Neo4jAddrbookService neo4jAddrbookService;
    private final String FOLDER_NAME_MEMBERS;
    boolean loaded = false;

    private static Logger log = LoggerFactory.getLogger(AddressBookSynchro.class);


    public AddressBookSynchro(Structure structure) throws NullPointerException {
        if(structure == null || structure.getUai().isEmpty()) {
            throw new NullPointerException("AddrBook : Empty Structure");
        }
        this.uai = structure.getUai();
        this.name = structure.getName();
        ServiceManager sm = ServiceManager.getServiceManager();
        this.neo4jAddrbookService = sm.getNeo4jAddrbookService();
        FOLDER_NAME_MEMBERS = I18n.getInstance().translate(
                I18nConstants.AB_MEMBERS_FOLDER,
                "default-domain",
                Zimbra.appConfig.getSynchroLang());
    }

    public void synchronize(String userId, Handler<AsyncResult<JsonObject>> handler) {
        load(res -> {
            if (res.failed()) {
                handler.handle(Future.failedFuture(res.cause()));
            } else {
                sync(userId, handler);
            }
        });
    }


    protected void load(Handler<AsyncResult<AddressBookSynchro>> handler) {
        Future<AddressBookSynchro> users = Future.future();
        Future<AddressBookSynchro> groups = Future.future();
        neo4jAddrbookService.getAllUsersFromStructure(uai, res ->  {
            if(res.failed()) {
                users.fail(res.cause());
            } else {
                processUsers(res.result(), users.completer());
            }
        });
        neo4jAddrbookService.getAllGroupsFromStructure(uai, res ->  {
            if(res.failed()) {
                groups.fail(res.cause());
            } else {
                processGroups(res.result(), groups.completer());
            }
        });
        CompositeFuture.all(users,groups).setHandler(compositeResult -> {
            if(compositeResult.failed()) {
                handler.handle(Future.failedFuture(compositeResult.cause()));
            } else {
                loaded = true;
                handler.handle(Future.succeededFuture(this));
            }
        });
    }


    private void sync(String userId, Handler<AsyncResult<JsonObject>> handler) {
        if(!loaded) {
            handler.handle(Future.failedFuture("ABSync data not loaded"));
            log.error("Trying to sync AddressBook, but data are not loaded");
            return;
        }
        AddressBookZimbraSynchro zimbraSynchro = new AddressBookZimbraSynchro(userId, uai, name);
        zimbraSynchro.initSync(res -> {
            if(res.failed()) {
                handler.handle(res);
            } else {
                zimbraSynchro.sync(folders, handler);
            }
        });
    }


    private void processUsers(JsonArray neoData, Handler<AsyncResult<AddressBookSynchro>> handler) {
        for(Object o : neoData) {
            if(!(o instanceof JsonObject)) continue;
            try {
                processUser((JsonObject)o);
            } catch (Exception e) {
                log.error("ABSync : Unknown Error when processing user : " + o, e);
            }
        }
        if(folders.isEmpty()) {
            handler.handle(Future.failedFuture("no address book generated"));
        } else {
            handler.handle(Future.succeededFuture(this));
        }
    }


    void processUser(JsonObject neoUser)  {
        String profile = neoUser.getString(PROFILE, "");
        if(profile.isEmpty()) {
            log.warn("ABSync : no profile for user " + neoUser.toString());
        } else {
            Contact contact;
            switch (profile) {
                case PROFILE_PERSONNEL:
                    contact = new Personnel(neoUser, uai);
                    break;
                case PROFILE_TEACHER:
                    contact = new Teacher(neoUser, uai);
                    break;
                case PROFILE_STUDENT:
                    contact = new Student(neoUser, uai);
                    break;
                case PROFILE_RELATIVE:
                    contact = new Relative(neoUser, uai);
                    break;
                default:
                    contact = new Guest(neoUser, uai);
                    profile = PROFILE_GUEST;
            }
            if(PROFILE_RELATIVE.equals(profile) || PROFILE_STUDENT.equals(profile)) {
                addToClassSubFolder(contact);
            } else if(PROFILE_GUEST.equals(profile) ) {
                getFolder(contact.getProfileFolderName()).addContact(contact);
            } else {
                addToMembersSubFolder(contact);
            }
        }
    }


    private void processGroups(JsonArray neoData, Handler<AsyncResult<AddressBookSynchro>> handler) {
        UserUtils.translateGroupsNames(neoData, Zimbra.appConfig.getSynchroLang());
        for(Object o : neoData) {
            if(!(o instanceof JsonObject)) continue;
            try{
                JsonObject neoGroup = ((JsonObject)o);
                Contact groupContact = new GroupContact(neoGroup, uai);
                addToProfileFolder(groupContact);
            }catch (Exception e) {
                log.error("ABSync : Unknown Error when processing group : " + o, e);
            }
        }
        handler.handle(Future.succeededFuture(this));
    }

    private AddressBookFolder getFolder(String folderName) {
        AddressBookFolder folder = folders.get(folderName);
        if(folder == null) {
            folder = new AddressBookFolder();
            folders.put(folderName, folder);
        }
        return folder;
    }

    private void addToMembersSubFolder(Contact user) {
        AddressBookFolder folderProfile = getFolder(user.getProfileFolderName());
        AddressBookFolder subFolder = folderProfile.getSubFolder(FOLDER_NAME_MEMBERS);
        subFolder.addContact(user);
    }


    // Users that must be displayed in a class subfolder that does not have a class are not displayed
    private void addToClassSubFolder(Contact user) {
        AddressBookFolder folder = getFolder(user.getProfileFolderName());
        if(user.getClasses().isEmpty()) {
            return;
        }
        String[] classes = user.getClasses().split(", ");
        for (String classe : classes) {
            folder.getSubFolder(classe).addContact(user);
        }
    }


    void addToProfileFolder(Contact contact) {
        AddressBookFolder folderProfile = getFolder(contact.getProfileFolderName());
        folderProfile.addContact(contact);
    }


}
