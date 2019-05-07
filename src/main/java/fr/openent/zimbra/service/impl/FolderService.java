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

import fr.openent.zimbra.model.constant.FrontConstants;
import fr.openent.zimbra.model.constant.SoapConstants;
import fr.openent.zimbra.model.constant.ZimbraConstants;
import fr.openent.zimbra.service.data.SoapZimbraService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

public class FolderService {


    private SoapZimbraService soapService;

    public FolderService(SoapZimbraService soapService) {
        this.soapService = soapService;
    }

    /**
     * Count messages in a folder
     * @param folderId folder id or name where to count messages
     * @param unread Count only unread messages ?
     * @param user User infos
     * @param result Handler result
     */
    public void countMessages(String folderId, Boolean unread, UserInfos user,
                              Handler<Either<String, JsonObject>> result) {

        folderId = getZimbraFolderId(folderId);

        JsonObject folderReq = new JsonObject()
                .put("view", "messages")
                .put("l", folderId);

        JsonObject getFolderRequest = new JsonObject()
                .put("name", "GetFolderRequest")
                .put("content", new JsonObject()
                        .put("depth", 0)
                        .put("folder", folderReq)
                        .put("_jsns", SoapConstants.NAMESPACE_MAIL));

        soapService.callUserSoapAPI(getFolderRequest, user, response -> {
            if(response.isLeft()) {
                result.handle(response);
            } else {
               processCountMessages(unread, response.right().getValue(), result);
            }
        });
    }

    /**
     * Process response from Zimbra API to count messages in folder
     * Json returned :
     * {
     *    data: count // number of (unread) messages
     * }
     * Zimbra API documentation for GetFolder :
     * https://files.zimbra.com/docs/soap_api/8.7.11/api-reference/zimbraMail/GetFolder.html
     * @param unread filter only unread messages.
     * @param jsonResponse Zimbra API Response
     * @param result Handler result
     */
    private void processCountMessages(Boolean unread, JsonObject jsonResponse,
                                      Handler<Either<String, JsonObject>> result) {
        try {
            JsonObject folder = jsonResponse.getJsonObject("Body")
                    .getJsonObject("GetFolderResponse")
                    .getJsonArray("folder").getJsonObject(0);

            Integer nbMsg;
            if(unread != null && unread) {
                if(folder.containsKey(ZimbraConstants.GETFOLDER_UNREAD)) {
                    nbMsg = folder.getInteger(ZimbraConstants.GETFOLDER_UNREAD);
                } else {
                    nbMsg = 0;
                }
            } else {
                nbMsg = folder.getInteger(ZimbraConstants.GETFOLDER_NBMSG);
            }

            JsonObject finalResponse = new JsonObject()
                    .put("count", nbMsg);

            result.handle(new Either.Right<>(finalResponse));

        } catch (NullPointerException e) {
            result.handle(new Either.Left<>("Error when reading response"));
        }
    }

    /**
     * List folders at root level, under one specifique folder or trashed, depending on parameters
     * Only get depth 1 folders
     * @param parentId [optional] id of parent folder
     * @param trashed [optional] only depth 1 trashed folders
     * @param user User infos
     * @param handler Result handler
     */
    public void listFolders(final String parentId, Boolean trashed, UserInfos user,
                            Handler<Either<String,JsonArray>> handler) {
        JsonObject folderReq = new JsonObject()
                .put("view", "messages");
        if(trashed) {
            folderReq.put("l", ZimbraConstants.FOLDER_TRASH_ID);
        } else if(parentId == null) {
            folderReq.put("l", ZimbraConstants.FOLDER_INBOX_ID);
        } else {
            folderReq.put("l", getZimbraFolderId(parentId));
        }

        JsonObject getFolderRequest = new JsonObject()
                .put("name", "GetFolderRequest")
                .put("content", new JsonObject()
                        .put("depth", 1)
                        .put("folder", folderReq)
                        .put("_jsns", SoapConstants.NAMESPACE_MAIL));

        soapService.callUserSoapAPI(getFolderRequest, user, response -> {
            if(response.isLeft()) {
                handler.handle(new Either.Left<>(response.left().getValue()));
            } else {
                processListFolders(response.right().getValue(), trashed, user, parentId, handler);
            }
        });
    }

    /**
     * Process response from Zimbra API to listMessages subfolders in folder
     * In case of success, return Json Array of folders :
     * [
     * 	{
     * 	    "id" : "folder-id",
     * 	    "parent_id : "parent-folder-id" or null,
     * 	    "user_id" : "id of owner of folder",
     * 	    "name" : "folder-name",
     * 	    "depth" : "folder-depth",
     * 	    "trashed" : "is-folder-trashed"
     * 	}
     * ]
     * @param jsonResponse Zimbra API Response
     * @param trashed [Optional] Get subfolders from Trash
     * @param user User infos
     * @param parentId [optional] ID of parent Folder from Front
     * @param handler Handler result
     */
    private void processListFolders(JsonObject jsonResponse, Boolean trashed, UserInfos user,
                                    String parentId, Handler<Either<String,JsonArray>> handler) {
        try {
            JsonArray sourceFolders = jsonResponse.getJsonObject("Body")
                    .getJsonObject("GetFolderResponse")
                    .getJsonArray("folder").getJsonObject(0)
                    .getJsonArray("folder");

            JsonArray resultArray = new JsonArray();

            if(sourceFolders == null) {
                sourceFolders = new JsonArray();
            }
            for(Object o : sourceFolders)  {
                if( !(o instanceof JsonObject) ) continue;
                JsonObject sFolder = (JsonObject)o;
                JsonObject resultFolder = new JsonObject();

                resultFolder.put("parent_id", parentId);
                resultFolder.put("user_id", user.getUserId());
                resultFolder.put("trashed", (trashed != null));
                // todo process depth ?
                resultFolder.put("depth", 1);
                resultFolder.put("id", sFolder.getString("id"));
                resultFolder.put("name", sFolder.getString("name"));

                resultArray.add(resultFolder);
            }
            handler.handle(new Either.Right<>(resultArray));
        } catch (NullPointerException e) {
            handler.handle(new Either.Left<>("Error when reading response"));
        }
    }


    /**
     * Create folder
     * Process response from Zimbra API to listMessages subfolders in folder
     * In case of success, return an empty Json Array.
     * @param parentId Id of parent folder
     * @param newFolderName New Folder Name
     * @param user User infos
     * @param handler Empty JsonObject returned, no process needed
     */
    public void createFolder(String newFolderName, String parentId, UserInfos user,
                             Handler<Either<String, JsonObject>> handler) {

        if(parentId == null) {
            parentId = ZimbraConstants.FOLDER_INBOX_ID;
        }

        JsonObject actionReq = new JsonObject()
                .put("l", parentId)
                .put("name", newFolderName)
                .put("view", "message");

        JsonObject createFolderRequest = new JsonObject()
                .put("name", "CreateFolderRequest")
                .put("content", new JsonObject()
                        .put("folder", actionReq)
                        .put("_jsns", SoapConstants.NAMESPACE_MAIL));

        soapService.callUserSoapAPI(createFolderRequest, user, response -> {
            if(response.isLeft()) {
                handler.handle(new Either.Left<>(response.left().getValue()));
            } else {
                handler.handle(new Either.Right<>(new JsonObject()));
            }
        });
    }

    /**
     * Trash folder
     * @param folderId Id of folder
     * @param user User infos
     * @param handler Empty JsonObject returned, no process needed
     */
    public void trashFolder(String folderId, UserInfos user,
                            Handler<Either<String,JsonObject>> handler) {
        JsonObject actionReq = new JsonObject()
                .put("id", folderId)
                .put("op", ZimbraConstants.OP_TRASH);

        JsonObject folderActionRequest = new JsonObject()
                .put("name", "FolderActionRequest")
                .put("content", new JsonObject()
                    .put("action", actionReq)
                    .put("_jsns", SoapConstants.NAMESPACE_MAIL));

        soapService.callUserSoapAPI(folderActionRequest, user, response -> {
            if(response.isLeft()) {
                handler.handle(new Either.Left<>(response.left().getValue()));
            } else {
                handler.handle(new Either.Right<>(new JsonObject()));
            }
        });
    }

    /**
     * Update folder
     * @param folderId Folder id
     * @param name New folder Name
     * @param user User infos
     * @param handler Empty JsonObject returned, no process needed
     */
    public void updateFolder(String folderId, String name, UserInfos user,
                             Handler<Either<String,JsonObject>> handler) {
        JsonObject actionReq = new JsonObject()
                .put("id", folderId)
                .put("name", name)
                .put("op", ZimbraConstants.OP_RENAME);

        JsonObject folderActionRequest = new JsonObject()
                .put("name", "FolderActionRequest")
                .put("content", new JsonObject()
                        .put("action", actionReq)
                        .put("_jsns", SoapConstants.NAMESPACE_MAIL));

        soapService.callUserSoapAPI(folderActionRequest, user, response -> {
            if(response.isLeft()) {
                handler.handle(new Either.Left<>(response.left().getValue()));
            } else {
                handler.handle(new Either.Right<>(new JsonObject()));
            }
        });
    }

    /**
     * Restore folder in Inbox
     * @param folderId Folder id
     * @param user User infos
     * @param handler Empty JsonObject returned, no process needed
     */
    public void restoreFolder(String folderId, UserInfos user,
                             Handler<Either<String,JsonObject>> handler) {
        JsonObject actionReq = new JsonObject()
                .put("id", folderId)
                .put("l", ZimbraConstants.FOLDER_INBOX_ID)
                .put("op", ZimbraConstants.OP_MOVE);

        JsonObject folderActionRequest = new JsonObject()
                .put("name", "FolderActionRequest")
                .put("content", new JsonObject()
                        .put("action", actionReq)
                        .put("_jsns", SoapConstants.NAMESPACE_MAIL));

        soapService.callUserSoapAPI(folderActionRequest, user, response -> {
            if(response.isLeft()) {
                handler.handle(new Either.Left<>(response.left().getValue()));
            } else {
                handler.handle(new Either.Right<>(new JsonObject()));
            }
        });
    }


    /**
     * Delete folder
     * @param folderId Id of folder
     * @param user User infos
     * @param handler Empty JsonObject returned, no process needed
     */
    public void deleteFolder(String folderId, UserInfos user,
                            Handler<Either<String,JsonObject>> handler) {
        JsonObject actionReq = new JsonObject()
                .put("id", folderId)
                .put("op", ZimbraConstants.OP_DELETE);

        JsonObject folderActionRequest = new JsonObject()
                .put("name", "FolderActionRequest")
                .put("content", new JsonObject()
                        .put("action", actionReq)
                        .put("_jsns", SoapConstants.NAMESPACE_MAIL));

        soapService.callUserSoapAPI(folderActionRequest, user, response -> {
            if(response.isLeft()) {
                handler.handle(new Either.Left<>(response.left().getValue()));
            } else {
                handler.handle(new Either.Right<>(new JsonObject()));
            }
        });
    }

    /**
     * Get folder information from Zimbra
     * @param folderId folder id
     * @param user User infos
     * @param handler Result handler
     */
    void getFolderInfos(String folderId, UserInfos user, Handler<Either<String,JsonObject>> handler) {

        folderId = getZimbraFolderId(folderId);

        JsonObject folderReq = new JsonObject()
                .put("view", "messages")
                .put("l", folderId);

        JsonObject getFolderRequest = new JsonObject()
                .put("name", "GetFolderRequest")
                .put("content", new JsonObject()
                        .put("depth", 0)
                        .put("folder", folderReq)
                        .put("_jsns", SoapConstants.NAMESPACE_MAIL));

        soapService.callUserSoapAPI(getFolderRequest, user, handler);
    }

    String getZimbraFolderId(String frontFolderId) {
        String folderId = frontFolderId;
        // fixme front is not consistent with case
        switch (folderId.toUpperCase()) {
            case FrontConstants.FOLDER_INBOX :
                folderId = ZimbraConstants.FOLDER_INBOX_ID;
                break;
            case FrontConstants.FOLDER_OUTBOX :
                folderId = ZimbraConstants.FOLDER_OUTBOX_ID;
                break;
            case FrontConstants.FOLDER_DRAFT :
                folderId = ZimbraConstants.FOLDER_DRAFT_ID;
                break;
            case FrontConstants.FOLDER_TRASH :
                folderId = ZimbraConstants.FOLDER_TRASH_ID;
                break;
        }
        return folderId;
    }

    String getFrontFolderId(String folderId) {
        if(FrontConstants.FOLDER_INBOX.equals(folderId)
                || FrontConstants.FOLDER_DRAFT.equals(folderId)
                || FrontConstants.FOLDER_OUTBOX.equals(folderId)
                || FrontConstants.FOLDER_TRASH.equals(folderId)) {
            return null;
        } else {
            return folderId;
        }
    }
}
