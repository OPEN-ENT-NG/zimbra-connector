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

package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.helper.JsonHelper;
import fr.openent.zimbra.model.MailAddress;
import fr.openent.zimbra.model.ZimbraUser;
import fr.openent.zimbra.model.constant.FrontConstants;
import fr.openent.zimbra.model.soap.SoapRequest;
import fr.openent.zimbra.model.constant.SoapConstants;
import fr.openent.zimbra.model.constant.ZimbraConstants;
import fr.openent.zimbra.service.DbMailService;
import fr.openent.zimbra.service.data.Neo4jZimbraService;
import fr.openent.zimbra.service.data.SoapZimbraService;
import fr.openent.zimbra.service.data.SqlDbMailService;
import fr.openent.zimbra.service.synchro.SynchroUserService;
import fr.wseduc.webutils.Either;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static fr.openent.zimbra.service.data.Neo4jZimbraService.*;

public class UserService {

    private SoapZimbraService soapService;
    private DbMailService dbMailService;
    private SynchroUserService synchroUserService;
    private Neo4jZimbraService neoService;
    private GroupService groupService;
    private static Logger log = LoggerFactory.getLogger(UserService.class);

    public UserService(SoapZimbraService soapService, SynchroUserService synchroUserService,
                       DbMailService dbMailService) {
        this.soapService = soapService;
        this.synchroUserService = synchroUserService;
        this.dbMailService = dbMailService;
        this.neoService = new Neo4jZimbraService();
        this.groupService = new GroupService(soapService, dbMailService, synchroUserService);
    }

    /**
     * Get used and max quota of user from Zimbra
     * @param user User infos
     * @param handler Result handler
     */
    public void getQuota(UserInfos user,
                         Handler<Either<String, JsonObject>> handler) {

        getUserInfo(user, response -> {
            if(response.isLeft()) {
                handler.handle(response);
            } else {
                processGetQuota(response.right().getValue(), handler);
            }
        });
    }


    /**
     * Process response from Zimbra API to get Quotas details of user logged in
     * In case of success, return a Json Object :
     * {
     * 	    "storage" : "quotaUsed"
     * 	    "quota" : "quotaTotalAllowed"
     * }
     * @param jsonResponse Zimbra API Response
     * @param handler Handler result
     */
    private void processGetQuota(JsonObject jsonResponse,
                                 Handler<Either<String,JsonObject>> handler) {

        if(jsonResponse.containsKey(UserInfoService.QUOTA)) {
            handler.handle(new Either.Right<>(jsonResponse.getJsonObject(UserInfoService.QUOTA)));
        } else {
            handler.handle(new Either.Left<>("Could not get Quota from GetInfoRequest"));
        }
    }

    /**
     * Get info from connected user
     * @param user Connected user
     * @param handler result handler
     */
    void getUserInfo(UserInfos user,
                             Handler<Either<String, JsonObject>> handler) {
        JsonObject getInfoRequest = new JsonObject()
                .put("name", "GetInfoRequest")
                .put("content", new JsonObject()
                        .put("_jsns", SoapConstants.NAMESPACE_ACCOUNT));

        soapService.callUserSoapAPI(getInfoRequest, user, response -> {
            if(response.isLeft()) {
                handler.handle(response);
            } else {
                processGetUserInfo(response.right().getValue(), handler);
            }
        });
    }

    /**
     * Process response from Zimbra API to get Info of user logged in
     * Return Json :
     * {
     *     "quota" : {quota_infos}, // see UserInfoService.processQuota
     *     "alias" : {alias_infos}  // see UserInfoService.processAliases
     * }
     * @param jsonResponse Zimbra API Response
     * @param handler result handler
     */
    private void processGetUserInfo(JsonObject jsonResponse,
                                    Handler<Either<String, JsonObject>> handler) {

        JsonObject getInfoResp = jsonResponse.getJsonObject("Body")
              .getJsonObject("GetInfoResponse");
        JsonObject frontData = new JsonObject();

        UserInfoService.processQuota(getInfoResp, frontData);
        UserInfoService.processAliases(getInfoResp, frontData);
        UserInfoService.processSignaturePref(getInfoResp, frontData);

        dbMailService.updateUsers(new JsonArray().add(frontData.getJsonObject(UserInfoService.ALIAS)),
                                sqlResponse -> {
            if(sqlResponse.isLeft()) {
                log.error("Error when updating Zimbra users : " + sqlResponse.left().getValue());
            }
        });

        handler.handle(new Either.Right<>(frontData));
    }

    /**
     * Process response from Zimbra API to get alias details of specified user
     * In case of success, return a Json Object :
     * {
     * 	    "name" : "user mail address"
     * 	    "aliases" :
     * 	    [
     * 	        "alias"
     * 	    ]
     * }
     * @param mail mail identifier for Zimbra ccount
     * @param handler Handler result
     */
    public void getAliases(String mail, Handler<AsyncResult<JsonObject>> handler) {

        ZimbraUser user;
        try {
            user = new ZimbraUser(new MailAddress(mail));
        } catch (IllegalArgumentException e) {
            log.warn("Badly formatted email : " + mail);
            log.debug(e);
            handler.handle(Future.failedFuture("Badly formatted email : " + mail));
            return;
        }
        user.checkIfExists(v -> {
           if(user.existsInZimbra()) {
               List<String> aliases = user.getAliases();
               if(aliases.isEmpty()) {
                   handler.handle(Future.failedFuture("No Matching Zimbra Alias for : " + mail));
               } else {
                   if(aliases.size() > 1) {
                       log.warn("More than one alias for : " + mail);
                   }
                   JsonObject result = new JsonObject()
                           .put("name", user.getName())
                           .put("aliases", new JsonArray(aliases));
                   handler.handle(Future.succeededFuture(result));
               }
           } else {
               handler.handle(Future.failedFuture("No Matching Zimbra Account for : " + mail));
           }
        });
    }

    /**
     * Process response from Zimbra API to get alias details of specified user
     * In case of success, return a Json Object :
     * {
     * 	    "name" : "user mail address"
     * 	    "aliases" :
     * 	    [
     * 	        "alias"
     * 	    ]
     * }
     * @param jsonResponse Zimbra API Response
     * @param handler Handler result
     */
    private void processGetAliases(JsonObject jsonResponse,
                                 Handler<Either<String,JsonObject>> handler) {

        if(jsonResponse.containsKey(UserInfoService.ALIAS)) {
            handler.handle(new Either.Right<>(jsonResponse.getJsonObject(UserInfoService.ALIAS)));
        } else {
            handler.handle(new Either.Left<>("Could not get Quota from GetInfoRequest"));
        }
    }

    /**
     * Process response from Zimbra API to get email address of specified user
     * In case of success, return a String with the address
     * @param jsonResponse Zimbra API Response
     * @param handler Handler result
     */
    private void processGetAddress(JsonObject jsonResponse,
                                   Handler<AsyncResult<String>> handler) {

        if(jsonResponse.containsKey(UserInfoService.ALIAS)) {
            handler.handle(Future.succeededFuture(jsonResponse.getJsonObject(UserInfoService.ALIAS).getString("name")));
        } else {
            handler.handle(Future.failedFuture("Could not get Quota from GetInfoRequest"));
        }
    }

    /**
     * Get account info from specified user from Zimbra
     * @param account Zimbra account name or alias
     * @param handler result handler
     */
    public void getUserAccount(String account,
                               Handler<AsyncResult<JsonObject>> handler) {

        JsonObject acct = new JsonObject()
                .put(SoapConstants.ID_BY, ZimbraConstants.ACCT_NAME)
                .put(SoapConstants.ATTR_VALUE, account);

        SoapRequest getAccountRequest = SoapRequest.AdminSoapRequest(SoapConstants.GET_ACCOUNT_REQUEST);
        getAccountRequest.setContent(new JsonObject().put(SoapConstants.ACCOUNT, acct));
        getAccountRequest.start(handler);
    }

    /**
     * Get a user account by its neo id
     * @param userId Neo4j id of the account to get
     * @param handler result handler
     */
    public void getUserAccountById(String userId,
                                   Handler<AsyncResult<JsonObject>> handler) {
        if(userId == null || userId.isEmpty()) {
            handler.handle(Future.failedFuture("Empty userId, can't get account"));
            return;
        }
        String account = userId + "@" + Zimbra.domain;
        getUserAccount(account, handler);
    }

    /**
     * Process response from Zimbra API to get Info of specified user
     * Return Json :
     * {
     *     "quota" : {quota_infos}, // see UserInfoService.processQuota
     *     "alias" : {alias_infos}  // see UserInfoService.processAliases
     * }
     * @param jsonResponse Zimbra API Responsek
     * @param handler result handler
     */
    private void processGetAccountInfo(JsonObject jsonResponse, Handler<JsonObject> handler) {

        JsonObject getInfoResp = jsonResponse.getJsonObject(SoapConstants.BODY)
                .getJsonObject(SoapConstants.GET_ACCOUNT_RESPONSE);
        JsonObject frontData = new JsonObject();

        UserInfoService.processAccountInfo(getInfoResp, frontData);

        dbMailService.updateUsers(new JsonArray().add(frontData.getJsonObject(UserInfoService.ALIAS)),
                sqlResponse -> {
                    if(sqlResponse.isLeft()) {
                        log.error("Error when updating Zimbra users : " + sqlResponse.left().getValue());
                    }
                });

        handler.handle(frontData);
    }

    /**
     * Get configuration for mail client for a specific user
     * @param userId id of the user
     * @param handler final handler
     */
    public void getMailConfig(String userId, Handler<AsyncResult<JsonObject>> handler) {
        JsonObject mailConfig = Zimbra.appConfig.getMailConfig();
        if(!mailConfig.containsKey(FrontConstants.MAILCONFIG_IMAP)
                || !mailConfig.containsKey(FrontConstants.MAILCONFIG_SMTP)) {
            handler.handle(Future.failedFuture("No mail configuration"));
            return;
        }
        getUserAddress(userId, res -> {
            if(res.failed() || res.result().isEmpty()) {
                handler.handle(Future.failedFuture("No user address"));
                log.error("No user adress for user " + userId);
            } else {
                mailConfig.put(FrontConstants.MAILCONFIG_LOGIN, res.result());
                handler.handle(Future.succeededFuture(mailConfig));
            }
        });
    }

    /**
     * Get a user adress
     * First query database
     * If not present, query Zimbra
     * If not existing in Zimbra, try to create it
     * @param userId User Id
     * @param handler result handler
     */
    private void getUserAddress(String userId, Handler<AsyncResult<String>> handler) {
        dbMailService.getUserMailFromId(userId, result -> {
            if(result.isLeft() || result.right().getValue().isEmpty()) {
                log.debug("no user in database for id : " + userId);
                String account = userId + "@" + Zimbra.domain;
                getUserAccount(account, response -> {
                    if(response.failed()) {
                        JsonObject callResult = new JsonObject(response.cause().getMessage());
                        if(ZimbraConstants.ERROR_NOSUCHACCOUNT
                                .equals(callResult.getString(SoapZimbraService.ERROR_CODE, ""))) {
                            synchroUserService.exportUser(userId, resultSync -> {
                                if (resultSync.failed()) {
                                    handler.handle(Future.failedFuture(resultSync.cause()));
                                } else {
                                    getUserAddress(userId, handler);
                                }
                            });
                        } else {
                            handler.handle(Future.failedFuture(response.cause()));
                        }
                    } else {
                        processGetAccountInfo(response.result(), resInfo ->
                                processGetAddress(resInfo, handler)
                        );
                    }
                });
            } else {
                JsonArray results = result.right().getValue();
                if(results.size() > 1) {
                    log.warn("More than one address for user id : " + userId);
                }
                String mail = results.getJsonObject(0).getString(SqlDbMailService.ZIMBRA_NAME);
                handler.handle(Future.succeededFuture(mail));
            }
        });
    }

    /**
     * Get addresses for a list of users or groupes
     * Return a Json Object :
     * {
     *     "userId1" : "userAddress1",
     *     "groupId1" : "groupAddress1",
     *     "userId2" : "userAddress2",
     *     ...
     * }
     * @param idList Array with the list of Ids
     * @param handler result handler
     */
    public void getMailAddresses(JsonArray idList, Handler<JsonObject> handler) {
        if(idList == null || idList.isEmpty()) {
            handler.handle(new JsonObject());
            return;
        }
        List<String> idStrList = new ArrayList<>();
        List<String> emailList = new ArrayList<>();
        try {
            idStrList = JsonHelper.getStringList(idList);
        } catch (IllegalArgumentException e) {
            handler.handle(new JsonObject());
            log.error("idList is not a String list");
            return;
        }
        for(String idStr : idStrList) {
            try {
                new MailAddress(idStr);
                emailList.add(idStr);
            } catch (Exception ignored) {}
        }

        neoService.getIdsType(idList, neoResult -> {
            if(neoResult.isLeft()) {
                handler.handle(new JsonObject());
                log.error("Could not get recipient ids from Neo4j");
                return;
            }
            JsonArray idListWithTypes = neoResult.right().getValue();
            for(String mail : emailList) {
                idListWithTypes.add(new JsonObject().put("id",mail).put("type", TYPE_EXTERNAL));
            }
            final AtomicInteger processedIds = new AtomicInteger(idListWithTypes.size());
            JsonObject addressList = new JsonObject();
            for(Object o : idListWithTypes) {
                if(!(o instanceof JsonObject)) continue;
                JsonObject idInfos = (JsonObject)o;
                JsonObject elemInfos = new JsonObject();
                if(!idInfos.getString("displayName", "").isEmpty()) {
                    elemInfos.put("displayName", idInfos.getString("displayName"));
                }
                String elemId = idInfos.getString("id");
                switch (idInfos.getString("type", "")) {
                    case TYPE_USER:
                        getUserAddress(elemId, result -> {
                            if(result.succeeded()) {
                                elemInfos.put("email", result.result());
                                addressList.put(elemId, elemInfos);
                            }
                            if(processedIds.decrementAndGet() == 0) {
                                handler.handle(addressList);
                            }
                        });
                        break;
                    case TYPE_GROUP:
                        groupService.getGroupAddress(elemId, result -> {
                            if(result.isRight()) {
                                elemInfos.put("email", result.right().getValue());
                                elemInfos.put("displayName",
                                        UserUtils.groupDisplayName(
                                                idInfos.getString("groupName", ""),
                                                idInfos.getString("displayName"),
                                                Zimbra.synchroLang));
                                addressList.put(elemId, elemInfos);
                            }
                            if(processedIds.decrementAndGet() == 0) {
                                handler.handle(addressList);
                            }
                        });
                        break;
                    case TYPE_EXTERNAL:
                        // todo get display name for external addresses
                        addressList.put(elemId, new JsonObject().put("email", elemId));
                        if(processedIds.decrementAndGet() == 0) {
                            handler.handle(addressList);
                        }
                        break;
                    default:
                        if(processedIds.decrementAndGet() == 0) {
                            handler.handle(addressList);
                        }
                }
            }
        });
    }

}
