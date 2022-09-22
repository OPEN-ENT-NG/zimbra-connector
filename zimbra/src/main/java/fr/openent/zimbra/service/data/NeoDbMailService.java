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

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.helper.AsyncHelper;
import fr.openent.zimbra.model.Group;
import fr.openent.zimbra.model.ZimbraUser;
import fr.openent.zimbra.model.constant.SynchroConstants;
import fr.openent.zimbra.service.DbMailService;
import fr.wseduc.webutils.Either;
import io.vertx.core.*;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.neo4j.Neo4j;

import java.util.ArrayList;
import java.util.List;

import static org.entcore.common.neo4j.Neo4jResult.validResultHandler;
import static org.entcore.common.neo4j.Neo4jResult.validUniqueResultHandler;

public class NeoDbMailService extends DbMailService {

    private Neo4j neo;
    private EventBus eb;

    private SqlSynchroService sqlSynchroService;

    @SuppressWarnings("FieldCanBeLocal")
    private static String FEEDER_BUSURL = "entcore.feeder";


    private static Logger log = LoggerFactory.getLogger(NeoDbMailService.class);

    public NeoDbMailService(Vertx vertx, SqlSynchroService sqlSynchroService) {
        this.eb = vertx.eventBus();
        this.neo = Neo4j.getInstance();
        this.sqlSynchroService = sqlSynchroService;
    }

    @Override
    public void getNeoIdFromMail(String mail, Handler<Either<String, JsonArray>> handler) {
        String id = getUserNameFromMail(mail);

        String queryUserEmail = "MATCH (u:User) " +
                "WHERE u.emailInternal = {mail} " +
                "RETURN u.id as " + NEO4J_UID + ", 'user' as type";

        String queryUserId = "MATCH (u:User) " +
                "WHERE u.id = {idmail} " +
                "RETURN u.id as " + NEO4J_UID + ", 'user' as type";

        String queryGroup = "MATCH (g:Group) " +
                "WHERE g.id = {idmail} " +
                "RETURN g.id as " + NEO4J_UID + ", 'group' as type";

        String query = queryUserEmail + " UNION " + queryUserId + " UNION " + queryGroup;

        neo.execute(query, new JsonObject().put("mail", mail).put("idmail", id), validUniqueResultToJArrayHandler(handler));
    }

    @Override
    public void getUserMailFromId(String uuid, Handler<Either<String, JsonArray>> handler) {
        String query = "MATCH (u:User) " +
                "WHERE u.id = {uuid} AND u.emailInternal is not null " +
                "RETURN u.emailInternal as " + ZIMBRA_NAME;
        neo.execute(query, new JsonObject().put("uuid", uuid), validUniqueResultToJArrayHandler(handler));
    }

    @Override
    public void getGroupMailFromId(String uuid, Handler<Either<String, JsonArray>> handler) {
        String query = "MATCH (g:Group) " +
                "WHERE g.id = {uuid}  AND g.emailInternal is not null " +
                "RETURN g.emailInternal as " + ZIMBRA_NAME;
        neo.execute(query, new JsonObject().put("uuid", uuid), validUniqueResultToJArrayHandler(handler));
    }

    private Handler<Message<JsonObject>> validUniqueResultToJArrayHandler(
            Handler<Either<String, JsonArray>> handler) {
        return validUniqueResultHandler(res -> {
            if(res.isRight())  {
                JsonObject result = res.right().getValue();
                JsonArray finalResult = new JsonArray();
                if(!result.isEmpty()) {
                    finalResult.add(result);
                }
                handler.handle(new Either.Right<>(finalResult));
            } else {
                handler.handle(new Either.Left<>(res.left().getValue()));
            }
        });
    }

    @Override
    public void removeUserFrombase(String userId, String userMail, Handler<Either<String, JsonObject>> handler) {
        // Not needed in neo4j implementation
        handler.handle(new Either.Right<>(new JsonObject()));
    }

    @Override
    public void updateUserAsync(ZimbraUser user) {
        try {
            List<ZimbraUser> userList = new ArrayList<>();
            userList.add(user);
            updateUsers(userList, neoResponse -> {
                if (neoResponse.failed()) {
                    log.error("Error when updating Zimbra users : " + neoResponse.cause().getMessage());
                }
            });
        } catch (Exception e) {
            //No Exception may be thrown in the main thread
            log.error("Error in updateUserAsync : " + e);
        }
    }

    private void updateUsers(List<ZimbraUser> users, Handler<AsyncResult<JsonObject>> handler) {
        JsonArray jsonUsers = new JsonArray();
        for(ZimbraUser user : users) {
            JsonObject jsonUser = new JsonObject()
                    .put(Field.NAME, user.getName())
                    .put("aliases", new JsonArray(user.getAliases()));
            jsonUsers.add(jsonUser);
        }
        updateUsersByFeeder(jsonUsers, AsyncHelper.getJsonObjectEitherHandler(handler));
    }

    public void updateUsers(JsonArray users, Handler<Either<String, JsonObject>> handler) {
        List<String> userIds = new ArrayList<>();
        for(Object obj : users) {
            if(!(obj instanceof JsonObject)) continue;
            JsonObject user = (JsonObject)obj;
            String userId = getUserNameFromMail(user.getJsonArray("aliases", new JsonArray()).getString(0));
            if(userId.isEmpty()) {
                log.error("Trying to update user with invalid id : " + userId);
            } else {
                userIds.add(userId);
            }
        }
        sqlSynchroService.addUsersToSynchronize(0, userIds, SynchroConstants.ACTION_MODIFICATION, res -> {
            handler.handle(AsyncHelper.jsonObjectAsyncToJsonObjectEither(res));
        });
    }

    private void updateUsersByFeeder(JsonArray users, Handler<Either<String, JsonObject>> handler) {

        List<Future> futureList = new ArrayList<>();

        for(Object obj : users) {
            if(!(obj instanceof JsonObject)) continue;
            JsonObject user = (JsonObject)obj;
            String email = user.getString(Field.NAME, "");
            String userId = getUserNameFromMail(user.getJsonArray("aliases", new JsonArray()).getString(0));
            if(email.isEmpty() || userId.isEmpty()) {
                log.error("Trying to update user with invalid mail : " + email + " or id : " + userId);
            } else {
                JsonObject action = new JsonObject()
                        .put("action", "manual-update-user")
                        .put("userId", userId)
                        .put("data", new JsonObject().put("emailInternal", email));
                Future<Message<JsonObject>> future = Future.future();
                futureList.add(future);
                eb.send(FEEDER_BUSURL, action, future.completer());
            }
        }

        if(futureList.isEmpty()) {
            handler.handle(new Either.Left<>("Incorrect data, can't update users"));
        } else {
            CompositeFuture.join(futureList).setHandler( res -> {
                if(res.succeeded()) {
                    handler.handle(new Either.Right<>(new JsonObject()));
                } else {
                    handler.handle(new Either.Left<>("Error when updating users : " + res.cause().getMessage()));
                }
            });
        }
    }

    @Override
    public void checkGroupsExistence(List<Group> groups, Handler<AsyncResult<JsonArray>> handler) {
        String query = "MATCH (g:Group) " +
                "WHERE g.id in {groupids} " +
                "AND g.emailInternal is null " +
                "RETURN g.id as id";
        JsonArray idList = new JsonArray();
        groups.forEach( t ->  idList.add(t.getId()) );
        neo.execute(query, new JsonObject().put("groupids", idList),
                validResultHandler(AsyncHelper.getJsonArrayEitherHandler(handler)));
    }

    @Override
    public void updateGroup(String groupId, String groupAddr, Handler<AsyncResult<JsonObject>> handler) {
        JsonObject action = new JsonObject()
                .put("action", "manual-update-email-group")
                .put("groupId", groupId)
                .put("email", groupAddr);
        eb.send(FEEDER_BUSURL, action, (Handler<AsyncResult<Message<JsonObject>>>) res -> {
            if (res.failed()) {
                handler.handle(Future.failedFuture(res.cause()));
            } else {
                if ("ok".equals(res.result().body().getString(Field.STATUS))) {
                    handler.handle(Future.succeededFuture(new JsonObject()));
                } else {
                    handler.handle(Future.failedFuture("update group failure"));
                }
            }
        });
    }

    @Override
    public void insertReturnedMail(JsonObject returnedMail, Handler<Either<String, JsonObject>> handler) {
        // Not needed in neo4j implementation
        handler.handle(new Either.Right<>(new JsonObject()));
    }

    @Override
    public void getMailReturned(String idStructure, Handler<Either<String, JsonArray>> handler) {
        // Not needed in neo4j implementation
        handler.handle(new Either.Right<>(new JsonArray()));
    }

    @Override
    public void removeMailReturned(String id, Handler<Either<String, JsonArray>> handler) {
        // Not needed in neo4j implementation
        handler.handle(new Either.Right<>(new JsonArray()));
    }

    @Override
    public void getMailReturnedByStatut(String statut, Handler<Either<String, JsonArray>> handler) {
        // Not needed in neo4j implementation
        handler.handle(new Either.Right<>(new JsonArray()));
    }

    @Override
    public void getMailReturnedByMailsIdsAndUser(List<String> ids, String user_id, Handler<Either<String, JsonArray>> handler) {
        // Not needed in neo4j implementation
        handler.handle(new Either.Right<>(new JsonArray()));
    }

    @Override
    public void updateStatut(JsonArray returnedMailsStatut, Handler<Either<String, JsonArray>> handler) {
        // Not needed in neo4j implementation
        handler.handle(new Either.Right<>(new JsonArray()));
    }

    @Override
    public void getMailReturnedByIds(List<String> ids, Handler<Either<String, JsonArray>> handler) {
        // Not needed in neo4j implementation
        handler.handle(new Either.Right<>(new JsonArray()));
    }
}
