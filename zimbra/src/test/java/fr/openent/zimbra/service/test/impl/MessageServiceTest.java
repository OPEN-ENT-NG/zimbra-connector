package fr.openent.zimbra.service.test.impl;

import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.model.constant.FrontConstants;
import fr.openent.zimbra.model.constant.ZimbraConstants;
import fr.openent.zimbra.service.impl.MessageService;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.reflect.Whitebox;
import java.util.HashMap;

@RunWith(VertxUnitRunner.class)
public class MessageServiceTest {

    private MessageService messageService;

    @Before
    public void setUp() {
        ServiceManager serviceManager = ServiceManager.init(Vertx.vertx(), Vertx.vertx().eventBus(), "", null);
        this.messageService = serviceManager.getMessageService();
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

}