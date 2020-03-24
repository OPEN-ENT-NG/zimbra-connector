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

package fr.openent.zimbra.filters;

import static org.entcore.common.user.UserUtils.findVisibles;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import org.entcore.common.http.filter.ResourcesProvider;
import org.entcore.common.neo4j.Neo4j;
import org.entcore.common.user.UserInfos;

import io.vertx.core.json.JsonObject;

import fr.wseduc.webutils.http.Binding;
import fr.wseduc.webutils.request.RequestUtils;


@SuppressWarnings("unused")
public class VisiblesFilter implements ResourcesProvider{

    private Neo4j neo;

    public VisiblesFilter() {
        neo = Neo4j.getInstance();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void authorize(HttpServerRequest request, Binding binding,
                          final UserInfos user, final Handler<Boolean> handler) {

        final String parentMessageId = request.params().get("In-Reply-To");
        final Set<String> ids = new HashSet<>();
        final String customReturn = "WHERE visibles.id IN {ids} RETURN DISTINCT visibles.id";
        final JsonObject params = new JsonObject();

        RequestUtils.bodyToJson(request, message -> {
                ids.addAll(message.getJsonArray("to", new fr.wseduc.webutils.collections.JsonArray()).getList());
                ids.addAll(message.getJsonArray("cc", new fr.wseduc.webutils.collections.JsonArray()).getList());

                final Handler<Void> checkHandler = v -> {
                        params.put("ids", new fr.wseduc.webutils.collections.JsonArray(new ArrayList<>(ids)));
                        findVisibles(neo.getEventBus(), user.getUserId(), customReturn, params, true, true, false, visibles ->
                                handler.handle(visibles.size() == ids.size())
                        );
                };

                if(parentMessageId == null || parentMessageId.trim().isEmpty()){
                    checkHandler.handle(null);
                }
        });

    }

}
