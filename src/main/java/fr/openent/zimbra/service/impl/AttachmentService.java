package fr.openent.zimbra.service.impl;
import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.helper.HttpClientHelper;
import fr.openent.zimbra.helper.ZimbraConstants;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.Utils;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.streams.Pump;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import org.entcore.common.user.UserInfos;
import io.vertx.core.json.JsonObject;

import io.vertx.core.logging.Logger;

import static fr.openent.zimbra.helper.ZimbraConstants.*;

public class AttachmentService {

    private SoapZimbraService soapService;
    private Vertx vertx;
    private HttpClient httpClient = null;
    private static Logger log = LoggerFactory.getLogger(AttachmentService.class);
    private String zimbraUrlAttachment;

    public AttachmentService( SoapZimbraService soapService, Vertx vertx, JsonObject config) {
        String zimbraUri = config.getString("zimbra-uri", "");
        this.zimbraUrlAttachment = zimbraUri + "/service/home/~/?auth=co";
        this.vertx = vertx;
        this.soapService = soapService;
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

        String disp = inline ? ZimbraConstants.DISPLAY_INLINE : ZimbraConstants.DISPLAY_ATTACHMENT;
        String urlAttachment = zimbraUrlAttachment + "&id=" + messageId + "&part="
                + attachmentPartId + "&disp=" + disp;

        soapService.getUserAuthToken(user, authTokenResponse -> {
            if(authTokenResponse.isLeft()) {
                handler.handle(authTokenResponse);
                return;
            }
            String authToken = authTokenResponse.right().getValue().getString("authToken");
            HttpClient httpClient = HttpClientHelper.createHttpClient(vertx);

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
     * @param inRequest Request containing the data
     * @param outRequest Request that must be filled
     */
    private void pumpRequests(HttpClient httpClient, ReadStream<Buffer> inRequest, WriteStream<Buffer> outRequest) {
            Pump pump = Pump.pump(inRequest, outRequest);
            inRequest.endHandler(event -> {
                outRequest.end();
                httpClient.close();
            });
            pump.start();
    }

    /**
     * Create response handler for zimbra api requests
     * @param handler final response handler
     * @return default handler
     */
    private Handler<HttpClientResponse> uploadRequestHandler(final String messageId,
                                                             final UserInfos user,
                                                             final Handler<Either<String,JsonObject>> handler) {
        return response -> {
            if(response.statusCode() == 200) {
                response.bodyHandler( body -> {
                    String newModifiedBody = body.toString();
                    newModifiedBody = newModifiedBody.replace("\n", "")
                                     .replace("'", "\"");
                    String finalBody = "[" + newModifiedBody + "]";
                    JsonArray result;
                    try {
                        result = new JsonArray(finalBody);
                        String attachmentIdUploaded = result.getJsonArray(2).getJsonObject(0).getString("aid");
                        updateDraft(messageId, attachmentIdUploaded, user, handler);
                    } catch (DecodeException e) {
                        handler.handle(new Either.Left<>("Error when decoding Zimbra upload response"));
                    }
                });
            } else {
                handler.handle(new Either.Left<>(response.statusMessage()));
            }
        };
    }

    public void addAttachment(String messageId,
                              UserInfos user,
                              HttpServerRequest requestFront,
                              Handler<Either<String, JsonObject>> result) {

        if(httpClient == null) {
            httpClient = HttpClientHelper.createHttpClient(vertx);
        }

        // todo: Modifier URL et headers en conséquences
        String finalUrl = "https://mailcrif.preprod-ent.fr/service/upload?fmt=extended,raw";
        HttpClientRequest requestZimbra;
        Handler<HttpClientResponse> handlerRequest = uploadRequestHandler(messageId, user, result);
        requestZimbra = httpClient.postAbs(finalUrl, handlerRequest);
        requestZimbra.setChunked(true)
                .putHeader("Content-Disposition","attachment; filename=\"4469407-sand-wallpapers.jpg\"")
                .putHeader("Cookie","ZM_AUTH_TOKEN=0_442580d7125a944940c17d13ab5620d3569a602f_69643d33363a61656532343466312d613332372d343934652d396631642d3661386231636438303834323b6578703d31333a313532383437313838383839333b76763d313a323b747970653d363a7a696d6272613b753d313a613b7469643d31303a313730393430303939303b76657273696f6e3d31343a382e372e31315f47415f313835343b");

        // Pump the http request to the write stream
        Pump pump = Pump.pump(requestFront, requestZimbra);

        requestFront.endHandler(event -> {
                //log.info("Sending zimbra request with " + pump.numberPumped() + "bytes");
                requestZimbra.end();
        });
        requestZimbra.exceptionHandler(event -> {
                log.info("Error on the zimbra request: " + event);
        });

        pump.start();

    };


    /**
     * Update Draft
     * @param idMessage ID Email
     * @param idAttachment ID Email attachment
     * @param result result handler
     */
    //todo : Correct bug ReWriting existing email without getting data before ...
    public void updateDraft(String idMessage, String idAttachment, UserInfos user,
                            Handler<Either<String, JsonObject>> result) {

        JsonObject saveDraftRequest = new JsonObject()
                .put("name", "SaveDraftRequest")
                .put("content", new JsonObject()
                        .put("_jsns", ZimbraConstants.NAMESPACE_MAIL)
                        .put("m", new JsonObject()
                                .put("id", idMessage)
                                .put("attach", new JsonObject()
                                        .put("aid", idAttachment))));

        soapService.callUserSoapAPI(saveDraftRequest, user, response -> {
            if(response.isLeft()) {
                result.handle(new Either.Left<>(response.left().getValue()));
            } else {
                result.handle(new Either.Right<>(new JsonObject().put("id",idAttachment)));
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
                msgFront.put("attachments", msgFront.getJsonArray("attachments").add(frontAtt));
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
