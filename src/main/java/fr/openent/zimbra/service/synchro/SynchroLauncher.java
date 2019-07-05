/*
 * Copyright (c) Région Ile-de-France, Région Nouvelle-Aquitaine, CGI, 2016.
 * This file is part of OPEN ENT NG. OPEN ENT NG is a versatile ENT Project based on the JVM and ENT Core Project.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with OPEN ENT NG is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of OPEN ENT NG, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package fr.openent.zimbra.service.synchro;

import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.model.constant.SynchroConstants;
import fr.openent.zimbra.service.data.SqlSynchroService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import static fr.openent.zimbra.service.synchro.SynchroUserService.EMPTY_BDD;

public class SynchroLauncher {

    @SuppressWarnings("WeakerAccess")
    public static final String NB_USER_SYNCED = "nbUserSynced";

    private SynchroUserService synchroUserService;
    private SqlSynchroService sqlSynchroService;
    private int nbUserSynchronized;
    private int oldNbUserSynced;

    private static Logger log = LoggerFactory.getLogger(SynchroLauncher.class);

    public SynchroLauncher(SynchroUserService synchroUserService, SqlSynchroService sqlSynchroService) {
        this.synchroUserService = synchroUserService;
        this.sqlSynchroService = sqlSynchroService;
    }

    boolean isAlreadyLaunched() {
        boolean isLaunched = oldNbUserSynced != nbUserSynchronized;
        oldNbUserSynced = nbUserSynchronized;
        return isLaunched;
    }


    // Start synchronisation
    void start(Handler<AsyncResult<JsonArray>> handler) {
        nbUserSynchronized = 0;
        oldNbUserSynced = 0;
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
                log.debug(nbUserSynchronized + " users synchronized");
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
