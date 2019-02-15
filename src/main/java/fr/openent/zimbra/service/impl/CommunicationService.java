package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.model.MailAddress;
import fr.openent.zimbra.service.data.Neo4jZimbraService;
import fr.wseduc.webutils.Either;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class CommunicationService {

    public static final String CAN_COMMUNICATE = "can_communicate";
    public static final String HAS_EXTERNAL_ROLE = "has_external_role";

    private Neo4jZimbraService neoZimbraService;
    private static Logger log = LoggerFactory.getLogger(CommunicationService.class);

    public CommunicationService() {
        neoZimbraService = new Neo4jZimbraService();
    }

    /**
     * Indicates if a sender (user) can send a mail to a receiver (user or group)
     * Returns JsonObject :
     * {
     *     can_communicate : true/false
     * }
     * @param inSender Mail address of the sender
     * @param inRecipient Maid address of the recipient
     * @param handler final handler
     */
    public void canCommunicate(String inSender, String inRecipient, Handler<Either<String,JsonObject>> handler) {

        MailAddress sender;
        MailAddress recipient;
        try {
            sender = MailAddress.createFromRawAddress(inSender);
            recipient = MailAddress.createFromRawAddress(inRecipient);
        } catch (IllegalArgumentException e) {
            log.error("Error when processing WS Data : " + e.getMessage());
            refuseCommunication(handler);
            return;
        }

        //noinspection CodeBlock2Expr
        sender.fetchNeoId(senderId -> {
            recipient.fetchNeoId(recipientId -> {

                if(sender.isExternal() && recipient.isExternal()) {
                    log.error(String.format("Sender %s and recipient %s are external", inSender, inRecipient));
                    refuseCommunication(handler);
                } else  if(sender.isExternal() && !recipientId.isEmpty()) {
                    neoZimbraService.hasExternalCommunicationRole(recipientId, neoResult ->
                        validateExternalCommunication(neoResult, handler)
                    );
                } else if(recipient.isExternal() && !senderId.isEmpty()) {
                    neoZimbraService.hasExternalCommunicationRole(senderId, neoResult ->
                        validateExternalCommunication(neoResult, handler)
                    );
                } else if(senderId.isEmpty() || recipientId.isEmpty()) {
                    handler.handle(new Either.Right<>(new JsonObject().put(CAN_COMMUNICATE, false)));
                } else {
                    // check if user
                    neoZimbraService.checkUserCommunication(senderId, recipientId, neoResult -> {
                        if (neoResult.isRight() && !neoResult.right().getValue().isEmpty()) {
                            handler.handle(neoResult);
                        } else {
                            // check if group
                            neoZimbraService.checkGroupCommunication(senderId, recipientId, neoResultGroup -> {
                                if (neoResultGroup.isRight()) {
                                    handler.handle(neoResultGroup);
                                } else {
                                    log.error("Error when checking communication rights : " + neoResultGroup.left().getValue());
                                    refuseCommunication(handler);
                                }
                            });
                        }
                    });
                }
            });
        });
    }

    private void validateExternalCommunication(AsyncResult<JsonObject> neoResult,
                                               Handler<Either<String, JsonObject>> handler) {
        if(neoResult.succeeded()) {
            JsonObject neoData = neoResult.result();
            if(neoData.getBoolean(HAS_EXTERNAL_ROLE, false)) {
                allowCommunication(handler);
            } else {
                refuseCommunication(handler);
            }
        } else {
            log.error("Error when validating external communication : " + neoResult.cause().getMessage());
            refuseCommunication(handler);
        }
    }

    private void refuseCommunication(Handler<Either<String, JsonObject>> handler) {
        handler.handle(new Either.Right<>(new JsonObject().put(CAN_COMMUNICATE, false)));
    }

    private void allowCommunication(Handler<Either<String, JsonObject>> handler) {
        handler.handle(new Either.Right<>(new JsonObject().put(CAN_COMMUNICATE, true)));
    }
}
