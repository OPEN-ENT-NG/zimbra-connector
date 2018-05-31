package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.Zimbra;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.user.UserInfos;

import static fr.openent.zimbra.helper.ZimbraConstants.*;

public class MessageService {

    private SoapZimbraService soapService;
    private FolderService folderService;
    private SqlZimbraService sqlService;
    private UserService userService;
    private static Logger log = LoggerFactory.getLogger(MessageService.class);

    public MessageService(SoapZimbraService soapService, FolderService folderService,
                          SqlZimbraService sqlService, UserService userService) {
        this.soapService = soapService;
        this.folderService = folderService;
        this.sqlService = sqlService;
        this.userService = userService;
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

                    String folderPath = folder.getString(GETFOLDER_FOLDERPATH);

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
                            processListMessages(searchResult.right().getValue(), result);
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
     * @param zimbraResponse Response from Zimbra API
     * @param result result handler
     */
    private void processListMessages(JsonObject zimbraResponse,
                                     Handler<Either<String, JsonArray>> result)  {
        JsonArray zimbraMessages;
        try {
            zimbraMessages = zimbraResponse.getJsonObject("Body")
                                .getJsonObject("SearchResponse")
                                .getJsonArray(MSG, new JsonArray());
        } catch (NullPointerException e) {
            result.handle(new Either.Left<>("Error when processing search result"));
            return;
        }

        JsonArray frontMessages = new JsonArray();
        processSearchResult(zimbraMessages, frontMessages, result);
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
     *     "date" : datesent,
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


        transformMessageZimbraToFront(zimbraMsg, frontMsg, response -> {
            zimbraMessages.remove(0);
            frontMessages.add(response);
            processSearchResult(zimbraMessages, frontMessages, result);
        });
    }

    /**
     * Transform a JsonObject Received from Zimbra to a JsonObject that can be sent to Front
     * @param msgZimbra object to transform
     * @param msgFront initial object sent to Front
     * @param result handler result
     */
    private void transformMessageZimbraToFront(JsonObject msgZimbra, JsonObject msgFront,
                                               Handler<JsonObject> result) {
        msgFront.put("id", msgZimbra.getString(MSG_ID));
        msgFront.put("date", msgZimbra.getLong(MSG_DATE));
        msgFront.put("subject", msgZimbra.getString(MSG_SUBJECT));
        String folderId = msgZimbra.getString(MSG_LOCATION);
        folderId = folderService.getFrontFolderId(folderId);
        msgFront.put("parent_id", folderId);
        if (msgZimbra.containsKey(MSG_CONVERSATION_ID)) {
            msgFront.put("thread_id", msgZimbra.getString(MSG_CONVERSATION_ID));
        }

        String flags = msgZimbra.getString(MSG_FLAGS, "");
        String state = flags.contains(MSG_FLAG_DRAFT) ? "DRAFT" : "SENT";
        msgFront.put("state", state);
        msgFront.put("unread", flags.contains(MSG_FLAG_UNREAD));

        msgFront.put("to", new JsonArray());
        msgFront.put("cc", new JsonArray());
        msgFront.put("displayNames", new JsonArray());
        msgFront.put("attachments", new JsonArray());


        JsonArray zimbraMails = msgZimbra.getJsonArray(MSG_EMAILS);

        if(msgZimbra.containsKey(MSG_MULTIPART)) {
            JsonArray multiparts = msgZimbra.getJsonArray(MSG_MULTIPART);
            processMessageMultipart(msgFront, multiparts);
        }

        translateMaillistToUidlist(msgFront, zimbraMails, result);
    }

    /**
     * Process multiparts from a Zimbra message, and add relevant info to Front JsonObject
     * Process recursively every multipart
     * @param msgFront Front JsonObject
     * @param multiparts Array of multipart structure
     */
    private void processMessageMultipart(JsonObject msgFront, JsonArray multiparts) {
        for(Object obj : multiparts) {
            if(!(obj instanceof JsonObject)) continue;
            JsonObject mpart = (JsonObject)obj;
            if(mpart.containsKey(MSG_MPART_ISBODY) && mpart.getBoolean(MSG_MPART_ISBODY)) {
                msgFront.put("body", mpart.getString("content", ""));
            }
            if(mpart.containsKey(MSG_MULTIPART)) {
                JsonArray innerMultiparts = mpart.getJsonArray(MSG_MULTIPART);
                processMessageMultipart(msgFront, innerMultiparts);
            }
        }
    }

    /**
     * Process list of mail address in a mail and transform it in Front data
     * @param frontMsg JsonObject receiving Front-formatted data
     * @param zimbraMails JsonObject containing mail addresses
     * @param handler result handler
     */
    private void translateMaillistToUidlist(JsonObject frontMsg, JsonArray zimbraMails,
                                            Handler<JsonObject> handler) {
        if(zimbraMails.isEmpty()) {
            handler.handle(frontMsg);
            return;
        }
        JsonObject zimbraUser = zimbraMails.getJsonObject(0);
        String type = (zimbraUser==null) ? "" :  zimbraUser.getString(MSG_EMAIL_TYPE);

        if(!(type.equals(ADDR_TYPE_FROM))
            && !(type.equals(ADDR_TYPE_CC))
            && !(type.equals(ADDR_TYPE_TO))) {
            zimbraMails.remove(0);
            translateMaillistToUidlist(frontMsg, zimbraMails, handler);
            return;
        }

        String zimbraMail = zimbraUser.getString(MSG_EMAIL_ADDR, "");
        if(zimbraMail.isEmpty()) {
            zimbraMails.remove(0);
            translateMaillistToUidlist(frontMsg, zimbraMails, handler);
        } else {
            translateMail(zimbraMail, userUuid -> {
                if (userUuid == null) {
                    userUuid = zimbraMail;
                }
                switch (type) {
                    case ADDR_TYPE_FROM:
                        frontMsg.put("from", userUuid);
                        break;
                    case ADDR_TYPE_TO:
                        frontMsg.put("to", frontMsg.getJsonArray("to").add(userUuid));
                        break;
                    case ADDR_TYPE_CC:
                        frontMsg.put("cc", frontMsg.getJsonArray("cc").add(userUuid));
                        break;
                }
                frontMsg.put("displayNames", frontMsg.getJsonArray("displayNames")
                        .add(new JsonArray()
                                .add(userUuid)
                                .add(zimbraUser.getString(MSG_EMAIL_COMMENT, zimbraMail))));
                zimbraMails.remove(0);
                translateMaillistToUidlist(frontMsg, zimbraMails, handler);
            });
        }
    }

    /**
     * Translate mail addresses to users uuids
     * Request database first
     * Then if not present, request Zimbra (not implemented)
     * @param mail Zimbra mail
     * @param handler result handler
     */
    private void translateMail(String mail, Handler<String> handler) {
        sqlService.getUserIdFromMail(mail, sqlResponse -> {
            if(sqlResponse.isLeft() || sqlResponse.right().getValue().isEmpty()) {
                log.debug("no user in database for address : " + mail);
                userService.getAliases(mail, zimbraResponse -> {
                    if(zimbraResponse.isRight()) {
                        JsonArray aliases = zimbraResponse.right().getValue().getJsonArray("aliases");
                        if(aliases.size() > 1) {
                            log.warn("More than one alias for address : " + mail);
                        }
                        if(aliases.isEmpty()) {
                            handler.handle(null);
                        } else {
                            handler.handle(aliases.getString(0));
                        }
                    } else {
                        handler.handle(null);
                    }
                });
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

    /**
     * Get a specific message content and metadata
     * @param messageId Id of message to get
     * @param user User infos
     * @param handler Final handler
     */
    public void getMessage(String messageId, UserInfos user, Handler<Either<String,JsonObject>> handler) {
        JsonObject messageReq = new JsonObject()
                .put("html", 1)
                .put("read", 1)
                .put("needExp", 1)
                .put("id", messageId);

        JsonObject getMsgRequest = new JsonObject()
                .put("name", "GetMsgRequest")
                .put("content", new JsonObject()
                    .put(MSG, messageReq)
                    .put("_jsns", NAMESPACE_MAIL));

        soapService.callUserSoapAPI(getMsgRequest, user, response -> {
            if(response.isLeft()) {
                handler.handle(new Either.Left<>(response.left().getValue()));
            } else {
                processGetMessage(response.right().getValue(), handler);
            }
        });
    }

    /**
     * Process Zimbra message and return JsonObject to Front
     Form of Front message returned :
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
     *     "date" : datesent,
     *     "unread" : boolean_unread
     *
     * }
     * @param response Zimbra API Response
     * @param handler final handler
     */
    private void processGetMessage(JsonObject response, Handler<Either<String,JsonObject>> handler) {
        JsonObject msgFront = new JsonObject();
        JsonArray listMessages;

        try {
            listMessages = response.getJsonObject("Body").getJsonObject("GetMsgResponse")
                    .getJsonArray(MSG);
            if (listMessages.size() > 1) {
                log.warn("More than one message");
            }
        } catch (NullPointerException|ClassCastException e) {
            handler.handle(new Either.Left<>("Could not process message from Zimbra"));
            return;
        }
        JsonObject msgZimbra = listMessages.getJsonObject(0);

        transformMessageZimbraToFront(msgZimbra, msgFront, result -> handler.handle(new Either.Right<>(msgFront)) );
    }

    /**
     * Empty trash
     * @param user User infos
     * @param handler Empty JsonObject returned, no process needed
     */
    public void emptyTrash(UserInfos user,
                           Handler<Either<String,JsonObject>> handler) {
        JsonObject actionReq = new JsonObject()
                .put("id", "3")
                .put("op", "empty")
                .put("recursive", "true");

        JsonObject folderActionRequest = new JsonObject()
                .put("name", "FolderActionRequest")
                .put("content", new JsonObject()
                        .put("action", actionReq)
                        .put("_jsns", NAMESPACE_MAIL));

        soapService.callUserSoapAPI(folderActionRequest, user, response -> {
            if(response.isLeft()) {
                handler.handle(new Either.Left<>(response.left().getValue()));
            } else {
                handler.handle(new Either.Right<>(new JsonObject()));
            }
        });
    }





    /**
     * Send Message
     * TODO:Comment
     */
    public void sendMessage(String subjectMessage, String bodyMessage, JsonArray toMessage, JsonArray ccMessage, UserInfos user,
                             Handler<Either<String, JsonObject>> result) {

        JsonArray mailContacts = new JsonArray()
                .add(new JsonObject()
                        .put("t", "f")
                        .put("a", "quentin.bouvier@ng.preprod-ent.fr"))
                .add(new JsonObject()
                        .put("t", "t")
                        .put("a", "quentin.bouvier@ng.preprod-ent.fr"));

        JsonArray mailMessages = new JsonArray()
                .add(new JsonObject()
                        .put ("content", new JsonObject()
                            .put("_content", "TEXTE test SOAP JAVA"))
                        .put("ct", "text/html"));

        JsonObject sendMsgRequest = new JsonObject()
                .put("name", "SendMsgRequest")
                .put("content", new JsonObject()
                        .put("_jsns", ZimbraConstants.NAMESPACE_MAIL)
                        .put("m", new JsonObject()
                                .put("e", mailContacts)
                                .put("_content", subjectMessage)
                                .put("mp", new JsonObject()
                                        .put("ct", "multipart/alternative")
                                        .put("mp", mailMessages)
                                )));

        soapService.callUserSoapAPI(sendMsgRequest, user, response -> {
            if(response.isLeft()) {
                result.handle(new Either.Left<>(response.left().getValue()));
            } else {
                result.handle(new Either.Right<>(new JsonObject()));
            }
        });
        /*
        soapService.callUserSoapAPI(getFolderRequest, user, response -> {
            if(response.isLeft()) {
                result.handle(response);
            } else {
                processSendMessage(unread, response.right().getValue(), result);
            }
        });*/

    }

/*
    private void processSendMessage(Boolean unread, JsonObject jsonResponse,
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
    }*/


}
