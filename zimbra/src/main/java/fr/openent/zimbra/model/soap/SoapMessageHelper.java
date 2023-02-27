package fr.openent.zimbra.model.soap;

import fr.openent.zimbra.model.constant.SoapConstants;
import fr.openent.zimbra.model.message.Message;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import static fr.openent.zimbra.model.constant.SoapConstants.*;
import static fr.openent.zimbra.model.constant.ZimbraConstants.*;

public class SoapMessageHelper {

    private static Logger log = LoggerFactory.getLogger(SoapMessageHelper.class);

    public static void getMessageById(String userId, String messageId,
                                           Handler<AsyncResult<Message>> resultHandler) {
        SoapRequest getMsgRequest = SoapRequest.MailSoapRequest(SoapConstants.GET_MESSAGE_REQUEST, userId);
        JsonObject content = new JsonObject()
                .put(MSG, new JsonObject()
                        .put(MSG_ID, messageId));
        getMsgRequest.setContent(content);
        try {
            getMsgRequest.start(processMessageHandler(GET_MESSAGE_RESPONSE, resultHandler));
        } catch (Exception e) {
            log.error("Exception in getConversationRequest ", e);
            resultHandler.handle(Future.failedFuture(e));
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static Handler<AsyncResult<JsonObject>> processMessageHandler(
            String respName, Handler<AsyncResult<Message>> handler) {
        return soapResult -> {
            if(soapResult.failed()) {
                handler.handle(Future.failedFuture(soapResult.cause()));
            } else {
                JsonObject jsonResponse = soapResult.result();
                try {
                    JsonObject messageObject = jsonResponse.getJsonObject(BODY)
                            .getJsonObject(respName)
                            .getJsonArray(MSG).getJsonObject(0);
                    Message message = Message.fromZimbra(messageObject);
                    handler.handle(Future.succeededFuture(message));
                } catch (Exception e) {
                    handler.handle(Future.failedFuture(e));
                }
            }
        };
    }

    private static Handler<AsyncResult<Message>> messageRequestHandler(Promise<Message> promise) {
        return message -> {
            if (message.succeeded()) {
                promise.complete(message.result());
            } else {
                String errMessage = String.format("[Zimbra@%s::messageRequestHandler]:  " +
                                "error while getting mail from zimbra: %s",
                        SoapMessageHelper.class.getSimpleName(), message.cause().getMessage());
                log.error(errMessage);
            }
        };
    }

    public static Future<Message> getMessageById(String userId, String messageId) {
        Promise<Message> promise = Promise.promise();

        getMessageById(userId, messageId, messageRequestHandler(promise));

        return promise.future();
    }
}
