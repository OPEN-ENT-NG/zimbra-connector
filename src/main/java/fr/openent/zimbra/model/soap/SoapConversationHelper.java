package fr.openent.zimbra.model.soap;

import fr.openent.zimbra.model.constant.SoapConstants;
import fr.openent.zimbra.model.message.Conversation;
import fr.openent.zimbra.model.soap.model.SoapFolder;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import static fr.openent.zimbra.model.constant.SoapConstants.*;
import static fr.openent.zimbra.model.constant.ZimbraConstants.*;

public class SoapConversationHelper {

    private static Logger log = LoggerFactory.getLogger(SoapConversationHelper.class);

    public static void getConversationById(String userId, String conversationId,
                                           Handler<AsyncResult<Conversation>> resultHandler) {
        SoapRequest getConvRequest = SoapRequest.MailSoapRequest(SoapConstants.GET_CONVERSATION_REQUEST, userId);
        JsonObject content = new JsonObject()
                .put(CONVERSATION, new JsonObject()
                        .put(CONVERSATION_ID, conversationId));
        getConvRequest.setContent(content);
        try {
            getConvRequest.start(processConversationHandler(GET_CONVERSATION_RESPONSE, resultHandler));
        } catch (Exception e) {
            log.error("Exception in getConversationRequest ", e);
            resultHandler.handle(Future.failedFuture(e));
        }
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
                            .getJsonObject(CONVERSATION, new JsonObject());
                    Conversation conversation = Conversation.fromZimbra(conversationObject);
                    handler.handle(Future.succeededFuture(conversation));
                } catch (Exception e) {
                    handler.handle(Future.failedFuture(e));
                }
            }
        };
    }
}
