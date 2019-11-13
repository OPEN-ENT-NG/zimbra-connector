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
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.Server;
import fr.wseduc.webutils.Utils;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.neo4j.Neo4j;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.entcore.common.validation.StringValidation;

import static org.entcore.common.neo4j.Neo4jResult.validResultHandler;

public class SearchService {

    private final EventBus eb;
    private static Neo4j neo;
    public SearchService(Vertx vertx) {
        this.eb = Server.getEventBus(vertx);
        this.neo = Neo4j.getInstance();
    }

    public void findVisibleRecipients(final UserInfos user, final String acceptLanguage, final String search,
                                      final Handler<Either<String, JsonObject>> result) {
        if (validationParamsError(user, result))
            return;

        final JsonObject visible = new JsonObject();
        String wordSearched = Utils.isNotEmpty(search)? StringValidation.removeAccents(search.trim()).toLowerCase() : "";
        callFindVisibles(user, acceptLanguage, result, visible, wordSearched);
    }

    private void callFindVisibles(UserInfos user, final String acceptLanguage, final Handler<Either<String, JsonObject>> result,
                                  final JsonObject visible, String wordSearched) {

        Future<JsonArray> findVisiblesGroupFuture = Future.future(), findVisiblesUsersFuture = Future.future();

        findVisiblesGroup(user.getUserId(),  wordSearched,  AsyncHelper.getJsonArrayEitherHandler(findVisiblesGroupFuture));
        findVisiblesUsers(user.getUserId(),  wordSearched, AsyncHelper.getJsonArrayEitherHandler(findVisiblesUsersFuture));

        CompositeFuture.all( findVisiblesGroupFuture, findVisiblesUsersFuture ).setHandler(responseFutures -> {
            if (responseFutures.failed()) {
                result.handle(new Either.Left<>("Error neo4j when you get visible users and groups : "));
                return;
            }

            JsonArray groups = findVisiblesGroupFuture.result(), users = findVisiblesUsersFuture.result();

            if (acceptLanguage != null) {
                UserUtils.translateGroupsNames(groups, acceptLanguage);
                UserUtils.translateGroupsNames(users, acceptLanguage);
            }
            visible.put("groups", groups).put("users", users);

            result.handle(new Either.Right<>(visible));
        });

    }

    private static void findVisiblesGroup(String userId, String wordSearched,  final Handler<Either<String, JsonArray>> responseGroups) {

        JsonObject params = new JsonObject()
                .put("userId",userId)
                .put("search", wordSearched);

        String queryNeo = "" +
                "MATCH p=(n:User)-[:COMMUNIQUE*0..2]->ipg-[:COMMUNIQUE*0..1]->g<-[:DEPENDS*0..1]-m " +
                "WHERE n.id = {userId} " +
                "AND m.name IS NOT NULL " +
                "AND (NOT(HAS(m.blocked)) OR m.blocked = false) " +
                "AND (m:Group OR m.displayNameSearchField CONTAINS {search}) " +
                "AND (( (length(p) >= 2 OR m.users <> 'INCOMING') " +
                "AND (length(p) < 3 " +
                "OR (ipg:Group AND (m:User OR g<-[:DEPENDS]-m) AND length(p) = 3)))) " +
                "RETURN DISTINCT " +
                "m.id as id, " +
                "m.name as name, " +
                "m.structureName as structureName ";

        neo.execute(queryNeo, params, validResultHandler(responseGroups));
    }

    private static void findVisiblesUsers(String userId, String wordSearched,  final Handler<Either<String, JsonArray>> responseUsers) {

        JsonObject params = new JsonObject()
                .put("userId",userId)
                .put("search", wordSearched);

        String queryNeo = "" +
                "MATCH p=(n:User)-[:COMMUNIQUE*0..2]->ipg-[:COMMUNIQUE*0..1]->g<-[:DEPENDS*0..1]-m " +
                "WHERE n.id = {userId} " +
                "AND m.displayName IS NOT NULL " +
                "AND (NOT(HAS(m.blocked)) OR m.blocked = false) " +
                "AND (m:Group OR m.displayNameSearchField CONTAINS {search}) " +
                "AND (( (length(p) >= 2 OR m.users <> 'INCOMING') " +
                "AND (length(p) < 3 " +
                "OR (ipg:Group AND (m:User OR g<-[:DEPENDS]-m) AND length(p) = 3)))) " +
                "RETURN DISTINCT " +
                "m.id as id, " +
                "m.displayName as displayName, " +
                "m.profiles[0] as profile ";

        neo.execute(queryNeo, params, validResultHandler(responseUsers));
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
