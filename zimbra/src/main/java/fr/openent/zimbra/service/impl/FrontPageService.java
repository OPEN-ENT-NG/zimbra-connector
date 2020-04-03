package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.model.constant.FrontConstants;
import fr.openent.zimbra.model.soap.model.SoapFolder;
import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

public class FrontPageService {

    FolderService folderService;
    UserService userService;

    public FrontPageService(FolderService folderService, UserService userService) {
        this.folderService = folderService;
        this.userService = userService;
    }

    public void getFrontPageInfos(UserInfos user, Handler<AsyncResult<JsonObject>> handler) {

        Future<JsonObject> userFuture = Future.future();
        Future<SoapFolder> foldersFuture = Future.future();

        CompositeFuture.all(userFuture, foldersFuture).setHandler(res -> {
            if (res.failed()) {
                handler.handle(Future.failedFuture(res.cause()));
            } else {
                JsonObject zUserInfo = userFuture.result();
                zUserInfo.put(FrontConstants.FRONT_PAGE_FOLDERS,
                        foldersFuture.result().getJsonSubfolders().toString().replaceAll("'", "\\\\'"));
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
