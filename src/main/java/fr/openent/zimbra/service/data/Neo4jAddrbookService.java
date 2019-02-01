package fr.openent.zimbra.service.data;

import fr.openent.zimbra.helper.AsyncHelper;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.neo4j.Neo4j;

import static org.entcore.common.neo4j.Neo4jResult.validResultHandler;
import static org.entcore.common.neo4j.Neo4jResult.validUniqueResultHandler;

public class Neo4jAddrbookService {

    private Neo4j neo;


    public static final String PROFILE_GUEST = "Guest";
    public static final String PROFILE_PERSONNEL = "Personnel";
    public static final String PROFILE_STUDENT = "Student";
    public static final String PROFILE_RELATIVE = "Teacher";
    public static final String PROFILE_TEACHER = "Relative";

    public static final String STRUCT_UAI = "uai";
    public static final String SUBDIRS = "subdirs";
    public static final String SUBDIR_NAME = "name";
    public static final String USERS = "users";
    public static final String USER_ID = "id";
    public static final String USER_DISPLAYNAME = "displayName";

    public Neo4jAddrbookService() {
        this.neo = Neo4j.getInstance();
    }

    public void getUsersProfileStructure(String uai, String profile,
                                         Handler<AsyncResult<JsonObject>> handler) {
        String query = "MATCH (u:User)-[:IN]-(pg:ProfileGroup)-[:DEPENDS]->(s:Structure) " +
                "WHERE s.UAI={uai} " +
                "and pg.filter={profile} " +
                "return s.UAI as "+STRUCT_UAI+", collect(" +
                getUsersReturn("u") + ") as "+USERS;

        neo.execute(query, new JsonObject().put("uai", uai).put("profile", profile),
                validUniqueResultHandler(AsyncHelper.getJsonObjectEitherHandler(handler)));
    }


    public void getUsersProfileWithFunction(String uai, String profile,
                                            Handler<AsyncResult<JsonArray>> handler) {
        String query = "MATCH (pg:ProfileGroup)-[:DEPENDS]->(s:Structure)," +
                "(pg)<-[:IN]-(u:User)-[:IN]->(fg:FuncGroup)-[:DEPENDS]->(s) " +
                "WHERE s.UAI={uai} " +
                "and pg.filter={profile} " +
                "return s.UAI as "+STRUCT_UAI + ", "
                + String.format("{%s:fg.name,%s:collect(%s)} as %s",
                    SUBDIR_NAME,USERS,
                    getUsersReturn("u"),
                    SUBDIRS);

        neo.execute(query, new JsonObject().put("uai", uai).put("profile", profile),
                validResultHandler(AsyncHelper.getJsonArrayEitherHandler(handler)));
    }


    public void getUsersProfileWithClass(String uai, String profile,
                                         Handler<AsyncResult<JsonArray>> handler) {
        String query = "MATCH (u:User)-[:IN]->(pg:ProfileGroup)-[:DEPENDS]->(c:Class)-[:BELONGS]->(s:Structure)" +
                "WHERE s.UAI={uai} " +
                "and pg.filter={profile} " +
                "return s.UAI as "+STRUCT_UAI + ", "
                + String.format("{%s:fg.name,%s:collect(%s)} as %s",
                SUBDIR_NAME,USERS,
                getUsersReturn("u"),
                SUBDIRS);

        neo.execute(query, new JsonObject().put("uai", uai).put("profile", profile),
                validResultHandler(AsyncHelper.getJsonArrayEitherHandler(handler)));
    }

    @SuppressWarnings("SameParameterValue")
    private String getUsersReturn(String name) {
        return String.format("{%s:%s.id, %s:%s.displayName}",
                USER_ID, name,
                USER_DISPLAYNAME, name);
    }
}
