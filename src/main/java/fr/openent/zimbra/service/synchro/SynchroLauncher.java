package fr.openent.zimbra.service.synchro;

import fr.openent.zimbra.helper.ServiceManager;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

class SynchroLauncher {

    public static final String NB_USER_SYNCED = "nbUserSynced";

    private SynchroUserService synchroUserService;
    private int nbUserSynchronized;

    SynchroLauncher() {
        nbUserSynchronized = 0;
        ServiceManager serviceManager = ServiceManager.getServiceManager();
        this.synchroUserService = serviceManager.getSynchroUserService();
    }

    void start(Handler<AsyncResult<JsonObject>> handler) {
        Handler<AsyncResult<JsonObject>> syncUserHandler = getSyncHandler(handler);
        synchroUserService.syncUserFromBase(syncUserHandler);
    }

    private Handler<AsyncResult<JsonObject>> getSyncHandler(Handler<AsyncResult<JsonObject>> handler) {
        return syncRes -> {
            if(syncRes.succeeded() && syncRes.result().isEmpty()) {
                // todo check finished synchro ?
                handler.handle(Future.succeededFuture(new JsonObject().put(NB_USER_SYNCED, nbUserSynchronized)));
            } else {
                nbUserSynchronized++;
                Handler<AsyncResult<JsonObject>> syncUserHandler = getSyncHandler(handler);
                synchroUserService.syncUserFromBase(syncUserHandler);
            }
        };
    }
}
