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
import fr.openent.zimbra.helper.AsyncHelper;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.model.Group;
import fr.openent.zimbra.model.MailAddress;
import fr.openent.zimbra.model.constant.SoapConstants;
import fr.openent.zimbra.model.constant.ZimbraConstants;
import fr.openent.zimbra.model.constant.ZimbraErrors;
import fr.openent.zimbra.model.soap.SoapError;
import fr.openent.zimbra.model.soap.SoapRequest;
import fr.openent.zimbra.service.DbMailService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.user.UserUtils;

import static fr.openent.zimbra.model.constant.SoapConstants.*;

/**
 * Group class used for synchronisation with Zimbra
 */
class SynchroGroup extends Group {

    private MailAddress ddlAddress;
    private String zimbraID = "";

    private DbMailService dbMailService;

    private static final String MEMBER_URL_TPL = "ldap:///??sub?(&(objectClass=zimbraAccount)(|(ou=%s)(ou=allgroupsaccount)))";
    private static Logger log = LoggerFactory.getLogger(SynchroGroup.class);


    SynchroGroup(String groupId) throws IllegalArgumentException {
        super(groupId);
        init();
    }


    /**
     * Synchronize Group in zimbra :
     *   - Get data from Neo
     *   - Create group in zimbra if it does not exists
     *   - Else update it
     * @param handler final handler. Contains zimbra data for updated group if successful
     */
    @SuppressWarnings("WeakerAccess")
    public void synchronize(Handler<AsyncResult<JsonObject>> handler) {
        fetchDataFromNeo( res -> {
            if(res.failed()) {
                handler.handle(Future.failedFuture(res.cause()));
            } else {
                createOrUpdate(handler);
            }
        });
    }


    private void init() {
        ddlAddress = new MailAddress(getId(), Zimbra.domain);
        ServiceManager sm = ServiceManager.getServiceManager();
        dbMailService = sm.getDbMailServiceSync();
    }


    private void checkIfExists(Handler<AsyncResult<String>> handler) {
        getZimbraInfos(result -> {
            if(result.succeeded()) {
                getGroupIdFromZimbraResponse(result.result(), handler);
            } else {
                String errorStr = result.cause().getMessage();
                try {
                    SoapError error = new SoapError(errorStr);
                    if(ZimbraErrors.ERROR_NOSUCHDLIST.equals(error.getCode())) {
                        handler.handle(Future.succeededFuture(EMPTY_VALUE));
                    } else {
                        handler.handle(Future.failedFuture(errorStr));
                    }
                } catch (DecodeException e) {
                    log.error("Unknown error when trying to fetch group info : " + errorStr);
                    handler.handle(Future.failedFuture("Unknown Zimbra error"));
                }
            }
        });
    }


    private void getGroupIdFromZimbraResponse(JsonObject zimbraResponse, Handler<AsyncResult<String>> handler) {
        try {
            JsonArray dlList = zimbraResponse
                    .getJsonObject(BODY)
                    .getJsonObject(GET_DISTRIBUTIONLIST_RESPONSE)
                    .getJsonArray(ZimbraConstants.DISTRIBUTION_LIST);
            if(dlList.size() > 1) {
                log.error("More than one distribution list with name " + getId());
            }
            zimbraID = dlList.getJsonObject(0).getString(ZimbraConstants.DLIST_ID, "");
            if(zimbraID.isEmpty()) {
                handler.handle(Future.succeededFuture(EMPTY_VALUE));
            } else {
                handler.handle(Future.succeededFuture(zimbraID));
            }
        } catch (Exception e) {
            handler.handle(Future.failedFuture(e));
            log.error("Error when reading getDistributionListResponse : " + e);
        }
    }


    private void createOrUpdate(Handler<AsyncResult<JsonObject>> handler) {
        Future<JsonObject> startFuture = AsyncHelper.getJsonObjectFinalFuture(handler);

        Future<String> chechedExistence = Future.future();
        checkIfExists(chechedExistence.completer());

        chechedExistence.compose( res -> {
            Future<JsonObject> zimbraUpdated = Future.future();
            if(!res.isEmpty()) {
                updateInZimbra(zimbraUpdated.completer());
            } else {
                createInZimbra(zimbraUpdated.completer());
            }
            return zimbraUpdated;
        }).compose( updatedRes ->
            dbMailService.updateGroup(getId(), ddlAddress.toString(), startFuture.completer())
        , startFuture);
    }


    private void updateInZimbra(Handler<AsyncResult<JsonObject>> handler) {
        SoapRequest modifyDistributionListRequest = SoapRequest.AdminSoapRequest(MODIFY_DISTRIBUTIONLIST_REQUEST);

        modifyDistributionListRequest.setContent(new JsonObject()
                .put(ZimbraConstants.DLIST_ID, zimbraID)
                .put(ATTR_LIST, getSoapData(false)));

        modifyDistributionListRequest.start(handler);
    }


    private JsonArray getSoapData(boolean isCreation) {
        String displayName = UserUtils.groupDisplayName(
                getGroupName(),
                getGroupDisplayName(),
                Zimbra.synchroLang);

        String memberUrl = String.format(MEMBER_URL_TPL, getId());

        JsonArray result =  new JsonArray()
                .add(new JsonObject()
                        .put(ATTR_NAME, ZimbraConstants.DLIST_DISPLAYNAME)
                        .put(ATTR_VALUE, displayName))
                .add(new JsonObject()
                        .put(ATTR_NAME, ZimbraConstants.DLIST_MEMBER_URL)
                        .put(ATTR_VALUE, memberUrl));
        if(isCreation) {
                result.add(new JsonObject()
                    .put(ATTR_NAME, ZimbraConstants.DLIST_IS_ACL_GROUP)
                    .put(ATTR_VALUE, FALSE_VALUE));
        }
        return result;
    }


    private void createInZimbra(Handler<AsyncResult<JsonObject>> handler) {
        SoapRequest createDistributionListRequest = SoapRequest.AdminSoapRequest(CREATE_DISTRIBUTIONLIST_REQUEST);

        createDistributionListRequest.setContent(new JsonObject()
                .put(ZimbraConstants.ACCT_NAME, ddlAddress.toString())
                .put(ZimbraConstants.DLIST_DYNAMIC, 1)
                .put(ATTR_LIST, getSoapData(true)));

        createDistributionListRequest.start(handler);
    }


    private void getZimbraInfos(Handler<AsyncResult<JsonObject>> handler) {
        SoapRequest getDListRequest = SoapRequest.AdminSoapRequest(SoapConstants.GET_DISTRIBUTIONLIST_REQUEST);

        JsonObject grpData = new JsonObject()
                .put(SoapConstants.ID_BY, ZimbraConstants.ACCT_NAME)
                .put(SoapConstants.ATTR_VALUE, ddlAddress.toString());

        getDListRequest.setContent(new JsonObject()
                .put(ZimbraConstants.DLIST_LIMIT_MEMBERS, 1)
                .put(ZimbraConstants.DISTRIBUTION_LIST, grpData));
        getDListRequest.start(handler);
    }
}
