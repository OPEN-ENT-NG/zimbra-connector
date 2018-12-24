package fr.openent.zimbra.service.data;

import fr.openent.zimbra.helper.AsyncHelper;
import fr.openent.zimbra.model.Group;
import fr.openent.zimbra.model.ZimbraUser;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.Server;
import fr.wseduc.webutils.Utils;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.entcore.common.validation.StringValidation;

import java.util.ArrayList;
import java.util.List;

public class SqlZimbraService {

    private final EventBus eb;
    private final Sql sql;


    private final String userTable;
    private final String groupTable;

    public static final String ZIMBRA_NAME = "mailzimbra";
    public static final String NEO4J_UID = "uuidneo";

    private static Logger log = LoggerFactory.getLogger(SqlZimbraService.class);

    public SqlZimbraService(Vertx vertx, String schema) {
        this.eb = Server.getEventBus(vertx);
        this.sql = Sql.getInstance();
        this.userTable = schema + ".users";
        this.groupTable = schema + ".groups";
    }

    private String getUserNameFromMail(String mail) {
        return mail.split("@")[0];
    }

    /**
     * Get user uuid from mail in database
     * @param mail Zimbra mail
     * @param handler result handler
     */
    public void getNeoIdFromMail(String mail, Handler<Either<String, JsonArray>> handler) {

        String query = "SELECT " + NEO4J_UID + " FROM "
                + userTable + " WHERE " + ZIMBRA_NAME + " = ? "
                + "UNION ALL "
                + "SELECT " + NEO4J_UID + " FROM "
                + groupTable + " WHERE " + ZIMBRA_NAME + " = ? ";
        JsonArray values = new JsonArray().add(mail).add(mail);

        sql.prepared(query, values, SqlResult.validResultHandler(handler));
    }

    /**
     * Get user mail from uuid in database
     * @param uuid User uuid
     * @param handler result handler
     */
    public void getUserMailFromId(String uuid, Handler<Either<String, JsonArray>> handler) {
        getMailFromId(uuid, userTable, handler);
    }

    /**
     * Get group mail from uuid in database
     * @param uuid Group uuid
     * @param handler result handler
     */
    public void getGroupMailFromId(String uuid, Handler<Either<String, JsonArray>> handler) {
        getMailFromId(uuid, groupTable, handler);
    }

    /**
     * Get mail from uuid in database
     * @param uuid User uuid
     * @param table table to use for correspondance
     * @param handler result handler
     */
    private void getMailFromId(String uuid, String table, Handler<Either<String, JsonArray>> handler) {

        String query = "SELECT " + ZIMBRA_NAME + " FROM "
                + table + " WHERE " + NEO4J_UID + " = ?";
        JsonArray values = new JsonArray().add(uuid);

        sql.prepared(query, values, SqlResult.validResultHandler(handler));
    }

    /**
     * Remove user from base
     * @param userId user id
     * @param userMail user mail
     * @param handler final handler
     */
    public void removeUserFrombase(String userId, String userMail, Handler<Either<String,JsonObject>> handler) {
        String query = "DELETE FROM " + userTable
                + " WHERE " + NEO4J_UID + " = ? OR "
                + ZIMBRA_NAME + " = ?";
        JsonArray values = new JsonArray().add(userId).add(userMail);
        sql.prepared(query, values, SqlResult.validRowsResultHandler(handler));
    }

    public void updateUserAsync(ZimbraUser user) {
        List<ZimbraUser> userList = new ArrayList<>();
        userList.add(user);
        updateUsers(userList, sqlResponse -> {
            if(sqlResponse.isLeft()) {
                log.error("Error when updating Zimbra users : " + sqlResponse.left().getValue());
            }
        });
    }

    /**
     * Update users mails in database if not already present
     * @param users Array of users :
     *              [
     *                {
     *                  "name" : user address in Zimbra,
     *                  "aliases :
     *                      [
     *                          "alias"
     *                      ]
     *                }
     *              ]
     * @param handler result handler
     */
    private void updateUsers(List<ZimbraUser> users, Handler<Either<String, JsonObject>> handler) {
        JsonArray jsonUsers = new JsonArray();
        for(ZimbraUser user : users) {
            JsonObject jsonUser = new JsonObject()
                    .put("name", user.getName())
                    .put("aliases", new JsonArray(user.getAliases()));
        }
        updateUsers(jsonUsers, handler);
    }

    public void updateUsers(JsonArray users, Handler<Either<String, JsonObject>> handler) {

        boolean atLeastOne = false;

        if(users.size() == 0) {
            handler.handle(new Either.Left<>("Incorrect model, can't update users"));
            return;
        }

        StringBuilder query = new StringBuilder();
        query.append("WITH model (name, alias) as ( values ");
        for(Object obj : users) {
            if(!(obj instanceof JsonObject)) continue;
            JsonObject user = (JsonObject)obj;
            String name = user.getString("name");
            JsonArray aliases = user.getJsonArray("aliases");
            for (Object o : aliases) {
                if(!(o instanceof String)) continue;
                String alias = o.toString();
                atLeastOne = true;
                query.append("( '").append(name).append("', '")
                        .append(getUserNameFromMail(alias))
                        .append("'),");
            }
        }
        query.deleteCharAt(query.length() - 1);
        query.append(") ");
        query.append("INSERT INTO ").append(userTable).append("( ")
                .append(ZIMBRA_NAME).append(", ")
                .append(NEO4J_UID).append(") ");
        query.append("SELECT d.name, d.alias ");
        query.append("FROM model d ");
        query.append("WHERE NOT EXISTS (SELECT 1 FROM ").append(userTable)
                .append(" u WHERE u.").append(ZIMBRA_NAME).append(" = d.name ")
                .append("AND u.").append(NEO4J_UID).append(" = d.alias ")
                .append(")");

        if(!atLeastOne) {
            handler.handle(new Either.Left<>("No users to process"));
        } else {
            sql.prepared(query.toString(), new JsonArray(), SqlResult.validUniqueResultHandler(handler));
        }
    }


    public void findVisibleRecipients(final UserInfos user, final String acceptLanguage, final String search,
                                      final Handler<Either<String, JsonObject>> result) {
        if (validationParamsError(user, result))
            return;

        final JsonObject visible = new JsonObject();

        final JsonObject params = new JsonObject();

        final String preFilter;
        if (Utils.isNotEmpty(search)) {
            preFilter = "AND (m:Group OR m.displayNameSearchField CONTAINS {search}) ";
            params.put("search", StringValidation.removeAccents(search.trim()).toLowerCase());
        } else {
            preFilter = null;
        }


            String customReturn =
                    "RETURN DISTINCT visibles.id as id, visibles.name as name, " +
                            "visibles.displayName as displayName, visibles.groupDisplayName as groupDisplayName, " +
                            "visibles.profiles[0] as profile, visibles.structureName as structureName ";
            callFindVisibles(user, acceptLanguage, result, visible, params, preFilter, customReturn);
    }

    private void callFindVisibles(UserInfos user, final String acceptLanguage, final Handler<Either<String, JsonObject>> result,
                                  final JsonObject visible, JsonObject params, String preFilter, String customReturn) {
        UserUtils.findVisibles(eb, user.getUserId(), customReturn, params, true, true, false,
                acceptLanguage, preFilter, visibles -> {

                JsonArray users = new fr.wseduc.webutils.collections.JsonArray();
                JsonArray groups = new fr.wseduc.webutils.collections.JsonArray();
                visible.put("groups", groups).put("users", users);
                for (Object o: visibles) {
                    if (!(o instanceof JsonObject)) continue;
                    JsonObject j = (JsonObject) o;
                    if (j.getString("name") != null) {
                        j.remove("displayName");
                        UserUtils.groupDisplayName(j, acceptLanguage);
                        groups.add(j);
                    } else {
                        j.remove("name");
                        users.add(j);
                    }
                }
                result.handle(new Either.Right<>(visible));
        });
    }


    private boolean validationParamsError(UserInfos user,
                                          Handler<Either<String, JsonObject>> result, String ... params) {
        if (user == null) {
            result.handle(new Either.Left<>("zimbra.invalid.user"));
            return true;
        }
        if (params.length > 0) {
            for (String s : params) {
                if (s == null) {
                    result.handle(new Either.Left<>("zimbra.invalid.parameter"));
                    return true;
                }
            }
        }
        return false;
    }



    public void checkGroupsExistence(List<Group> groups, Handler<AsyncResult<JsonArray>> handler) {
        if(groups.isEmpty()) {
            handler.handle(Future.succeededFuture(new JsonArray()));
            return;
        }
        JsonArray params = new JsonArray();
        StringBuilder query = new StringBuilder();
        query.append("SELECT * ")
                .append("FROM (")
                .append("VALUES");
        String delim = " ";
        for(Group group : groups) {
            query.append(delim).append("(?)");
            params.add(group.getId());
            delim = ",";
        }
        query.append(") as gid(id) ")
                .append("WHERE NOT EXISTS (")
                .append("SELECT 1")
                .append("FROM ").append(groupTable).append(" g ")
                .append("WHERE gid.id = ").append(groupTable).append(".").append(NEO4J_UID)
                .append(")");
        sql.prepared(query.toString(), new JsonArray(),
                SqlResult.validResultHandler(AsyncHelper.getJsonArrayEitherHandler(handler)));
    }

}
