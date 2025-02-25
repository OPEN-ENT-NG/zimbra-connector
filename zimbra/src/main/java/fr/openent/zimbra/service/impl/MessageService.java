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
import fr.openent.zimbra.core.enums.ErrorEnum;
import fr.openent.zimbra.core.enums.RecipientType;
import fr.openent.zimbra.helper.*;
import fr.openent.zimbra.model.MailAddress;
import fr.openent.zimbra.model.ZimbraUser;
import fr.openent.zimbra.model.constant.FrontConstants;
import fr.openent.zimbra.model.constant.I18nConstants;
import fr.openent.zimbra.model.constant.SoapConstants;
import fr.openent.zimbra.model.message.Multipart;
import fr.openent.zimbra.model.message.Recipient;
import fr.openent.zimbra.model.soap.SoapMessageHelper;
import fr.openent.zimbra.model.soap.SoapSearchHelper;
import fr.openent.zimbra.service.DbMailService;
import fr.openent.zimbra.service.data.HttpService;
import fr.openent.zimbra.service.data.SoapZimbraService;
import fr.openent.zimbra.service.synchro.SynchroUserService;
import fr.wseduc.webutils.Either;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.user.UserInfos;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static fr.openent.zimbra.helper.PromiseHelper.handlerJsonObject;
import static fr.openent.zimbra.model.constant.FrontConstants.*;
import static fr.openent.zimbra.model.constant.SoapConstants.*;
import static fr.openent.zimbra.model.constant.ZimbraConstants.*;
import static fr.openent.zimbra.model.constant.ZimbraErrors.ERROR_NOSUCHMSG;

public class MessageService {

    private final SoapZimbraService soapService;
    private final FolderService folderService;
    private final DbMailService dbMailService;
    private final UserService userService;
    private final GroupService groupService;

    private final HttpService httpService;

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private final int BATCH_SIZE = 100;

    public MessageService(SoapZimbraService soapService, FolderService folderService,
                          DbMailService dbMailService, UserService userService, SynchroUserService synchroUserService, HttpService httpService) {
        this.soapService = soapService;
        this.folderService = folderService;
        this.dbMailService = dbMailService;
        this.userService = userService;
        this.groupService = new GroupService(soapService, dbMailService, synchroUserService);
        this.httpService = httpService;
    }

    /**
     * List messages in folders
     * If unread is true, filter only unread messages.
     * If search is set, must be at least 3 characters. Then filter by search.
     *
     * @param folderPath folder id where to listMessages messages
     * @param unread     filter only unread messages ?
     * @param user       user infos
     * @param page       page used for pagination, default 0
     * @param searchText [optional] text used for search
     * @param result     Handler results
     */
    public void listMessages(String folderPath, Boolean unread, UserInfos user, int page,
                             final String searchText, Handler<Either<String, JsonArray>> result) {
        String query = pathToQuery(folderPath);
        if (unread) {
            query += " is:unread";
        }
        if (searchText != null && !searchText.isEmpty()) {
            query += " *" + searchText + "*";
        }
        int pageSize = Zimbra.appConfig.getMailListLimit();
        JsonObject searchReq = new JsonObject()
                .put("query", query)
                .put("types", "message")
                .put("recip", "2")
                .put("limit", pageSize)
                .put("offset", page * pageSize)
                .put("_jsns", SoapConstants.NAMESPACE_MAIL);

        JsonObject searchRequest = new JsonObject()
                .put(Field.NAME, "SearchRequest")
                .put("content", searchReq);

        soapService.callUserSoapAPI(searchRequest, user, searchResult -> {
            if (searchResult.isLeft()) {
                result.handle(new Either.Left<>(searchResult.left().getValue()));
            } else {
                processListMessages(searchResult.right().getValue(), user, result);
            }
        });
    }

    /**
     * Start processing message list. Forward each message to processSearchResult
     *
     * @param zimbraResponse Response from Zimbra API
     * @param result         result handler
     */
    private void processListMessages(JsonObject zimbraResponse, UserInfos user,
                                     Handler<Either<String, JsonArray>> result) {
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
        Map<String, String> addressMap = new HashMap<>();
        processSearchResult(zimbraMessages, frontMessages, addressMap, result);
    }

    /**
     * Add displayNames to frontMsg response
     *
     * @param frontMessages
     * @return
     */
    private Future<Void> addDisplayNames(JsonArray frontMessages) {
        Promise<Void> promise = Promise.promise();

        List<JsonArray> displayNames = this.getDisplayNames(frontMessages);
        JsonArray userIds = new JsonArray();
        displayNames.forEach(displayName -> userIds.add(displayName.getString(0)));

        this.retrieveUsernames(userIds)
                .onSuccess(usernames -> {
                    displayNames.forEach(displayName -> {
                        String username = usernames.get(displayName.getString(0));
                        displayName.set(1, username == null ? displayName.getString(1) : username);
                    });
                    promise.complete();
                })
                .onFailure(e -> promise.fail(e.getMessage()));

        return promise.future();
    }

    /**
     * Extract displayNames from frontMsg
     *
     * @param frontMessages
     * @return
     */
    private List<JsonArray> getDisplayNames(JsonArray frontMessages) {
        List<JsonArray> response = new ArrayList<>();
        for (Object obj : frontMessages) {
            JsonObject mail = (JsonObject) obj;
            JsonArray displayNames = mail.getJsonArray(FrontConstants.MAIL_DISPLAYNAMES, new JsonArray());
            for (Object displayName : displayNames) {
                if (!(displayName instanceof JsonArray) || ((JsonArray) displayName).size() == 0) continue;
                String userId = ((JsonArray) displayName).getString(0);
                if (userId != null && StringHelper.isUUID(userId)) {
                    response.add((JsonArray) displayName);
                }
            }
        }
        return response;
    }

    /**
     * Retrieve username from entcore with JsonArray of userIds
     *
     * @param userIds JsonArray of userIds
     * @return Future of HashMap<String, String>
     */
    private Future<HashMap<String, String>> retrieveUsernames(JsonArray userIds) {
        Promise<HashMap<String, String>> promise = Promise.promise();

        userService.getUsers(userIds, userIds, (response) -> {
            if (response.isLeft()) {
                promise.fail(response.left().getValue());
            } else {
                List<JsonObject> users = response.right().getValue().getList();

                HashMap<String, String> usernames = new HashMap<>();
                for (JsonObject user : users) {
                    String id = user.getString(Field.ID);
                    String username = user.getString(Field.USERNAME);
                    if (id != null && username != null) {
                        usernames.put(id, username);
                    }
                }

                promise.complete(usernames);
            }
        });

        return promise.future();
    }

    /**
     * Process recursively a zimbra searchResult and transform it to Front Message
     * Form of Front message returned :
     * {
     * "id" : "message_id",
     * "subject" : "message_subject",
     * "from" : "user_id_from",
     * "to" : [
     * "user_id_to",
     * ],
     * "cc" : [
     * "user_id_cc",
     * ],
     * "display_names" : [
     * "user_id",
     * "user_display_name",
     * ],
     * "date" : datesent,
     * "unread" : boolean_unread
     * <p>
     * }
     *
     * @param zimbraMessages array of unprocessed messages from zimbra
     * @param frontMessages  array of processed messages
     * @param addressMap     mapping of user email addresses and neo ids
     * @param result         final handler
     */
    private void processSearchResult(JsonArray zimbraMessages, JsonArray frontMessages, Map<String, String> addressMap, Handler<Either<String, JsonArray>> result) {
        if (zimbraMessages.isEmpty()) {
            this.addDisplayNames(frontMessages)
                    .onSuccess(res -> result.handle(new Either.Right<>(frontMessages)))
                    .onFailure(res -> result.handle(new Either.Left<>(res.getMessage())));
            return;
        }
        JsonObject zimbraMsg;
        try {
            zimbraMsg = zimbraMessages.getJsonObject(0);
        } catch (ClassCastException e) {
            zimbraMessages.remove(0);
            processSearchResult(zimbraMessages, frontMessages, addressMap, result);
            return;
        }
        final JsonObject frontMsg = new JsonObject();

        transformMessageZimbraToFront(zimbraMsg, frontMsg, addressMap, response -> {
            zimbraMessages.remove(0);
            frontMessages.add(response);
            processSearchResult(zimbraMessages, frontMessages, addressMap, result);
        });
    }

    /**
     * Transform a JsonObject Received from Zimbra to a JsonObject that can be sent to Front
     *
     * @param msgZimbra object to transform
     * @param msgFront  initial object sent to Front
     * @param result    handler result
     */
    private void transformMessageZimbraToFront(JsonObject msgZimbra, JsonObject msgFront,
                                               Map<String, String> addressMap, Handler<JsonObject> result) {
        msgFront.put(Field.ID, msgZimbra.getString(MSG_ID));
        msgFront.put("date", msgZimbra.getLong(MSG_DATE));
        msgFront.put("subject", msgZimbra.getString(MSG_SUBJECT));
        String folderId = msgZimbra.getString(MSG_LOCATION);
        folderId = folderService.getFrontFolderId(folderId);
        msgFront.put("parent_id", folderId);
        if (msgZimbra.containsKey(MSG_CONVERSATION_ID)) {
            msgFront.put("thread_id", msgZimbra.getString(MSG_CONVERSATION_ID));
        }

        String flags = msgZimbra.getString(MSG_FLAGS, "");
        String state = flags.contains(MSG_FLAG_DRAFT) ? STATE_DRAFT : STATE_SENT;
        String folder;
        if (flags.contains(MSG_FLAG_DRAFT)) {
            folder = FrontConstants.FOLDER_DRAFT;
        } else if (flags.contains(MSG_FLAG_SENTBYME)) {
            folder = FrontConstants.FOLDER_OUTBOX;
        } else {
            folder = FrontConstants.FOLDER_INBOX;
        }

        if (msgZimbra.containsKey(MSG_EMAILID) && msgZimbra.getString(MSG_EMAILID) != null && msgZimbra.getString(MSG_EMAILID).length() > 1) {
            String mid = msgZimbra.getString(MSG_EMAILID).substring(1, msgZimbra.getString(MSG_EMAILID).length() - 1);
            msgFront.put("mid", mid);
        }

        msgFront.put("state", state);
        msgFront.put("unread", flags.contains(MSG_FLAG_UNREAD));
        msgFront.put("response", flags.contains(MSG_FLAG_REPLIED));
        msgFront.put("hasAttachment", flags.contains(MSG_FLAG_HASATTACHMENT));
        msgFront.put("systemFolder", folder);
        msgFront.put("to", new JsonArray());
        msgFront.put("cc", new JsonArray());
        msgFront.put("bcc", new JsonArray());
        msgFront.put("displayNames", new JsonArray());
        msgFront.put("attachments", new JsonArray());



        JsonArray zimbraMails = msgZimbra.getJsonArray(MSG_EMAILS);

        if (msgZimbra.containsKey(MSG_MULTIPART)) {
            JsonArray multiparts = msgZimbra.getJsonArray(MSG_MULTIPART);
            processMessageMultipart(msgFront, multiparts);
        }


        translateMaillistToUidlist(msgFront, zimbraMails, addressMap,
                msgZimbra.getString(MSG_FLAGS, "").contains(MSG_FLAG_NOTIFICATIONSENT), result);
    }

    /**
     * Process multiparts from a Zimbra message, and add relevant info to Front JsonObject
     * Process recursively every multipart
     *
     * @param msgFront   Front JsonObject
     * @param multiparts Array of multipart structure
     */
    private void processMessageMultipart(JsonObject msgFront, JsonArray multiparts) {
        Multipart mparts = new Multipart(msgFront.getString(FrontConstants.MESSAGE_ID), multiparts);
        msgFront.put(MESSAGE_BODY, mparts.getBody());
        msgFront.put(MESSAGE_ATTACHMENTS, mparts.getAttachmentsJson());
    }

    private boolean displayNamesContainsUserId(JsonArray jsonArray, String userId) {
        for (Object object : jsonArray) {
            JsonArray array = (JsonArray) object;
            if (array.size() > 0 && array.getString(0).equals(userId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Process list of mail address in a mail and transform it in Front data
     *
     * @param frontMsg    JsonObject receiving Front-formatted data
     * @param zimbraMails JsonObject containing mail addresses
     * @param handler     result handler
     */
    private void translateMaillistToUidlist(JsonObject frontMsg, JsonArray zimbraMails, Map<String, String> addressMap,
                                            boolean isReported, Handler<JsonObject> handler) {
        if (zimbraMails == null || zimbraMails.isEmpty()) {
            handler.handle(frontMsg);
            return;
        }
        JsonObject zimbraUser = zimbraMails.getJsonObject(0);
        String type = (zimbraUser == null) ? "" : zimbraUser.getString(MSG_EMAIL_TYPE);

        if (!(type.equals(ADDR_TYPE_FROM))
                && !(type.equals(ADDR_TYPE_CC))
                && !(type.equals(ADDR_TYPE_TO))
                && !(type.equals(ADDR_TYPE_BCC))
                && !(type.equals(ADDR_TYPE_READRECEIPT))) {
            zimbraMails.remove(0);
            translateMaillistToUidlist(frontMsg, zimbraMails, addressMap, isReported, handler);
            return;
        }

        String zimbraMail = zimbraUser.getString(MSG_EMAIL_ADDR, "");
        if (zimbraMail.isEmpty()) {
            zimbraMails.remove(0);
            translateMaillistToUidlist(frontMsg, zimbraMails, addressMap, isReported, handler);
        } else {
            Handler<Recipient> translatedUuidHandler = recipient -> {
                String userUuid;
                if (recipient.getRecipientType() == RecipientType.UNKNOWN || recipient.getUserId() == null) {
                    userUuid = zimbraMail;
                } else {
                    userUuid = recipient.getUserId();
                }
                addressMap.put(zimbraMail, userUuid);
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
                    case ADDR_TYPE_BCC:
                        frontMsg.put("bcc", frontMsg.getJsonArray("bcc").add(userUuid));
                        break;
                    case ADDR_TYPE_READRECEIPT:
                        if (!isReported)
                            frontMsg.put(FrontConstants.IS_REPORT_REQUIRED, true);
                        break;
                }

                // Get current displayNames
                JsonArray displayNames = frontMsg.getJsonArray(MAIL_DISPLAYNAMES, new JsonArray());

                // Check if user is already in displayNames
                if (!displayNamesContainsUserId(displayNames, userUuid)) {
                    displayNames.add(new JsonArray()
                            .add(userUuid)
                            .add(zimbraUser.getString(MSG_EMAIL_COMMENT, zimbraMail)));
                }

                zimbraMails.remove(0);
                translateMaillistToUidlist(frontMsg, zimbraMails, addressMap, isReported, handler);
            };

            if (addressMap.containsKey(zimbraMail)) {
                translatedUuidHandler.handle(new Recipient(zimbraMail, addressMap.get(zimbraMail), RecipientType.USER));
            } else {
                translateMail(zimbraMail, translatedUuidHandler);
            }
        }
    }


    public void translateMailFuture(String mail, Handler<AsyncResult<Recipient>> handler) {
        translateMail(mail, res -> {
            handler.handle(Future.succeededFuture(res));
        });
    }

    /**
     * Translate mail addresses to users uuids
     * Request database first
     * Then if not present, request Zimbra (not implemented)
     *
     * @param mail    Zimbra mail
     * @param handler result handler
     */
    private void translateMail(String mail, Handler<Recipient> handler) {
        try {
            String domain = mail.split("@")[1];
            if (!Zimbra.domain.equals(domain)) {
                handler.handle(new Recipient(mail, mail, RecipientType.UNKNOWN));
                return;
            }
        } catch (Exception e) {
            handler.handle(new Recipient(mail, mail, RecipientType.UNKNOWN));
            return;
        }
        dbMailService.getNeoIdFromMail(mail, sqlResponse -> {
            if (sqlResponse.isLeft() || sqlResponse.right().getValue().isEmpty()) {
                log.debug("no user in database for address : " + mail);
                handler.handle(new Recipient(mail, groupService.getGroupId(mail), RecipientType.GROUP));
            } else {
                JsonArray results = sqlResponse.right().getValue();
                if (results.size() > 1) {
                    log.warn("More than one user id for address : " + mail);
                }
                JsonObject resultData = results.getJsonObject(0);
                String uuid = resultData.getString(DbMailService.NEO4J_UID);
                RecipientType recipientType = RecipientType.fromString(resultData.getString(Field.TYPE));
                handler.handle(new Recipient(mail, uuid, recipientType));
            }
        });
    }

    /**
     * Translate path to query :
     * in:"path/to/folder"
     *
     * @param path path to translate
     * @return result query
     */
    private String pathToQuery(String path) {
        return "in:\"" + path + "\"";
    }

    /**
     * Get a specific message content and metadata
     * and process it for Front
     *
     * @param messageId Id of message to get
     * @param user      User infos
     * @param handler   Final handler
     */
    public void getMessage(String messageId, UserInfos user, Boolean setRead, Handler<Either<String, JsonObject>> handler) {
        JsonObject messageReq = new JsonObject()
                .put("html", 1)
                .put("read", setRead != null ? setRead : Zimbra.appConfig.isActionBlocked(ConfigManager.UPDATE_ACTION) ? 0 : 1)
                .put("needExp", 1)
                .put("neuter", 0)
                .put(Field.ID, messageId);

        JsonObject getMsgRequest = new JsonObject()
                .put(Field.NAME, "GetMsgRequest")
                .put("content", new JsonObject()
                        .put(MSG, messageReq)
                        .put("_jsns", SoapConstants.NAMESPACE_MAIL));

        soapService.callUserSoapAPI(getMsgRequest, user, response -> {
            if (response.isLeft()) {
                handler.handle(new Either.Left<>(response.left().getValue()));
            } else {
                processGetMessage(response.right().getValue(), handler);
            }
        });
    }

    public Future<JsonObject> getMessage(String messageId, UserInfos user, Boolean setRead) {
        Promise<JsonObject> promise = Promise.promise();
        getMessage(messageId, user, setRead, event -> {
            if (event.isLeft()){
                promise.fail(event.left().getValue());
                return;
            }
            promise.complete(event.right().getValue());
        });
        return promise.future();
    }

    /**
     * Process Zimbra message and return JsonObject to Front
     * Form of Front message returned :
     * {
     * "id" : "message_id",
     * "subject" : "message_subject",
     * "from" : "user_id_from",
     * "to" : [
     * "user_id_to",
     * ],
     * "cc" : [
     * "user_id_cc",
     * ],
     * "displayNames" : [
     * "user_id",
     * "user_display_name",
     * ],
     * "date" : datesent,
     * "unread" : boolean_unread,
     * "attachments" : [{
     * "id" : "attachment_id",
     * "filename" : "attachment_filename",
     * "contentType" : "attachment_type",
     * "size" : "attachment_size"
     * },
     * ]
     * <p>
     * }
     *
     * @param response Zimbra API Response
     * @param handler  final handler
     */
    private void processGetMessage(JsonObject response, Handler<Either<String, JsonObject>> handler) {
        JsonObject msgFront = new JsonObject();
        JsonArray listMessages;

        try {
            listMessages = response.getJsonObject("Body").getJsonObject("GetMsgResponse")
                    .getJsonArray(MSG);
            if (listMessages.size() > 1) {
                log.warn("More than one message");
            }
        } catch (NullPointerException | ClassCastException e) {
            handler.handle(new Either.Left<>("Could not process message from Zimbra"));
            return;
        }
        JsonObject msgZimbra = listMessages.getJsonObject(0);

        Map<String, String> addressMap = new HashMap<>();
        transformMessageZimbraToFront(msgZimbra, msgFront, addressMap,
                result -> handler.handle(new Either.Right<>(msgFront)));
    }

    /**
     * Empty trash
     *
     * @param user    User infos
     * @param handler Empty JsonObject returned, no process needed
     */
    public void emptyTrash(UserInfos user,
                           Handler<Either<String, JsonObject>> handler) {
        JsonObject actionReq = new JsonObject()
                .put(MSG_ID, FOLDER_TRASH_ID)
                .put(OPERATION, OP_EMPTY)
                .put("recursive", "true");

        JsonObject folderActionRequest = new JsonObject()
                .put(Field.NAME, "FolderActionRequest")
                .put("content", new JsonObject()
                        .put("action", actionReq)
                        .put("_jsns", SoapConstants.NAMESPACE_MAIL));

        soapService.callUserSoapAPI(folderActionRequest, user, response -> {
            if (response.isLeft()) {
                handler.handle(new Either.Left<>(response.left().getValue()));
            } else {
                handler.handle(new Either.Right<>(new JsonObject()));
            }
        });
    }


    /**
     * Send Message
     *
     * @param frontMessage JsonObject containing front message :
     *                     {
     *                     "subject" : "message subject",
     *                     "body" : "message body"
     *                     "to" : [
     *                     "torecipient1",
     *                     "torecipient2",
     *                     ...
     *                     ]
     *                     "cc" : [
     *                     "ccrecipient1",
     *                     "ccrecipient2",
     *                     ...
     *                     ]
     *                     "bcc" : [
     *                     "bccrecipient1",
     *                     "bccrecipient2",
     *                     .
     *                     }
     * @param user         User infos
     * @param result       result handler
     */
    public void sendMessage(String messageId, JsonObject frontMessage, UserInfos user, String parentMessageId,
                            Handler<Either<String, JsonObject>> result) {

        Integer maxRecipients = Zimbra.appConfig.getMaxRecipients();

        JsonArray listRecipientsTo = frontMessage.getJsonArray(MAIL_TO, new JsonArray());
        JsonArray listRecipientsCC = frontMessage.getJsonArray(MAIL_CC, new JsonArray());
        JsonArray listRecipientsBCC = frontMessage.getJsonArray(MAIL_BCC, new JsonArray());

        int totalRecipients = listRecipientsTo.size() + listRecipientsCC.size() + listRecipientsBCC.size();

        if (totalRecipients > maxRecipients) {
            result.handle(new Either.Left<>(I18nConstants.ERROR_MAXRECIPIENTS));
            return;
        }

        getMessageMidFromId(user, parentMessageId, parentMessageMailId ->
                transformMessageFrontToZimbra(frontMessage, messageId, mailContent -> {
                    if (messageId != null && !messageId.isEmpty()) {
                        mailContent.put(MSG_ID, messageId);
                        mailContent.put(MSG_DRAFT_ID, messageId);
                    }
                    if (parentMessageMailId != null && !parentMessageMailId.isEmpty()) {
                        mailContent.put(MSG_REPLYTYPE, MSG_RT_REPLY);
                        mailContent.put(MSG_REPLIEDTO_ID, parentMessageMailId);
                        mailContent.put(MSG_ORIGINAL_ID, parentMessageId);
                    }
                    mailContent.getJsonArray(MSG_EMAILS, new JsonArray())
                            .add(new JsonObject()
                                    .put(MSG_EMAIL_ADDR, "")
                                    .put(MSG_EMAIL_TYPE, ADDR_TYPE_FROM)
                                    .put(MSG_EMAIL_COMMENT, user.getUsername()));
                    JsonObject sendMsgRequest = new JsonObject()
                            .put(Field.NAME, "SendMsgRequest")
                            .put("content", new JsonObject()
                                    .put("_jsns", SoapConstants.NAMESPACE_MAIL)
                                    .put(MSG, mailContent)
                                    .put("fetchSavedMsg", ONE_TRUE));

                    soapService.callUserSoapAPI(sendMsgRequest, user, response -> {
                        if (response.isLeft()) {
                            result.handle(response);
                        } else {
                            String id = "";
                            String threadid = "";
                            try {
                                JsonObject responseObj = response.right().getValue().getJsonObject("Body").getJsonObject("SendMsgResponse")
                                        .getJsonArray(MSG).getJsonObject(0);
                                id = responseObj.getString(MSG_ID, "");
                                threadid = responseObj.getString(MSG_CONVERSATION_ID, "");
                            } catch (Exception e) {
                                log.debug("unfindable id ", e);
                            }
                            JsonObject rightResponse = new JsonObject()
                                    .put("sent", 1)
                                    .put(Field.ID, id)
                                    .put("thread_id", threadid);
                            result.handle(new Either.Right<>(rightResponse));
                        }
                    });
                }));
    }

    /**
     * Add an email recipient in recipientList, from a list of Ids and correspondance
     *
     * @param recipientList  Final email recipient List
     * @param originList     Initial Id list
     * @param correspondance Correspondance between ids and emails
     * @param type           Type of list (to, cc...)
     */
    private void addRecipientToList(JsonArray recipientList, JsonArray originList,
                                    JsonObject correspondance, String type) {
        for (Object o : originList) {
            String elemId = (String) o;
            if (elemId != null && correspondance.containsKey(elemId)) {
                JsonObject elemInfos = correspondance.getJsonObject(elemId);
                JsonObject recipient = new JsonObject()
                        .put(MSG_EMAIL_TYPE, type)
                        .put(MSG_EMAIL_ADDR, elemInfos.getString("email"));
                if (!elemInfos.getString("displayName", "").isEmpty()) {
                    recipient.put(MSG_EMAIL_COMMENT, elemInfos.getString("displayName", ""));
                }
                recipientList.add(recipient);
            } else {
                if (elemId != null) log.error("No Zimbra correspondance for ID : " + elemId);
            }
        }
    }


    /**
     * Save Draft
     *
     * @param frontMessage    JsonObject containing front message :
     *                        {
     *                        "subject" : "message subject",
     *                        "body" : "message body"
     *                        "to" : [
     *                        "torecipient1",
     *                        "torecipient2",
     *                        ...
     *                        ]
     *                        "cc" : [
     *                        "ccrecipient1",
     *                        "ccrecipient2",
     *                        ...
     *                        ]
     *                        "bcc" : [
     *                        "bccrecipient1",
     *                        "bccrecipient2",
     *                        ...
     *                        ]
     *                        }
     * @param user            User infos
     * @param messageId       Id of existing draft to update. Null for draft creation
     * @param parentMessageId Id of the message being replied-to. Null if original message.
     * @param replyType       Type of reply ((r)eply, (f)orward or null, already checked by controller)
     * @param result          result handler
     */
    public void saveDraft(JsonObject frontMessage, UserInfos user, String messageId, String parentMessageId,
                          String replyType, Handler<Either<String, JsonObject>> result) {

        transformMessageFrontToZimbra(frontMessage, messageId, mailContent -> {
            if (messageId != null && !messageId.isEmpty()) {
                mailContent.put(MSG_ID, messageId);
            }
            if (parentMessageId != null && !parentMessageId.isEmpty()) {
                mailContent.put(MSG_ORIGINAL_ID, parentMessageId);
            }
            if (replyType != null) {
                mailContent.put(MSG_REPLYTYPE,
                        FrontConstants.REPLYTYPE_REPLY.equalsIgnoreCase(replyType)
                                ? MSG_RT_REPLY
                                : MSG_RT_FORWARD);
            }
            execSaveDraft(mailContent, user, res -> {
                if (res.isLeft()) {
                    JsonObject callResult = new JsonObject(res.left().getValue());
                    if (!callResult.getString(SoapZimbraService.ERROR_CODE, "").equals(ERROR_NOSUCHMSG)) {
                        log.error("Error when saving draft. User : " + user.getUserId() +
                                " messageId : " + messageId +
                                "error : " + res.left().getValue());
                    }
                }
                result.handle(res);
            });
        });
    }

    private void execSaveDraft(JsonObject mailContent, UserInfos user, Handler<Either<String, JsonObject>> result) {
        execSaveDraftRaw(mailContent, user, response -> {
            if (response.isLeft()) {
                result.handle(response);
            } else {
                processSaveDraft(response.right().getValue(), result);
            }
        });
    }

    /**
     * Save draft without response processing
     *
     * @param mailContent Mail Content
     * @param user        User Infos
     * @param handler     processing handler
     */
    void execSaveDraftRaw(JsonObject mailContent, UserInfos user, Handler<Either<String, JsonObject>> handler) {
        JsonObject saveDraftRequest = new JsonObject()
                .put(Field.NAME, "SaveDraftRequest")
                .put("content", new JsonObject()
                        .put("_jsns", SoapConstants.NAMESPACE_MAIL)
                        .put(MSG, mailContent));

        soapService.callUserSoapAPI(saveDraftRequest, user, handler);
    }

    private String replaceLink(String content) {
        Pattern pattern = Pattern.compile("(?<=<a href=\")(/[^\"]*)");
        Matcher matcher = pattern.matcher(content);
        List<String> modifiedUrls = new ArrayList<>();
        while (matcher.find()) {
            String url = matcher.group(1);
            if (modifiedUrls.contains(url)) continue;
            content = content.replaceAll(url, Zimbra.appConfig.getHost() + url);
            modifiedUrls.add(url);
        }

        return content;
    }

    /**
     * Transform a front message in Zimbra format.
     * Get mail addresses instead of user ids
     *
     * @param frontMessage Front message
     * @param messageId    Message Id
     * @param handler      result handler
     */
    void transformMessageFrontToZimbra(JsonObject frontMessage, String messageId, Handler<JsonObject> handler) {
        JsonArray toFront = frontMessage.getJsonArray(MAIL_TO, new JsonArray());
        JsonArray ccFront = frontMessage.getJsonArray(MAIL_CC, new JsonArray());
        JsonArray bccFront = frontMessage.getJsonArray(MAIL_BCC, new JsonArray()).addAll(frontMessage.getJsonArray(MAIL_BCC_MOBILEAPP, new JsonArray()));
        String bodyFront = replaceLink(frontMessage.getString("body", ""));
        String subjectFront = frontMessage.getString("subject", "");
        JsonArray attsFront = frontMessage.getJsonArray("attachments", new JsonArray());
        JsonArray mailContacts = new JsonArray();
        userService.getMailAddresses(toFront, toResult -> {
            addRecipientToList(mailContacts, toFront, toResult, ADDR_TYPE_TO);
            userService.getMailAddresses(ccFront, ccResult -> {
                addRecipientToList(mailContacts, ccFront, ccResult, ADDR_TYPE_CC);
                userService.getMailAddresses(bccFront, bccResult -> {
                    addRecipientToList(mailContacts, bccFront, bccResult, ADDR_TYPE_BCC);

                    JsonArray mailMessages = new JsonArray()
                            .add(new JsonObject()
                                    .put("content", new JsonObject()
                                            .put("_content", bodyFront))
                                    .put(MULTIPART_CONTENT_TYPE, "text/html"));
                    JsonArray attsZimbra = new JsonArray();
                    if (messageId != null && !messageId.isEmpty() && !attsFront.isEmpty()) {
                        for (Object o : attsFront) {
                            if (!(o instanceof JsonObject)) continue;
                            JsonObject attFront = (JsonObject) o;
                            JsonObject attZimbra = new JsonObject();
                            attZimbra.put(MULTIPART_PART_ID, attFront.getString(Field.ID));
                            attZimbra.put(MULTIPART_MSG_ID, messageId);
                            attsZimbra.add(attZimbra);
                        }
                    }

                    JsonObject mailContent = new JsonObject()
                            .put(MSG_EMAILS, mailContacts)
                            .put(MSG_SUBJECT, new JsonObject()
                                    .put("_content", subjectFront)
                            )
                            .put(MSG_MULTIPART, new JsonObject()
                                    .put(MULTIPART_CONTENT_TYPE, "multipart/alternative")
                                    .put(MSG_MULTIPART, mailMessages)
                            );
                    if (!attsZimbra.isEmpty()) {
                        JsonObject atts = new JsonObject().put(MSG_MULTIPART, attsZimbra);
                        mailContent.put(MSG_NEW_ATTACHMENTS, atts);
                    }
                    handler.handle(mailContent);
                });
            });
        });
    }

    /**
     * Process response from Zimbra API to draft an email
     * In case of success, return Json Object :
     * {
     * "id" : "new-email-zimbra-id"
     * }
     *
     * @param zimbraResponse Zimbra API Response
     * @param handler        Handler result
     */
    private void processSaveDraft(JsonObject zimbraResponse,
                                  Handler<Either<String, JsonObject>> handler) {
        try {
            String idNewDraftEmail = zimbraResponse.getJsonObject("Body")
                    .getJsonObject("SaveDraftResponse")
                    .getJsonArray(MSG).getJsonObject(0)
                    .getString(MSG_ID);

            JsonObject finalResponse = new JsonObject()
                    .put(Field.ID, idNewDraftEmail);

            handler.handle(new Either.Right<>(finalResponse));
        } catch (NullPointerException e) {
            handler.handle(new Either.Left<>("Error when reading response"));
        }
    }

    /**
     * Process save draft to forward response to Front
     *
     * @param zimbraResponse Zimbra API response
     * @param handler        final handler
     */
    void processSaveDraftFull(JsonObject zimbraResponse,  Handler<Either<String, JsonObject>> handler) {
        JsonObject msgDraftedContent = zimbraResponse
                .getJsonObject("Body")
                .getJsonObject("SaveDraftResponse")
                .getJsonArray(MSG).getJsonObject(0);
        JsonArray multiparts = msgDraftedContent.getJsonArray(MSG_MULTIPART, new JsonArray());
        JsonObject resultJson = new JsonObject();
        processMessageMultipart(resultJson, multiparts);
        handler.handle(new Either.Right<>(resultJson));
    }


    /**
     * Move emails to Folder
     *
     * @param listMessageIds Messages ID list selected
     * @param folderId       Folder ID destination
     * @param user           User infos
     * @param result         Empty JsonObject returned, no process needed
     * @deprecated           Use {@link #moveMessagesToFolder(List, String, UserInfos)} instead
     */
    @Deprecated
    public void moveMessagesToFolder(List<String> listMessageIds, String folderId, UserInfos user,
                                     Handler<Either<String, JsonObject>> result) {

        final AtomicInteger processedIds = new AtomicInteger(listMessageIds.size());
        final AtomicInteger successMessages = new AtomicInteger(0);
        String zimbraFolderId = folderService.getZimbraFolderId(folderId);
        for (String messageID : listMessageIds) {
            moveMessageToFolder(messageID, zimbraFolderId, user, resultHandler -> {
                if (resultHandler.isRight()) {
                    successMessages.incrementAndGet();
                }
                if (processedIds.decrementAndGet() == 0) {
                    if (successMessages.get() == listMessageIds.size()) {
                        result.handle(resultHandler);
                    } else {
                        result.handle(new Either.Left<>("Not every message processed"));
                    }
                }
            });
        }
    }

    /**
     * Move emails to Folder
     *
     * @param messageIDs     List of Message IDs
     * @param folderId       Folder ID destination
     * @param user           User infos
     */
    public Future<Void> moveMessagesToFolder(List<String> messageIDs, String folderId, UserInfos user) {
        Promise<Void> promise = Promise.promise();
        List<Future<Void>> futures = new ArrayList<>();
        String zimbraFolderId = folderService.getZimbraFolderId(folderId);

        List<List<String>> batchMessageIds = ArrayHelper.split(messageIDs, BATCH_SIZE);
        for (List<String> ids : batchMessageIds) {
            futures.add(this.moveBatchMessageToFolder(ids, zimbraFolderId, user));
        }

        FutureHelper.all(futures)
                .onSuccess(res -> promise.complete())
                .onFailure(err -> promise.fail(err.getMessage()));

        return promise.future();
    }

    /**
     * Move a batch of emails to Folder
     *
     * @param messageIDs List of Message IDs
     * @param folderId   Folder ID destination
     * @param user       User
     */
    private Future<Void> moveBatchMessageToFolder(List<String> messageIDs, String folderId, UserInfos user) {
        Promise<Void> promise = Promise.promise();

        JsonObject actionReq = new JsonObject()
                .put(MSG_ID, String.join(",", messageIDs))
                .put(MSG_LOCATION, folderId)
                .put(OPERATION, OP_MOVE);

        JsonObject convActionRequest = new JsonObject()
                .put(Field.NAME, "MsgActionRequest")
                .put(SoapConstants.REQ_CONTENT, new JsonObject()
                        .put(SoapConstants.ACTION, actionReq)
                        .put(SoapConstants.REQ_NAMESPACE, SoapConstants.NAMESPACE_MAIL));

        soapService.callUserSoapAPI(convActionRequest, user, response -> {
            if (response.isLeft()) {
                log.error(String.format("[Zimbra@MessageService::moveBatchMessageToFolder] failed to callUserSoapAPI: %s", response.left().getValue()));
                promise.fail(response.left().getValue());
            } else {
                promise.complete();
            }
        });

        return promise.future();
    }


    /**
     * Move an email to Folder
     *
     * @param messageID Message ID
     * @param folderId  Folder ID destination
     * @param user      User
     * @param result    result handler
     * @deprecated      Use {@link #moveMessagesToFolder(List, String, UserInfos)} instead
     */
    @Deprecated
    private void moveMessageToFolder(String messageID, String folderId, UserInfos user,
                                     Handler<Either<String, JsonObject>> result) {
        JsonObject actionReq = new JsonObject()
                .put(MSG_ID, messageID)
                .put(MSG_LOCATION, folderId)
                .put(OPERATION, OP_MOVE);

        JsonObject convActionRequest = new JsonObject()
                .put(Field.NAME, "MsgActionRequest")
                .put("content", new JsonObject()
                        .put("action", actionReq)
                        .put("_jsns", SoapConstants.NAMESPACE_MAIL));

        soapService.callUserSoapAPI(convActionRequest, user, response -> {
            if (response.isLeft()) {
                result.handle(response);
            } else {
                result.handle(new Either.Right<>(new JsonObject()));
            }
        });
    }


    /**
     * Delete emails from trash
     *
     * @param listMessageIds    Messages ID list selected
     * @param user              User infos
     * @param trashConstraint   boolean constraint that messages should be in trash
     * @param result            Empty JsonObject returned, no process needed
     * @deprecated              Use {@link #deleteMessages(List, UserInfos, boolean)} instead
     */
    @Deprecated
    public void deleteMessages(List<String> listMessageIds, UserInfos user, boolean trashConstraint,
                               Handler<Either<String, JsonObject>> result) {

        final AtomicInteger processedIds = new AtomicInteger(listMessageIds.size());
        final AtomicInteger successMessages = new AtomicInteger(0);
        for (String messageID : listMessageIds) {
            deleteMessage(messageID, user, trashConstraint, resultHandler -> {
                if (resultHandler.isRight()) {
                    successMessages.incrementAndGet();
                }
                if (processedIds.decrementAndGet() == 0) {
                    if (successMessages.get() == listMessageIds.size()) {
                        result.handle(resultHandler);
                    } else {
                        result.handle(new Either.Left<>("Not every message processed"));
                    }
                }
            });
        }
    }

    /**
     * Delete list of message Ids from trash
     *
     * @param messageIDs        List of Message ID
     * @param trashConstraint   boolean constraint that the message should be in trash
     */
    public Future<Void> deleteMessages(List<String> messageIDs, String userId, boolean trashConstraint) {
        Promise<Void> promise = Promise.promise();
        List<Future<Void>> futures = new ArrayList<>();

        List<List<String>> batchMessageIds = ArrayHelper.split(messageIDs, BATCH_SIZE);
        for (List<String> ids : batchMessageIds) {
            futures.add(this.deleteBatchMessages(ids, userId, trashConstraint));
        }

        FutureHelper.all(futures)
                .onSuccess(res -> promise.complete())
                .onFailure(err -> promise.fail(err.getMessage()));

        return promise.future();
    }

    /**
     * Delete a batch of messages
     *
     * @param messageIDs        List of Message ID
     * @param userId            User id
     * @param trashConstraint   boolean constraint that the message should be in trash
     */
    private Future<Void> deleteBatchMessages(List<String> messageIDs, String userId, boolean trashConstraint) {
        Promise<Void> promise = Promise.promise();

        JsonObject actionReq = new JsonObject()
                .put(MSG_ID, String.join(",", messageIDs))
                .put(OPERATION, OP_DELETE);

        if (trashConstraint) {
            actionReq.put(MSG_CONSTRAINTS, MSG_CON_TRASH);
        }

        JsonObject convActionRequest = new JsonObject()
                .put(Field.NAME, "MsgActionRequest")
                .put(SoapConstants.REQ_CONTENT, new JsonObject()
                        .put(SoapConstants.ACTION, actionReq)
                        .put(SoapConstants.REQ_NAMESPACE, SoapConstants.NAMESPACE_MAIL));

        soapService.callUserSoapAPI(convActionRequest, userId, response -> {
            if (response.failed()) {
                log.error(String.format("[Zimbra@MessageService::deleteBatchMessages] failed to callUserSoapAPI: %s", response.cause().getMessage()));
                promise.fail(response.cause().getMessage());
            } else {
                    Boolean success = response.result().getBoolean(Field.SUCCESS, false);
                if (success) {
                    promise.complete();
                } else {
                    promise.fail(ErrorEnum.FAIL_DELETE_MAIL.method());
                }
                
            }
        });

        return promise.future();
    }


    /**
     * Delete an email from trash
     *
     * @param messageID         Message ID
     * @param user              User
     * @param trashConstraint   boolean constraint that the message should be in trash
     * @param result            result handler
     * @deprecated      Use {@link #deleteMessages(List, UserInfos, boolean)} instead
     */
    @Deprecated
    private void deleteMessage(String messageID, UserInfos user, boolean trashConstraint,
                               Handler<Either<String, JsonObject>> result) {
        JsonObject actionReq = new JsonObject()
                .put(MSG_ID, messageID)
                .put(OPERATION, OP_DELETE);

        if (trashConstraint) {
            actionReq.put(MSG_CONSTRAINTS, MSG_CON_TRASH);
        }

        JsonObject convActionRequest = new JsonObject()
                .put(Field.NAME, "MsgActionRequest")
                .put("content", new JsonObject()
                        .put("action", actionReq)
                        .put("_jsns", SoapConstants.NAMESPACE_MAIL));

        soapService.callUserSoapAPI(convActionRequest, user, response -> {
            if (response.isLeft()) {
                result.handle(response);
            } else {
                result.handle(new Either.Right<>(new JsonObject()));
            }
        });
    }

    public Future<List<String>> retrieveMailFromZimbra(JsonObject returnedMail, JsonObject toUserInfos) {
        Promise<List<String>> promise = Promise.promise();

        retrieveMailFromZimbra(returnedMail, toUserInfos, res -> {
            if (res.isRight()) {
                promise.complete(res.right().getValue());
            } else {
                String errMessage = String.format("[Zimbra@%s::retrieveMailFromZimbra]:  " +
                                "error while retrieving mail from zimbra : %s",
                        this.getClass().getSimpleName(), res.left().getValue());
                log.error(errMessage);
                promise.fail(ErrorEnum.ERROR_RETRIEVING_MAIL.method());
            }
        });

        return promise.future();
    }

    /**
     * Retrieve an unique email from inbox
     *
     * @param returnedMail  JsonObject containing data like object, user_id or mail_date
     * @param to_user_infos User id and mail of the recipient
     * @param result        result handler
     */
    public void retrieveMailFromZimbra(JsonObject returnedMail, JsonObject to_user_infos, Handler<Either<String, List<String>>> result) {
        String msgid = returnedMail.getString("mid");

        String to_user_id = to_user_infos.getString(Field.ID);
        ZimbraUser user = new ZimbraUser(new MailAddress(to_user_infos.getString("mail")));
        user.checkIfExists(userResponse -> {
            if (userResponse.failed()) {
                log.error("[Zimbra] retrieveMailFromZimbra : Error while checking if user exists in Zimbra :" + userResponse.cause().getMessage());
                result.handle(new Either.Left<>(ErrorEnum.USER_NOT_DEFINED.method()));
            } else {
                if (user.existsInZimbra()) {
                    String query = "* msgid:\"" + msgid + "\"";
                    SoapSearchHelper.searchAllMailedConv(to_user_id, 0, query, event -> {
                        if (event.succeeded()) {
                            if (event.result().size() > 0) {
                                List<String> ids = new ArrayList<>();
                                ids.add(event.result().get(0).getMessageList().get(0).getId());
                                result.handle(new Either.Right<>(ids));
                            } else {
                                result.handle(new Either.Right<>(new ArrayList<>()));
                            }
                        } else {
                            log.error("[Zimbra] retrieveMailFromZimbra : Error while searching mails : " + event.cause().getMessage());
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            reccursiveSearch(to_user_id, query, result, 0);
                        }
                    });
                } else {
                    log.error("[Zimbra] retrieveMailFromZimbra : No Zimbra acc found");
                    result.handle(new Either.Right<>(new ArrayList<>()));
                }
            }
        });
    }

    //Todo : A tester sans reccursif dans le cas où on ne trouve pas de mail pour l'utilsiateur mais à laisser dans le cas d'une erreur Zimbra
    private void reccursiveSearch(String user_id, String query, Handler<Either<String, List<String>>> result, int nb) {
        log.info(query);
        SoapSearchHelper.searchAllMailedConv(user_id, 0, query, event -> {
            if (event.succeeded()) {
                if (event.result().size() > 0) {
                    List<String> ids = new ArrayList<>();
                    ids.add(event.result().get(0).getMessageList().get(0).getId());
                    result.handle(new Either.Right<>(ids));
                } else {
                    if (nb < 5) {
                        reccursiveSearch(user_id, query, result, nb + 1);
                    } else {
                        result.handle(new Either.Right<>(new ArrayList<>()));
                        log.error("[Zimbra] retrieveMailFromZimbra : No mails found");
                    }
                }
            } else {
                log.error("[Zimbra] reccursiveSearch : Error while searching mails : " + event.cause().getMessage());
                result.handle(new Either.Left<>(event.cause().getMessage()));
            }
        });
    }


    /**
     * Mark emails as unread / read
     *
     * @param listMessageIds Messages ID list selected
     * @param unread         boolean
     * @param user           User infos
     * @param result         Empty JsonObject returned, no process needed
     */
    public void toggleUnreadMessages(List<String> listMessageIds, boolean unread, UserInfos user, Handler<Either<String, JsonObject>> result) {

        final AtomicInteger processedIds = new AtomicInteger(listMessageIds.size());
        final AtomicInteger successMessages = new AtomicInteger(0);
        for (String messageID : listMessageIds) {
            toggleUnreadMessage(messageID, unread, user, resultHandler -> {
                if (resultHandler.isRight()) {
                    successMessages.incrementAndGet();
                }
                if (processedIds.decrementAndGet() == 0) {
                    if (successMessages.get() == listMessageIds.size()) {
                        result.handle(resultHandler);
                    } else {
                        result.handle(new Either.Left<>("Not every message processed"));
                    }
                }
            });
        }

    }

    /**
     * Mark email as unread / read
     *
     * @param messageID Message ID
     * @param unread    boolean
     * @param user      User
     * @param result    result handler
     */
    private void toggleUnreadMessage(String messageID,
                                     boolean unread,
                                     UserInfos user,
                                     Handler<Either<String, JsonObject>> result) {

        String insertField = unread
                ? OP_UNREAD
                : OP_READ;

        JsonObject actionReq = new JsonObject()
                .put(MSG_ID, messageID)
                .put(OPERATION, insertField);

        JsonObject msgActionRequest = new JsonObject()
                .put(Field.NAME, "MsgActionRequest")
                .put("content", new JsonObject()
                        .put("action", actionReq)
                        .put("_jsns", SoapConstants.NAMESPACE_MAIL));

        soapService.callUserSoapAPI(msgActionRequest, user, response -> {
            if (response.isLeft()) {
                result.handle(response);
            } else {
                result.handle(new Either.Right<>(new JsonObject()));
            }
        });
    }

    private void getMessageMidFromId(UserInfos user, String messageId, Handler<String> handler) {
        if (messageId == null || messageId.isEmpty()) {
            handler.handle(null);
        } else {
            SoapMessageHelper.getMessageById(user.getUserId(), messageId, result -> {
                if (result.succeeded() && !result.result().getMailId().isEmpty()) {
                    handler.handle(result.result().getMailId());
                } else {
                    handler.handle(null);
                }
            });
        }
    }

    public Future<JsonObject> sendDeliveryReport(UserInfos user, String messageId) {
        Promise<JsonObject> promise = Promise.promise();
        JsonObject convActionRequest = new JsonObject()
                .put(ACCT_NAME, SEND_DELIVERY_REPORT_REQUEST)
                .put(MULTIPART_CONTENT, new JsonObject()
                        .put(SoapConstants.MESSAGE_ID, messageId)
                        .put(SoapConstants.REQ_NAMESPACE, SoapConstants.NAMESPACE_MAIL));

        soapService.callUserSoapAPI(convActionRequest, user, handlerJsonObject(promise,
                String.format("[Zimbra@%s::sendDeliveryReport]: %s",
                        FutureHelper.class.getSimpleName(), "Failed to send delivery report.")));

        return promise.future();
    }

    public Future<JsonObject> renderHtmlContent (JsonObject message, UserInfos user){
        Promise<JsonObject> promise = Promise.promise();

            List<String> cids = CidHelper.getMessageCids(message);
            Map<String, String> mapCidToImageUrl = CidHelper.mapCidToImageUrl(message.getString(FrontConstants.MESSAGE_ID), cids);
            soapService.getUserAuthToken(user)
                    .compose(authToken -> fetchAllImagesBuffer(mapCidToImageUrl, authToken))
                    .compose(mapCidImageBuffer -> {
                        Map<String,String> mapCidToBase64 = CidHelper.mapCidToBase64(mapCidImageBuffer);
                        JsonObject messageReplaced = CidHelper.replaceCidByBase64(message, mapCidToBase64);
                        return Future.succeededFuture(messageReplaced);
                    })
                    .onSuccess(promise::complete)
                    .onFailure(err -> promise.complete(message));
        return promise.future();
    }


    private Future<Map<String, Buffer>> fetchAllImagesBuffer(Map<String, String> mapCidToImageUrl, String authToken) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put(Field.COOKIE, "ZM_AUTH_TOKEN=" + authToken);

        List<Future<AbstractMap.SimpleEntry<String, Buffer>>> futures = new ArrayList<>();


        mapCidToImageUrl.forEach((cid, url) ->
                futures.add(httpService.get(url, headers)
                        .map(buffer -> new AbstractMap.SimpleEntry<>(cid, buffer))) //Pairing cid with buffer
        );

        return FutureHelper.all(futures)
                .map(compositeFuture -> futures.stream()
                        .collect(Collectors.toMap(
                                future -> future.result().getKey(),
                                future -> future.result().getValue()
                        ))
                );
    }
}


