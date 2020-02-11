package fr.openent.zimbra.model.soap;

import fr.openent.zimbra.helper.StringHelper;
import fr.openent.zimbra.model.constant.SoapConstants;
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

    @SuppressWarnings("unused")
    private static Logger log = LoggerFactory.getLogger(SoapConversationHelper.class);

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

}
