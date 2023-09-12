package fr.openent.zimbra.helper;

import fr.openent.zimbra.Zimbra;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import static fr.openent.zimbra.model.constant.FrontConstants.MESSAGE_BODY;


@RunWith(VertxUnitRunner.class)
public class CidHelperTest {

    @Before
    public void before(){
        ConfigManager cm = new ConfigManager(new JsonObject());
        Zimbra.appConfig = cm;
    }
    @Test
    public void testGetCids_shouldSucceed (TestContext ctx) {
        JsonObject emptyMessage = new JsonObject();
        JsonObject message = new JsonObject().put(MESSAGE_BODY, "dfhskjhfksfhsrc=\"cid:ciddetest\"");

        List<String> emptyCids = CidHelper.getMessageCids(emptyMessage);
        List<String> oneCid = CidHelper.getMessageCids(message);

        List<String> oneCidTest = new ArrayList<>();
        oneCidTest.add("ciddetest");

        ctx.assertEquals(emptyCids, new ArrayList<String>());
        ctx.assertEquals(oneCid, oneCidTest);
    }
}
