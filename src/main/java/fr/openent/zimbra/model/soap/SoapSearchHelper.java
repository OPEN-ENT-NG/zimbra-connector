package fr.openent.zimbra.model.soap;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.model.constant.SoapConstants;
import fr.openent.zimbra.model.message.Conversation;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static fr.openent.zimbra.model.constant.SoapConstants.*;
import static fr.openent.zimbra.model.constant.ZimbraConstants.*;

public class SoapSearchHelper {

    private static Logger log = LoggerFactory.getLogger(SoapSearchHelper.class);

    public static void searchAllConv(String userId, int page, Handler<AsyncResult<List<Conversation>>> handler) {
        search(userId, SEARCH_QUERY_ALL, page, SEARCH_TYPE_CONVERSATION, SEARCH_RECIP_ALL, searchResult -> {
            if(searchResult.failed()) {
                handler.handle(Future.failedFuture(searchResult.cause()));
            } else {
                JsonArray convArray = searchResult.result().getJsonArray(CONVERSATION, new JsonArray());
                List<Conversation> convList = new ArrayList<>();
                convArray.forEach( item -> {
                    if(item instanceof JsonObject) {
                        Conversation conversation = Conversation.fromZimbra((JsonObject)item);
                        convList.add(conversation);
                    }
                });
                handler.handle(Future.succeededFuture(convList));
            }
        });
    }

    @SuppressWarnings("SameParameterValue")
    private static String generateSearchQuery(String folderPath, boolean unread, String searchText) {
        String searchQuery = "in:\"" + folderPath + "\"";
        if(unread) {
            searchQuery += " is:unread";
        }
        if(searchText != null && ! searchText.isEmpty()) {
            searchQuery += " *" + searchText +"*";
        }
        return searchQuery;
    }

    @SuppressWarnings("SameParameterValue")
    private static void search(String userId, String searchQuery, int page, String types, String recipientsToReturn,
                               Handler<AsyncResult<JsonObject>> rawHandler) {
        SoapRequest searchRequest = SoapRequest.MailSoapRequest(SoapConstants.SEARCH_REQUEST, userId);
        int pageSize = Zimbra.appConfig.getMailListLimit();
        JsonObject content = new JsonObject()
                .put(SEARCH_QUERY, searchQuery)
                .put(SEARCH_TYPES, types)
                .put(SEARCH_RECIPIENTS_TO_RETURN, recipientsToReturn)
                .put(SEARCH_LIMIT, pageSize)
                .put(SEARCH_OFFSET, page * pageSize);
        searchRequest.setContent(content);
        try {
            searchRequest.start(processRawSearchHandler(rawHandler));
        } catch (Exception e) {
            log.error("Exception in searchRequest ", e);
            rawHandler.handle(Future.failedFuture(e));
        }
    }

    private static Handler<AsyncResult<JsonObject>> processRawSearchHandler(
            Handler<AsyncResult<JsonObject>> handler) {
        return soapResult -> {
            if(soapResult.failed()) {
                handler.handle(Future.failedFuture(soapResult.cause()));
            } else {
                JsonObject jsonResponse = soapResult.result();
                try {
                    JsonObject searchData = jsonResponse.getJsonObject(BODY)
                            .getJsonObject(SEARCH_RESPONSE);
                    handler.handle(Future.succeededFuture(searchData));
                } catch (Exception e) {
                    handler.handle(Future.failedFuture(e));
                }
            }
        };
    }
}
