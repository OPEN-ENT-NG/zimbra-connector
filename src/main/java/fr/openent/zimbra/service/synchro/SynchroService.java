package fr.openent.zimbra.service.synchro;

import fr.openent.zimbra.model.constant.BusConstants;
import fr.openent.zimbra.model.synchro.SynchroInfos;
import fr.openent.zimbra.model.constant.SynchroConstants;
import fr.openent.zimbra.service.data.SqlSynchroService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class SynchroService {

    private static final String UAI_REGEX = "[a-zA-Z0-9]{8}";


    private SqlSynchroService sqlSynchroService;

    private static SynchroLauncher synchroLauncher = null;
    private static Logger log = LoggerFactory.getLogger(SynchroService.class);


    public SynchroService(SqlSynchroService sqlSynchroService) {
        this.sqlSynchroService = sqlSynchroService;
    }

    public void startSynchro(Handler<AsyncResult<JsonObject>> handler) {
        if(synchroLauncher == null) {
            synchroLauncher = new SynchroLauncher();
            synchroLauncher.start( res -> {
                synchroLauncher = null;
                if(res.succeeded()) {
                        handler.handle(Future.succeededFuture(new JsonObject()));
                } else {
                        handler.handle(Future.failedFuture(res.cause()));
                }
            });
        } else {
            handler.handle(Future.succeededFuture(new JsonObject()
                    .put(BusConstants.BUS_MESSAGE, "Synchronisation already running")));
        }
    }

    public void updateDeployedStructures(List<String> updatedList, Handler<AsyncResult<JsonObject>> handler) {
        Future<JsonObject> startFuture = Future.future();
        startFuture.setHandler(handler);

        Future<List<String>> fetchedInfos = Future.future();
        sqlSynchroService.getDeployedStructures(fetchedInfos.completer());
        fetchedInfos.compose(databaseList -> {
            compareLists(updatedList, databaseList);
            if(updatedList.isEmpty() && databaseList.isEmpty()) {
                log.info("No changes in deployed structures");
                startFuture.complete(new JsonObject());
            } else {
                if(checkValidUais(updatedList)) {
                    sqlSynchroService.updateDeployedStructures(updatedList, databaseList, startFuture.completer());
                } else {
                    log.error("Invalid UAI in list : " + updatedList.toString());
                    startFuture.fail("Invalid UAI");
                }
            }
        }, startFuture);
    }

    private void compareLists(List<String> updatedList, List<String> databaseList) {
        List<String> templist = new ArrayList<>(updatedList);
        for(String s : templist) {
            if(databaseList.contains(s)) {
                databaseList.remove(s);
                updatedList.remove(s);
            }
        }
    }

    private boolean checkValidUais(List<String> uaiList) {
        for(String uai : uaiList) {
            if(!uai.matches(UAI_REGEX)) { return false; }
        }
        return true;
    }

    public void updateUsers(SynchroInfos synchroInfos, Handler<AsyncResult<JsonObject>> handler) {
        addUsersToDatabase( synchroInfos, res -> {
            if(res.succeeded()) {
                JsonObject result = new JsonObject().put("idsynchro", synchroInfos.getId());
                handler.handle(Future.succeededFuture(result));
            } else {
                log.error("Synchro : error when adding users to database : " + res.cause().getMessage());
            }
        });
    }

    private void addUsersToDatabase(SynchroInfos synchroInfos, Handler<AsyncResult<JsonObject>> handler) {
        Future<JsonObject> startFuture = Future.future();
        startFuture.setHandler(handler);

        Future<JsonObject> syncInitialized = Future.future();
        sqlSynchroService.initializeSynchro(synchroInfos.getMaillinglistRaw(), syncInitialized.completer());
        syncInitialized.compose(initResult -> {
            synchroInfos.setId(initResult.getInteger(SqlSynchroService.SYNCHRO_ID));

            Future<JsonObject> createdAdded = Future.future();
            sqlSynchroService.addUsersToSynchronize(synchroInfos.getId(), synchroInfos.getUsersCreated(),
                    SynchroConstants.ACTION_CREATION, createdAdded.completer());
            return createdAdded;

        }).compose( createdResult -> {
            Future<JsonObject> modifiedAdded = Future.future();
            sqlSynchroService.addUsersToSynchronize(synchroInfos.getId(), synchroInfos.getUsersModified(),
                    SynchroConstants.ACTION_MODIFICATION, modifiedAdded.completer());
            return modifiedAdded;
        }).compose( modifiedResult ->
            sqlSynchroService.addUsersToSynchronize(synchroInfos.getId(), synchroInfos.getUsersDeleted(),
                    SynchroConstants.ACTION_DELETION, startFuture.completer())
        , startFuture);
    }
}
