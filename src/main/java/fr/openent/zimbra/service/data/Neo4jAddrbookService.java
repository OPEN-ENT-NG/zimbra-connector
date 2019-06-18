package fr.openent.zimbra.service.data;

import fr.openent.zimbra.helper.AsyncHelper;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.neo4j.Neo4j;

import static org.entcore.common.neo4j.Neo4jResult.validResultHandler;

public class Neo4jAddrbookService {

    private Neo4j neo;


    public static final String FIRSTNAME = "firstName";
    public static final String LASTNAME = "lastName";
    public static final String EMAIL = "email";
    public static final String PROFILE = "profile";
    public static final String FUNCTIONS = "functions";
    public static final String SUBJECTS = "subjects";
    public static final String CLASSES = "classes";
    public static final String GROUPNAME = "name";
    public static final String GROUP_TYPE = "grouptype";
    public static final String I18N_NEEDED = "i18n_needed";
    public static final String PROFILE_GUEST = "Guest";
    public static final String PROFILE_PERSONNEL = "Personnel";
    public static final String PROFILE_STUDENT = "Student";
    public static final String PROFILE_RELATIVE = "Relative";
    public static final String PROFILE_TEACHER = "Teacher";
    public static final String GROUP_OTHER = "other";

    public Neo4jAddrbookService() {
        this.neo = Neo4j.getInstance();
    }

    public void getAllUsersFromStructure(String uai, Handler<AsyncResult<JsonArray>> handler) {
        String query = "MATCH (u:User)-[:IN]-(pg:ProfileGroup)-[:DEPENDS]-(s:Structure) " +
                "WHERE s.UAI={uai} " +
                "OPTIONAL MATCH (u)-[:IN]-(pgc:ProfileGroup)-[:DEPENDS]-(c:Class)-[:BELONGS]-(s) " +
                "OPTIONAL MATCH (u)-[:IN]-(fg:FunctionGroup)-[:DEPENDS]-(s) " +
                "OPTIONAL MATCH (u)-[:TEACHES]-(subj:Subject)-[:SUBJECT]-(s) " +
                "RETURN u.lastName as " + LASTNAME + ", " +
                "u.firstName as " + FIRSTNAME + ", " +
                "u.emailInternal as " + EMAIL + ", " +
                "collect(distinct fg.filter) as " + FUNCTIONS + ", " +
                "collect(distinct subj.label) as " + SUBJECTS + ", " +
                "pg.filter as " + PROFILE + ", " +
                "collect(distinct c.name) as " + CLASSES +
                " ORDER BY " + PROFILE + ", " + CLASSES + "[0], " + LASTNAME + ", " + FIRSTNAME;

        JsonObject params = new JsonObject()
                .put("uai", uai);

        neo.execute(query, params, validResultHandler(AsyncHelper.getJsonArrayEitherHandler(handler)));
    }

    public void getAllGroupsFromStructure(String uai, Handler<AsyncResult<JsonArray>> handler) {
        String query =
                "MATCH (g:Group)-[:DEPENDS*]->(s:Structure) " +
                    "WHERE s.UAI={uai} AND g.nbUsers is not null AND g.nbUsers > 0 " +
                "OPTIONAL MATCH (g)-[:HAS_PROFILE]->(p1:Profile) " +
                "OPTIONAL MATCH (g)-[:DEPENDS*]->(:Group)-[:HAS_PROFILE]->(p2:Profile) " +
                "RETURN g.name AS " + GROUPNAME + ", " +
                    "CASE WHEN (g:ProfileGroup) THEN coalesce(p1,p2).name" +
                        " WHEN (g:FuncGroup) THEN " + PROFILE_PERSONNEL +
                        " WHEN (g:DisciplineGroup) THEN " + PROFILE_TEACHER +
                        " WHEN (g:HTGroup) THEN " + PROFILE_TEACHER +
                        " ELSE " + GROUP_OTHER +
                        " END AS " + GROUP_TYPE + ", " +
                    " CASE WHEN (g:ProfileGroup) THEN true" +
                        " WHEN (g:HTGroup) THEN true" +
                        " WHEN (g:FunctionGroup) THEN true" +
                        " ELSE false"+
                        " END AS " + I18N_NEEDED;

        JsonObject params = new JsonObject()
                .put("uai", uai);

        neo.execute(query, params, validResultHandler(AsyncHelper.getJsonArrayEitherHandler(handler)));
    }
}
