package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.model.MailAddress;
import fr.openent.zimbra.service.data.Neo4jZimbraService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class CommunicationService {

    public static final String CAN_COMMUNICATE = "can_communicate";

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

                if(sender.isExternal() || recipient.isExternal()) {
                    //todo handle external sender
                    log.error("External emails not handled");
                    refuseCommunication(handler);
                    return;
                }

                if(senderId.isEmpty() || recipientId.isEmpty()) {
                    handler.handle(new Either.Right<>(new JsonObject().put(CAN_COMMUNICATE, false)));
                    return;
                }
                // check if user
                neoZimbraService.checkUserCommunication(senderId, recipientId, neoResult -> {
                    if(neoResult.isRight()) {
                        handler.handle(neoResult);
                    } else {
                        // check if group
                        neoZimbraService.checkGroupCommunication(senderId, recipientId, neoResultGroup -> {
                            if(neoResultGroup.isRight()) {
                                handler.handle(neoResultGroup);
                            } else {
                                // todo handle error
                                handler.handle(neoResultGroup);
                            }
                        });
                    }
                });
            });
        });
    }

    private void refuseCommunication(Handler<Either<String, JsonObject>> handler) {
        handler.handle(new Either.Right<>(new JsonObject().put(CAN_COMMUNICATE, false)));
    }
}
