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
    public static final String PROFILE_GUEST = "Guest";
    public static final String PROFILE_PERSONNEL = "Personnel";
    public static final String PROFILE_STUDENT = "Student";
    public static final String PROFILE_RELATIVE = "Relative";
    public static final String PROFILE_TEACHER = "Teacher";
    private static final String GROUP_OTHER = "OtherGroups";
    public static final String STRUCTURE_NAME = "structure_name";


    public Neo4jAddrbookService() {
        this.neo = Neo4j.getInstance();
    }

    /*
        MATCH (m:User)-[:IN]-(pg:ProfileGroup)-[:DEPENDS]-(s:Structure)
        WHERE s.UAI={uai}
        OPTIONAL MATCH (m)-[:IN]-(pgc:ProfileGroup)-[:DEPENDS]-(c:Class)-[:BELONGS]-(s)
        OPTIONAL MATCH (m)-[:IN]-(fg:FunctionGroup)-[:DEPENDS]-(s)
        OPTIONAL MATCH (m)-[:TEACHES]-(subj:Subject)-[:SUBJECT]-(s)
        RETURN m.lastName as lastName, m.firstName as firstName, m.emailInternal as email,
            collect(distinct fg.filter) as functions,
            collect(distinct subj.label) as subjects, pg.filter as profile,
            collect(distinct c.name) as classes
     */
    public void getAllUsersFromStructure(String uai, Handler<AsyncResult<JsonArray>> handler) {
        String query = "MATCH (u:User)-[:IN]-(pg:ProfileGroup)-[:DEPENDS]-(s:Structure) " +
                "WHERE s.UAI={uai} " +
                getOptionalMatchesUser("u", "c", "fg", "subj") +
                getOptionalMatchesProfile("u", "pg","p") +
                "RETURN distinct u.lastName as " + LASTNAME + ", " +
                "u.firstName as " + FIRSTNAME + ", " +
                "u.emailInternal as " + EMAIL + ", " +
                "collect(distinct fg.filter) as " + FUNCTIONS + ", " +
                "collect(distinct subj.label) as " + SUBJECTS + ", " +
                "p.name as " + PROFILE + ", " +
                "collect(distinct c.name) as " + CLASSES;

        JsonObject params = new JsonObject()
                .put("uai", uai);

        neo.execute(query, params, validResultHandler(AsyncHelper.getJsonArrayEitherHandler(handler)));
    }

    @SuppressWarnings("SameParameterValue")
    private String getOptionalMatchesUser(String userName, String className, String funcgroupName, String subjectName) {
        String optionalMatches =
                String.format(" OPTIONAL MATCH (%s)-[:IN]-(pgc:ProfileGroup)-[:DEPENDS]-(%s:Class)-[:BELONGS]-(s) ",
                    userName, className);
        optionalMatches += String.format("OPTIONAL MATCH (%s)-[:IN]-(%s:FunctionGroup)-[:DEPENDS]-(s) ",
                userName, funcgroupName);
        optionalMatches += String.format("OPTIONAL MATCH (%s)-[:TEACHES]-(%s:Subject)-[:SUBJECT]-(s) ",
                userName, subjectName);
        return optionalMatches;
    }

    @SuppressWarnings("SameParameterValue")
    private String getOptionalMatchesProfile(String nodeName, String profilegroupName, String profileName) {
        return String.format(" OPTIONAL MATCH (%s)-[:IN*0..1]-(%s:ProfileGroup)-[:DEPENDS*0..1]->" +
                        "(:ProfileGroup)-[:HAS_PROFILE]->(%s:Profile) ",
                nodeName, profilegroupName, profileName);
    }

    /*
        MATCH (g:Group)-[:DEPENDS*]->(s:Structure)
        WHERE s.UAI={uai}
            AND g.nbUsers is not null
            AND g.nbUsers > 0
        OPTIONAL MATCH (g)-[:DEPENDS*0..1]->(:ProfileGroup)-[:HAS_PROFILE]->(p:Profile)
        RETURN g.name AS name, g.emailInternal as email,
        CASE WHEN (g:ProfileGroup) THEN p.name
            WHEN (g:FuncGroup) THEN 'Personnel'
            WHEN (g:DisciplineGroup) THEN 'Teacher'
            WHEN (g:HTGroup) THEN 'Teacher'
            ELSE 'OtherGroups'
        END AS grouptype
     */
    public void getAllGroupsFromStructure(String uai, Handler<AsyncResult<JsonArray>> handler) {
        String query =
                "MATCH (g:Group)-[:DEPENDS*]->(s:Structure) " +
                    "WHERE s.UAI={uai} AND exists(g-[:IN]-(:User)) " +
                getOptionalMatchesProfile("g", "","p") +
                "RETURN distinct g.name AS " + GROUPNAME + ", " +
                    "g.emailInternal as " + EMAIL + ", " +
                    "CASE WHEN (g:ProfileGroup) THEN p.name" +
                        " WHEN (g:FuncGroup) THEN '" + PROFILE_PERSONNEL + "'" +
                        " WHEN (g:DisciplineGroup) THEN '" + PROFILE_TEACHER + "'" +
                        " WHEN (g:HTGroup) THEN '" + PROFILE_TEACHER + "'" +
                        " ELSE '" + GROUP_OTHER + "'" +
                        " END AS " + GROUP_TYPE;

        JsonObject params = new JsonObject()
                .put("uai", uai);

        neo.execute(query, params, validResultHandler(AsyncHelper.getJsonArrayEitherHandler(handler)));
    }

    /*
    	MATCH p=(n:User)-[:COMMUNIQUE*0..2]->ipg-[:COMMUNIQUE*0..1]->g<-[:DEPENDS*0..1]-m
        WHERE n.id = {userId}
            AND (NOT(HAS(m.blocked)) OR m.blocked = false)
            AND (( (length(p) >= 2 OR m.users <> 'INCOMING')
            AND (length(p) < 3
                OR (ipg:Group AND (m:User OR g<-[:DEPENDS]-m) AND length(p) = 3))))
            AND (m:User OR m.nbUsers is not null AND m.nbUsers > 0)
        WITH DISTINCT m as visibles
            MATCH (visibles-[:IN*0..1]-(pgs:ProfileGroup)-[:DEPENDS*1..2]-(s:Structure))
            WHERE s.UAI = {uai}
            OPTIONAL MATCH pgs-[:DEPENDS*0..1]->(pg:ProfileGroup)-[:HAS_PROFILE]->(profile:Profile)
            OPTIONAL MATCH (visibles)-[:IN]-(pgc:ProfileGroup)-[:DEPENDS]-(c:Class)-[:BELONGS]-(s)
            OPTIONAL MATCH (visibles)-[:IN]-(fg:FunctionGroup)-[:DEPENDS]-(s)
            OPTIONAL MATCH (visibles)-[:TEACHES]-(subj:Subject)-[:SUBJECT]-(s)
        RETURN distinct visibles.lastName as lastName, visibles.firstName as firstName, visibles.emailInternal as email,
            collect(distinct fg.filter) as functions, collect(distinct subj.label) as subjects,
            profile.name as profile, collect(distinct c.name) as classes, visibles.name AS name,
            CASE WHEN (visibles:ProfileGroup) THEN profile.name
                WHEN (visibles:FuncGroup) THEN 'Personnel'
                WHEN (visibles:DisciplineGroup) THEN 'Teacher'
                WHEN (visibles:HTGroup) THEN 'Teacher'
                ELSE 'OtherGroups'
                END AS grouptype
    UNION
        MATCH (n:User)-[:COMMUNIQUE_DIRECT]->m-[:IN]-(pg:ProfileGroup)-[:DEPENDS]-(s:Structure)
        WHERE n.id = {userId} AND s.UAI = {uai}
        WITH DISTINCT m as visibles
            OPTIONAL MATCH (visibles)-[:IN*0..1]-(:ProfileGroup)-[:DEPENDS*0..1]->(:ProfileGroup)-[:HAS_PROFILE]->(profile:Profile)
            OPTIONAL MATCH (visibles)-[:IN]-(pgc:ProfileGroup)-[:DEPENDS]-(c:Class)-[:BELONGS]-(s)
            OPTIONAL MATCH (visibles)-[:IN]-(fg:FunctionGroup)-[:DEPENDS]-(s)
            OPTIONAL MATCH (visibles)-[:TEACHES]-(subj:Subject)-[:SUBJECT]-(s)
        RETURN distinct visibles.lastName as lastName, visibles.firstName as firstName, visibles.emailInternal as email,
            null as functions, null as subjects,
            profile.name as profile, collect(distinct c.name) as classes, null AS name,
            null AS grouptype
     */
    public void getVisibles(String userId, String uai, Handler<AsyncResult<JsonArray>> handler) {

        String queryGeneric = "MATCH p=(n:User)-[:COMMUNIQUE*0..2]->ipg-[:COMMUNIQUE*0..1]->g<-[:DEPENDS*0..1]-m ";
        queryGeneric += "WHERE n.id = {userId} ";
        queryGeneric += "AND (NOT(HAS(m.blocked)) OR m.blocked = false) ";
        queryGeneric += "AND (( (length(p) >= 2 OR m.users <> 'INCOMING') AND (length(p) < 3 OR (ipg:Group AND (m:User OR g<-[:DEPENDS]-m) AND length(p) = 3)))) ";
        queryGeneric += "AND (m:User OR exists(m-[:IN]-(:User))) ";
        queryGeneric += "WITH DISTINCT m as visibles ";
        queryGeneric += "MATCH (visibles-[:IN*0..1]-(pgs:Group)-[:DEPENDS*1..2]-(s:Structure)) ";
        queryGeneric += "WHERE s.UAI = {uai} AND (visibles:Group OR pgs:ProfileGroup) ";
        queryGeneric += "OPTIONAL MATCH pgs-[:DEPENDS*0..1]->(pg:ProfileGroup)-[:HAS_PROFILE]->(profile:Profile) ";
        queryGeneric += getOptionalMatchesUser("visibles", "c", "fg", "subj");
        queryGeneric += "RETURN distinct visibles.lastName as " + LASTNAME + ", ";
        queryGeneric += "visibles.firstName as " + FIRSTNAME + ", ";
        queryGeneric += "visibles.emailInternal as " + EMAIL + ", ";
        queryGeneric += "collect(distinct fg.filter) as " + FUNCTIONS + ", ";
        queryGeneric += "collect(distinct subj.label) as " + SUBJECTS + ", ";
        queryGeneric += "profile.name as " + PROFILE + ", ";
        queryGeneric += "collect(distinct c.name) as " + CLASSES+ ", ";
        queryGeneric += "visibles.name AS " + GROUPNAME + ", ";
        queryGeneric += "CASE WHEN (visibles:ProfileGroup) THEN profile.name";
        queryGeneric += " WHEN (visibles:FuncGroup) THEN '" + PROFILE_PERSONNEL + "'";
        queryGeneric += " WHEN (visibles:DisciplineGroup) THEN '" + PROFILE_TEACHER + "'";
        queryGeneric += " WHEN (visibles:HTGroup) THEN '" + PROFILE_TEACHER + "'";
        queryGeneric += " ELSE '" + GROUP_OTHER + "'";
        queryGeneric += " END AS " + GROUP_TYPE;

        JsonObject params = new JsonObject()
            .put("userId", userId)
            .put("uai", uai);

        String queryRelative = "MATCH (n:User)-[:COMMUNIQUE_DIRECT]->m-[:IN]-(pg:ProfileGroup)-[:DEPENDS]-(s:Structure) ";
        queryRelative += "WHERE n.id = {userId} AND s.UAI = {uai} ";
        queryRelative += "WITH DISTINCT m as visibles ";
        queryRelative += getOptionalMatchesProfile("visibles", "", "profile");
        queryRelative += getOptionalMatchesUser("visibles", "c", "fg", "subj");
        queryRelative += "RETURN distinct visibles.lastName as " + LASTNAME + ", ";
        queryRelative += "visibles.firstName as " + FIRSTNAME + ", ";
        queryRelative += "visibles.emailInternal as " + EMAIL + ", ";
        queryRelative += "null as " + FUNCTIONS + ", ";
        queryRelative += "null as " + SUBJECTS + ", ";
        queryRelative += "profile.name as " + PROFILE + ", ";
        queryRelative += "collect(distinct c.name) as " + CLASSES+ ", ";
        queryRelative += "null AS " + GROUPNAME + ", ";
        queryRelative += "null AS " + GROUP_TYPE;

        String query = queryGeneric + " UNION " + queryRelative;

        neo.execute(query, params, validResultHandler(AsyncHelper.getJsonArrayEitherHandler(handler)));
    }
}
