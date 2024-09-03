package fr.openent.zimbra.service.impl;


import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.core.constants.Field;
import io.vertx.core.Handler;
import io.vertx.core.AsyncResult;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import static fr.openent.zimbra.model.constant.FrontConstants.*;
import static fr.openent.zimbra.model.constant.ModuleConstants.*;

public class RedirectionService {

    private UserService userService;
    private EventBus eb;

    private static final Logger log = LoggerFactory.getLogger(RedirectionService.class);

    public RedirectionService(EventBus eb, UserService userService) {
        this.eb = eb;
        this.userService = userService;
    }

    public void getRedirectionUrl(String userId, String recipientId, String recipientName, String recipientType,
                                  Handler<JsonObject> handler) {
        isExpertPreferredMode(userId, isExpertPreferred -> {
            JsonObject result = new JsonObject().put(REDIR_MODE, isExpertPreferred ? EXPERT_MODE : SIMPLE_MODE);
            if(isExpertPreferred) {
                getExpertUrl(recipientId, recipientName, recipientType, url -> handler.handle(result.put(REDIR_URL, url)));
            } else {
                handler.handle(result.put(REDIR_URL, getModuleUrl(recipientId, recipientType)));
            }
        });

    }

    private void isExpertPreferredMode(String userid, Handler<Boolean> handler) {
        if(Zimbra.appConfig.isForceExpertMode()) {
            handler.handle(true);
        } else {
            JsonObject params = new JsonObject().put("action", "get.userlist").put("application", "zimbra")
                    .put("userIds", new JsonArray().add(userid));

            eb.request("userbook.preferences", params, (Handler<AsyncResult<Message<JsonObject>>>) res -> {
                if (res.failed() || res.result().body().getString(Field.STATUS, Field.ERROR).equals(Field.ERROR)) {
                    handler.handle(false);
                } else {
                    try {
                        handler.handle(res.result().body().getJsonArray("results", new JsonArray())
                                .getJsonObject(0).getJsonObject("preferences").getBoolean(PREF_EXPERT_MODE, false));
                    } catch (Exception e) {
                        handler.handle(false);
                    }
                }
            });
        }
    }

    private String getModuleUrl(String recipientId, String recipientType) {
        return URL_PREFIX + URL_ROOT + URL_MIDDLE_WRITEMAIL + recipientId + "/" + recipientType;
    }

    private void getExpertUrl(String recipientId, String recipientName, String recipientType,
                              Handler<String> handler) {
        StringBuilder redirectionUrl = new StringBuilder()
                .append(URL_PREFIX)
                .append(URL_PREAUTH);
        if(recipientId != null && !recipientId.isEmpty()) {
            StringBuilder params = new StringBuilder().append("&view=compose&to=");
            try {
                String encodedName = URLEncoder.encode(recipientName, "UTF-8");
                params.append(encodedName);
            } catch (UnsupportedEncodingException e) {
                log.error("Error when encoding name from mail", e);
            }
            getEmail(recipientId, recipientType, mailResult -> {
                params.append(mailResult);
                try {
                    String encodedParams = URLEncoder.encode(params.toString(), "UTF-8");
                    redirectionUrl.append("?params=").append(encodedParams);
                } catch (UnsupportedEncodingException e) {
                    log.error("Error when encoding params for redirection", e);
                }
                handler.handle(redirectionUrl.toString());
            });
        } else {
            handler.handle(redirectionUrl.toString());
        }
    }

    private void getEmail(String id, String type, Handler<String> handler) {
        if(TYPE_USER.equals(type)) {
            JsonArray idList = new JsonArray().add(id);
            userService.getMailAddresses(idList, jsonResult -> {
                JsonObject jsonUser = jsonResult.getJsonObject(id, new JsonObject());
                String email = jsonUser.getString("email", "");
                if(email.isEmpty()) {
                    email = generateMailFromId(id);
                } else {
                    email = "<" + email + ">";
                }
                handler.handle(email);
            });
        } else {
            handler.handle(generateMailFromId(id));
        }
    }

    private String generateMailFromId(String id) {
        return "<" + id + "@" + Zimbra.appConfig.getZimbraDomain() + ">";
    }
}
