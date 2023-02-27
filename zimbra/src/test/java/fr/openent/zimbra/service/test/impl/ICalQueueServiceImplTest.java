package fr.openent.zimbra.service.test.impl;

import fr.openent.zimbra.core.enums.ActionType;
import fr.openent.zimbra.core.enums.TaskStatus;
import fr.openent.zimbra.model.action.Action;
import fr.openent.zimbra.model.constant.SoapConstants;
import fr.openent.zimbra.model.task.ICalTask;
import fr.openent.zimbra.service.impl.ICalQueueServiceImpl;
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


import java.util.UUID;

import static org.mockito.Mockito.mock;

@RunWith(PowerMockRunner.class) //Using the PowerMock runner
@PowerMockRunnerDelegate(VertxUnitRunner.class) //And the Vertx runner
@PrepareForTest({Sql.class}) //Prepare the static class you want to test
public class ICalQueueServiceImplTest {

    Sql sql = mock(Sql.class);

    private ICalQueueServiceImpl queueServiceImpl;

    private static final UUID USER_ID = UUID.randomUUID();

    @Before
    public void setUp() throws Exception {
        this.sql = Mockito.spy(Sql.getInstance());
        PowerMockito.spy(Sql.class);
        PowerMockito.when(Sql.getInstance()).thenReturn(sql);
        queueServiceImpl = new ICalQueueServiceImpl("schema");
    }

    @Test
    public void createActionInQueue_normalUse(TestContext context) {
        //todo
        Async async = context.async();

        //Arguments
        UserInfos user = new UserInfos();
        user.setOtherProperty("owner", new JsonObject());
        user.setUserId(USER_ID.toString());

        String type = "type";
        boolean approved = false;
        ActionType actionType = ActionType.ICAL;

        //Expected query
        String expectedQuery = "INSERT INTO schema.actions (user_id, type, approved) VALUES (?, ?, ?) RETURNING " +
                "id, user_id, created_at, type, approved";
        JsonArray expectedValues = new JsonArray().add(user.getUserId()).add("ical").add(false);

        Mockito.doAnswer(invocation -> {
            String query = invocation.getArgument(0);
            String values = invocation.getArgument(1).toString();
            context.assertEquals(query, expectedQuery);
            context.assertEquals(values, expectedValues.toString());

            async.complete();

            return Future.succeededFuture();
        }).when(this.sql).prepared(Mockito.any(), Mockito.any(), Mockito.any());

        queueServiceImpl.createAction(UUID.fromString(user.getUserId()), actionType, approved);
        async.awaitSuccess(10000);
    }

    @Test
    public void createICalTask_normalUse(TestContext context) {
        //todo
        Async async = context.async();

        //Arguments
        Action<ICalTask> action = new Action<ICalTask>(USER_ID, ActionType.ICAL, false);
        action.setId(111);
        ICalTask task = new ICalTask(action, TaskStatus.PENDING, null, null);

        JsonObject queryData = new JsonObject()
                .put("name", "requestName")
                .put("content", new JsonObject());

        //Expected query
        String expectedQuery = "INSERT INTO schema.ical_request_tasks (action_id, status, jsns, body) VALUES (?, ?, ?, ?) RETURNING *";
        JsonArray expectedValues = new JsonArray().add(111).add("PENDING").add(SoapConstants.NAMESPACE_MAIL).add(new JsonObject());

        Mockito.doAnswer(invocation -> {
            String query = invocation.getArgument(0);
            String values = invocation.getArgument(1).toString();
            context.assertEquals(query, expectedQuery);
            context.assertEquals(values, expectedValues.toString());

            async.complete();

            return Future.succeededFuture();
        }).when(this.sql).prepared(Mockito.any(), Mockito.any(), Mockito.any());
        queueServiceImpl.createTask(action, task);
        async.awaitSuccess(10000);
    }
}
