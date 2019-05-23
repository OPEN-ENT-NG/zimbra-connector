package fr.openent.zimbra.model.synchro.addressbook;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.model.constant.I18nConstants;
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


import static fr.openent.zimbra.service.data.Neo4jAddrbookService.*;

// todo documentation synchronisation
public class DefaultAddressBookSynchroImpl extends AddressBookSynchro {

    private Neo4jAddrbookService neo4jAddrbookService;
    private final String FNAME_MEMBERS;

    private static Logger log = LoggerFactory.getLogger(DefaultAddressBookSynchroImpl.class);


    public DefaultAddressBookSynchroImpl(String uai) throws NullPointerException {
        super(uai);
        ServiceManager sm = ServiceManager.getServiceManager();
        this.neo4jAddrbookService = sm.getNeo4jAddrbookService();
        FNAME_MEMBERS = I18n.getInstance().translate(
                I18nConstants.AB_MEMBERS_FOLDER,
                "default-domain",
                Zimbra.appConfig.getSynchroLang());
    }

    @Override
    public void load(Handler<AsyncResult<AddressBookSynchro>> handler) {
        Future<AddressBookSynchro> users = Future.future();
        Future<AddressBookSynchro> groups = Future.future();
        neo4jAddrbookService.getAllUsersFromStructure(uai, res ->  {
            if(res.failed()) {
                users.fail(res.cause());
            } else {
                processUsers(res.result(), users.completer());
            }
        });
        // todo get and process lists
        CompositeFuture.all(users,groups).setHandler(compositeResult -> {
            if(compositeResult.failed()) {
                handler.handle(Future.failedFuture(compositeResult.cause()));
            } else {
                handler.handle(Future.succeededFuture(this));
            }
        });
    }


    private void processUsers(JsonArray neoData, Handler<AsyncResult<AddressBookSynchro>> handler) {
        for(Object o : neoData) {
            if(!(o instanceof JsonObject)) continue;
            try{
                processUser((JsonObject)o);
            } catch (IllegalArgumentException e) {
                log.warn("ABSync : Unable to process user " + o);
            }
        }
        if(folders.isEmpty()) {
            handler.handle(Future.failedFuture("no address book generated"));
        } else {
            handler.handle(Future.succeededFuture(this));
        }
    }

    private void processUser(JsonObject neoUser) throws IllegalArgumentException {
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
                addToClassSubFolder(contact, profile);
            } else if(PROFILE_GUEST.equals(profile) ) {
                getFolder(profile).addUser(contact);
            } else {
                addToMembersSubFolder(contact, profile);
            }
        }
    }

    // Users that must be displayed in a class subfolder that does not have a class are not displayed
    private void addToMembersSubFolder(Contact user, String profile) {
        AddressBookFolder folderProfile = getFolder(profile);
        AddressBookFolder subFolder = folderProfile.getSubFolder(FNAME_MEMBERS);
        subFolder.addUser(user);
    }

    // Users that must be displayed in a class subfolder that does not have a class are not displayed
    private void addToClassSubFolder(Contact user, String foldername) {
        AddressBookFolder folder = getFolder(foldername);
        if(user.getClasses().isEmpty()) {
            return;
        }
        String[] classes = user.getClasses().split(", ");
        for (String classe : classes) {
            folder.getSubFolder(classe).addUser(user);
        }
    }




}
