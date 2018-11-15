package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.data.MailAddress;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class CommunicationService {

    static final String CAN_COMMUNICATE = "can_communicate";

    private Neo4jZimbraService neoZimbraService;
    private MessageService messageService;
    private static Logger log = LoggerFactory.getLogger(CommunicationService.class);

    public CommunicationService(MessageService messageService) {
        neoZimbraService = new Neo4jZimbraService();
        this.messageService = messageService;
    }

    /**
     * Indicates if a sender (user) can send a mail to a receiver (user or group)
     * todo
     * @param inSender
     * @param inRecipient
     * @param handler
     */
    public void canCommunicate(String inSender, String inRecipient, Handler<Either<String,JsonObject>> handler) {

        MailAddress sender;
        MailAddress recipient;
        try {
            sender = MailAddress.createFromRawAddress(inSender);
            recipient = MailAddress.createFromRawAddress(inRecipient);
        } catch (IllegalArgumentException e) {
            log.error("Error when processing WS Data : " + e.getMessage());
            answerCanCommunicate(handler, false);
            return;
        }

        sender.fetchNeoId(senderId -> {
            recipient.fetchNeoId(recipientId -> {

                if(sender.isExternal() || recipient.isExternal()) {
                    //todo handle external sender
                    log.error("External emails not handled");
                    answerCanCommunicate(handler, false);
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

    private void answerCanCommunicate(Handler<Either<String,JsonObject>> handler, boolean canCommunicate) {
        handler.handle(new Either.Right<>(new JsonObject().put(CAN_COMMUNICATE, canCommunicate)));
    }
}
