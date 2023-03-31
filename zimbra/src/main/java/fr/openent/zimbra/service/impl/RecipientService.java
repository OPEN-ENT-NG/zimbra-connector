package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.core.enums.ErrorEnum;
import fr.openent.zimbra.helper.AsyncContainer;
import fr.openent.zimbra.model.message.Message;
import fr.openent.zimbra.model.message.Recipient;
import io.vertx.core.*;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.*;

public class RecipientService {

    private MessageService messageService;

    private Map<String, Recipient> zimbraMap = new HashMap<>();

    private static Logger log = LoggerFactory.getLogger(RecipientService.class);

    public RecipientService(MessageService messageService) {
        this.messageService = messageService;
    }

    public Future<Map<String, Recipient>> getUserIdsFromEmails(Set<String> emailList) {
        Promise<Map<String, Recipient>> promise = Promise.promise();

        getUseridsFromEmails(emailList, list -> {
            if (list.succeeded()) {
                promise.complete(list.result());
            } else {
                String errMessage = String.format("[Zimbra@%s::getUseridsFromEmails]:  " +
                                "Error while retrieving ids: %s",
                        this.getClass().getSimpleName(), list.cause().getMessage());
                log.error(errMessage);
                promise.fail(ErrorEnum.ERROR_FETCHING_IDS.method());
            }
        });

        return promise.future();
    }

    @SuppressWarnings("rawtypes")
    public void getUseridsFromEmails(Set<String> emailList, Handler<AsyncResult<Map<String, Recipient>>> handler) {
        List<Future> toSearch = new ArrayList<>();
        Map<String, Recipient> resultMap = new HashMap<>();
        emailList.forEach(email -> {
            if(zimbraMap.containsKey(email)) {
                resultMap.put(email, zimbraMap.get(email));
            } else {
                Future<Recipient> emailResolved = Future.future();
                messageService.translateMailFuture(email, emailResolved);
                toSearch.add(emailResolved);
            }
        });
        if(toSearch.isEmpty()) {
            handler.handle(Future.succeededFuture(resultMap));
        } else {
            CompositeFuture.join(toSearch).setHandler(compo -> {
                AsyncContainer<Boolean> atLeastOne = new AsyncContainer<>();
                atLeastOne.setValue(false);
                toSearch.forEach(future -> {
                    if (future.succeeded()) {
                        try {
                            Recipient futureRes = (Recipient) future.result();
                            zimbraMap.put(futureRes.getEmailAddress(), futureRes);
                            resultMap.put(futureRes.getEmailAddress(), futureRes);
                            atLeastOne.setValue(true);
                        } catch (Exception e) {
                            log.error("Error when translating recipient", e);
                        }
                    }
                });
                if (atLeastOne.getValue()) {
                    handler.handle(Future.succeededFuture(resultMap));
                }
            });
        }
    }

}
