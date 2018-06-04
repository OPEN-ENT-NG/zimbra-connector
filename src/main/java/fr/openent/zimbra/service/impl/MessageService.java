package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.helper.ZimbraConstants;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import org.entcore.common.user.UserInfos;

public class MessageService {

    private SoapZimbraService soapService;
    private FolderService folderService;
    private SqlZimbraService sqlService;
    private UserService userService;
    private Logger log;

    public MessageService(Logger log, SoapZimbraService soapService, FolderService folderService,
                          SqlZimbraService sqlService, UserService userService) {
        this.soapService = soapService;
        this.folderService = folderService;
        this.sqlService = sqlService;
        this.userService = userService;
        this.log = log;
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

                    String query = pathToQuery(folderPath);
                    if(unread) {
                        query += " is:unread";
                    }
                    JsonObject searchReq = new JsonObject()
                            .put("query", query)
                            .put("types", "message")
                            .put("recip", "2")
                            .put("limit", Zimbra.MAIL_LIST_LIMIT)
                            .put("offset", page * Zimbra.MAIL_LIST_LIMIT)
                            .put("_jsns", "urn:zimbraMail");

                    JsonObject searchRequest = new JsonObject()
                            .put("name", "SearchRequest")
                            .put("content", searchReq);

                    soapService.callUserSoapAPI(searchRequest, user, searchResult -> {
                        if(searchResult.isLeft()) {
                            result.handle(new Either.Left<>(searchResult.left().getValue()));
                        } else {
                            processListMessages(user, searchResult.right().getValue(), result);
                        }
                    });

                } catch (NullPointerException e ) {
                    result.handle(new Either.Left<>("Error when reading response for folder"));
                }
            }
        });
    }

    /**
     * Start processing message list. Forward each message to processSearchResult
     * @param user User infos
     * @param zimbraResponse Response from Zimbra API
     * @param result result handler
     */
    private void processListMessages(UserInfos user, JsonObject zimbraResponse,
                                     Handler<Either<String, JsonArray>> result)  {
        JsonArray zimbraMessages;
        try {
            zimbraMessages = zimbraResponse.getJsonObject("Body")
                                .getJsonObject("SearchResponse")
                                .getJsonArray(ZimbraConstants.SEARCH_MESSAGES, new JsonArray());
        } catch (NullPointerException e) {
            result.handle(new Either.Left<>("Error when processing search result"));
            return;
        }

        JsonArray frontMessages = new JsonArray();
        processSearchResult(user, zimbraMessages, frontMessages, result);
    }

    /**
     * Process recursively a zimbra searchResult and transform it to Front Message
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
    private void processSearchResult(UserInfos user, JsonArray zimbraMessages, JsonArray frontMessages,
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
            processSearchResult(user, zimbraMessages, frontMessages, result);
            return;
        }
        final JsonObject frontMsg = new JsonObject();
        frontMsg.put("id", zimbraMsg.getString(ZimbraConstants.SEARCH_MSG_ID));
        frontMsg.put("subject", zimbraMsg.getString(ZimbraConstants.SEARCH_MSG_SUBJECT));
        frontMsg.put("date", zimbraMsg.getLong(ZimbraConstants.SEARCH_MSG_DATE));
        JsonArray zimbraMails = zimbraMsg.getJsonArray(ZimbraConstants.SEARCH_MSG_EMAILS);

        String flags = zimbraMsg.getString(ZimbraConstants.SEARCH_MSG_FLAGS, "");

        String state = flags.contains(ZimbraConstants.SEARCH_MSG_FLAG_DRAFT) ? "DRAFT" : "SENT";
        frontMsg.put("state", state);

        frontMsg.put("unread", flags.contains(ZimbraConstants.SEARCH_MSG_FLAG_UNREAD));

        frontMsg.put("to", new JsonArray());
        frontMsg.put("cc", new JsonArray());
        frontMsg.put("displayNames", new JsonArray());
        frontMsg.put("attachments", new JsonArray());

        translateMaillistToUidlist(user, frontMsg, zimbraMails, response -> {
            zimbraMessages.remove(0);
            frontMessages.add(response);
            processSearchResult(user, zimbraMessages, frontMessages, result);
        });
    }

    /**
     * Process list of mail address in a mail and transform it in Front data
     * @param user User infos
     * @param frontMsg JsonObject receiving Front-formatted data
     * @param zimbraMails JsonObject containing mail addresses
     * @param handler result handler
     */
    private void translateMaillistToUidlist(UserInfos user, JsonObject frontMsg, JsonArray zimbraMails,
                                            Handler<JsonObject> handler) {
        if(zimbraMails.isEmpty()) {
            handler.handle(frontMsg);
            return;
        }
        JsonObject zimbraUser = zimbraMails.getJsonObject(0);
        String type = (zimbraUser==null) ? "" :  zimbraUser.getString(ZimbraConstants.SEARCH_MSG_EMAIL_TYPE);

        if(!(type.equals(ZimbraConstants.ADDR_TYPE_FROM))
            && !(type.equals(ZimbraConstants.ADDR_TYPE_CC))
            && !(type.equals(ZimbraConstants.ADDR_TYPE_TO))) {
            zimbraMails.remove(0);
            translateMaillistToUidlist(user, frontMsg, zimbraMails, handler);
            return;
        }

        String zimbraMail = zimbraUser.getString(ZimbraConstants.SEARCH_MSG_EMAIL_ADDR);
        translateMail(zimbraMail, user, userUuid -> {
            if(userUuid == null) {
                userUuid = zimbraMail;
            }
            switch (type) {
                case ZimbraConstants.ADDR_TYPE_FROM:
                    frontMsg.put("from", userUuid);
                    break;
                case ZimbraConstants.ADDR_TYPE_TO:
                    frontMsg.put("to", frontMsg.getJsonArray("to").add(userUuid));
                    break;
                case ZimbraConstants.ADDR_TYPE_CC:
                    frontMsg.put("cc", frontMsg.getJsonArray("cc").add(userUuid));
                    break;
            }
            frontMsg.put("displayNames", frontMsg.getJsonArray("displayNames")
                            .add(new JsonArray()
                                    .add(userUuid)
                                    .add(zimbraUser.getString(ZimbraConstants.SEARCH_MSG_EMAIL_COMMENT, zimbraMail))));
            zimbraMails.remove(0);
            translateMaillistToUidlist(user, frontMsg, zimbraMails, handler);
        });
    }

    /**
     * Translate mail addresses to users uuids
     * Request database first
     * Then if not present, request Zimbra (not implemented)
     * @param mail Zimbra mail
     * @param user User infos
     * @param handler result handler
     */
    private void translateMail(String mail, UserInfos user, Handler<String> handler) {
        sqlService.getUserIdFromMail(mail, sqlResponse -> {
            if(sqlResponse.isLeft() || sqlResponse.right().getValue().isEmpty()) {
                //todo request zimbra
                log.debug("no user in database for address : " + mail);
                handler.handle(null);
            } else {
                JsonArray results = sqlResponse.right().getValue();
                if(results.size() > 1) {
                    log.warn("More than one user id for address : " + mail);
                }
                String uuid = results.getJsonObject(0).getString(SqlZimbraService.USER_NEO4J_UID);
                handler.handle(uuid);
            }
        });
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
