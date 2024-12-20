package fr.openent.zimbra.model.synchro.addressbook;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.model.constant.I18nConstants;
import fr.openent.zimbra.model.synchro.Structure;
import fr.openent.zimbra.model.synchro.addressbook.contacts.*;
import fr.openent.zimbra.service.data.Neo4jAddrbookService;
import fr.wseduc.webutils.I18n;
import io.vertx.core.*;
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
    private boolean adminSync = false;
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
                if(folders.isEmpty()){
                    handler.handle(Future.succeededFuture());
                }else{
                    sync(userId, handler);
                }
            }
        });
    }

    public void synchronize(String userId, boolean adminSync, Handler<AsyncResult<JsonObject>> handler) {
        this.adminSync = adminSync;
        synchronize(userId, handler);
    }


    protected void load(Handler<AsyncResult<AddressBookSynchro>> handler) {
        Promise<AddressBookSynchro> users = Promise.promise();
        Promise<AddressBookSynchro> groups = Promise.promise();
        neo4jAddrbookService.getAllUsersFromStructure(uai, res ->  {
            if(res.failed()) {
                log.error("Error in getAllUsersFromStructure during loading the Adress Book",res.cause());
                users.fail(res.cause());
            } else {
                processUsers(res.result(), users);
            }
        });
        neo4jAddrbookService.getAllGroupsFromStructure(uai, res ->  {
            if(res.failed()) {
                log.error("Error in getAllGroupsFromStructure during loading the Adress Book",res.cause());
                groups.fail(res.cause());
            } else {
                processGroups(res.result(), groups);
            }
        });
        Future.all(users.future(),groups.future()).onComplete(compositeResult -> {
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
        AddressBookZimbraSynchro zimbraSynchro = new AddressBookZimbraSynchro(userId, uai, adminSync ? "" : name);
        zimbraSynchro.initSync(res -> {
            if(res.failed()) {
                log.error("Trying to initSync zimbraSynchro but failure",res.cause());
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
            log.info("No address book generated because there are no users in the structure");
        }
        handler.handle(Future.succeededFuture(this));
    }


    void processUser(JsonObject neoUser)  {
        String profile = neoUser.getString(PROFILE, "");
        if(profile.isEmpty()) {
            log.warn("ABSync : no profile for user " + neoUser);
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
        try {
            UserUtils.translateGroupsNames(neoData, Zimbra.appConfig.getSynchroLang());
        } catch (Exception e){
            log.error("ABSync : Error when translating groups : " + neoData, e);
        }
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
