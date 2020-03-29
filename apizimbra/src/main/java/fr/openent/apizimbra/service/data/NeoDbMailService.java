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

package fr.openent.apizimbra.service.data;

import fr.wseduc.webutils.Either;
import io.vertx.core.*;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.neo4j.Neo4j;

import static org.entcore.common.neo4j.Neo4jResult.validUniqueResultHandler;

public class NeoDbMailService extends DbMailService {

    private Neo4j neo;

    public NeoDbMailService() {
        this.neo = Neo4j.getInstance();
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
}
