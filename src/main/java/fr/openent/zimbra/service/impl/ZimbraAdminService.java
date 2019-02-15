package fr.openent.zimbra.service.impl;

import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.neo4j.Neo4j;
import org.entcore.common.neo4j.Neo4jResult;


import java.util.UUID;

import static org.entcore.common.neo4j.Neo4jResult.validUniqueResultHandler;

public class ZimbraAdminService {

    private static final Neo4j neo = Neo4j.getInstance();
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    public static final String ROLE_NAME = "zimbra.outside.donotchange";
    private static final String ACTION_NAME = "fr.openent.zimbra.controllers.ZimbraController|zimbraOutside";

    public static void listGroupsWithRole(String structureId, boolean classGroups, Handler<Either<String, JsonArray>> handler) {
       getTheRole(event -> {
           if(event.isRight()){
               String query =
                       "MATCH (a:Application {id: {appId}})-[:PROVIDE]->(:Action)<-[:AUTHORIZE]-(r:Role) " +
                               "WITH r,a " +
                               "MATCH (r)-[:AUTHORIZE]->(:Action)<-[:PROVIDE]-(apps:Application) " +
                               "    OPTIONAL MATCH (s:Structure {id: {structureId}})<-[:DEPENDS*1..2]-(g:Group)-[:AUTHORIZED]->(r) " +
                               "    WITH r, a, apps, CASE WHEN g IS NOT NULL THEN COLLECT(DISTINCT{ id: g.id, name: g.name }) ELSE [] END as groups " +
                               "RETURN r.id as id, r.name as name, a.id as appId, groups, COUNT(DISTINCT apps) > 1 as transverse";
               JsonObject params = new JsonObject()
                       .put("appId", event.right().getValue().getJsonObject("role").getString("id"))
                       .put("structureId", structureId);
               neo.execute(query, params, Neo4jResult.validResultHandler(handler));
           }else{
               handler.handle(new Either.Left<>("no.role"));
           }
       });
    }
    public static void getTheRole(Handler<Either<String, JsonObject>> handler){
        StringBuilder query = new StringBuilder()
                .append(" MATCH (w:Action{name:{actionName}}) " )
                .append(" MERGE (n:Role {name:{roleName}}) ON CREATE SET n.id = {roleId}" )
                .append(" MERGE (n)-[a:AUTHORIZE]->(w) ")
                .append(" return  {name :n.name, id: n.id} as role");
        JsonObject params = new JsonObject()
                .put("actionName",ACTION_NAME)
                .put("roleName",ROLE_NAME)
                .put("roleId",  UUID.randomUUID().toString());
        neo.execute(query.toString(), params,validUniqueResultHandler(handler));
    }
}
