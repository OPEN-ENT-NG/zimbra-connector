package fr.openent.zimbra.service.messages;

import fr.openent.zimbra.model.message.Conversation;
import fr.openent.zimbra.model.message.Recipient;
import fr.openent.zimbra.model.soap.SoapConversationHelper;
import fr.openent.zimbra.model.soap.SoapSearchHelper;
import fr.openent.zimbra.service.impl.RecipientService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.user.UserInfos;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MobileThreadService {

    RecipientService recipientService;

    public MobileThreadService(RecipientService recipientService) {
        this.recipientService = recipientService;
    }

    private static Logger log = LoggerFactory.getLogger(MobileThreadService.class);

    // Get all messages content from a thread in json format
    public void getMessages(String threadId, UserInfos user, Handler<AsyncResult<JsonArray>> handler) {
        getMessages(threadId, user, 0, handler);
    }


    public void getMessages(String threadId, UserInfos user, int page, Handler<AsyncResult<JsonArray>> handler) {
        String userId = user.getUserId();
        if(userId == null || userId.isEmpty()) {
            log.error("Empty user id");
            handler.handle(Future.failedFuture("Empty user"));
        } else {
            SoapConversationHelper.getConversationById(userId, threadId, page, zimbraResult -> {
                if(zimbraResult.failed()) {
                    handler.handle(Future.failedFuture(zimbraResult.cause()));
                } else {
                    getMessagesFromConversation(zimbraResult.result(), handler);
                }
            });
        }
    }

    private void getMessagesFromConversation(Conversation conversation, Handler<AsyncResult<JsonArray>> finalHandler) {
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

    public void listThreads(UserInfos user, int page, Handler<AsyncResult<JsonArray>> handler) {
        String userId = user.getUserId();
        if(userId == null || userId.isEmpty()) {
            log.error("Empty user id");
            handler.handle(Future.failedFuture("Empty user"));
        } else {
            SoapSearchHelper.searchAllMailedConv(userId, page, searchResult -> {
                if(searchResult.failed()) {
                    handler.handle(Future.failedFuture(searchResult.cause()));
                } else {
                    List<Conversation> convList = searchResult.result();
                    Set<String> allAdresses = new HashSet<>();
                    convList.forEach( conversation -> allAdresses.addAll(conversation.getAllAddresses()));
                    recipientService.getUseridsFromEmails(allAdresses, result -> {
                        if(result.failed()) {
                            handler.handle(Future.failedFuture(result.cause()));
                        } else {
                            Map<String, Recipient> allusers = result.result();
                            convList.forEach(conversation -> conversation.setUserMapping(allusers));
                            JsonArray allConversationData = new JsonArray();
                            convList.forEach( conversation -> allConversationData.add(conversation.getJsonObject()));
                            handler.handle(Future.succeededFuture(allConversationData));
                        }
                    });
                }
            });
        }
    }

    public void toggleUnreadThreads(List<String> threadIds, boolean unread, UserInfos user,
                                    Handler<AsyncResult<JsonObject>> handler) {
        String userId = user.getUserId();
        if(userId == null || userId.isEmpty()) {
            log.error("Empty user id");
            handler.handle(Future.failedFuture("Empty user"));
        } else {
            SoapConversationHelper.toggleReadConversations(threadIds, unread, userId, res -> {
                if(res.failed()) {
                    handler.handle(Future.failedFuture(res.cause()));
                } else {
                    handler.handle(Future.succeededFuture(new JsonObject()));
                }
            });
        }
    }

    public void trashThreads(List<String> threadIds, UserInfos user,
                                    Handler<AsyncResult<JsonObject>> handler) {
        String userId = user.getUserId();
        if(userId == null || userId.isEmpty()) {
            log.error("Empty user id");
            handler.handle(Future.failedFuture("Empty user"));
        } else {
            SoapConversationHelper.trashConversations(threadIds, userId, res -> {
                if(res.failed()) {
                    handler.handle(Future.failedFuture(res.cause()));
                } else {
                    handler.handle(Future.succeededFuture(new JsonObject()));
                }
            });
        }
    }
}
