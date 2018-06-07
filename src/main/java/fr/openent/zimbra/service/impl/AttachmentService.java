package fr.openent.zimbra.service.impl;
import fr.openent.zimbra.helper.HttpClientHelper;
import fr.openent.zimbra.helper.ZimbraConstants;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.streams.Pump;
import org.entcore.common.user.UserInfos;
import io.vertx.core.json.JsonObject;
import io.vertx.core.http.HttpClient;

import io.vertx.core.logging.Logger;

public class AttachmentService {

    private SoapZimbraService soapService;
    private Vertx vertx;
    private HttpClient httpClient = null;
    private UserService userService;
    private String zimbraUri;
    private Logger log;

    public AttachmentService(Logger log, SoapZimbraService soapService, Vertx vertx, JsonObject config) {
        this.userService = null;
        this.zimbraUri = config.getString("zimbra-uri", "");
        this.vertx = vertx;
        this.log = log;
        this.soapService = soapService;
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

}
