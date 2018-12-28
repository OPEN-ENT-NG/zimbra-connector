package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.service.data.SoapZimbraService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
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
    private UserService userService;


    public NotificationService(UserService userService,
                               String pathPrefix, TimelineHelper timelineHelper) {
        this.neo = Neo4j.getInstance();
        this.pathPrefix = pathPrefix;
        this.timelineHelper = timelineHelper;
        this.userService = userService;
    }

    public void sendNewMailNotification(String zimbraSender, String zimbraRecipient, String messageId, String subject,
                                        Handler<Either<String,JsonObject>> handler) {
        String timelineSubject = (subject != null && !subject.isEmpty())
                ? subject
                : "<span translate key=\"timeline.no.subject\"></span>";
        userService.getAliases(zimbraSender, aliasRes -> {
            String userId = aliasRes.succeeded()
                    ? aliasRes.result().getJsonArray("aliases").getString(0).split("@")[0]
                    : "";
            getUserInfos(userId, user -> {
                String timelineSender = (user != null && user.getUsername() != null)
                        ? user.getUsername()
                        : "<span translate key=\"timeline.no.sender\"></span>";
                String messageUri = pathPrefix + "/zimbra#/read-mail/" + messageId;
                final JsonObject params = new JsonObject()
                        .put("uri", "/userbook/annuaire#" + userId )
                        .put("username", timelineSender)
                        .put("subject", timelineSubject)
                        .put("messageUri", messageUri)
                        .put("resourceUri", messageUri)
                        .put("disableAntiFlood", true);
                List<String> recipients = new ArrayList<>();
                recipients.add(zimbraRecipient);
                timelineHelper.notifyTimeline(null, "messagerie.send-message",
                        user, recipients, messageId, params);
                handler.handle(new Either.Right<>(new JsonObject()));
            });
        });
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
