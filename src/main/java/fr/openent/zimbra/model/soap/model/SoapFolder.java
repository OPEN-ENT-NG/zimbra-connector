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

package fr.openent.zimbra.model.soap.model;

import fr.openent.zimbra.model.constant.SoapConstants;
import fr.openent.zimbra.model.soap.SoapRequest;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;


import static fr.openent.zimbra.model.constant.SoapConstants.*;
import static fr.openent.zimbra.model.constant.ZimbraConstants.*;
import static fr.openent.zimbra.model.constant.ZimbraErrors.*;
import static fr.openent.zimbra.service.data.SoapZimbraService.*;

public class SoapFolder {

    private String id;
    private String uuid;
    private String name;
    private String absolutePath;
    private String parentId;
    private String parentUuid;
    private String flags;
    private Integer color;
    private String rgb;
    private Integer nbUnread;
    private String view;
    private Long modifiedDate;
    private Integer nbItems;
    private Integer sizeItems;
    private JsonArray subFolders;


    private static Logger log = LoggerFactory.getLogger(SoapFolder.class);

    public String getId() { return id; }


    public static void createFolderByPath(String userId, String folderPath, String view,
                                          Handler<AsyncResult<SoapFolder>> handler) {
        createFolderByPath(userId, folderPath, view, false, handler);
    }

    public static void getOrCreateFolderByPath(String userId, String folderPath, String view,
                                               Handler<AsyncResult<SoapFolder>> handler) {
        createFolderByPath(userId, folderPath, view, true, handler);
    }

    private static void createFolderByPath(String userId, String folderPath, String view, boolean getIfExists,
                                            Handler<AsyncResult<SoapFolder>> handler) {
        SoapRequest createFolderRequest = SoapRequest.MailSoapRequest(SoapConstants.CREATE_FOLDER_REQUEST, userId);
        JsonObject content = new JsonObject()
                .put(FOLDER, new JsonObject()
                        .put(VIEW, view)
                        .put(FETCH_IF_EXISTS, getIfExists ? 1 : 0)
                        .put(FOLDER_NAME, folderPath));
        createFolderRequest.setContent(content);
        createFolderRequest.start(processFolderHandler(CREATE_FOLDER_RESPONSE, handler));
    }

    public static void getFolderByPath(String userId, String folderPath, String view, int depth,
                                       Handler<AsyncResult<SoapFolder>> handler) {
        SoapRequest getFolderRequest = SoapRequest.MailSoapRequest(SoapConstants.GET_FOLDER_REQUEST, userId);
        JsonObject content = new JsonObject()
                .put(VIEW, view)
                .put(FOLDER_DEPTH, depth)
                .put(FOLDER, new JsonObject()
                        .put(FOLDER_PATH, folderPath));
        getFolderRequest.setContent(content);
        getFolderRequest.start(processFolderHandler(GET_FOLDER_RESPONSE, handler));
    }

    public void emptyFolder(String userId, Handler<AsyncResult<JsonObject>> handler) {
        SoapRequest actionRequest = SoapRequest.MailSoapRequest(SoapConstants.FOLDER_ACTION_REQUEST, userId);
        JsonObject content = new JsonObject()
                .put(ACTION, new JsonObject()
                        .put(RECURSIVE, ONE_TRUE)
                        .put(ZIMBRA_ID, id)
                        .put(OPERATION, OP_EMPTY));
        actionRequest.setContent(content);
        actionRequest.start(handler);
    }

    public void shareFolderReadonly(String userId, String grantee, Handler<AsyncResult<JsonObject>> handler) {
        SoapRequest actionRequest = SoapRequest.MailSoapRequest(SoapConstants.FOLDER_ACTION_REQUEST, userId);
        JsonObject content = new JsonObject()
                .put(ACTION, new JsonObject()
                        .put(OPERATION, OP_GRANT)
                        .put(ZIMBRA_ID, id)
                        .put(FOLDER_GRANT, new JsonObject()
                                .put(FOLDER_SHARE_PERM, GRANT_READ)
                                .put(SHARE_GRANTEE_TYPE, GRANTEE_TYPE_USER)
                                .put(GRANTEE_NAME, grantee)));
        actionRequest.setContent(content);
        actionRequest.start( reqRes -> {
            if(reqRes.failed()) {
                JsonObject error = new JsonObject(reqRes.cause().getMessage());
                String errorCode = error.getString(ERROR_CODE, "");
                if (ERROR_GRANT_EXISTS.equals(errorCode)) {
                    handler.handle(Future.succeededFuture(new JsonObject()));
                } else {
                    handler.handle(reqRes);
                }
            } else  {
                handler.handle(reqRes);
            }
        });
    }

    private static Handler<AsyncResult<JsonObject>> processFolderHandler(String respName,
                                                                 Handler<AsyncResult<SoapFolder>> handler) {
        return res -> {
            if(res.failed()) {
                handler.handle(Future.failedFuture(res.cause()));
            } else {
                JsonObject jsonResponse = res.result();
                try {
                    JsonArray folderList = jsonResponse.getJsonObject(BODY)
                            .getJsonObject(respName)
                            .getJsonArray(FOLDER, new JsonArray());
                    if(folderList.size() != 1) {
                        log.warn("Invalid number of folders : " + jsonResponse.toString());
                    }
                    JsonObject jsonFolder = folderList.getJsonObject(0);
                    SoapFolder folder = createFromJson(jsonFolder);
                    handler.handle(Future.succeededFuture(folder));
                } catch (Exception e) {
                    handler.handle(Future.failedFuture(e));
                }
            }
        };
    }

    private static SoapFolder createFromJson(JsonObject folderData) throws IllegalArgumentException {
        SoapFolder folder = new SoapFolder();
        folder.id = folderData.getString(ZIMBRA_ID, "");
        folder.uuid = folderData.getString(UUID, "");
        folder.name = folderData.getString(FOLDER_NAME, "");
        folder.absolutePath = folderData.getString(FOLDER_ABSPATH, "");
        if(folder.id.isEmpty() || folder.uuid.isEmpty() || folder.name.isEmpty() || folder.absolutePath.isEmpty()) {
            throw new IllegalArgumentException("Invalid folder data");
        }
        folder.parentId = folderData.getString(FOLDER_PARENTID, "");
        folder.parentUuid = folderData.getString(FOLDER_PARENTUUID, "");
        folder.flags = folderData.getString(FOLDER_FLAGS, "");
        folder.color = folderData.getInteger(FOLDER_COLOR, 0);
        folder.rgb = folderData.getString(FOLDER_RGB, "");
        folder.nbUnread = folderData.getInteger(FOLDER_NBUNREAD, 0);
        folder.view = folderData.getString(FOLDER_VIEW, "");
        folder.modifiedDate = folderData.getLong(FOLDER_MODIFIED_DATE);
        folder.nbItems = folderData.getInteger(FOLDER_NBITEMS, 0);
        folder.sizeItems = folderData.getInteger(FOLDER_SIZEITEMS, 0);
        folder.subFolders = folderData.getJsonArray(FOLDER, new JsonArray());
        return folder;
    }

}
