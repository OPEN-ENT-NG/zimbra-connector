package fr.openent.zimbra.service.test.impl;

import fr.openent.zimbra.service.impl.ZimbraAdminService;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.entcore.common.neo4j.Neo4j;
import org.entcore.common.neo4j.Neo4jRest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.util.reflection.FieldSetter;

import static org.mockito.Mockito.mock;

import org.mockito.stubbing.Answer;
import org.mockito.Mockito;

@RunWith(VertxUnitRunner.class)
public class ZimbraAdminServiceTest {
    private Vertx vertx;
    private final Neo4j neo4j = Neo4j.getInstance();
    private final Neo4jRest neo4jRest = mock(Neo4jRest.class);
    private ZimbraAdminService zimbraAdminService;

    @Before
    public void setUp(TestContext ctx) throws NoSuchFieldException {
        zimbraAdminService = new ZimbraAdminService();
        vertx = Vertx.vertx();
        FieldSetter.setField(neo4j, neo4j.getClass().getDeclaredField("database"), neo4jRest);
    }

    @Test
    public void testListGroupeWithRole(TestContext ctx) {
        String expectedQuery = " MATCH (w:Action{name:{actionName}}) "
                + " MERGE (n:Role {name:{roleName}}) ON CREATE SET n.id = {roleId}"
                + " MERGE (n)-[a:AUTHORIZE]->(w) "
                + " return  {name :n.name, id: n.id} as role";

        Mockito.doAnswer((Answer<Void>) invocation -> {
            JsonObject expectedParams = expectedTestGetTheRole();
            String queryResult = invocation.getArgument(0);
            JsonObject queryParams = invocation.getArgument(1);
            ctx.assertEquals(queryResult, expectedQuery);
            queryParams.remove("roleId");
            ctx.assertEquals(queryParams, expectedParams);
            return null;
        }).when(neo4jRest).execute(Mockito.anyString(), Mockito.any(JsonObject.class), Mockito.any(Handler.class));
        ZimbraAdminService.listGroupsWithRole("structureId", event -> {

        });
    }

    private JsonObject expectedTestGetTheRole() {
        return new JsonObject("{" +
                "\"actionName\" : \"fr.openent.zimbra.controllers.ZimbraController|zimbraOutside\"," +
                "\"roleName\" : \"zimbra.outside.donotchange\"" +
                "}");
    }

}

