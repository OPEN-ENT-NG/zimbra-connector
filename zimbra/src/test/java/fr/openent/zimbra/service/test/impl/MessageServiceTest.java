package fr.openent.zimbra.service.test.impl;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.model.constant.FrontConstants;
import fr.openent.zimbra.model.constant.ZimbraConstants;
import fr.openent.zimbra.service.impl.MessageService;
import fr.openent.zimbra.service.impl.UserService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.powermock.reflect.Whitebox;
import java.util.HashMap;
import java.util.List;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(VertxUnitRunner.class)
@PrepareForTest({MessageService.class, UserService.class})
public class MessageServiceTest {

    private MessageService messageService;
    private UserService userService;

    @Before
    public void setUp() {
        this.userService = Mockito.mock(UserService.class);
        PowerMockito.spy(MessageService.class);
        this.messageService = PowerMockito.spy(new MessageService(null, null, null, userService, null, null));
    }

    @Test
    public void testTranslateMaillistToUidlist_set_is_report_required(TestContext ctx) throws Exception {
        JsonObject frontMsg = new JsonObject();
        JsonArray zimbraMails = new JsonArray()
                .add(new JsonObject()
                        .put(ZimbraConstants.MSG_EMAIL_ADDR, "test")
                        .put(ZimbraConstants.MSG_EMAIL_TYPE, ZimbraConstants.ADDR_TYPE_READRECEIPT));
        boolean isReported = false;

        Whitebox.invokeMethod(messageService, "translateMaillistToUidlist",
                frontMsg, zimbraMails, new HashMap<String, String>(), isReported, (Handler<JsonObject>) result ->
                        ctx.assertNotNull(result.getBoolean(FrontConstants.IS_REPORT_REQUIRED)));
    }

    @Test
    public void testTranslateMaillistToUidlist_not_set_is_report_required(TestContext ctx) throws Exception {
        JsonObject frontMsg = new JsonObject();
        JsonArray zimbraMails = new JsonArray()
                .add(new JsonObject()
                        .put(ZimbraConstants.MSG_EMAIL_ADDR, "test")
                        .put(ZimbraConstants.MSG_EMAIL_TYPE, ZimbraConstants.ADDR_TYPE_READRECEIPT));
        boolean isReported = true;

        Whitebox.invokeMethod(messageService, "translateMaillistToUidlist",
                frontMsg, zimbraMails, new HashMap<String, String>(), isReported, (Handler<JsonObject>) result ->
                        ctx.assertNull(result.getBoolean(FrontConstants.IS_REPORT_REQUIRED)));
    }

    @Test
    public void testGetDisplayNames(TestContext ctx) throws Exception {
        JsonArray frontMessages = new JsonArray();
        JsonObject frontMessage = new JsonObject();
        frontMessages.add(frontMessage);
        JsonArray displayNames = new JsonArray();
        displayNames.add(new JsonArray().add("a6fc1dbf-5df5-4fd1-a64a-d65d53179f05").add(""));
        displayNames.add(new JsonArray().add("email@email.com").add(""));
        displayNames.add(new JsonArray().add("90391c10-996e-444e-8308-9deb921ce51d").add(""));
        displayNames.add(new JsonArray().add("email@email.com"));
        frontMessage.put(FrontConstants.MAIL_DISPLAYNAMES, displayNames);

        List<JsonArray> getDisplayNames = Whitebox.invokeMethod(messageService, "getDisplayNames", frontMessages);
        ctx.assertEquals(2, getDisplayNames.size());
    }

    @Test
    public void testRetrieveUsername(TestContext ctx) throws Exception {
        Async async = ctx.async();

        PowerMockito.doAnswer((Answer<HashMap<String, String>>) invocation -> {
            Handler<Either<String, JsonArray>> handler = invocation.getArgument(2);
            JsonArray userIds = new JsonArray();
            userIds.add(new JsonObject().put(Field.ID, "5eba594c-0d88-4287-af0e-b57e4d981aec").put(Field.USERNAME, "user1"));
            userIds.add(new JsonObject().put(Field.ID, "2aaad182-659b-4f4f-bb1c-001daf954cd6").put(Field.USERNAME, "user2"));
            handler.handle(new Either.Right<>(userIds));
            return null;
        }).when(userService).getUsers(Mockito.any(), Mockito.any(), Mockito.any());

        Future<HashMap<String, String>> future = Whitebox.invokeMethod(messageService, "retrieveUsernames", Mockito.any());
        future.onSuccess(res -> {
            ctx.assertEquals(2, res.size());
            ctx.assertEquals("user1", res.get("5eba594c-0d88-4287-af0e-b57e4d981aec"));
            ctx.assertEquals("user2", res.get("2aaad182-659b-4f4f-bb1c-001daf954cd6"));
            async.complete();
        }).onFailure(e -> {
            ctx.fail();
        });

        async.awaitSuccess(10000);
    }
}