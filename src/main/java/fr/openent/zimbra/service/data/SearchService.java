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

import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.Server;
import fr.wseduc.webutils.Utils;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.entcore.common.validation.StringValidation;

public class SearchService {

    private final EventBus eb;

    public SearchService(Vertx vertx) {
        this.eb = Server.getEventBus(vertx);
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
}
