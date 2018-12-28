package fr.openent.zimbra.service.synchro;

import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.model.constant.SynchroConstants;
import fr.openent.zimbra.service.data.SqlSynchroService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import static fr.openent.zimbra.service.synchro.SynchroUserService.EMPTY_BDD;

class SynchroLauncher {

    @SuppressWarnings("WeakerAccess")
    public static final String NB_USER_SYNCED = "nbUserSynced";

    private SynchroUserService synchroUserService;
    private SqlSynchroService sqlSynchroService;
    private int nbUserSynchronized;


    SynchroLauncher() {
        nbUserSynchronized = 0;
        ServiceManager serviceManager = ServiceManager.getServiceManager();
        this.synchroUserService = serviceManager.getSynchroUserService();
        this.sqlSynchroService = serviceManager.getSqlSynchroService();
    }


    // Start synchronisation
    void start(Handler<AsyncResult<JsonArray>> handler) {
        sqlSynchroService.updateSynchros(SynchroConstants.STATUS_TODO, SynchroConstants.STATUS_INPROGRESS, v -> {
            if(v.failed()) {
                handler.handle(Future.failedFuture(v.cause()));
            } else {
                Handler<AsyncResult<JsonObject>> finalHandler = getFinalHandler(handler);
                Handler<AsyncResult<JsonObject>> syncUserHandler = getSyncHandler(finalHandler);
                synchroUserService.syncUserFromBase(syncUserHandler);
            }
        });
    }


    // Get handler for recursion
    private Handler<AsyncResult<JsonObject>> getSyncHandler(Handler<AsyncResult<JsonObject>> handler) {
        return syncRes -> {
            if(syncRes.succeeded() && syncRes.result().getBoolean(EMPTY_BDD, false)) {
                handler.handle(Future.succeededFuture(new JsonObject().put(NB_USER_SYNCED, nbUserSynchronized)));
            } else {
                nbUserSynchronized++;
                Handler<AsyncResult<JsonObject>> syncUserHandler = getSyncHandler(handler);
                synchroUserService.syncUserFromBase(syncUserHandler);
            }
        };
    }


    // Get the handler for the end of process
    private Handler<AsyncResult<JsonObject>> getFinalHandler(Handler<AsyncResult<JsonArray>> handler) {
        return finalSyncRes ->
            sqlSynchroService.updateSynchros(SynchroConstants.STATUS_INPROGRESS,
                    SynchroConstants.STATUS_DONE,
                    handler);
    }
}
