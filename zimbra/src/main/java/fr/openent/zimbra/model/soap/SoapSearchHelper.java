package fr.openent.zimbra.model.soap;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.model.constant.SoapConstants;
import fr.openent.zimbra.model.constant.ZimbraErrors;
import fr.openent.zimbra.model.message.Conversation;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.DecodeException;
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

    public static void searchAllMailedConv(String userId, int page, String queryString, Handler<AsyncResult<List<Conversation>>> handler) {
        String query =  excludeFolder(SEARCH_QUERY_ALL, FOLDER_DRAFT);
        if(queryString != null) {
            query = queryString;
        }
        search(userId, query, page, SEARCH_TYPE_CONVERSATION, SEARCH_RECIP_ALL, searchResult -> {
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

    public static void searchMailsInConv(String userId, String conversationId, int page,
                                           Handler<AsyncResult<Conversation>> resultHandler) {
        SoapRequest getConvRequest = SoapRequest.MailSoapRequest(SoapConstants.SEARCH_CONV_REQUEST, userId);
        int pageSize = Zimbra.appConfig.getMailListLimitConversation();
        JsonObject content = getCommonPaginatedSearchParams(SEARCH_QUERY_ALL, SEARCH_RECIP_ALL, pageSize, page);
        content.put(CONVERSATION_CID, conversationId)
                .put(CONVERSATION_EXPAND_MESSAGES, CONV_EXPAND_ALL)
                .put(MSG_HTML, ONE_TRUE)
                .put(MSG_NEUTER_IMAGES, ZERO_FALSE)
                .put(SEARCH_FULL_CONVERSATION, ONE_TRUE)
                .put(SEARCH_NEST_RESULT, ONE_TRUE)
                ;
        getConvRequest.setContent(content);
        try {
            getConvRequest.start(processConvSearchHandler( result -> {
                if(result.failed()) {
                    resultHandler.handle(Future.failedFuture(result.cause()));
                } else {
                    JsonObject searchResult = result.result();
                    if(searchResult.getJsonArray(CONVERSATION, new JsonArray()).isEmpty()) {
                        resultHandler.handle(Future.succeededFuture(null));
                    } else {
                        JsonObject conversationObject = searchResult.getJsonArray(CONVERSATION).getJsonObject(0);
                        Conversation conversation = Conversation.fromZimbra(conversationObject);
                        resultHandler.handle(Future.succeededFuture(conversation));
                    }
                }
            }));
        } catch (Exception e) {
            log.error("Exception in getConversationRequest ", e);
            resultHandler.handle(Future.failedFuture(e));
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static String excludeFolder(String query, String folderPath) {
        return query + " NOT in:\"" + folderPath + "\"";
    }

    private static JsonObject getCommonPaginatedSearchParams(String searchQuery, String recipientsToReturn,
                                                             int pageSize, int page) {
        return new JsonObject()
                .put(SEARCH_QUERY, searchQuery)
                .put(SEARCH_RECIPIENTS_TO_RETURN, recipientsToReturn)
                .put(SEARCH_LIMIT, pageSize)
                .put(SEARCH_OFFSET, page * pageSize);
    }

    @SuppressWarnings("SameParameterValue")
    private static void search(String userId, String searchQuery, int page, String types, String recipientsToReturn,
                               Handler<AsyncResult<JsonObject>> rawHandler) {
        SoapRequest searchRequest = SoapRequest.MailSoapRequest(SEARCH_REQUEST, userId);
        int pageSize = Zimbra.appConfig.getMailListLimit();
        JsonObject content = getCommonPaginatedSearchParams(searchQuery, recipientsToReturn, pageSize, page);
        content.put(SEARCH_TYPES, types);
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
        return processSearchHandler(SEARCH_RESPONSE, handler);
    }

    private static Handler<AsyncResult<JsonObject>> processConvSearchHandler(
            Handler<AsyncResult<JsonObject>> handler) {
        return processSearchHandler(SEARCH_CONV_RESPONSE, handler);
    }

    private static Handler<AsyncResult<JsonObject>> processSearchHandler(
            String requestResponseName, Handler<AsyncResult<JsonObject>> handler) {
        return soapResult -> {
            if(soapResult.failed()) {
                handleSearchError(soapResult.cause().getMessage(), handler);
            } else {
                JsonObject jsonResponse = soapResult.result();
                try {
                    JsonObject searchData = jsonResponse.getJsonObject(BODY)
                            .getJsonObject(requestResponseName);
                    handler.handle(Future.succeededFuture(searchData));
                } catch (Exception e) {
                    handler.handle(Future.failedFuture(e));
                }
            }
        };
    }

    private static void handleSearchError(String errorStr, Handler<AsyncResult<JsonObject>> handler) {
        try {
            SoapError error = new SoapError(errorStr);
            if(ZimbraErrors.ERROR_GENERIC.equals(error.getCode())
                && error.getMessage().contains(ZimbraErrors.ERROR_JAVA_OUTOFBOUND)) {
                handler.handle(Future.succeededFuture(new JsonObject()));
            } else {
                handler.handle(Future.failedFuture(errorStr));
            }
        } catch (DecodeException e) {
            handler.handle(Future.failedFuture(errorStr));
        }
    }
}
