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

package fr.openent.zimbra.model.synchro;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.helper.JsonHelper;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.model.EntUser;
import fr.openent.zimbra.model.soap.SoapRequest;
import fr.openent.zimbra.model.ZimbraUser;
import fr.openent.zimbra.model.constant.SoapConstants;
import fr.openent.zimbra.model.constant.ZimbraConstants;
import fr.openent.zimbra.service.data.SoapZimbraService;
import fr.openent.zimbra.service.data.SqlSynchroService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;

import static fr.openent.zimbra.model.constant.SynchroConstants.*;
import static fr.openent.zimbra.model.constant.SoapConstants.*;
import static fr.openent.zimbra.model.constant.ZimbraConstants.*;
import static fr.openent.zimbra.model.constant.ZimbraErrors.*;


public class SynchroUser extends EntUser {

    private int idRowBdd;
    private String sync_action;
    private ZimbraUser zimbraData = null;


    private static Logger log = LoggerFactory.getLogger(SynchroUser.class);

    public SynchroUser(String userid) throws IllegalArgumentException {
        super(userid);
    }

    public void synchronize(String sync_action, Handler<AsyncResult<JsonObject>> handler) {
        synchronize(0, sync_action, handler);
    }

    public void synchronize(int idRow, String sync_action, Handler<AsyncResult<JsonObject>> handler) {
        this.idRowBdd = idRow;
        this.sync_action = sync_action;
        Future<JsonObject> updateDbFuture = Future.future();
        updateDbFuture.setHandler(result ->
            updateDatabase(result, handler)
        );

        Future<Void> updatedFromNeo = Future.future();
        fetchDataFromNeo(updatedFromNeo.completer());
        updatedFromNeo.compose( v -> {
            Future<String> updatedFromZimbra = Future.future();
            getZimbraId(updatedFromZimbra.completer());
            return updatedFromZimbra;
        }).compose( zimbraId ->
            updateZimbra(zimbraId, updateDbFuture.completer())
        , updateDbFuture);
    }

    @Override
    public void fetchDataFromNeo(Handler<AsyncResult<Void>> handler) {
        if(ACTION_CREATION.equals(sync_action)
                || ACTION_MODIFICATION.equals(sync_action)) {
            super.fetchDataFromNeo(handler);
        } else {
            handler.handle(Future.succeededFuture());
        }
    }

    private void getZimbraId(Handler<AsyncResult<String>> handler) {
        // todo return ZimbraUser
        if(ACTION_CREATION.equals(sync_action)) {
            handler.handle(Future.succeededFuture(""));
        } else {
            SoapRequest getInfoRequest = SoapRequest.AdminSoapRequest(GET_ACCOUNT_INFO_REQUEST);
            getInfoRequest.setContent(new JsonObject()
                    .put(ACCT_INFO_ACCOUNT, new JsonObject()
                        .put(SoapConstants.ID_BY, ZimbraConstants.ACCT_NAME)
                        .put(SoapConstants.ATTR_VALUE, getUserStrAddress())));
            getInfoRequest.start( result -> {
                if(result.failed()) {
                    handler.handle(Future.failedFuture(result.cause()));
                } else {
                    try {
                        String zimbraId = getZimbraIdFromGetUserInfoResponse(result.result());
                        handler.handle(Future.succeededFuture(zimbraId));
                    } catch (Exception e) {
                        handler.handle(Future.failedFuture("Error when processing getInfoRequest : " + e.getMessage()));
                    }
                }
            });
        }
    }

    private void updateZimbra(String zimbraId, Handler<AsyncResult<JsonObject>> handler) {
        if(sync_action == null) {
            handler.handle(Future.failedFuture("No defined modification type for synchronisation"));
            return;
        }
        switch(sync_action) {
            case ACTION_CREATION:
            case ACTION_MODIFICATION:
                createUserIfNotExists(handler);
                syncGroupsAsync();
                break;
            case ACTION_DELETION:
                deleteUser(zimbraId, handler);
                break;
            default:
                handler.handle(Future.failedFuture("Unknown sync_action : " + sync_action));
                log.error("Unknown sync_action : " + sync_action);
        }
    }

    private void syncGroupsAsync() {
        try {
            dbMailService.checkGroupsExistence(getGroups(), sqlResult -> {
                if(sqlResult.failed()) {
                    log.error("Error when getting unsynced groups : " + sqlResult.cause().getMessage());
                } else {
                    try {
                        List<String> unsyncedGroupIds = JsonHelper.extractValueFromJsonObjects(sqlResult.result(), "id");
                        for(String groupId : unsyncedGroupIds) {
                            SynchroGroup group = new SynchroGroup(groupId);
                            group.synchronize(v -> {
                                if(v.failed()) {
                                    log.error("Group synchronisation failed for group : " + groupId
                                            + ", Error : " + v);
                                }
                            });
                        }
                    } catch (IllegalArgumentException e) {
                        log.error("Error when trying to process sql groups result : " + sqlResult.result().toString());
                    }
                }
            });
        } catch (Exception e) {
            //No Exception may be thrown in the main thread
            log.error("Error in syncGroupsAsync : " + e);
        }
    }


    private void createUserIfNotExists(Handler<AsyncResult<JsonObject>> handler) {
        ZimbraUser user = new ZimbraUser(getUserMailAddress());
        user.checkIfExists(userResponse -> {
            if(userResponse.failed()) {
                handler.handle(Future.failedFuture(userResponse.cause()));
            } else {
                if(user.existsInZimbra()) {
                    log.info("Updating user " + getUserId());
                    updateUser(user.getZimbraID(), handler);
                    dbMailService.updateUserAsync(user);
                } else {
                    log.info("Creating user " + getUserId());
                    createUser(0, createRes -> {
                        if(createRes.succeeded()) {
                            postCreateUserAsync(user);
                        }
                        handler.handle(createRes);
                    });
                }
            }
        });
    }

    private void postCreateUserAsync(ZimbraUser user) {
        try {
            // Fetch updated information from user after creation
            user.checkIfExists(userResponse -> {
                if (userResponse.succeeded()) {
                    if (user.existsInZimbra()) {
                        dbMailService.updateUserAsync(user);
                    } else {
                        log.error("Error does not exists after creation " + user.getAddressStr());
                    }
                } else {
                    log.warn("Unexpected error when updating user post creation " + userResponse.cause());
                }
            });
        } catch (Exception e) {
            //No Exception may be thrown in the main thread
            log.error("Error in postCreateUserAsync : " + e);
        }
    }

    private void createUser(int increment, Handler<AsyncResult<JsonObject>> handler) {
        String login = getLogin();

        String accountName = increment > 0
                ? login + "-" + increment + "@" + Zimbra.domain
                : login + "@" + Zimbra.domain;

        SoapRequest createAccountRequest = SoapRequest.AdminSoapRequest(CREATE_ACCOUNT_REQUEST);
        createAccountRequest.setContent(
                new JsonObject()
                        .put(ACCT_NAME, accountName)
                        .put(ATTR_LIST, getSoapData()));

        createAccountRequest.start( res -> {
            if(res.succeeded()) {
                JsonObject zimbraResponse = res.result();
                try {
                    String zimbraId = getZimbraIdFromCreateUserResponse(zimbraResponse);
                    addAlias(zimbraId, handler);
                } catch (Exception e) {
                    log.error("Could not add alias to account : " + accountName);
                    handler.handle(Future.failedFuture("Could not get account id from Zimbra response " + zimbraResponse.toString()));
                }
            } else {
                try {
                    JsonObject callResult = new JsonObject(res.cause().getMessage());
                    if(callResult.getString(SoapZimbraService.ERROR_CODE,"").equals(ERROR_ACCOUNTEXISTS)) {
                        createUser(increment+1, handler);
                    } else {
                        handler.handle(res);
                    }
                } catch (Exception e) {
                    handler.handle(res);
                }
            }
        });
    }

    @SuppressWarnings("RedundantThrows")
    private String getZimbraIdFromCreateUserResponse(JsonObject createUserResponse) throws Exception{
        JsonObject account = createUserResponse
                .getJsonObject(BODY)
                .getJsonObject(CREATE_ACCOUNT_RESPONSE)
                .getJsonArray(ACCOUNT).getJsonObject(0);
        return account.getString(ACCT_ID);
    }

    private String getZimbraIdFromGetUserInfoResponse(JsonObject getUserInfoResponse) throws Exception{

        JsonArray attrs = getUserInfoResponse
                .getJsonObject(BODY)
                .getJsonObject(GET_ACCOUNT_INFO_RESPONSE)
                .getJsonArray(ACCT_ATTRIBUTES);
        for(Object o : attrs) {
            if(!(o instanceof JsonObject)) {
                continue;
            }
            JsonObject attr = (JsonObject)o;
            if(ACCT_INFO_ZIMBRA_ID.equals(attr.getString(ACCT_ATTRIBUTES_NAME))) {
                return attr.getString(ACCT_ATTRIBUTES_CONTENT);
            }
        }
        throw new Exception("No zimbraId in attributes");
    }

    private void addAlias(String zimbraId, Handler<AsyncResult<JsonObject>> handler) {
        SoapRequest addAliasRequest = SoapRequest.AdminSoapRequest(ADD_ALIAS_REQUEST);
        addAliasRequest.setContent(new JsonObject()
                .put(ACCT_ID, zimbraId)
                .put(ACCT_ALIAS, getUserStrAddress()));
        addAliasRequest.start(handler);
    }

    private void updateUser(String zimbraId, Handler<AsyncResult<JsonObject>> handler) {
        SoapRequest modifyAccountRequest = SoapRequest.AdminSoapRequest(MODIFY_ACCOUNT_REQUEST);
        modifyAccountRequest.setContent(
                new JsonObject()
                        .put(ATTR_LIST, getSoapData())
                        .put(ACCT_ID, zimbraId));
        modifyAccountRequest.start(handler);
    }

    private void deleteUser(String zimbraId, Handler<AsyncResult<JsonObject>> handler) {
        SoapRequest modifyAccountRequest = SoapRequest.AdminSoapRequest(MODIFY_ACCOUNT_REQUEST);
        modifyAccountRequest.setContent(
                new JsonObject()
                        .put(ATTR_LIST, getDeleteSoapData())
                        .put(ACCT_ID, zimbraId));
        modifyAccountRequest.start(handler);
    }

    private void updateDatabase(AsyncResult<JsonObject> result, Handler<AsyncResult<JsonObject>> handler) {
        ServiceManager sm = ServiceManager.getServiceManager();
        SqlSynchroService sqlSynchroService = sm.getSqlSynchroService();
        String status = STATUS_DONE;
        String logs = "";
        if(result.failed()) {
            status = STATUS_ERROR;
            logs = result.cause().getMessage();
        }
        if(idRowBdd == 0) {
            handler.handle(result);
        } else {
            sqlSynchroService.updateSynchroUser(idRowBdd, status, logs, bddres -> {
                if(bddres.failed()) {
                    log.error("Update in bdd failed : " + bddres.cause().getMessage());
                }
                handler.handle(result);
            });
        }
    }
}
