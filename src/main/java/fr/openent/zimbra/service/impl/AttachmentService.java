package fr.openent.zimbra.service.impl;
import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.helper.HttpClientHelper;
import fr.openent.zimbra.service.data.SoapZimbraService;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.Utils;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.streams.Pump;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import org.entcore.common.user.UserInfos;
import io.vertx.core.json.JsonObject;

import io.vertx.core.logging.Logger;

import java.util.LinkedList;
import java.util.Queue;

import static fr.openent.zimbra.model.constant.ZimbraConstants.*;

public class AttachmentService {

    private SoapZimbraService soapService;
    private MessageService messageService;
    private Vertx vertx;
    private static Logger log = LoggerFactory.getLogger(AttachmentService.class);
    private final String zimbraUrlAttachment;
    private final String zimbraUrlUpload;
    private final Queue<HttpClient> httpClientPool;

    public AttachmentService( SoapZimbraService soapService, MessageService messageService,
                              Vertx vertx, JsonObject config) {
        String zimbraUri = config.getString("zimbra-uri", "");
        this.zimbraUrlAttachment = zimbraUri + "/service/home/~/?auth=co";
        this.zimbraUrlUpload = zimbraUri + "/service/upload?fmt=extended,raw";
        this.vertx = vertx;
        this.soapService = soapService;
        this.messageService = messageService;
        this.httpClientPool = new LinkedList<>();
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
     * @param frontRequest Request from the front
     * @param handler final handler, only use in case of error
     */
    public void getAttachment(String messageId, String attachmentPartId, UserInfos user, Boolean inline,
                              HttpServerRequest frontRequest, Handler<Either<String,JsonObject>> handler) {

        String disp = inline ? DISPLAY_INLINE : DISPLAY_ATTACHMENT;
        String urlAttachment = zimbraUrlAttachment + "&id=" + messageId + "&part="
                + attachmentPartId + "&disp=" + disp;

        soapService.getUserAuthToken(user, authTokenResponse -> {
            if(authTokenResponse.isLeft()) {
                handler.handle(authTokenResponse);
                return;
            }
            String authToken = authTokenResponse.right().getValue().getString("authToken");

            if(httpClientPool.isEmpty()) {
                httpClientPool.add(HttpClientHelper.createHttpClient(vertx));
            }
            HttpClient httpClient = httpClientPool.poll();
            if(httpClient == null) {
                log.error("Null httpClient for attachment upload");
                handler.handle(new Either.Left<>("Null httpClient, can't upload attachment"));
                return;
            }

            HttpClientRequest zimbraRequest = httpClient.getAbs(urlAttachment, zimbraResponse -> {
                HttpServerResponse frontResponse = frontRequest.response();
                String cdHeader = Utils.getOrElse(zimbraResponse.getHeader("Content-Disposition"), "inline");

                frontResponse.setChunked(true)
                        .putHeader("Content-Disposition", cdHeader);
                pumpRequests(httpClient, zimbraResponse, frontResponse);

                frontResponse.exceptionHandler(event -> {
                    log.error("Error when transferring attachment", event);
                    handler.handle(new Either.Left<>("Error when transferring attachement"));
                });
            });

            zimbraRequest.setChunked(true)
                    .putHeader("Cookie","ZM_AUTH_TOKEN=" + authToken);
            zimbraRequest.end();
        });
    }

    /**
     * Dump one request into another.
     * Used to transfer attachment from Zimbra to Front, or Front to Zimbra
     * @param httpClient HttpClient used for pump, can be closed afterwards
     * @param inRequest Request containing the model
     * @param outRequest Request that must be filled
     */
    private void pumpRequests(HttpClient httpClient, ReadStream<Buffer> inRequest, WriteStream<Buffer> outRequest) {
            Pump pump = Pump.pump(inRequest, outRequest);
            outRequest.exceptionHandler( event -> log.error(event.getMessage()) );
            inRequest.exceptionHandler( event -> log.error(event.getMessage()) );
            inRequest.endHandler(event -> {
                outRequest.end();
                httpClientPool.add(httpClient);
            });
            pump.start();
    }

    /**
     * Add attachment to a mail.
     * Pump model from frontRequest to Zimbra, then update existing draft with attachment.
     * Send back new draft content to front
     * @param messageId Message Id
     * @param user User Infos
     * @param requestFront Request from front with attachment model
     * @param handler Final handler
     */
    public void addAttachment(String messageId,
                              UserInfos user,
                              HttpServerRequest requestFront,
                              Handler<Either<String, JsonObject>> handler) {
        soapService.getUserAuthToken(user, authTokenResponse -> {
            if(authTokenResponse.isLeft()) {
                handler.handle(authTokenResponse);
                return;
            }
            String authToken = authTokenResponse.right().getValue().getString("authToken");
            HttpClient httpClient = HttpClientHelper.createHttpClient(vertx);

            String cdHeader = Utils.getOrElse(requestFront.getHeader("Content-Disposition"), "attachment");
            HttpClientRequest requestZimbra;
            requestZimbra = httpClient.postAbs(zimbraUrlUpload, response -> {
                if(response.statusCode() == 200) {
                    response.bodyHandler( body -> {
                        String aid = body.toString().replaceAll("^.*\"aid\"\\s*:\\s*\"([^\"]*)\".*\n$", "$1");
                        updateDraft(messageId, aid, user, null, handler);
                    });
                } else {
                    handler.handle(new Either.Left<>(response.statusMessage()));
                }
            });
            requestZimbra.setChunked(true)
                    .putHeader("Content-Disposition", cdHeader)
                    .putHeader("Cookie","ZM_AUTH_TOKEN=" + authToken);

            pumpRequests(httpClient, requestFront, requestZimbra);
        });

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
                    if(attachmentId.equals(attachsOrig.getJsonObject(i).getString("id"))) {
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
                    String idOrig = ((JsonObject) o).getString("id", "");
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

    /**
     * Add attachments into a front message
     * Inline images are replaced inside the body
     * @param msgFront Front Message to modify
     * @param attachments Array of attachment infos
     */
    static void processAttachments(JsonObject msgFront, JsonArray attachments) {
        for(Object o : attachments) {
            if(!(o instanceof JsonObject)) continue;
            JsonObject zimbraAtt = (JsonObject)o;
            JsonObject frontAtt = new JsonObject();
            if(MULTIPART_ATTACHMENT.equals(zimbraAtt.getString(MULTIPART_CONTENT_DISPLAY))) {
                frontAtt.put("id", zimbraAtt.getString(MULTIPART_PART_ID));
                frontAtt.put("filename", zimbraAtt.getString(MULTIPART_FILENAME,
                        zimbraAtt.getString(MULTIPART_PART_ID)));
                frontAtt.put("contentType", zimbraAtt.getString(MULTIPART_CONTENT_TYPE));
                frontAtt.put("size", zimbraAtt.getLong(MULTIPART_SIZE));
                msgFront.put("attachments", msgFront.getJsonArray("attachments", new JsonArray()).add(frontAtt));
            } else if(MULTIPART_INLINE.equals(zimbraAtt.getString(MULTIPART_CONTENT_DISPLAY))
                    && zimbraAtt.containsKey(MULTIPART_CONTENT_INLINE)) {
                String msgId = msgFront.getString("id");
                String partId = zimbraAtt.getString(MULTIPART_PART_ID);
                String attchUrl = Zimbra.URL + "/message/" + msgId + "/attachment/" + partId;
                String cid = zimbraAtt.getString(MULTIPART_CONTENT_INLINE);

                String regex = "<img([^>]*)\\ssrc=\"cid:" + cid.substring(1, cid.length() - 1);
                String replaceRegex = "<img$1 src=\"" + attchUrl;
                String body = msgFront.getString("body", "").replaceAll(regex, replaceRegex);
                msgFront.put("body", body);
            }
        }
    }

}
