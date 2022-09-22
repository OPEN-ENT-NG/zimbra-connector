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

package fr.openent.zimbra.service.synchro;

import fr.openent.zimbra.core.constants.Field;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.neo4j.Neo4j;


import java.util.List;

import static org.entcore.common.neo4j.Neo4jResult.validResultHandler;

public class SynchroExportService {

    private Neo4j neo;

    public SynchroExportService(){
        this.neo = Neo4j.getInstance();
    }

    /**
     * List Structures for Zimbra Synchronisation
     * @param results final handler
     */
    public void listStructures(Handler<Either<String, JsonArray>> results) {
        JsonArray fields = new JsonArray().add(Field.ID).add("externalId").add(Field.NAME).add("UAI");
        StringBuilder query = new StringBuilder();
        query.append("MATCH (s:Structure) RETURN ");
        for (Object field : fields) {
            query.append(" s.").append(field).append(" as ").append(field).append(",");
        }
        query.deleteCharAt(query.length() - 1);
        neo.execute(query.toString(), (JsonObject) null, validResultHandler(results));
    }

    /**
     * List users for one or more structure
     * for Zimbra Synchronisation
     * @param structures List of structures UAI to process
     * @param results final handler
     */
    public void listUsersByStructure(List<String> structures, Handler<Either<String, JsonArray>> results) {
        if (structures == null || structures.isEmpty()) {
            results.handle(new Either.Left<>("missing.uai"));
            return;
        }

        JsonArray fields = new JsonArray().add("externalId").add("lastName").add("firstName").add("login");
        fields.add("email").add("emailAcademy").add("mobile").add("deleteDate").add("functions").add("displayName");
        fields.add(Field.ID).add("blocked").add("emailInternal");

        StringBuilder query = new StringBuilder();

        query.append("MATCH (s:Structure)<-[:DEPENDS]-(pg:ProfileGroup)")
            .append("-[:HAS_PROFILE]->(p:Profile)")
            .append(", pg<-[:IN]-(u:User) ");

        String  filter =  "WHERE s.UAI IN {uai} ";
        JsonObject params = new JsonObject().put("uai", new fr.wseduc.webutils.collections.JsonArray(structures));

        query.append(filter);
        query.append("OPTIONAL MATCH (g:Group)<-[:IN]-u ");

        query.append("RETURN DISTINCT ");
        for (Object field : fields) {
            query.append(" u.").append(field);
            query.append(" as ").append(field).append(",");
        }
        query.deleteCharAt(query.length() - 1);
        query.append(", p.name as profiles");
        query.append(", s.UAI as UAI");
        query.append(", not exists(u.activationCode) as isActive");
        query.append(", s.externalId as structures")
                .append(" , collect(distinct {groupName:g.name, groupId:g.id}) as groups");
        neo.execute(query.toString(), params, validResultHandler(results));
    }
}
