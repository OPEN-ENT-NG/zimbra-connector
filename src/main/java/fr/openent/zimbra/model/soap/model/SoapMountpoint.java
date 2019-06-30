package fr.openent.zimbra.model.soap.model;

import fr.openent.zimbra.model.soap.SoapRequest;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import static fr.openent.zimbra.model.constant.SoapConstants.CREATE_MOUNTPOINT_REQUEST;
import static fr.openent.zimbra.model.constant.SoapConstants.CREATE_MOUNTPOINT_RESPONSE;
import static fr.openent.zimbra.model.constant.ZimbraConstants.*;

public class SoapMountpoint {

    private static Logger log = LoggerFactory.getLogger(SoapMountpoint.class);

    public static void createMountpoint(String userId, String name, String parentFolderId, String view,
                                        String shareUserMail, String shareFolderId,
                                        Handler<AsyncResult<SoapFolder>> handler) {
        createMountpoint(userId, name, parentFolderId, view, shareUserMail, shareFolderId, false, handler);
    }

    public static void getOrCreateMountpoint(String userId, String name, String parentFolderId, String view,
                                             String shareUserMail, String shareFolderId,
                                             Handler<AsyncResult<SoapFolder>> handler) {
        createMountpoint(userId, name, parentFolderId, view, shareUserMail, shareFolderId, true, handler);
    }

    private static void createMountpoint(String userId, String name, String parentFolderId, String view,
                                              String shareUserMail, String shareFolderId, boolean getIfExists,
                                              Handler<AsyncResult<SoapFolder>> handler) {
        SoapRequest createMountpointRequest = SoapRequest.MailSoapRequest(CREATE_MOUNTPOINT_REQUEST, userId);
        JsonObject content = new JsonObject()
                .put(MOUNTPOINT, new JsonObject()
                        .put(VIEW, view)
                        .put(MOUNTPOINT_OWNER_MAIL, shareUserMail)
                        .put(FOLDER_PARENTID, parentFolderId)
                        .put(MOUNTPOINT_REMOTE_FOLDER_ID, shareFolderId)
                        .put(FOLDER_NAME, name));
        createMountpointRequest.setContent(content);
        createMountpointRequest.start(SoapFolder.processFolderHandler(CREATE_MOUNTPOINT_RESPONSE, handler));
    }

}
