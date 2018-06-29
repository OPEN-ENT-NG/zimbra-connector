package fr.openent.zimbra.service.synchro;

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
        JsonArray fields = new JsonArray().add("id").add("externalId").add("name").add("UAI");
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


        StringBuilder query = new StringBuilder();

        query.append("MATCH (s:Structure)<-[:DEPENDS]-(pg:ProfileGroup)")
            .append("-[:HAS_PROFILE]->(p:Profile)")
            .append(", pg<-[:IN]-(u:User) ")
            .append(", (g:Group)<-[:IN]-u ");

        String  filter =  "WHERE s.UAI IN {uai} ";
        JsonObject params = new JsonObject().put("uai", new fr.wseduc.webutils.collections.JsonArray(structures));

        query.append(filter);

        query.append("RETURN DISTINCT ");
        for (Object field : fields) {
            query.append(" u.").append(field);
            query.append(" as ").append(field).append(",");
        }
        query.deleteCharAt(query.length() - 1);
        query.append(", p.name as profiles");
        query.append(", s.externalId as structures")
                .append(" , CASE WHEN size(u.classes) > 0  THEN  last(collect(u.classes)) END as classes")
                .append(" , collect({groupName:g.name, groupId:g.id}) + {groupName:pg.name, groupId:pg.id} as groups");
        neo.execute(query.toString(), params, validResultHandler(results));
    }
}
