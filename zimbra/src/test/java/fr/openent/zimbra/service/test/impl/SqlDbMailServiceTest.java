package fr.openent.zimbra.service.test.impl;

import fr.openent.zimbra.service.data.SqlDbMailService;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.entcore.common.sql.Sql;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.FieldSetter;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import java.util.ArrayList;
import java.util.List;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(VertxUnitRunner.class)
@PrepareForTest({Sql.class})
public class SqlDbMailServiceTest {

    private SqlDbMailService sqlDbMailService;
    private final Sql sql = PowerMockito.mock(Sql.class);

    @Before
    public void setUp() throws NoSuchFieldException {
        this.sqlDbMailService = new SqlDbMailService("test");
        FieldSetter.setField(sqlDbMailService, sqlDbMailService.getClass().getDeclaredField("sql"), sql);
        FieldSetter.setField(sqlDbMailService, sqlDbMailService.getClass().getDeclaredField("returnedMailTable"), "schema.mail_returned");
    }

    @Test
    public void testGetMailReturnedByIds_empty_ids(TestContext ctx) {
        List<String> emptyList = new ArrayList<>();
        Async async = ctx.async();

        this.sqlDbMailService.getMailReturnedByIds(emptyList, result -> {
            JsonArray expected = new JsonArray();
            ctx.assertEquals(expected, result.right().getValue());
            async.complete();
        });

        async.awaitSuccess(10000);
    }

    @Test
    public void testGetMailReturnedByIds_not_empty(TestContext ctx) {
        Async async = ctx.async();
        List<String> userIds = new ArrayList<>();
        userIds.add("1");
        String expectedQuery = "SELECT * FROM schema.mail_returned WHERE id IN (?)";
        JsonArray expectedParams = new JsonArray().add(1);

        Mockito.doAnswer((Answer<Void>) invocation -> {
            String queryResult = invocation.getArgument(0);
            JsonArray paramsResult = invocation.getArgument(1);
            ctx.assertEquals(expectedQuery, queryResult);
            ctx.assertEquals(expectedParams.toString(), paramsResult.toString());
            async.complete();
            return null;
        }).when(sql).prepared(Mockito.anyString(), Mockito.any(JsonArray.class), Mockito.any(Handler.class));

        this.sqlDbMailService.getMailReturnedByIds(userIds, null);

        async.awaitSuccess(10000);
    }
}
