package fr.openent.zimbra.service.test.impl;

import fr.openent.zimbra.service.data.Neo4jZimbraService;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.entcore.common.neo4j.Neo4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import java.util.ArrayList;
import java.util.List;

import static org.powermock.api.mockito.PowerMockito.mock;

@RunWith(PowerMockRunner.class) //Using the PowerMock runner
@PowerMockRunnerDelegate(VertxUnitRunner.class) //And the Vertx runner
@PrepareForTest({Neo4j.class, Vertx.class, Context.class}) //Prepare the static class you want to test
public class Neo4jZimbraServiceTest {
    Neo4j neo4j = mock(Neo4j.class);
    Neo4jZimbraService neo4jService;
    @Before
    public void setUp() throws Exception {
        this.neo4j = Mockito.spy(Neo4j.getInstance());
        PowerMockito.spy(Context.class);
        Context context = mock(Context.class);

        PowerMockito.spy(Vertx.class);
        PowerMockito.when(Vertx.class, "currentContext").thenReturn(context);

        PowerMockito.spy(Neo4j.class);
        PowerMockito.when(Neo4j.getInstance()).thenReturn(neo4j);

        neo4jService = new Neo4jZimbraService();
    }

    @Test
    public void createActionInQueue_normalUse(TestContext context) {
        Async async = context.async();

        //Arguments
        List<String> structuresIdList = new ArrayList<>();
        structuresIdList.add("test1");
        structuresIdList.add("test2");

        String expectedQuery =
                "MATCH (n)<-[:DEPENDS]-(g:FunctionGroup)<-[:IN]-(u:User) " +
                        "WHERE (n:Structure OR n:Class) AND n.id in {structuresId} AND g.name =~ '^.*-AdminLocal$' " +
                        "OPTIONAL MATCH u-[:IN]->(pg:ProfileGroup)-[:HAS_PROFILE]->(profile:Profile) " +
                        "RETURN collect(distinct u.id) as admls, n.id  as structure;";
        JsonObject expectedValues = new JsonObject().put("structuresId" ,structuresIdList);
        Mockito.doAnswer(invocation -> {
            String query = invocation.getArgument(0);
            String values = invocation.getArgument(1).toString();
            context.assertEquals(query, expectedQuery);
            context.assertEquals(values, expectedValues.toString());

            async.complete();

            return Future.succeededFuture();
        }).when(this.neo4j).execute(Mockito.any(String.class), Mockito.any(JsonObject.class), Mockito.any(Handler.class));

        neo4jService.listAdml(structuresIdList);
        async.awaitSuccess(10000);
    }
}
