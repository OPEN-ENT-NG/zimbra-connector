package fr.openent.zimbra.service.impl;


import fr.openent.zimbra.model.constant.FrontConstants;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

import java.util.List;

/**
 * TEMPORARY Implementation of thread for mobile app
 * Only 1 message by thread
 * /!\ Do not use this file to properly implement threads /!\
 */
public class TempThreadService {

    private MessageService messageService;

    public TempThreadService(MessageService messageService) {
        this.messageService = messageService;
    }


    public void listThreads(UserInfos user, int page, Handler<Either<String, JsonArray>> result) {
        messageService.listMessages(FrontConstants.FOLDER_INBOX, false, user, page, null, msgRes -> {
            if(msgRes.isLeft()) {
                result.handle(msgRes);
            } else  {
                JsonArray msgList = msgRes.right().getValue();
                msgList.forEach( item -> {
                    if(item instanceof JsonObject) {
                        JsonObject msg = (JsonObject)item;
                        msg.put(FrontConstants.MESSAGE_UNREAD,
                                msg.getBoolean(FrontConstants.MESSAGE_UNREAD, false) ? 1 : 0);
                    }
                });
                result.handle(new Either.Right<>(msgList));
            }
        });
    }

    public void toggleUnreadThreads(List<String> listMessageIds, boolean unread, UserInfos user, Handler<Either<String, JsonObject>> result) {
        messageService.toggleUnreadMessages(listMessageIds, unread, user, result);
    }

    public void trashTreads(List<String> messageIds, UserInfos user, Handler<Either<String, JsonObject>> result) {
        messageService.moveMessagesToFolder(messageIds, FrontConstants.FOLDER_TRASH, user, result);
    }

    public void getMessages(String threadId, UserInfos user, Handler<Either<String,JsonArray>> handler) {
        messageService.getMessage(threadId, user, res -> {
            if(res.isLeft()) {
                handler.handle(new Either.Left<>(res.left().getValue()));
            } else {
                JsonObject message = res.right().getValue();
                message.put("thread_id", message.getString("id"));
                handler.handle(new Either.Right<>(new JsonArray().add(message)));
            }
        });
    }
}
