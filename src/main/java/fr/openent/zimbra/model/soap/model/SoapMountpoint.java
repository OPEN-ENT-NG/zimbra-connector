package fr.openent.zimbra.model.soap.model;

import fr.openent.zimbra.model.soap.SoapRequest;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import static fr.openent.zimbra.model.constant.SoapConstants.*;
import static fr.openent.zimbra.model.constant.ZimbraConstants.*;

public class SoapMountpoint {

    private String id;

    private static Logger log = LoggerFactory.getLogger(SoapMountpoint.class);

    @SuppressWarnings("unused")
    public static void createMountpoint(String userId, String name, String parentFolderId, String view,
                                        String shareUserMail, String shareFolderId,
                                        Handler<AsyncResult<SoapMountpoint>> handler) {
        createMountpoint(userId, name, parentFolderId, view, shareUserMail, shareFolderId, false, handler);
    }

    public static void getOrCreateMountpoint(String userId, String name, String parentFolderId, String view,
                                             String shareUserMail, String shareFolderId,
                                             Handler<AsyncResult<SoapMountpoint>> handler) {
        createMountpoint(userId, name, parentFolderId, view, shareUserMail, shareFolderId, true, handler);
    }

    @SuppressWarnings("SameParameterValue")
    private static Handler<AsyncResult<JsonObject>> processMountpointHandler(String respName,
                                                                             Handler<AsyncResult<SoapMountpoint>> handler) {
        return res -> {
            if(res.failed()) {
                handler.handle(Future.failedFuture(res.cause()));
            } else {
                JsonObject jsonResponse = res.result();
                try {
                    JsonArray mountpointList = jsonResponse.getJsonObject(BODY)
                            .getJsonObject(respName)
                            .getJsonArray(MOUNTPOINT, new JsonArray());
                    if(mountpointList.size() != 1) {
                        log.warn("Invalid number of mountpoints : " + jsonResponse.toString());
                    }
                    JsonObject jsonMountpoint = mountpointList.getJsonObject(0);
                    SoapMountpoint mountpoint = createFromJson(jsonMountpoint);
                    handler.handle(Future.succeededFuture(mountpoint));
                } catch (Exception e) {
                    handler.handle(Future.failedFuture(e));
                }
            }
        };
    }

    private static void createMountpoint(String userId, String name, String parentFolderId, String view,
                                              String shareUserMail, String shareFolderId, boolean getIfExists,
                                              Handler<AsyncResult<SoapMountpoint>> handler) {
        SoapRequest createMountpointRequest = SoapRequest.MailSoapRequest(CREATE_MOUNTPOINT_REQUEST, userId);
        JsonObject content = new JsonObject()
                .put(MOUNTPOINT, new JsonObject()
                        .put(FETCH_IF_EXISTS, getIfExists ? 1 : 0)
                        .put(VIEW, view)
                        .put(MOUNTPOINT_OWNER_MAIL, shareUserMail)
                        .put(FOLDER_PARENTID, parentFolderId)
                        .put(MOUNTPOINT_REMOTE_FOLDER_ID, shareFolderId)
                        .put(FOLDER_NAME, name));
        createMountpointRequest.setContent(content);
        createMountpointRequest.start(processMountpointHandler(CREATE_MOUNTPOINT_RESPONSE, handler));
    }

    private static SoapMountpoint createFromJson(JsonObject mountpointData) throws IllegalArgumentException {
        SoapMountpoint mountpoint = new SoapMountpoint();
        mountpoint.id = mountpointData.getString(ZIMBRA_ID, "");
        return mountpoint;
    }



}
