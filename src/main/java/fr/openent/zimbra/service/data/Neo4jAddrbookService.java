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
    public static final String CLASSES = "classes";
    public static final String PROFILE_GUEST = "Guest";
    public static final String PROFILE_PERSONNEL = "Personnel";
    public static final String PROFILE_STUDENT = "Student";
    public static final String PROFILE_RELATIVE = "Relative";
    public static final String PROFILE_TEACHER = "Teacher";

    public Neo4jAddrbookService() {
        this.neo = Neo4j.getInstance();
    }

    public void getAllUsersFromStructure(String uai, Handler<AsyncResult<JsonArray>> handler) {
        String query = "MATCH (u:User)-[:IN]-(pg:ProfileGroup)-[:DEPENDS]-(s:Structure) " +
                "WHERE s.UAI={uai} " +
                "OPTIONAL MATCH (u)-[:IN]-(pgc:ProfileGroup)-[:DEPENDS]-(c:Class)-[:BELONGS]-(s) " +
                "OPTIONAL MATCH (u)-[:IN]-(fg:FunctionGroup)-[:DEPENDS]-(s) " +
                "RETURN u.lastName as " + LASTNAME + ", " +
                "u.firstName as " + FIRSTNAME + ", " +
                "u.emailInternal as " + EMAIL + ", " +
                "fg.filter as " + FUNCTIONS + ", " +
                "pg.filter as " + PROFILE + ", " +
                "collect(c.name) as " + CLASSES +
                " ORDER BY " + PROFILE + ", " + CLASSES + "[0], " + LASTNAME + ", " + FIRSTNAME;

        JsonObject params = new JsonObject()
                .put("uai", uai);

        neo.execute(query, params, validResultHandler(AsyncHelper.getJsonArrayEitherHandler(handler)));
    }
}
