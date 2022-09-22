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
import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.model.constant.SoapConstants;
import fr.openent.zimbra.model.constant.ZimbraConstants;
import fr.openent.zimbra.model.constant.ZimbraErrors;
import fr.openent.zimbra.service.DbMailService;
import fr.openent.zimbra.service.data.SoapZimbraService;
import fr.openent.zimbra.service.data.SqlDbMailService;
import fr.openent.zimbra.service.synchro.SynchroGroupService;
import fr.openent.zimbra.service.synchro.SynchroUserService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class GroupService {

    private SoapZimbraService soapService;
    private DbMailService dbMailService;
    private SynchroGroupService synchroGroupService;
    private static Logger log = LoggerFactory.getLogger(GroupService.class);

    public GroupService(SoapZimbraService soapService, DbMailService dbMailService, SynchroUserService synchroUserService) {
        this.soapService = soapService;
        this.dbMailService = dbMailService;
        this.synchroGroupService = new SynchroGroupService(soapService, synchroUserService);
    }

    /**
     * Get a group adress
     * First query database
     * If not present, query Zimbra
     * If not existing in Zimbra, try to create it
     * No need to translate group mail, final form is id@domain
     * @param groupId Group Id
     * @param handler result handler
     */
    void getGroupAddress(String groupId, Handler<Either<String,String>> handler) {
        dbMailService.getGroupMailFromId(groupId, result -> {
            if(result.isLeft() || result.right().getValue().isEmpty()) {
                log.debug("no group in database for id : " + groupId);
                String groupAddress = groupId + "@" + Zimbra.domain;
                getGroupAccount(groupAddress, response -> {
                    if(response.isLeft()) {
                        JsonObject callResult = new JsonObject(response.left().getValue());
                        if(ZimbraErrors.ERROR_NOSUCHDLIST
                                .equals(callResult.getString(SoapZimbraService.ERROR_CODE, ""))) {
                            synchroGroupService.exportGroup(groupId, resultSync -> {
                                if (resultSync.isLeft()) {
                                    handler.handle(new Either.Left<>(resultSync.left().getValue()));
                                } else {
                                    getGroupAddress(groupId, handler);
                                }
                            });
                        } else {
                            handler.handle(new Either.Left<>(response.left().getValue()));
                        }
                    } else {
                        handler.handle(new Either.Right<>(groupAddress));
                    }
                });
            } else {
                JsonArray results = result.right().getValue();
                if(results.size() > 1) {
                    log.warn("More than one address for user id : " + groupId);
                }
                String mail = results.getJsonObject(0).getString(SqlDbMailService.ZIMBRA_NAME);
                handler.handle(new Either.Right<>(mail));
            }
        });
    }

    /**
     * Get account info from specified group from Zimbra
     * @param account Zimbra account name
     * @param handler result handler
     */
    private void getGroupAccount(String account,
                        Handler<Either<String, JsonObject>> handler) {

        JsonObject acct = new JsonObject()
                .put("by", Field.NAME)
                .put("_content", account);

        JsonObject getInfoRequest = new JsonObject()
                .put(Field.NAME, "GetDistributionListRequest")
                .put("content", new JsonObject()
                        .put("_jsns", SoapConstants.NAMESPACE_ADMIN)
                        .put("limit", 1)
                        .put(ZimbraConstants.DISTRIBUTION_LIST, acct));

        soapService.callAdminSoapAPI(getInfoRequest, handler);
    }

    /**
     * Get the id part of an group email address
     * @param email address of the group
     * @return id part if it's a group address, null otherwise
     */
    public String getGroupId(String email) {
        if(email.matches("[0-9a-z-]*@" + Zimbra.domain)) {
            return email.substring(0, email.indexOf("@"));
        }
        else {
            return null;
        }
    }
}
