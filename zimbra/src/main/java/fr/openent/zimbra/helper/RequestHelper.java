package fr.openent.zimbra.helper;

import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.security.XSSUtils;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;

public class RequestHelper {
    private static void resumeQuietly(HttpServerRequest request) {
        try {
            request.resume();
        } catch (Exception var2) {
        }

    }

    /**
     * Handle empty body
     *
     * @param request
     * @param handler
     */
    public static void bodyToJson(final HttpServerRequest request, final Handler<JsonObject> handler) {
        request.bodyHandler(event -> {
            try {
                JsonObject json = new fr.wseduc.webutils.collections.JsonObject(XSSUtils.stripXSS(event.toString("UTF-8")));
                handler.handle(json);
            } catch (RuntimeException err) {
                if (event.toString().isEmpty()) {
                    handler.handle(new JsonObject());
                } else {
                    Renders.badRequest(request, err.getMessage());
                }
            }
        });
        resumeQuietly(request);
    }
}
