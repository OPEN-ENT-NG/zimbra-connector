package fr.openent.zimbra.service.impl;

import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.Server;
import fr.wseduc.webutils.Utils;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.entcore.common.validation.StringValidation;

public class SqlZimbraService {

    private final EventBus eb;
    private final Sql sql;


    private final String userTable;
    private final String groupTable;

    static final String USER_ZIMBRA_NAME = "mailzimbra";
    static final String USER_NEO4J_UID = "uuidneo";

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
    void getUserIdFromMail(String mail, Handler<Either<String, JsonArray>> handler) {

        String query = "SELECT " + USER_NEO4J_UID + " FROM "
                + userTable + " WHERE " + USER_ZIMBRA_NAME + " = ?";
        JsonArray values = new JsonArray().add(mail);

        sql.prepared(query, values, SqlResult.validResultHandler(handler));
    }

    /**
     * Get user mail from uuid in database
     * @param uuid User uuid
     * @param handler result handler
     */
    void getUserMailFromId(String uuid, Handler<Either<String, JsonArray>> handler) {
        getMailFromId(uuid, userTable, handler);
    }

    /**
     * Get group mail from uuid in database
     * @param uuid Group uuid
     * @param handler result handler
     */
    void getGroupMailFromId(String uuid, Handler<Either<String, JsonArray>> handler) {
        getMailFromId(uuid, groupTable, handler);
    }

    /**
     * Get mail from uuid in database
     * @param uuid User uuid
     * @param table table to use for correspondance
     * @param handler result handler
     */
    private void getMailFromId(String uuid, String table, Handler<Either<String, JsonArray>> handler) {

        String query = "SELECT " + USER_ZIMBRA_NAME + " FROM "
                + table + " WHERE " + USER_NEO4J_UID + " = ?";
        JsonArray values = new JsonArray().add(uuid);

        sql.prepared(query, values, SqlResult.validResultHandler(handler));
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
    void updateUsers(JsonArray users, Handler<Either<String, JsonObject>> handler) {

        boolean atLeastOne = false;

        if(users.size() == 0) {
            handler.handle(new Either.Left<>("Incorrect data, can't update users"));
            return;
        }

        StringBuilder query = new StringBuilder();
        query.append("WITH data (name, alias) as ( values ");
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
                .append(USER_ZIMBRA_NAME).append(", ")
                .append(USER_NEO4J_UID).append(") ");
        query.append("SELECT d.name, d.alias ");
        query.append("FROM data d ");
        query.append("WHERE NOT EXISTS (SELECT 1 FROM ").append(userTable)
                .append(" u WHERE u.").append(USER_ZIMBRA_NAME).append(" = d.name ")
                .append("AND u.").append(USER_NEO4J_UID).append(" = d.alias ")
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
                            "visibles.profiles[0] as profile";
            callFindVisibles(user, acceptLanguage, result, visible, params, preFilter, customReturn);
    }

    private void callFindVisibles(UserInfos user, final String acceptLanguage, final Handler<Either<String, JsonObject>> result,
                                  final JsonObject visible, JsonObject params, String preFilter, String customReturn) {
        UserUtils.findVisibles(eb, user.getUserId(), customReturn, params, true, true, true,
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

}
