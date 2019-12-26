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
import fr.openent.zimbra.helper.ConfigManager;
import fr.openent.zimbra.model.ZimbraUser;
import fr.openent.zimbra.model.constant.FrontConstants;
import fr.openent.zimbra.model.constant.I18nConstants;
import fr.openent.zimbra.model.constant.SoapConstants;
import fr.openent.zimbra.model.message.Multipart;
import fr.openent.zimbra.service.DbMailService;
import fr.openent.zimbra.service.data.SoapZimbraService;
import fr.openent.zimbra.service.synchro.SynchroUserService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.user.UserInfos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static fr.openent.zimbra.model.constant.FrontConstants.*;
import static fr.openent.zimbra.model.constant.ZimbraErrors.ERROR_NOSUCHMSG;
import static fr.openent.zimbra.model.constant.ZimbraConstants.*;
import static fr.openent.zimbra.model.constant.SoapConstants.*;

public class MessageService {

    private SoapZimbraService soapService;
    private FolderService folderService;
    private DbMailService dbMailService;
    private UserService userService;
    private GroupService groupService;
    private static Logger log = LoggerFactory.getLogger(MessageService.class);

    public MessageService(SoapZimbraService soapService, FolderService folderService,
                          DbMailService dbMailService, UserService userService, SynchroUserService synchroUserService) {
        this.soapService = soapService;
        this.folderService = folderService;
        this.dbMailService = dbMailService;
        this.userService = userService;
        this.groupService = new GroupService(soapService, dbMailService, synchroUserService);
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

                    String folderPath = folder.getString(FOLDER_ABSPATH);

                    String query = pathToQuery(folderPath);
                    if(unread) {
                        query += " is:unread";
                    }
                    if(searchText != null && ! searchText.isEmpty()) {
                        query += " *" + searchText +"*";
                    }
                    JsonObject searchReq = new JsonObject()
                            .put("query", query)
                            .put("types", "message")
                            .put("recip", "2")
                            .put("limit", Zimbra.MAIL_LIST_LIMIT)
                            .put("offset", page * Zimbra.MAIL_LIST_LIMIT)
                            .put("_jsns", SoapConstants.NAMESPACE_MAIL);

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
        Map<String,String> addressMap = new HashMap<>();
        processSearchResult(zimbraMessages, frontMessages, addressMap, result);
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
     * @param addressMap mapping of user email addresses and neo ids
     * @param result final handler
     */
    private void processSearchResult(JsonArray zimbraMessages, JsonArray frontMessages, Map<String,String> addressMap,
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
     * @param msgZimbra object to transform
     * @param msgFront initial object sent to Front
     * @param result handler result
     */
    private void transformMessageZimbraToFront(JsonObject msgZimbra, JsonObject msgFront,
                                               Map<String,String> addressMap, Handler<JsonObject> result) {
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
        String folder ;
        if(flags.contains(MSG_FLAG_DRAFT)){
            folder = "DRAFT";
        }else if (flags.contains(MSG_FLAG_SENTBYME)) {
            folder = "OUTBOX";
        }else {
            folder = "INBOX";
        }

        msgFront.put("state", state);
        msgFront.put("unread", flags.contains(MSG_FLAG_UNREAD));
        msgFront.put("response", flags.contains(MSG_FLAG_REPLIED));//TODO QMER : verify sendmsgresponse : modified f : r ?
        msgFront.put("hasAttachment", flags.contains(MSG_FLAG_HASATTACHMENT));
        msgFront.put("systemFolder",folder);
        msgFront.put("to", new JsonArray());
        msgFront.put("cc", new JsonArray());
        msgFront.put("bcc", new JsonArray());
        msgFront.put("displayNames", new JsonArray());
        msgFront.put("attachments", new JsonArray());


        JsonArray zimbraMails = msgZimbra.getJsonArray(MSG_EMAILS);

        if(msgZimbra.containsKey(MSG_MULTIPART)) {
            JsonArray multiparts = msgZimbra.getJsonArray(MSG_MULTIPART);
            processMessageMultipart(msgFront, multiparts);
        }

        translateMaillistToUidlist(msgFront, zimbraMails, addressMap, result);
    }

    /**
     * Process multiparts from a Zimbra message, and add relevant info to Front JsonObject
     * Process recursively every multipart
     * @param msgFront Front JsonObject
     * @param multiparts Array of multipart structure
     */
    private void processMessageMultipart(JsonObject msgFront, JsonArray multiparts) {
        Multipart mparts = new Multipart(msgFront.getString(MESSAGE_ID), multiparts);
        msgFront.put(MESSAGE_BODY, mparts.getBody());
        msgFront.put(MESSAGE_ATTACHMENTS, mparts.getAttachmentsJson());
    }



    /**
     * Process list of mail address in a mail and transform it in Front data
     * @param frontMsg JsonObject receiving Front-formatted data
     * @param zimbraMails JsonObject containing mail addresses
     * @param handler result handler
     */
    private void translateMaillistToUidlist(JsonObject frontMsg, JsonArray zimbraMails, Map<String,String> addressMap,
                                            Handler<JsonObject> handler) {
        if(zimbraMails == null || zimbraMails.isEmpty()) {
            handler.handle(frontMsg);
            return;
        }
        JsonObject zimbraUser = zimbraMails.getJsonObject(0);
        String type = (zimbraUser==null) ? "" :  zimbraUser.getString(MSG_EMAIL_TYPE);

        if(!(type.equals(ADDR_TYPE_FROM))
            && !(type.equals(ADDR_TYPE_CC))
            && !(type.equals(ADDR_TYPE_TO))
            && !(type.equals(ADDR_TYPE_BCC))) {
            zimbraMails.remove(0);
            translateMaillistToUidlist(frontMsg, zimbraMails, addressMap, handler);
            return;
        }

        String zimbraMail = zimbraUser.getString(MSG_EMAIL_ADDR, "");
        if(zimbraMail.isEmpty()) {
            zimbraMails.remove(0);
            translateMaillistToUidlist(frontMsg, zimbraMails, addressMap, handler);
        } else {
            Handler<String> translatedUuidHandler = userUuid -> {
                if (userUuid == null) {
                    userUuid = zimbraMail;
                }
                addressMap.put(zimbraMail,userUuid);
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
                }
                frontMsg.put("displayNames", frontMsg.getJsonArray("displayNames")
                        .add(new JsonArray()
                                .add(userUuid)
                                .add(zimbraUser.getString(MSG_EMAIL_COMMENT, zimbraMail))));
                zimbraMails.remove(0);
                translateMaillistToUidlist(frontMsg, zimbraMails, addressMap, handler);
            };

            if(addressMap.containsKey(zimbraMail)) {
                translatedUuidHandler.handle(addressMap.get(zimbraMail));
            } else {
                translateMail(zimbraMail,translatedUuidHandler);
            }
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
        try {
            String domain = mail.split("@")[1];
            if(!Zimbra.domain.equals(domain)) {
                handler.handle(null);
                return;
            }
        } catch (Exception e) {
            handler.handle(null);
            return;
        }
        dbMailService.getNeoIdFromMail(mail, sqlResponse -> {
            if(sqlResponse.isLeft() || sqlResponse.right().getValue().isEmpty()) {
                log.debug("no user in database for address : " + mail);
                userService.getAliases(mail, zimbraResponse -> {
                    if(zimbraResponse.succeeded()) {
                        ZimbraUser user = zimbraResponse.result();
                        if(user.getFirstAliasName().isEmpty()) {
                            handler.handle(null);
                        } else {
                            handler.handle(user.getFirstAliasName());
                        }
                    } else {
                        handler.handle(groupService.getGroupId(mail));
                    }
                });
            } else {
                JsonArray results = sqlResponse.right().getValue();
                if(results.size() > 1) {
                    log.warn("More than one user id for address : " + mail);
                }
                String uuid = results.getJsonObject(0).getString(DbMailService.NEO4J_UID);
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
     * and process it for Front
     * @param messageId Id of message to get
     * @param user User infos
     * @param handler Final handler
     */
    public void getMessage(String messageId, UserInfos user, Handler<Either<String,JsonObject>> handler) {
        JsonObject messageReq = new JsonObject()
                .put("html", 1)
                .put("read", Zimbra.appConfig.isActionBlocked(ConfigManager.UPDATE_ACTION) ? 0 : 1)
                .put("needExp", 1)
                .put("neuter", 0)
                .put("id", messageId);

        JsonObject getMsgRequest = new JsonObject()
                .put("name", "GetMsgRequest")
                .put("content", new JsonObject()
                    .put(MSG, messageReq)
                    .put("_jsns", SoapConstants.NAMESPACE_MAIL));

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
     *     "displayNames" : [
     *      "user_id",
     *      "user_display_name",
     *     ],
     *     "date" : datesent,
     *     "unread" : boolean_unread,
     *     "attachments" : [{
     *      "id" : "attachment_id",
     *      "filename" : "attachment_filename",
     *      "contentType" : "attachment_type",
     *      "size" : "attachment_size"
     *     },
     *     ]
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

        Map<String,String> addressMap = new HashMap<>();
        transformMessageZimbraToFront(msgZimbra, msgFront, addressMap,
                result -> handler.handle(new Either.Right<>(msgFront)) );
    }

    /**
     * Empty trash
     * @param user User infos
     * @param handler Empty JsonObject returned, no process needed
     */
    public void emptyTrash(UserInfos user,
                           Handler<Either<String,JsonObject>> handler) {
        JsonObject actionReq = new JsonObject()
                .put(MSG_ID, FOLDER_TRASH_ID)
                .put(OPERATION, OP_EMPTY)
                .put("recursive", "true");

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
     * Send Message
     * @param frontMessage JsonObject containing front message :
     * {
     *   "subject" : "message subject",
     *   "body" : "message body"
     *   "to" : [
     *     "torecipient1",
     *     "torecipient2",
     *     ...
     *   ]
     *   "cc" : [
     *     "ccrecipient1",
     *     "ccrecipient2",
     *     ...
     *   ]
     * }
     * @param user User infos
     * @param result result handler
     */
    public void sendMessage(String messageId, JsonObject frontMessage, UserInfos user,
                            Handler<Either<String, JsonObject>> result) {

        Integer maxRecipients = Zimbra.appConfig.getMaxRecipients();

        JsonArray listRecipientsTo = frontMessage.getJsonArray(FrontConstants.MAIL_TO, new JsonArray());
        JsonArray listRecipientsCC = frontMessage.getJsonArray(FrontConstants.MAIL_CC, new JsonArray());

        Integer totalRecipients = listRecipientsTo.size() + listRecipientsCC.size();

        if (totalRecipients > maxRecipients) {
            result.handle(new Either.Left<>(I18nConstants.ERROR_MAXRECIPIENTS));
            return;
        }

        transformMessageFrontToZimbra(frontMessage, messageId, mailContent -> {
            if(messageId != null && !messageId.isEmpty()) {
                mailContent.put(MSG_ID, messageId);
                mailContent.put(MSG_DRAFT_ID, messageId);
            }
            JsonObject sendMsgRequest = new JsonObject()
                    .put("name", "SendMsgRequest")
                    .put("content", new JsonObject()
                            .put("_jsns", SoapConstants.NAMESPACE_MAIL)
                            .put(MSG, mailContent));

            soapService.callUserSoapAPI(sendMsgRequest, user, response -> {
                 if (response.isLeft()) {
                     result.handle(response);
                 } else {
                     JsonObject rightResponse = new JsonObject()
                             .put("sent", 1);
                     result.handle(new Either.Right<>(rightResponse));
                 }
            });
        });

    }

    /**
     * Add an email recipient in recipientList, from a list of Ids and correspondance
     * @param recipientList Final email recipient List
     * @param originList Initial Id list
     * @param correspondance Correspondance between ids and emails
     * @param type Type of list (to, cc...)
     */
    private void addRecipientToList(JsonArray recipientList, JsonArray originList,
                                    JsonObject correspondance, String type) {
        for(Object o : originList) {
            String elemId = (String)o;
            if(correspondance.containsKey(elemId)) {
                JsonObject elemInfos = correspondance.getJsonObject(elemId);
                JsonObject recipient = new JsonObject()
                        .put(MSG_EMAIL_TYPE, type)
                        .put(MSG_EMAIL_ADDR, elemInfos.getString("email"));
                if(!elemInfos.getString("displayName", "").isEmpty()) {
                    recipient.put(MSG_EMAIL_COMMENT, elemInfos.getString("displayName", ""));
                }
                recipientList.add(recipient);
            } else {
                log.error("No Zimbra correspondance for ID : " + elemId);
            }
        }
    }


    /**
     * Save Draft
     * @param frontMessage JsonObject containing front message :
     * {
     *   "subject" : "message subject",
     *   "body" : "message body"
     *   "to" : [
     *     "torecipient1",
     *     "torecipient2",
     *     ...
     *   ]
     *   "cc" : [
     *     "ccrecipient1",
     *     "ccrecipient2",
     *     ...
     *   ]
     *   "bcc" : [
     *     "bccrecipient1",
     *     "bccrecipient2",
     *     ...
     *   ]
     * }
     * @param user User infos
     * @param messageId Id of existing draft to update. Null for draft creation
     * @param parentMessageId Id of the message being replied-to. Null if original message.
     * @param replyType Type of reply ((r)eply, (f)orward or null, already checked by controller)
     * @param result result handler
     */
    public void saveDraft(JsonObject frontMessage, UserInfos user, String messageId, String parentMessageId,
                          String replyType, Handler<Either<String, JsonObject>> result) {

        transformMessageFrontToZimbra(frontMessage, messageId, mailContent -> {
            if(messageId != null && !messageId.isEmpty()) {
                mailContent.put(MSG_ID, messageId);
            }
            if(parentMessageId != null && !parentMessageId.isEmpty()) {
                mailContent.put(MSG_ORIGINAL_ID, parentMessageId);
            }
            if(replyType != null) {
                mailContent.put(MSG_REPLYTYPE,
                        FrontConstants.REPLYTYPE_REPLY.equalsIgnoreCase(replyType)
                        ? MSG_RT_REPLY
                        : MSG_RT_FORWARD);
            }
            execSaveDraft(mailContent, user, res -> {
                if(res.isLeft()) {
                    JsonObject callResult = new JsonObject(res.left().getValue());
                    if(!callResult.getString(SoapZimbraService.ERROR_CODE,"").equals(ERROR_NOSUCHMSG)) {
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
            if(response.isLeft()) {
                result.handle(response);
            } else {
                processSaveDraft(response.right().getValue(), result);
            }
        });
    }

    /**
     * Save draft without response processing
     * @param mailContent Mail Content
     * @param user User Infos
     * @param handler processing handler
     */
    void execSaveDraftRaw(JsonObject mailContent, UserInfos user, Handler<Either<String, JsonObject>> handler) {
        JsonObject saveDraftRequest = new JsonObject()
                .put("name", "SaveDraftRequest")
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
     * @param frontMessage Front message
     * @param messageId Message Id
     * @param handler result handler
     */
    void transformMessageFrontToZimbra(JsonObject frontMessage, String messageId, Handler<JsonObject> handler) {
        JsonArray toFront = frontMessage.getJsonArray("to");
        JsonArray ccFront = frontMessage.getJsonArray("cc");
        JsonArray bccFront = frontMessage.getJsonArray("bcc");
        String bodyFront = replaceLink(frontMessage.getString("body"));
        String subjectFront = frontMessage.getString("subject");
        JsonArray attsFront = frontMessage.getJsonArray("attachments");
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
                    if(messageId != null && !messageId.isEmpty() && !attsFront.isEmpty()) {
                        for(Object o : attsFront) {
                            if(!(o instanceof JsonObject)) continue;
                            JsonObject attFront = (JsonObject) o;
                            JsonObject attZimbra = new JsonObject();
                            attZimbra.put(MULTIPART_PART_ID, attFront.getString("id"));
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
                    if(!attsZimbra.isEmpty()) {
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
     * 	    "id" : "new-email-zimbra-id"
     * }
     * @param zimbraResponse Zimbra API Response
     * @param handler Handler result
     */
    private void processSaveDraft(JsonObject zimbraResponse,
                                  Handler<Either<String, JsonObject>> handler) {
        try {
            String idNewDraftEmail = zimbraResponse.getJsonObject("Body")
                    .getJsonObject("SaveDraftResponse")
                    .getJsonArray(MSG).getJsonObject(0)
                    .getString(MSG_ID);

            JsonObject finalResponse = new JsonObject()
                    .put("id", idNewDraftEmail);

            handler.handle(new Either.Right<>(finalResponse));
        } catch (NullPointerException e) {
            handler.handle(new Either.Left<>("Error when reading response"));
        }
    }

    /**
     * Process save draft to forward response to Front
     * @param zimbraResponse Zimbra API response
     * @param handler final handler
     */
    void processSaveDraftFull(JsonObject zimbraResponse, Handler<Either<String, JsonObject>> handler) {
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
     * @param listMessageIds Messages ID list selected
     * @param folderId Folder ID destination
     * @param user User infos
     * @param result Empty JsonObject returned, no process needed
     */
    public void moveMessagesToFolder(List<String> listMessageIds, String folderId, UserInfos user,
                             Handler<Either<String, JsonObject>> result) {

        final AtomicInteger processedIds = new AtomicInteger(listMessageIds.size());
        final AtomicInteger successMessages = new AtomicInteger(0);
        String zimbraFolderId = folderService.getZimbraFolderId(folderId);
        for(String messageID : listMessageIds) {
            moveMessageToFolder(messageID, zimbraFolderId, user, resultHandler -> {
                if(resultHandler.isRight()) {
                    successMessages.incrementAndGet();
                }
                if(processedIds.decrementAndGet() == 0) {
                    if(successMessages.get() == listMessageIds.size()) {
                        result.handle(resultHandler);
                    } else {
                        result.handle(new Either.Left<>("Not every message processed"));
                    }
                }
            });
        }
    }


    /**
     * Move an email to Folder
     * @param messageID Message ID
     * @param folderId Folder ID destination
     * @param user User
     * @param result result handler
     */
    private void moveMessageToFolder(String messageID, String folderId, UserInfos user,
                                     Handler<Either<String,JsonObject>> result) {
        JsonObject actionReq = new JsonObject()
                .put(MSG_ID, messageID)
                .put(MSG_LOCATION, folderId)
                .put(OPERATION, OP_MOVE);

        JsonObject convActionRequest = new JsonObject()
                .put("name", "MsgActionRequest")
                .put("content", new JsonObject()
                        .put("action", actionReq)
                        .put("_jsns", SoapConstants.NAMESPACE_MAIL));

        soapService.callUserSoapAPI(convActionRequest, user, response -> {
            if(response.isLeft()) {
                result.handle(response);
            } else {
                result.handle(new Either.Right<>(new JsonObject()));
            }
        });
    }



    /**
     * Delete emails from trash
     * @param listMessageIds Messages ID list selected
     * @param user User infos
     * @param result Empty JsonObject returned, no process needed
     */
    public void deleteMessages(List<String> listMessageIds, UserInfos user,
                                     Handler<Either<String, JsonObject>> result) {

        final AtomicInteger processedIds = new AtomicInteger(listMessageIds.size());
        final AtomicInteger successMessages = new AtomicInteger(0);
        for(String messageID : listMessageIds) {
            deleteMessage(messageID, user, resultHandler -> {
                if(resultHandler.isRight()) {
                    successMessages.incrementAndGet();
                }
                if(processedIds.decrementAndGet() == 0) {
                    if(successMessages.get() == listMessageIds.size()) {
                        result.handle(resultHandler);
                    } else {
                        result.handle(new Either.Left<>("Not every message processed"));
                    }
                }
            });
        }
    }


    /**
     * Delete an email from trash
     * @param messageID Message ID
     * @param user User
     * @param result result handler
     */
    private void deleteMessage(String messageID, UserInfos user,
                                     Handler<Either<String,JsonObject>> result) {
        JsonObject actionReq = new JsonObject()
                .put(MSG_ID, messageID)
                .put(OPERATION, OP_DELETE)
                .put(MSG_CONSTRAINTS, MSG_CON_TRASH);

        JsonObject convActionRequest = new JsonObject()
                .put("name", "MsgActionRequest")
                .put("content", new JsonObject()
                        .put("action", actionReq)
                        .put("_jsns", SoapConstants.NAMESPACE_MAIL));

        soapService.callUserSoapAPI(convActionRequest, user, response -> {
            if(response.isLeft()) {
                result.handle(response);
            } else {
                result.handle(new Either.Right<>(new JsonObject()));
            }
        });
    }


    /**
     * Mark emails as unread / read
     * @param listMessageIds Messages ID list selected
     * @param unread boolean
     * @param user User infos
     * @param result Empty JsonObject returned, no process needed
     */
    public void toggleUnreadMessages(List<String> listMessageIds, boolean unread, UserInfos user, Handler<Either<String, JsonObject>> result) {

        final AtomicInteger processedIds = new AtomicInteger(listMessageIds.size());
        final AtomicInteger successMessages = new AtomicInteger(0);
        for(String messageID : listMessageIds) {
            toggleUnreadMessage(messageID, unread, user, resultHandler -> {
                if(resultHandler.isRight()) {
                    successMessages.incrementAndGet();
                }
                if(processedIds.decrementAndGet() == 0) {
                    if(successMessages.get() == listMessageIds.size()) {
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
     * @param messageID Message ID
     * @param unread boolean
     * @param user User
     * @param result result handler
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
                .put("name", "MsgActionRequest")
                .put("content", new JsonObject()
                        .put("action", actionReq)
                        .put("_jsns", SoapConstants.NAMESPACE_MAIL));

        soapService.callUserSoapAPI(msgActionRequest, user, response -> {
            if(response.isLeft()) {
                result.handle(response);
            } else {
                result.handle(new Either.Right<>(new JsonObject()));
            }
        });
    }
}
