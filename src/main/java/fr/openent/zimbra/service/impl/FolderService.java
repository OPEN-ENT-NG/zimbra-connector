package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.helper.FrontConstants;
import fr.openent.zimbra.helper.ZimbraConstants;
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
        JsonObject folderReq = new JsonObject()
                .put("view", "messages");

        switch (folderId) {
            case FrontConstants.FOLDER_INBOX :
                folderReq.put("path", ZimbraConstants.FOLDER_INBOX);
                break;
            case FrontConstants.FOLDER_OUTBOX :
                folderReq.put("path", ZimbraConstants.FOLDER_INBOX);
                break;
            case FrontConstants.FOLDER_DRAFT :
                folderReq.put("path", ZimbraConstants.FOLDER_DRAFT);
                break;
            case FrontConstants.FOLDER_TRASH :
                folderReq.put("path", ZimbraConstants.FOLDER_TRASH);
                break;
            default:
                folderReq.put("uuid", folderId);
        }

        JsonObject getFolderRequest = new JsonObject()
                .put("name", "GetFolderRequest")
                .put("content", new JsonObject()
                        .put("depth", 0)
                        .put("folder", folderReq)
                        .put("_jsns", "urn:zimbraMail"));

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
                    .put("data", nbMsg);

            result.handle(new Either.Right<>(finalResponse));

        } catch (NullPointerException e) {
            result.handle(new Either.Left<>("Error when reading response"));
        }
    }

    /**
     * List folders at root level, under one specifique folder or trashed, depending on parameters
     * Only get depth 1 folders
     * @param parentId [optional] uuid of parent folder
     * @param trashed [optional] only depth 1 trashed folders
     * @param user User infos
     * @param handler Result handler
     */
    public void listFolders(String parentId, Boolean trashed, UserInfos user,
                            Handler<Either<String,JsonArray>> handler) {
        JsonObject folderReq = new JsonObject()
                .put("view", "messages");

        if(trashed) {
            folderReq.put("path", ZimbraConstants.FOLDER_TRASH);
        } else if(parentId == null) {
            folderReq.put("path", ZimbraConstants.FOLDER_INBOX);
        } else {
            folderReq.put("uuid", parentId);
        }

        JsonObject getFolderRequest = new JsonObject()
                .put("name", "GetFolderRequest")
                .put("content", new JsonObject()
                        .put("depth", 1)
                        .put("folder", folderReq)
                        .put("_jsns", "urn:zimbraMail"));

        soapService.callUserSoapAPI(getFolderRequest, user, response -> {
            if(response.isLeft()) {
                handler.handle(new Either.Left<>(response.left().getValue()));
            } else {
                processListFolders(response.right().getValue(), trashed, user, parentId, handler);
            }
        });
    }

    /**
     * Process response from Zimbra API to list subfolders in folder
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

            for(Object o : sourceFolders)  {
                if( !(o instanceof JsonObject) ) continue;
                JsonObject sFolder = (JsonObject)o;
                JsonObject resultFolder = new JsonObject();

                resultFolder.put("parent_id", parentId);
                resultFolder.put("user_id", user.getUserId());
                resultFolder.put("trashed", (trashed != null));
                // todo process depth ?
                resultFolder.put("depth", 1);
                resultFolder.put("id", sFolder.getString("uuid"));
                resultFolder.put("name", sFolder.getString("name"));

                resultArray.add(resultFolder);
            }
            handler.handle(new Either.Right<>(resultArray));
        } catch (NullPointerException e) {
            handler.handle(new Either.Left<>("Error when reading response"));
        }
    }
}
