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

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.helper.FileHelper;
import fr.openent.zimbra.helper.HttpClientHelper;
import fr.openent.zimbra.helper.PromiseHelper;
import fr.openent.zimbra.service.data.SoapZimbraService;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.Utils;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.streams.Pump;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.entcore.common.bus.WorkspaceHelper;
import org.entcore.common.storage.Storage;
import org.entcore.common.user.UserInfos;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.Queue;
import java.util.regex.Pattern;

import static fr.openent.zimbra.model.constant.ZimbraConstants.*;
import static fr.wseduc.webutils.Utils.handlerToAsyncHandler;

public class AttachmentService {

    private final SoapZimbraService soapService;
    private final MessageService messageService;
    private final Vertx vertx;
    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);
    private final String zimbraUrlAttachment;
    private final String zimbraUrlUpload;
    private final Queue<HttpClient> httpClientPool;
    private final WebClient client;

    public AttachmentService( SoapZimbraService soapService, MessageService messageService,
                              Vertx vertx, JsonObject config, WebClient webClient) {
        String zimbraUri = config.getString("zimbra-uri", "");
        this.zimbraUrlAttachment = zimbraUri + "/service/home/~/?auth=co";
        this.zimbraUrlUpload = zimbraUri + "/service/upload?fmt=extended,raw";
        this.vertx = vertx;
        this.soapService = soapService;
        this.messageService = messageService;
        this.httpClientPool = new LinkedList<>();
        this.client = webClient;
    }

    /**
     * Get attachment to computer from Zimbra
     * dump zimbra request content in the response made to the Front
     *
     * @param messageId        Id of the message that has the attachment
     * @param attachmentPartId Id of the part where the attachment is
     * @param user             User infos
     * @param inline           Must the part be sent inline ?
     * @param httpServerRequest httpServerRequest where the file must be sent
     * @param handler          final handler, only use in case of error
     */
    public void getAttachmentToComputer(String messageId, String attachmentPartId, UserInfos user, Boolean inline,
                                        HttpServerRequest httpServerRequest, Handler<Either<String,JsonObject>> handler) {
        getAttachment(messageId, attachmentPartId, user, inline)
                .compose(zimbraResponse -> uploadToComputer(httpServerRequest, zimbraResponse))
                .onSuccess(res -> handler.handle(new Either.Right<>(new JsonObject())))
                .onFailure(err -> {
                    String messageToFormat = "[Zimbra@getAttachmentToComputer] Error in getAttachmentToComputer : " + err.getMessage();
                    handler.handle(new Either.Left<>(messageToFormat));
                });
    }

    /**
     * Get attachment to workspace from Zimbra
     * dump zimbra request content in the response made to the Front
     * @param messageId Id of the message that has the attachment
     * @param attachmentPartId Id of the part where the attachment is
     * @param user User infos
     * @param inline Must the part be sent inline
     * @param storage storage
     * @param workspaceHelper workspaceHelper
     * @param handler final handler, only use in case of error
     */
    public void getAttachmentToWorkspace(String messageId, String attachmentPartId, UserInfos user, Boolean inline, Storage storage,
                                                      WorkspaceHelper workspaceHelper, Handler<Either<String,JsonObject>> handler) {
        getAttachment(messageId, attachmentPartId, user, inline)
                .compose(zimbraResponse -> uploadToWorkspace(user, storage, workspaceHelper, zimbraResponse))
                .onSuccess(res -> handler.handle(new Either.Right<>(new JsonObject())))
                .onFailure(err -> {
                    String messageToFormat = "[Zimbra@getAttachmentToWorkspace] Error in getAttachmentToWorkspace : " + err.getMessage();
                    handler.handle(new Either.Left<>(messageToFormat));
                });
    }

    /**
     * Get attachment from Zimbra
     * 1- get auth token
     * 2- create request to get attachment from Zimbra
     * 3- dump zimbra request content in the response made to the Front
     * @param messageId Id of the message that has the attachment
     * @param attachmentPartId Id of the part where the attachment is
     * @param user User infos
     * @param inline Must the part be sent inline ?
     */
    public Future<HttpClientResponse> getAttachment(String messageId, String attachmentPartId, UserInfos user, Boolean inline) {
        Promise<HttpClientResponse> promise = Promise.promise();
        String disp = inline ? DISPLAY_INLINE : DISPLAY_ATTACHMENT;
        String urlAttachment = zimbraUrlAttachment + "&id=" + messageId + "&part="
                + attachmentPartId + "&disp=" + disp;

        soapService.getUserAuthToken(user, authTokenResponse -> {
            if(authTokenResponse.isLeft()) {
                String messageToFormat = "Zimbra@getAttachment : getUserAuthToken failure : " + authTokenResponse.left().getValue();
                PromiseHelper.reject(log, messageToFormat, AttachmentService.class.getSimpleName(), authTokenResponse, promise);
            } else {
                String authToken = authTokenResponse.right().getValue().getString(Field.AUTH_TOKEN);
                if (httpClientPool.isEmpty()) {
                    httpClientPool.add(HttpClientHelper.createHttpClient(vertx));
                }
                HttpClient httpClient = httpClientPool.poll();
                if (httpClient == null) {
                    String messageToFormat = "Zimbra@getAttachment : Null httpClient, can't upload attachment";
                    PromiseHelper.reject(log, messageToFormat, AttachmentService.class.getSimpleName(), new Exception(messageToFormat), promise);
                } else {
                    HttpClientRequest zimbraRequest = httpClient.getAbs(urlAttachment, promise::complete);
                    zimbraRequest.exceptionHandler(err -> {
                        String messageToFormat = "Zimbra@getAttachment : Error when getting attachment : " + err.getMessage();
                        PromiseHelper.reject(log, messageToFormat, AttachmentService.class.getSimpleName(), err, promise);
                    });
                    zimbraRequest.setChunked(true)
                            .putHeader(Field.COOKIE, "ZM_AUTH_TOKEN=" + authToken);
                    zimbraRequest.end();
                }
            }
        });
        return promise.future();

    }

    private Future<Void> uploadToWorkspace(UserInfos user, Storage storage, WorkspaceHelper workspaceHelper, HttpClientResponse zimbraResponse) {
        Promise<Void> promise = Promise.promise();
        try {
            String contentDisposition = zimbraResponse.getHeader(Field.CONTENT_DISPOSITION).replace("*=UTF-8''","=");
            String fileName = contentDisposition.substring(contentDisposition.indexOf("filename=") + 9).replaceAll("\"","");
            String contentType = zimbraResponse.getHeader(Field.CONTENT_TYPE);
            contentType = contentType.substring(0, contentType.indexOf(";"));
            fileName = URLDecoder.decode(fileName, StandardCharsets.UTF_8.name());
            if (!fileName.isEmpty() && !contentType.isEmpty()) {
                String finalContentType = contentType;
                String finalFileName = fileName;
                zimbraResponse.bodyHandler(buffer ->
                        FileHelper.writeBuffer(storage, buffer, finalContentType, finalFileName)
                                .compose(writeInfo -> FileHelper.addFileReference(writeInfo, user, finalFileName, workspaceHelper))
                                .onSuccess(res -> promise.complete())
                                .onFailure(err -> {
                                    String messageToFormat = "[Zimbra@uploadToWorkspace] Error while storing file : " + err.getMessage();
                                    PromiseHelper.reject(log, messageToFormat, AttachmentService.class.getSimpleName(), err, promise);
                                }));
            } else {
                String messageToFormat = "Zimbra@getAttachment : Missing Infos";
                PromiseHelper.reject(log, messageToFormat, AttachmentService.class.getSimpleName(), new Exception(messageToFormat), promise);
            }
        } catch (Exception e) {
            String messageToFormat = "Zimbra@getAttachment : Error when URLDecoder.decode : " + e.getMessage();
            PromiseHelper.reject(log, messageToFormat, AttachmentService.class.getSimpleName(), e, promise);
        }
        return promise.future();
    }

    private Future<Void> uploadToComputer(HttpServerRequest frontRequest, HttpClientResponse zimbraResponse) {
        Promise<Void> promise = Promise.promise();
        HttpServerResponse frontResponse = frontRequest.response();
        String cdHeader = Utils.getOrElse(zimbraResponse.getHeader(Field.CONTENT_DISPOSITION), Field.INLINE);
        frontResponse.setChunked(true)
                .putHeader(Field.CONTENT_DISPOSITION, cdHeader);
        if (httpClientPool.isEmpty()) {
            httpClientPool.add(HttpClientHelper.createHttpClient(vertx));
        }
        HttpClient httpClient = httpClientPool.poll();
        if (httpClient == null) {
            String messageToFormat = "Zimbra@uploadToComputer : Null httpClient, can't upload attachment";
            PromiseHelper.reject(log, messageToFormat, AttachmentService.class.getSimpleName(), new Exception(messageToFormat), promise);
        } else {
            pumpRequests(httpClient, zimbraResponse, frontResponse)
                    .onSuccess(res -> promise.complete())
                    .onFailure(error -> {
                        String messageToFormat = "Zimbra@uploadComputer@pumpRequests Error in pumpRequests : " + error.getMessage();
                        PromiseHelper.reject(log, messageToFormat, AttachmentService.class.getSimpleName(), error, promise);
                    });

            frontResponse.exceptionHandler(event -> {
                String messageToFormat = "Zimbra@uploadToComputer Error when transferring attachment : " + event.getMessage();
                PromiseHelper.reject(log, messageToFormat, AttachmentService.class.getSimpleName(), event, promise);
            });
        }
        return promise.future();
    }

    /**
     * Dump one request into another.
     * Used to transfer attachment from Zimbra to Front, or Front to Zimbra
     * @param httpClient HttpClient used for pump, can be closed afterwards
     *
     * @param inRequest Request containing the data
     * @param outRequest Request that must be filled
     */
    private Future<Void> pumpRequests(HttpClient httpClient, ReadStream<Buffer> inRequest, WriteStream<Buffer> outRequest) {
        Promise<Void> promise = Promise.promise();
        Pump pump = Pump.pump(inRequest, outRequest);
            outRequest.exceptionHandler( event -> {
                String messageToFormat = "Zimbra@pumpRequests Error in outRequest : " + event.getMessage();
                PromiseHelper.reject(log, messageToFormat, AttachmentService.class.getSimpleName(), event, promise);
            });
            inRequest.exceptionHandler( event -> {
                String messageToFormat = "Zimbra@pumpRequests Error in inRequest : " + event.getMessage();
                PromiseHelper.reject(log, messageToFormat, AttachmentService.class.getSimpleName(), event, promise);
            });
            inRequest.endHandler(event -> {
                outRequest.end();
                httpClientPool.add(httpClient);
                promise.complete();
            });
            pump.start();
        return promise.future();
    }

    /**
     * Pump data from frontRequest to Zimbra, then update existing draft with attachment.
     * Send back new draft content to front
     * @param messageId Message Id
     * @param user User Infos
     * @param buffer Attachment as a stream
     * @param document Document as a JsonObject
     * @param handler Final handler
     */
    public void addAttachment(String messageId,
                              UserInfos user,
                              ReadStream<Buffer> buffer,
                              JsonObject document,
                              Handler<Either<String, JsonObject>> handler) {
        soapService.getUserAuthToken(user, authTokenResponse -> {
            if(authTokenResponse.isLeft()) {
                handler.handle(authTokenResponse);
                return;
            }
            String authToken = authTokenResponse.right().getValue().getString(Field.AUTH_TOKEN);
            String filename = null;
            try {
                filename = encodeFileName(document.getString(Field.NAME));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            this.client.postAbs(zimbraUrlUpload)
                    .putHeader(Field.COOKIE,"ZM_AUTH_TOKEN=" + authToken)
                    .putHeader(Field.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .putHeader(Field.CONTENT_TYPE, document.getJsonObject("metadata").getString(Field.CONTENT_TYPE))
                    .sendStream(buffer, ar -> {
                        if(ar.failed()){
                            log.error("An error has occured while fetching the attachment");
                        } else {
                            HttpResponse<Buffer> response = ar.result();
                            if (response.statusCode() == 200) {
                                    if (!(Pattern.compile("^.*\"aid\"\\s*:\\s*\"([^\"]*)\".*\n$")).matcher(response.bodyAsString()).find()) {
                                        JsonObject res = new JsonObject()
                                                .put("code", "mail.INVALID_REQUEST");
                                        handler.handle(new Either.Left<>(res.encode()));
                                        return;
                                    }
                                    String aid = response.bodyAsString().replaceAll("^.*\"aid\"\\s*:\\s*\"([^\"]*)\".*\n$", "$1");
                                    updateDraft(messageId, aid, user, null, handler);
                            } else {
                                handler.handle(new Either.Left<>(response.statusMessage()));
                            }
                        }
                    });
        });
    }

    /**
     * Add attachment to a mail.
     * Pump data from frontRequest to Zimbra, then update existing draft with attachment.
     * Send back new draft content to front
     * @param messageId Message Id
     * @param user User Infos
     * @param requestFront Request from front with attachment data
     * @param handler Final handler
     */
    public void addAttachmentBuffer(String messageId,
                              UserInfos user,
                              HttpServerRequest requestFront,
                              Handler<Either<String, JsonObject>> handler) {
        soapService.getUserAuthToken(user, authTokenResponse -> {
            if(authTokenResponse.isLeft()) {
                handler.handle(authTokenResponse);
                return;
            }
            String authToken = authTokenResponse.right().getValue().getString(Field.AUTH_TOKEN);
            HttpClient httpClient = HttpClientHelper.createHttpClient(vertx);

            requestFront.resume();
            String cdHeader = Utils.getOrElse(requestFront.getHeader(Field.CONTENT_DISPOSITION), "attachment");
            HttpClientRequest requestZimbra;
            requestZimbra = httpClient.postAbs(zimbraUrlUpload, response -> {
                if(response.statusCode() == 200) {
                    response.bodyHandler( body -> {
                        if (!(Pattern.compile("^.*\"aid\"\\s*:\\s*\"([^\"]*)\".*\n$")).matcher(body.toString()).find()) {
                            JsonObject res = new JsonObject()
                                    .put("code", "mail.INVALID_REQUEST");
                            handler.handle(new Either.Left<>(res.encode()));
                            return;
                        }

                        String aid = body.toString().replaceAll("^.*\"aid\"\\s*:\\s*\"([^\"]*)\".*\n$", "$1");
                        updateDraft(messageId, aid, user, null, handler);
                    });
                } else {
                    handler.handle(new Either.Left<>(response.statusMessage()));
                }
            });
            requestZimbra.exceptionHandler( err -> {
                log.error("Error when uploading attachment : ", err);
                handler.handle(new Either.Left<>("Error when uploading attachment"));
            });
            requestZimbra.setChunked(true)
                    .putHeader(Field.CONTENT_DISPOSITION, cdHeader)
                    .putHeader(Field.COOKIE,"ZM_AUTH_TOKEN=" + authToken);

            pumpRequests(httpClient, requestFront, requestZimbra)
                    .onSuccess(res -> handler.handle(new Either.Right<>(new JsonObject())))
                    .onFailure(error -> {
                        String messageToFormat = "Zimbra@addAttachmentBuffer Error in pumpRequest : " + error.getMessage();
                        log.error(messageToFormat);
                        handler.handle(new Either.Left<>(messageToFormat));
                    });
        });

    }

    private static String encodeFileName(String fileName) throws UnsupportedEncodingException {
        return URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
    }

    /**
     * Remove an attachment from an existing draft or message
     * Get existing message, remove attachment from list
     * Then save draft again
     * @param messageId Message Id
     * @param attachmentId Part Id of the attachment
     * @param user User Infos
     * @param result final handler
     */
    public void removeAttachment(String messageId, String attachmentId, UserInfos user,
                                 Handler<Either<String, JsonObject>> result) {
        messageService.getMessage(messageId, user, response -> {
            if (response.isLeft()) {
                result.handle(new Either.Left<>(response.left().getValue()));
            } else {
                JsonObject msgOrig = response.right().getValue();
                JsonArray attachsOrig = msgOrig.getJsonArray("attachments", new JsonArray());
                int i = 0;
                while (i < attachsOrig.size()) {
                    if(attachmentId.equals(attachsOrig.getJsonObject(i).getString(Field.ID))) {
                        break;
                    }
                    i++;
                }
                if(i < attachsOrig.size()) {
                    attachsOrig.remove(i);
                }
                messageService.transformMessageFrontToZimbra(msgOrig, messageId, mailContent -> {
                    mailContent.put(MSG_ID, messageId);
                    messageService.execSaveDraftRaw(mailContent, user, responseSave -> {
                        if (responseSave.isLeft()) {
                            result.handle(new Either.Left<>(responseSave.left().getValue()));
                        } else {
                            messageService.processSaveDraftFull(responseSave.right().getValue(), result);
                        }
                    });
                });
            }
        });
    }

    public void forwardAttachments(String origMessageId, String finalMessageid, UserInfos user,
                                   Handler<Either<String, JsonObject>> handler) {
        messageService.getMessage(origMessageId, user, response -> {
            if (response.isLeft()) {
                handler.handle(new Either.Left<>(response.left().getValue()));
            } else {
                JsonObject msgOrig = response.right().getValue();
                JsonArray attachsOrig = msgOrig.getJsonArray("attachments", new JsonArray());
                JsonArray newAttachs = new JsonArray();
                for (Object o : attachsOrig) {
                    if(!(o instanceof JsonObject)) continue;
                    String idOrig = ((JsonObject) o).getString(Field.ID, "");
                    if(!idOrig.isEmpty()) {
                        JsonObject attchNew = new JsonObject();
                        attchNew.put(MULTIPART_PART_ID, idOrig);
                        attchNew.put(MULTIPART_MSG_ID, origMessageId);
                        newAttachs.add(attchNew);
                    }
                }
                updateDraft(finalMessageid, null, user, newAttachs, handler);
            }
        });
    }


    /**
     * Update Draft with new attachments
     * @param messageId Message Id
     * @param uploadAttchId New attachment upload Id
     * @param user User Infos
     * @param result result handler
     */
    private void updateDraft(String messageId, String uploadAttchId, UserInfos user, JsonArray forwardedAtts,
                            Handler<Either<String, JsonObject>> result) {
        messageService.getMessage(messageId, user, response -> {
            if (response.isLeft()) {
                result.handle(new Either.Left<>(response.left().getValue()));
            } else {
                JsonObject msgOrig = response.right().getValue();

                messageService.transformMessageFrontToZimbra(msgOrig, messageId, mailContent -> {
                    mailContent.put(MSG_ID, messageId);
                    JsonObject attachs = mailContent.getJsonObject(MSG_NEW_ATTACHMENTS, new JsonObject());
                    if(uploadAttchId != null) {
                        attachs.put(MSG_NEW_UPLOAD_ID, uploadAttchId);
                        mailContent.put(MSG_NEW_ATTACHMENTS, attachs);
                    }
                    if(forwardedAtts != null) {
                        attachs.put(MSG_MULTIPART, forwardedAtts);
                        mailContent.put(MSG_NEW_ATTACHMENTS, attachs);
                    }
                    messageService.execSaveDraftRaw(mailContent, user, responseSave -> {
                        if (responseSave.isLeft()) {
                            result.handle(new Either.Left<>(responseSave.left().getValue()));
                        } else {
                            messageService.processSaveDraftFull(responseSave.right().getValue(), result);
                        }
                    });
                });
            }
        });
    }

    public void getDocument(EventBus eb, String idImage, Handler<Either<String, JsonObject>> handler) {
        JsonObject action = new JsonObject().put("action", "getDocument").put(Field.ID, idImage);
        String WORKSPACE_BUS_ADDRESS = "org.entcore.workspace";
        eb.send(WORKSPACE_BUS_ADDRESS, action, handlerToAsyncHandler(message -> {
            JsonObject body = message.body();
            if (!"ok".equals(body.getString(Field.STATUS))) {
                handler.handle(new Either.Left<>("[AttachmentService@getImage] An error occured: inexistant document id"));
            } else {
                handler.handle(new Either.Right<>(message.body().getJsonObject("result")));
            }
        }));
    }

}
