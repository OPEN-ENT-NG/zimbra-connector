package fr.openent.zimbra.model.soap;

import fr.openent.zimbra.helper.StringHelper;
import fr.openent.zimbra.model.constant.SoapConstants;
import fr.openent.zimbra.model.message.Conversation;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;

import static fr.openent.zimbra.model.constant.SoapConstants.*;
import static fr.openent.zimbra.model.constant.ZimbraConstants.*;

public class SoapConversationHelper {

    private static Logger log = LoggerFactory.getLogger(SoapConversationHelper.class);

    public static void getConversationById(String userId, String conversationId,
                                           Handler<AsyncResult<Conversation>> resultHandler) {
        SoapRequest getConvRequest = SoapRequest.MailSoapRequest(SoapConstants.GET_CONVERSATION_REQUEST, userId);
        JsonObject content = new JsonObject()
                .put(CONVERSATION, new JsonObject()
                        .put(CONVERSATION_ID, conversationId)
                        .put(CONVERSATION_EXPAND_MESSAGES, CONV_EXPAND_ALL));
        getConvRequest.setContent(content);
        try {
            getConvRequest.start(processConversationHandler(GET_CONVERSATION_RESPONSE, resultHandler));
        } catch (Exception e) {
            log.error("Exception in getConversationRequest ", e);
            resultHandler.handle(Future.failedFuture(e));
        }
    }

    public static void toggleReadConversations(List<String> conversationIdList, boolean read, String userId,
                                               Handler<AsyncResult<Void>> handler) {
        String operation = read ? OP_READ : OP_UNREAD;
        conversationAction(conversationIdList, userId, operation, handler);
    }

    public static void trashConversations(List<String> conversationIdList, String userId,
                                          Handler<AsyncResult<Void>> handler) {
        conversationAction(conversationIdList, userId, OP_TRASH, handler);
    }

    private static void conversationAction(List<String> conversationIdList, String userId, String operation,
                                      Handler<AsyncResult<Void>> handler) {
        SoapRequest convActionRequest = SoapRequest.MailSoapRequest(SoapConstants.CONVERSATION_ACTION_REQUEST, userId);
        JsonObject actionRequest = new JsonObject()
                .put(CONVERSATION_ID, StringHelper.joinList(conversationIdList, SoapConstants.SEPARATOR))
                .put(OPERATION, operation);

        convActionRequest.setContent(new JsonObject().put(ACTION, actionRequest));
        convActionRequest.start( result -> {
            // Todo process result for all successes
            if(result.failed()) {
                handler.handle(Future.failedFuture(result.cause()));
            } else {
                handler.handle(Future.succeededFuture());
            }
        });
    }

    @SuppressWarnings("SameParameterValue")
    private static Handler<AsyncResult<JsonObject>> processConversationHandler(
            String respName, Handler<AsyncResult<Conversation>> handler) {
        return soapResult -> {
            if(soapResult.failed()) {
                handler.handle(Future.failedFuture(soapResult.cause()));
            } else {
                JsonObject jsonResponse = soapResult.result();
                try {
                    JsonObject conversationObject = jsonResponse.getJsonObject(BODY)
                            .getJsonObject(respName)
                            .getJsonArray(CONVERSATION).getJsonObject(0);
                    Conversation conversation = Conversation.fromZimbra(conversationObject);
                    handler.handle(Future.succeededFuture(conversation));
                } catch (Exception e) {
                    handler.handle(Future.failedFuture(e));
                }
            }
        };
    }
}