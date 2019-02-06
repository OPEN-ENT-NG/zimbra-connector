package fr.openent.zimbra.model.synchro.addressbook;

import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.service.data.Neo4jAddrbookService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;

import static fr.openent.zimbra.service.data.Neo4jAddrbookService.*;

public class AddressBookSynchroNeo extends AddressBookSynchro {

    private Neo4jAddrbookService neo4jAddrbookService;


    private static Logger log = LoggerFactory.getLogger(AddressBookSynchroNeo.class);

    public AddressBookSynchroNeo(String uai) throws NullPointerException {
        super(uai);
        ServiceManager sm = ServiceManager.getServiceManager();
        this.neo4jAddrbookService = sm.getNeo4jAddrbookService();
    }

    @Override
    public void load(Handler<AsyncResult<AddressBookSynchro>> handler) {
        // todo use compose for load
        loadGuests(vGuest ->  {
            loadUsersByProfile(PROFILE_PERSONNEL, vPers -> {
                loadUsersByProfile(PROFILE_STUDENT, vStud -> {
                    loadUsersByProfile(PROFILE_RELATIVE, vRelative -> {
                        loadUsersByProfile(PROFILE_TEACHER, vTeach -> {
                            handler.handle(Future.succeededFuture(AddressBookSynchroNeo.this));
                        });
                    });
                });
            });
        });
    }

    private void loadGuests(Handler<AsyncResult<Void>> handler) {
        neo4jAddrbookService.getUsersProfileStructure(uai, PROFILE_GUEST, res ->  {
            if(res.failed()) {
                handler.handle(Future.failedFuture(res.cause()));
            } else {
                JsonObject guestData = res.result();
                if(!validateData(guestData)) {
                    handler.handle(Future.failedFuture("Invalid guests Neo4j data"));
                    return;
                }
                JsonArray guestJsonList = guestData.getJsonArray(USERS, new JsonArray());
                for(Object o : guestJsonList) {
                    if(!(o instanceof JsonObject)) continue;
                    try {
                        AddressBookUser abUser = new AddressBookUser((JsonObject)o);
                        guestList.put(abUser.getId(), abUser);
                    } catch (IllegalArgumentException e) {
                        log.error("Error when loading guest : " + o.toString());
                    }
                }
                handler.handle(Future.succeededFuture());
            }
        });
    }


    private void loadUsersByProfile(String profile, Handler<AsyncResult<Void>> handler) {
        fetchUsersInBdd(uai, profile, res -> {
            if(res.failed()) {
                handler.handle(Future.failedFuture(res.cause()));
            } else {
                JsonArray usersArray = res.result();
                if(!validateData(usersArray)) {
                    handler.handle(Future.failedFuture("Invalid users Neo4j data"));
                    return;
                }
                for(Object o : usersArray) {
                    if(!(o instanceof JsonObject)) continue;
                    try {
                        log.info("Subdir : " + o.toString());
                        JsonObject subdirContent = ((JsonObject)o).getJsonObject(SUBDIRS);
                        AddressBookFolder abSubdir = new AddressBookFolder(subdirContent);
                        List<AddressBookFolder> curList = getListForProfile(profile);
                        if(curList != null) {
                            curList.add(abSubdir);
                        }
                    } catch (IllegalArgumentException e) {
                        log.error("Error when loading personnel function : " + o.toString());
                    }
                }
                handler.handle(Future.succeededFuture());
            }
        });
    }

    private void fetchUsersInBdd(String uai, String profile,
                                 Handler<AsyncResult<JsonArray>> handler) {
        switch (profile) {
            case PROFILE_PERSONNEL:
                neo4jAddrbookService.getUsersProfileWithFunction(uai, profile, handler);
                break;
            case PROFILE_STUDENT:
                neo4jAddrbookService.getUsersProfileWithClass(uai, profile, handler);
                break;
            case PROFILE_RELATIVE:
                neo4jAddrbookService.getUsersProfileWithClass(uai, profile, handler);
                break;
            case PROFILE_TEACHER:
                neo4jAddrbookService.getUsersProfileWithClass(uai, profile, handler);
                break;
            default:
                handler.handle(Future.failedFuture("Unrecognized profile."));
        }
    }

    private List<AddressBookFolder> getListForProfile(String profile) {
        switch (profile) {
            case PROFILE_PERSONNEL:
                return persList;
            case PROFILE_STUDENT:
                return studentList;
            case PROFILE_RELATIVE:
                return relativeList;
            case PROFILE_TEACHER:
                return teacherList;
            default:
                return null;
        }
    }

    private boolean validateData(JsonObject data) {
        return (data != null && data.getString(STRUCT_UAI, "").equals(uai));
    }

    private boolean validateData(JsonArray data) {
        if(data == null) return false;
        for(Object o : data) {
            if(!(o instanceof JsonObject)) return false;
            JsonObject jObj = (JsonObject)o;
            if(!jObj.getString(STRUCT_UAI, "").equals(uai)) return false;
        }
        return true;
    }
}
