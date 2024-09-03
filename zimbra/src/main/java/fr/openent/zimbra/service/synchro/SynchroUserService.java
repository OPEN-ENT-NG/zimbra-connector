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

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.helper.AsyncHelper;
import fr.openent.zimbra.helper.ConfigManager;
import fr.openent.zimbra.model.soap.SoapRequest;
import fr.openent.zimbra.model.synchro.SynchroUser;
import fr.openent.zimbra.model.constant.SoapConstants;
import fr.openent.zimbra.model.constant.SynchroConstants;
import fr.openent.zimbra.service.DbMailService;
import fr.openent.zimbra.service.data.SqlSynchroService;
import fr.openent.zimbra.service.impl.*;
import fr.wseduc.webutils.Either;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

import static fr.openent.zimbra.model.constant.ZimbraConstants.*;
import static fr.openent.zimbra.model.constant.ZimbraErrors.*;
import static fr.openent.zimbra.service.data.SoapZimbraService.ERROR_CODE;

public class SynchroUserService {

    static final String EMPTY_BDD = "empty_bdd";

    private UserService userService;
    private DbMailService dbMailService;
    private SqlSynchroService sqlSynchroService;

    private static Logger log = LoggerFactory.getLogger(SynchroUserService.class);

    public SynchroUserService(DbMailService dbMailService,
                              SqlSynchroService sqlSynchroService){
        this.dbMailService = dbMailService;
        this.sqlSynchroService = sqlSynchroService;
    }

    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    /**
     * Remove outdated user info from base
     * @param userId User ID (potentially obsolete)
     * @param userMail User Mail (potentially obsolete)
     * @param handler Final handler
     */
    public void removeUserFromBase(String userId, String userMail, Handler<Either<String,JsonObject>> handler) {
        dbMailService.removeUserFrombase(userId, userMail, handler);
    }


    /**
     * Get the first user to synchronize from bdd (if any) and synchronize its information in Zimbra
     * @param handler synchronization result
     */
    void syncUserFromBase(Handler<AsyncResult<JsonObject>> handler) {
        Promise<JsonObject> startPromise = AsyncHelper.getJsonObjectFinalPromise(handler);
        Promise<JsonObject> fetchedUser = Promise.promise();

        log.info("Fetching user to sync");
        sqlSynchroService.fetchUserToSynchronize(fetchedUser);
        fetchedUser.future().compose(bddRes -> {
            if (bddRes.isEmpty()) {
                return Future.succeededFuture(new JsonObject().put(EMPTY_BDD, true));
            } else {
                int idRow = bddRes.getInteger(SqlSynchroService.USER_IDROW);
                String idUser = bddRes.getString(SqlSynchroService.USER_IDUSER);
                String syncAction = bddRes.getString(SqlSynchroService.USER_SYNCACTION);
                log.info("Syncing user " + idUser);

                try {
                    SynchroUser user = new SynchroUser(idUser);
                    Promise<JsonObject> syncPromise = Promise.promise();
                    user.synchronize(idRow, syncAction, syncPromise);

                    return syncPromise.future();
                } catch (IllegalArgumentException e) {
                    log.info("Failed to sync user " + idUser);
                    return Future.failedFuture(e);
                }
            }
        }).onComplete(startPromise);
    }

    /**
     * Export a user to Zimbra
     * Get data from Neo4j, then create user in Zimbra
     * @param userId userId
     * @param handler result handler
     */
    public void exportUser(String userId, Handler<AsyncResult<JsonObject>> handler) {
        if(Zimbra.appConfig.isActionBlocked(ConfigManager.SYNC_ACTION)) {
            handler.handle(Future.failedFuture("action blocked by devlevel"));
            log.error("user synchro blocked by dev level");
            return;
        }
        try {
            SynchroUser user = new SynchroUser(userId);
            user.synchronize(SynchroConstants.ACTION_CREATION, handler);
        } catch (IllegalArgumentException e) {
            handler.handle(Future.failedFuture(e));
        }
    }

    /**
     * Add Manual Group to users asynchronously
     * @param groupId Group Id
     * @param groupMembersId Users Id
     * @param handler result handler, always succeeding
     */
    void addGroupToMembers(String groupId, JsonArray groupMembersId,
                           Handler<Either<String, JsonObject>> handler) {

        AtomicInteger nbRemaining = new AtomicInteger(groupMembersId.size());

        for(Object o : groupMembersId) {
            if(!(o instanceof JsonObject)) {
                log.error("addGroupToMembers : invalid group member from Neo4j");
                if(nbRemaining.decrementAndGet() == 0) {
                    handler.handle(new Either.Right<>(new JsonObject()));
                }
                continue;
            }
            String userId = ((JsonObject)o).getString(Field.ID);

            Handler<AsyncResult<JsonObject>> addGroupHandler = resAdd -> {
                if(resAdd.failed()) {
                    log.error("Error when adding group " + groupId
                            + " to user " + userId + " : " + resAdd.cause().getMessage());
                }
                if(nbRemaining.decrementAndGet() == 0) {
                    handler.handle(new Either.Right<>(new JsonObject()));
                }
            };

            userService.getUserAccountById(userId, result -> {
                if(result.succeeded()) {
                    JsonObject accountInfo = result.result();
                    addGroup(groupId, accountInfo, addGroupHandler);
                } else {
                    JsonObject newCallResult = new JsonObject(result.cause().getMessage());
                    if(ERROR_NOSUCHACCOUNT.equals(newCallResult.getString(ERROR_CODE, ""))) {
                        exportUser(userId, resultCreate -> {
                            if (resultCreate.failed()) {
                                log.error("Error when creating account " + userId + " : "
                                        + resultCreate.cause().getMessage());
                                if(nbRemaining.decrementAndGet() == 0) {
                                    handler.handle(new Either.Right<>(new JsonObject()));
                                }
                            } else {
                                JsonObject accountData = resultCreate.result();
                                JsonObject accountInfo = new JsonObject();
                                UserInfoService.processAccountInfo(accountData, accountInfo);
                                addGroup(groupId, accountInfo, addGroupHandler);
                            }
                        });
                    } else {
                        log.error("Error when getting user account " + userId + " : " + result.cause().getMessage());
                        if(nbRemaining.decrementAndGet() == 0) {
                            handler.handle(new Either.Right<>(new JsonObject()));
                        }
                    }
                }
            });
        }
    }


    private void addGroup(String groupId, JsonObject accountInfo,
                          Handler<AsyncResult<JsonObject>> handler) {
        JsonArray attrs = new JsonArray().add(
                new JsonObject()
                        .put(SynchroConstants.ADDGROUPID, groupId));

        SoapRequest modifyAccountRequest = SoapRequest.AdminSoapRequest(SoapConstants.MODIFY_ACCOUNT_REQUEST);
        modifyAccountRequest.setContent(new JsonObject()
                .put(ACCT_ID, accountInfo.getValue(UserInfoService.ZIMBRA_ID))
                .put(ACCT_INFO_ATTRIBUTES, attrs));
        modifyAccountRequest.start(handler);
    }

}
