package fr.openent.zimbra.service.impl;

import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;

public class SqlZimbraService {

    private final Sql sql;


    private final String userTable;

    static final String USER_ZIMBRA_NAME = "mailzimbra";
    static final String USER_NEO4J_UID = "uuidneo";

    public SqlZimbraService(String schema) {
        this.sql = Sql.getInstance();
        this.userTable = schema + ".users";
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
}
