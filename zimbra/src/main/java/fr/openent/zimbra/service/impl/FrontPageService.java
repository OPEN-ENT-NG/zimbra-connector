package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.model.constant.FrontConstants;
import fr.openent.zimbra.model.soap.model.SoapFolder;
import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

import static fr.openent.zimbra.Zimbra.appConfig;

public class FrontPageService {

    FolderService folderService;
    UserService userService;

    public FrontPageService(FolderService folderService, UserService userService) {
        this.folderService = folderService;
        this.userService = userService;
    }

    public void getFrontPageInfos(UserInfos user, Handler<AsyncResult<JsonObject>> handler) {

        Promise<JsonObject> userFuture = Promise.promise();
        Promise<SoapFolder> foldersFuture = Promise.promise();

        Future.all(userFuture.future(), foldersFuture.future()).onComplete(res -> {
            if (res.failed()) {
                handler.handle(Future.failedFuture(res.cause()));
            } else {
                JsonObject zUserInfo = userFuture.future().result();
                zUserInfo.put(FrontConstants.FRONT_PAGE_FOLDERS,
                        foldersFuture.future().result().getJsonSubfolders().toString().replaceAll("['|\\\\]", "\\\\$0"));
                zUserInfo.put(FrontConstants.CONFIG_SAVE_DRAFT_AUTO_TIME, appConfig.getsaveDraftAutoTime());
                zUserInfo.put(FrontConstants.CONFIG_SEND_TIMEOUT, appConfig.getSendTimeout());
                handler.handle(Future.succeededFuture(zUserInfo));
            }
        });

        folderService.getRootFolder(user, foldersFuture);
        userService.getUserInfo(user, evt -> {
            if (evt.isLeft()) userFuture.fail(evt.left().getValue());
            else userFuture.complete(evt.right().getValue());
        });
    }
}
