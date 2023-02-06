package fr.openent.zimbra.service.test.impl;

import com.redis.S;
import fr.openent.zimbra.service.impl.QueueServiceImpl;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.entcore.common.sql.Sql;
import org.entcore.common.user.UserInfos;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import static org.mockito.Mockito.mock;

@RunWith(PowerMockRunner.class) //Using the PowerMock runner
@PowerMockRunnerDelegate(VertxUnitRunner.class) //And the Vertx runner
@PrepareForTest({Sql.class}) //Prepare the static class you want to test
public class QueueServiceImplTest {

    Sql sql = mock(Sql.class);

    private static final String USER_ID = "000";

    private QueueServiceImpl queueServiceImpl;

    @Before
    public void setUp() throws Exception {
        this.sql = Mockito.spy(Sql.getInstance());
        PowerMockito.spy(Sql.class);
        PowerMockito.when(Sql.getInstance()).thenReturn(sql);

        this.queueServiceImpl = new QueueServiceImpl("schema");
    }

    @Test
    public void createActionInQueue_normalUse(TestContext context) {
        Async async = context.async();

        //Arguments
        UserInfos user = new UserInfos();
        user.setOtherProperty("owner", new JsonObject());
        user.setUserId(USER_ID);

        String type = "type";
        Boolean approved = false;

        //Expected query
        String expectedQuery = "INSERT INTO schema.actions (user_id, type, approved) VALUES (?, ?, ?) RETURNING id";
        JsonArray expectedValues = new JsonArray().add(user.getUserId()).add("type").add(false);

        Mockito.doAnswer(invocation -> {
            String query = invocation.getArgument(0);
            String values = invocation.getArgument(1).toString();
            context.assertEquals(query, expectedQuery);
            context.assertEquals(values, expectedValues.toString());

            async.complete();

            return Future.succeededFuture();
        }).when(this.sql).prepared(Mockito.any(), Mockito.any(), Mockito.any());

        queueServiceImpl.createActionInQueue(user, type, approved);
        async.awaitSuccess(10000);
    }

    @Test
    public void createICalTask_normalUse(TestContext context) {
        Async async = context.async();

        //Arguments
        Integer actionId = 111;

        JsonObject queryData = new JsonObject()
                .put("name", "requestName")
                .put("content", new JsonObject());

        //Expected query
        String expectedQuery = "INSERT INTO schema.ical_request_tasks (action_id, status, name, body) VALUES (?, ?, ?, ?)";
        JsonArray expectedValues = new JsonArray().add(111).add("pending").add("requestName").add(new JsonObject());

        Mockito.doAnswer(invocation -> {
            String query = invocation.getArgument(0);
            String values = invocation.getArgument(1).toString();
            context.assertEquals(query, expectedQuery);
            context.assertEquals(values, expectedValues.toString());

            async.complete();

            return Future.succeededFuture();
        }).when(this.sql).prepared(Mockito.any(), Mockito.any(), Mockito.any());

        queueServiceImpl.createICalTask(actionId, queryData);
        async.awaitSuccess(10000);
    }
}
