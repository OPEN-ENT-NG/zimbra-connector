package fr.openent.zimbra.model.soap;

import fr.openent.zimbra.Zimbra;
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

    public static void getConversationById(String userId, String conversationId, int page,
                                           Handler<AsyncResult<Conversation>> resultHandler) {
        SoapRequest getConvRequest = SoapRequest.MailSoapRequest(SoapConstants.SEARCH_CONV_REQUEST, userId);
        int pageSize = Zimbra.appConfig.getMailListLimit();
        JsonObject content = new JsonObject()
                        .put(CONVERSATION_CID, conversationId)
                        .put(CONVERSATION_EXPAND_MESSAGES, CONV_EXPAND_ALL)
                        .put(MSG_HTML, ONE_TRUE)
                        .put(MSG_NEUTER_IMAGES, ZERO_FALSE)
                        .put(SEARCH_RECIPIENTS_TO_RETURN, SEARCH_RECIP_ALL)
                        .put(SEARCH_FULL_CONVERSATION, ONE_TRUE)
                        .put(SEARCH_LIMIT, pageSize)
                        .put(SEARCH_OFFSET, page * pageSize)
                        .put(SEARCH_NEST_RESULT, ONE_TRUE);
        getConvRequest.setContent(content);
        try {
            getConvRequest.start(processConversationHandler(SEARCH_CONV_RESPONSE, resultHandler));
        } catch (Exception e) {
            log.error("Exception in getConversationRequest ", e);
            resultHandler.handle(Future.failedFuture(e));
        }
    }

    public static void toggleReadConversations(List<String> conversationIdList, boolean unread, String userId,
                                               Handler<AsyncResult<Void>> handler) {
        String operation = unread ? OP_UNREAD : OP_READ;
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
                    // todo how to handle error ?
                    handler.handle(Future.succeededFuture(null));
                }
            }
        };
    }
}
