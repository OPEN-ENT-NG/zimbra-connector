package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.Zimbra;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

public class CommunicationService {

    static final String CAN_COMMUNICATE = "can_communicate";

    private Neo4jZimbraService neoZimbraService;
    private MessageService messageService;

    public CommunicationService(MessageService messageService) {
        neoZimbraService = new Neo4jZimbraService();
        this.messageService = messageService;
    }


    private JsonObject processMail(String addrIn) {
        String addrOut = "";
        addrOut = addrIn.replaceAll(".*<","");
        addrOut = addrIn.replaceAll(">.*","");

        String addrDomain = addrOut.substring(addrOut.indexOf('@')+1);
        // TODO check mail format, etc...
        JsonObject result = new JsonObject()
                .put("user", addrOut)
                .put("domain", addrDomain);
        return result;
    }

    private void processWsData(String inSender, String inRecipient, Handler<JsonObject> handler) {
        JsonObject sender = processMail(inSender);
        JsonObject recipient = processMail(inRecipient);
        String senderAddr = sender.getString("user");
        String recipientAddr = recipient.getString("user");

        boolean senderIsLocal = Zimbra.domain.equals(sender.getString("domain"));
        sender.put("isExternal", !senderIsLocal);
        boolean recipientIsLocal = Zimbra.domain.equals(recipient.getString("domain"));
        recipient.put("isExternal", !recipientIsLocal);

        JsonObject result = new JsonObject()
                .put("sender", sender)
                .put("recipient", recipient);

        if(senderIsLocal) {
            messageService.translateMail(senderAddr, senderId -> {
                sender.put("id", senderId == null ? "" : senderId);
                if(recipientIsLocal) {
                    messageService.translateMail(recipientAddr, recipientId -> {
                        recipient.put("id", recipientId == null ? "" : recipientId);
                        handler.handle(result);
                    });
                }
            });
        } else if(recipientIsLocal) {
            messageService.translateMail(recipientAddr, recipientId -> {
                recipient.put("id", recipientId == null ? "" : recipientId);
                handler.handle(result);
            });
        } else {
            handler.handle(result);
        }
    }

    /**
     * Indicates if a sender (user) can send a mail to a receiver (user or group)
     * todo
     * @param inSender
     * @param inRecipient
     * @param handler
     */
    public void canCommunicate(String inSender, String inRecipient, Handler<Either<String,JsonObject>> handler) {

        processWsData(inSender, inRecipient, wsResult -> {
            JsonObject sender = wsResult.getJsonObject("sender");
            JsonObject recipient = wsResult.getJsonObject("recipient");

            // check email domains
            // if external, check mem cache for the local user
            // if empty, check neo4j
            if(sender.getBoolean("isExternal")) {
                //todo handle external sender
                handler.handle(new Either.Left<>("External emails not handled"));
                return;
            }
            if(recipient.getBoolean("isExternal")) {
                //todo handle external recipient
                handler.handle(new Either.Left<>("External emails not handled"));
                return;
            }

            String senderId = sender.getString("id", "");
            String recipientId = recipient.getString("id", "");

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
    }
}
