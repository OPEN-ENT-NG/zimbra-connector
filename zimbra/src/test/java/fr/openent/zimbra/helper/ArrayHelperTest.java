package fr.openent.zimbra.helper;

import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

@RunWith(VertxUnitRunner.class)
public class ArrayHelperTest {
    @Test
    public void testSplit (TestContext ctx) {
        List<String> list = Arrays.asList("a", "b", "c", "d");
        List<List<String>> batch = ArrayHelper.split(list, 3);

        ctx.assertEquals(2, batch.size());
        ctx.assertEquals(3, batch.get(0).size());
        ctx.assertEquals(1, batch.get(1).size());
    }
}
