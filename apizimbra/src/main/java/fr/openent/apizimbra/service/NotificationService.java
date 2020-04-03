/*
 * Copyright (c) Région Ile-de-France, Région Nouvelle-Aquitaine, CGI, 2016.
 * This file is part of OPEN ENT NG. OPEN ENT NG is a versatile ENT Project based on the JVM and ENT Core Project.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with OPEN ENT NG is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of OPEN ENT NG, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package fr.openent.apizimbra.service;

import fr.openent.apizimbra.model.MailAddress;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.neo4j.Neo4j;
import org.entcore.common.notification.TimelineHelper;
import org.entcore.common.user.UserInfos;

import java.util.ArrayList;
import java.util.List;

import static org.entcore.common.neo4j.Neo4jResult.validUniqueResultHandler;

public class NotificationService {

    private Neo4j neo;
    private String pathPrefix;
    private TimelineHelper timelineHelper;

    private static Logger log = LoggerFactory.getLogger(NotificationService.class);

    public NotificationService(String pathPrefix, TimelineHelper timelineHelper) {
        this.neo = Neo4j.getInstance();
        this.pathPrefix = pathPrefix;
        this.timelineHelper = timelineHelper;
    }

    public void sendNewMailNotification(String zimbraSender, String zimbraRecipient, String messageId, String subject,
                                        Handler<Either<String,JsonObject>> handler) {
        MailAddress sender;
        try {
            sender = MailAddress.createFromRawAddress(zimbraSender);
        } catch (IllegalArgumentException e) {
            log.error("Invalid Zimbra email : " + zimbraSender + " " + e.getMessage());
            handler.handle(new Either.Left<>("Invalid email"));
            return;
        }
        String timelineSubject = (subject != null && !subject.isEmpty())
                ? subject
                : "<span translate key=\"timeline.no.subject\"></span>";
        sender.fetchNeoId(userId ->
            getUserInfos(userId, user -> {
                String timelineSender = (user != null && user.getUsername() != null)
                        ? user.getUsername()
                        : null;
                String messageUri = pathPrefix + "/zimbra#/read-mail/" + messageId;
                final JsonObject params = new JsonObject()
                        .put("subject", timelineSubject)
                        .put("messageUri", messageUri)
                        .put("resourceUri", messageUri)
                        .put("disableAntiFlood", true);
                params.put("pushNotif", new JsonObject().put("title", "push.notif.new.message").put("body", ""));
                if(timelineSender != null) {
                    params.put("username", timelineSender)
                            .put("uri", "/userbook/annuaire#" + userId );
                } else {
                    params.put("username", zimbraSender);
                }
                List<String> recipients = new ArrayList<>();
                recipients.add(zimbraRecipient);
                timelineHelper.notifyTimeline(null, "apimessagerie.send-message",
                        user, recipients, messageId, params);
                handler.handle(new Either.Right<>(new JsonObject()));
            })
        );
    }

    private void getUserInfos(String userId, Handler<UserInfos> handler)  {

        if(userId == null || userId.isEmpty()) {
            handler.handle(null);
            return;
        }
        String query = "MATCH (u:User) "
                + "WHERE u.id = {userId} "
                + "return u.id as id, u.displayName as displayName";
        JsonObject params = new JsonObject().put("userId", userId);
        neo.execute(query, params, validUniqueResultHandler(result -> {
            UserInfos returnUser = null;
            if(result.isRight()) {
                JsonObject neoData = result.right().getValue();
                returnUser = new UserInfos();
                returnUser.setUserId(neoData.getString("id"));
                returnUser.setUsername(neoData.getString("displayName"));
            }
            handler.handle(returnUser);
        }));
    }
}
