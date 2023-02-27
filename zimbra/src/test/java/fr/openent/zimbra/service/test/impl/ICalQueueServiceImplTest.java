package fr.openent.zimbra.service.test.impl;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.ActionType;
import fr.openent.zimbra.core.enums.TaskStatus;
import fr.openent.zimbra.model.action.Action;
import fr.openent.zimbra.model.constant.SoapConstants;
import fr.openent.zimbra.model.task.ICalTask;
import fr.openent.zimbra.tasks.service.DbActionService;
import fr.openent.zimbra.tasks.service.DbTaskService;
import fr.openent.zimbra.tasks.service.QueueService;
import fr.openent.zimbra.tasks.service.impl.data.SqlActionService;
import fr.openent.zimbra.tasks.service.impl.data.SqlICalTaskService;
import fr.openent.zimbra.tasks.service.impl.ICalQueueServiceImpl;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
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
@PrepareForTest({Sql.class, Vertx.class, Context.class}) //Prepare the static class you want to test
public class ICalQueueServiceImplTest {

    Sql sql = mock(Sql.class);
    private DbTaskService<ICalTask> dbTaskService;
    private QueueService<ICalTask> queueServiceImpl;
    private DbActionService dbActionService;

    private static final UUID USER_ID = UUID.randomUUID();

    @Before
    public void setUp() throws Exception {
        this.sql = Mockito.spy(Sql.getInstance());
        PowerMockito.spy(Context.class);
        Context context = PowerMockito.mock(Context.class);
        PowerMockito.doReturn(new JsonObject().put("zimbraICalWorkerMaxQueue", 10000)).when(context).config();

        PowerMockito.spy(Vertx.class);
        PowerMockito.when(Vertx.class, "currentContext").thenReturn(context);

        PowerMockito.spy(Sql.class);
        PowerMockito.when(Sql.getInstance()).thenReturn(sql);

        dbTaskService = new SqlICalTaskService("zimbra");
        dbActionService = new SqlActionService("zimbra");
        queueServiceImpl = new ICalQueueServiceImpl("zimbra.ical_request_tasks", dbTaskService, dbActionService);

    }

    @Test
    public void createActionInQueue_normalUse(TestContext context) {
        Async async = context.async();

        //Arguments
        UserInfos user = new UserInfos();
        user.setOtherProperty("owner", new JsonObject());
        user.setUserId(USER_ID.toString());

        String type = "type";
        boolean approved = false;
        ActionType actionType = ActionType.ICAL;

        //Expected query
        String expectedQuery = "INSERT INTO zimbra.actions (user_id, type, approved) VALUES (?, ?, ?) " +
                "RETURNING id, user_id, created_at, type, approved";
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
        task.setActionId(111);

        //Expected query
        String expectedQuery = "INSERT INTO zimbra.ical_request_tasks (action_id, status, name, body) VALUES (?, ?, ?, ?) RETURNING *";
        JsonArray expectedValues = new JsonArray().add(111).add(TaskStatus.PENDING.method())
                .add(Field.GETICALREQUEST).add(new JsonObject().put(Field._JSNS, SoapConstants.NAMESPACE_MAIL));

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
