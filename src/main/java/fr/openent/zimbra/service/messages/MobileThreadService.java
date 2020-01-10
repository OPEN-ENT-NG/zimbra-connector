package fr.openent.zimbra.service.messages;

import fr.openent.zimbra.model.message.Conversation;
import fr.openent.zimbra.model.message.Recipient;
import fr.openent.zimbra.model.soap.SoapConversationHelper;
import fr.openent.zimbra.service.impl.RecipientService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.user.UserInfos;

import java.util.Map;
import java.util.Set;

public class MobileThreadService {

    RecipientService recipientService;

    public MobileThreadService(RecipientService recipientService) {
        this.recipientService = recipientService;
    }

    private static Logger log = LoggerFactory.getLogger(MobileThreadService.class);

    public void getMessages(String threadId, UserInfos user, Handler<AsyncResult<JsonArray>> handler) {
        String userId = user.getUserId();
        if(userId == null || userId.isEmpty()) {
            log.error("Empty user id");
            handler.handle(Future.failedFuture("Empty user"));
        } else {
            SoapConversationHelper.getConversationById(userId, threadId, zimbraResult -> {
                if(zimbraResult.failed()) {
                    handler.handle(Future.failedFuture(zimbraResult.cause()));
                } else {
                    getMessagesFromConversation(zimbraResult.result(), handler);
                }
            });
        }
    }

    private void getMessagesFromConversation(Conversation conversation, Handler<AsyncResult<JsonArray>> finalHandler) {
        JsonArray results = new JsonArray();
        Set<String> addresses = conversation.getAllAddresses();
        recipientService.getUseridsFromEmails(addresses, result -> {
            if(result.failed()) {
                finalHandler.handle(Future.failedFuture(result.cause()));
            } else {
                Map<String, Recipient> allusers = result.result();
                conversation.setUserMapping(allusers);
                JsonArray data = conversation.getAllMessagesJson();
                finalHandler.handle(Future.succeededFuture(data));
            }
        });
    }
}
