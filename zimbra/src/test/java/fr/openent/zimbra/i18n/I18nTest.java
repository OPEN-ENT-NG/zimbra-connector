package fr.openent.zimbra.i18n;

import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class I18nTest {
    @After
    public void after(TestContext context) {
        Vertx.vertx().close(context.asyncAssertSuccess());
    }

    @Test
    public void testReadI18nFile_in_Fr_should_not_throw_error_decode_exception(TestContext ctx)  {
        Async async = ctx.async();
        Vertx.vertx().fileSystem().readFile("./i18n/fr.json", ar -> assertValidJSONHandler(ctx, async, ar));
    }

    @Test
    public void testReadI18nFile_in_En_should_not_throw_error_decode_exception(TestContext ctx) {
        Async async = ctx.async();
        Vertx.vertx().fileSystem().readFile("./i18n/en.json",ar -> assertValidJSONHandler(ctx, async, ar));
    }

    @Test
    public void testReadI18nFile_in_Timeline_Fr_should_not_throw_error_decode_exception(TestContext ctx)  {
        Async async = ctx.async();
        Vertx.vertx().fileSystem().readFile("./i18n/timeline/fr.json",ar -> assertValidJSONHandler(ctx, async, ar));
    }

    @Test
    public void testReadI18nFile_in_Timeline_En_should_not_throw_error_decode_exception(TestContext ctx) {
        Async async = ctx.async();
        Vertx.vertx().fileSystem().readFile("./i18n/timeline/en.json",ar -> assertValidJSONHandler(ctx, async, ar));
    }

    private void assertValidJSONHandler(TestContext ctx, Async async, AsyncResult<Buffer> ar) {
        if (ar.failed()) {
            ctx.fail(ar.cause());
        } else {
            try {
                new JsonObject(ar.result());
                async.complete();
            } catch (Exception e) {
                ctx.fail();
            }
        }
    }
}