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

package fr.openent.zimbra.service.data;

import fr.openent.zimbra.helper.AsyncHelper;
import fr.openent.zimbra.model.Group;
import fr.openent.zimbra.model.ZimbraUser;
import fr.openent.zimbra.service.DbMailService;
import fr.wseduc.webutils.Either;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;

import java.util.ArrayList;
import java.util.List;

public class SqlDbMailService extends DbMailService {

    private final Sql sql;


    private final String userTable;
    private final String groupTable;

    private static Logger log = LoggerFactory.getLogger(SqlDbMailService.class);

    public SqlDbMailService(String schema) {
        this.sql = Sql.getInstance();
        this.userTable = schema + ".users";
        this.groupTable = schema + ".groups";
    }

    /**
     * Get user uuid from mail in database
     * @param mail Zimbra mail
     * @param handler result handler
     */
    public void getNeoIdFromMail(String mail, Handler<Either<String, JsonArray>> handler) {

        String query = "SELECT " + NEO4J_UID + ", 'user' as type FROM "
                + userTable + " WHERE " + ZIMBRA_NAME + " = ? "
                + "UNION ALL "
                + "SELECT " + NEO4J_UID + ", 'group' as type FROM "
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
        try {
            List<ZimbraUser> userList = new ArrayList<>();
            userList.add(user);
            updateUsers(userList, sqlResponse -> {
                if(sqlResponse.isLeft()) {
                    log.error("Error when updating Zimbra users : " + sqlResponse.left().getValue());
                }
            });
        } catch (Exception e) {
            //No Exception may be thrown in the main thread
            log.error("Error in updateUserAsync : " + e);
        }
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
            jsonUsers.add(jsonUser);
        }
        updateUsers(jsonUsers, handler);
    }

    public void updateUsers(JsonArray users, Handler<Either<String, JsonObject>> handler) {

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
                .append(ZIMBRA_NAME).append(", ")
                .append(NEO4J_UID).append(") ");
        query.append("SELECT d.name, d.alias ");
        query.append("FROM data d ");
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
                .append("WHERE gid.id = g.").append(NEO4J_UID)
                .append(")");
        sql.prepared(query.toString(), params,
                SqlResult.validResultHandler(AsyncHelper.getJsonArrayEitherHandler(handler)));
    }


    public void updateGroup(String groupId, String groupAddr, Handler<AsyncResult<JsonObject>> handler) {

        StringBuilder query = new StringBuilder();
        JsonArray params = new JsonArray();
        query.append("WITH new_group (id, addr) as ( values (?,?)");
        params.add(groupId).add(groupAddr);
        query.append(") INSERT INTO ").append(groupTable)
                .append(String.format("(%s,%s) ", NEO4J_UID, ZIMBRA_NAME))
                .append("SELECT ng.id, ng.addr ")
                .append("FROM new_group ng ")
                .append("WHERE NOT EXISTS ")
                    .append("( SELECT 1 ")
                    .append("FROM ").append(groupTable).append(" g ")
                    .append("WHERE g.").append(NEO4J_UID).append(" = ng.id ")
                    .append(")");

        sql.prepared(query.toString(), params,
                SqlResult.validUniqueResultHandler(AsyncHelper.getJsonObjectEitherHandler(handler)));
    }

}
