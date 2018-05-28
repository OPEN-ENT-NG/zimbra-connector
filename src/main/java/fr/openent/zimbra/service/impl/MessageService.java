package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.helper.FrontConstants;
import fr.openent.zimbra.helper.ZimbraConstants;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

public class MessageService {

    private SoapZimbraService soapService;
    private FolderService folderService;

    public MessageService(SoapZimbraService soapService, FolderService folderService) {
        this.soapService = soapService;
        this.folderService = folderService;
    }

    /**
     * List messages in folders
     * If unread is true, filter only unread messages.
     * If search is set, must be at least 3 characters. Then filter by search.
     * @param folderId folder id where to listMessages messages
     * @param unread filter only unread messages ?
     * @param user user infos
     * @param page page used for pagination, default 0
     * @param searchText [optional] text used for search
     * @param result Handler results
     */
    public void listMessages(String folderId, Boolean unread, UserInfos user, int page,
                             final String searchText, Handler<Either<String, JsonArray>> result) {
        folderService.getFolderInfos(folderId, user, response -> {
            if(response.isLeft()) {
                result.handle(new Either.Left<>(response.left().getValue()));
            } else {
                JsonObject folderInfos = response.right().getValue();
                try {

                    JsonObject folder = folderInfos.getJsonObject("Body")
                            .getJsonObject("GetFolderResponse")
                            .getJsonArray("folder").getJsonObject(0);

                    String folderPath = folder.getString(ZimbraConstants.GETFOLDER_FOLDERPATH);

                    JsonObject searchReq = new JsonObject()
                            .put("query", pathToQuery(folderPath))
                            .put("types", "message")
                            .put("_jsns", "urn:zimbraMail");

                    JsonObject searchRequest = new JsonObject()
                            .put("name", "SearchRequest")
                            .put("content", searchReq);

                    soapService.callUserSoapAPI(searchRequest, user, searchResult -> {
                        if(searchResult.isLeft()) {
                            result.handle(new Either.Left<>(searchResult.left().getValue()));
                        } else {
                            processListMessages(searchResult.right().getValue(), result);
                        }
                    });

                } catch (NullPointerException e ) {
                    result.handle(new Either.Left<>("Error when reading response for folder"));
                }
            }
        });
    }

    private void processListMessages(JsonObject zimbraResponse, Handler<Either<String, JsonArray>> result)  {
        JsonArray zimbraMessages;
        Integer msgCount;
        try {
            zimbraMessages = zimbraResponse.getJsonObject("Body")
                                .getJsonObject("SearchResponse")
                                .getJsonArray(ZimbraConstants.SEARCH_MESSAGES);
            msgCount = zimbraMessages.size();
        } catch (NullPointerException e) {
            result.handle(new Either.Left<>("Error when processing search result"));
            return;
        }

        JsonArray frontMessages = new JsonArray();
        processSearchResult(zimbraMessages, frontMessages, result);
    }

    /**
     * Process a zimbra searchResult and transform it to Front Message
     * Form of Front message returned :
     * {
     *     "id" : "message_id",
     *     "subject" : "message_subject",
     *     "from" : "user_id_from",
     *     "to" : [
     *      "user_id_to",
     *     ],
     *     "cc" : [
     *      "user_id_cc",
     *     ],
     *     "display_names" : [
     *      "user_id",
     *      "user_display_name",
     *     ],
     *     "date" : datesent, // format ??
     *     "unread" : boolean_unread
     *
     * }
     * @param zimbraMessages array of unprocessed messages from zimbra
     * @param frontMessages array of processed messages
     * @param result final handler
     */
    private void processSearchResult(JsonArray zimbraMessages, JsonArray frontMessages,
                                     Handler<Either<String, JsonArray>> result) {
        if(zimbraMessages.isEmpty()) {
            result.handle(new Either.Right<>(frontMessages));
            return;
        }
        JsonObject zimbraMsg;
        try {
            zimbraMsg = zimbraMessages.getJsonObject(0);
        } catch (ClassCastException e) {
            zimbraMessages.remove(0);
            processSearchResult(zimbraMessages, frontMessages, result);
            return;
        }
        final JsonObject frontMsg = new JsonObject();
        frontMsg.put("id", zimbraMsg.getString(ZimbraConstants.SEARCH_MSG_ID));
        frontMsg.put("subject", zimbraMsg.getString(ZimbraConstants.SEARCH_MSG_SUBJECT));
        JsonObject zimbraFrom = zimbraMsg.getJsonArray(ZimbraConstants.SEARCH_MSG_EMAILS).getJsonObject(0);
        frontMsg.put("from", zimbraFrom.getString(ZimbraConstants.SEARCH_MSG_EMAIL_ADDR));
        frontMsg.put("displayNames", new JsonArray()
                .add(new JsonArray()
                    .add(zimbraFrom.getString(ZimbraConstants.SEARCH_MSG_EMAIL_ADDR))
                    .add(zimbraFrom.getString(ZimbraConstants.SEARCH_MSG_EMAIL_COMMENT, ""))));
        zimbraMessages.remove(0);
        frontMessages.add(frontMsg);
        processSearchResult(zimbraMessages, frontMessages, result);
    }

    /**
     * Search paths can't start with '/'
     * Remove leading '/' if needed
     * @param path path to check
     * @return modified path
     */
    private String translatePathForSearch(String path) {
        String newPath = path;
        if(path.charAt(0) == '/') {
            newPath = path.substring(1);
        }
        return newPath;
    }

    /**
     * Translate path to query :
     *  in:"path/to/folder"
     * @param path path to translate
     * @return result query
     */
    private String pathToQuery(String path) {
        return "in:\"" + path + "\"";
    }
}
