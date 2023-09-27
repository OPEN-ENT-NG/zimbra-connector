package fr.openent.zimbra.service.data;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

import java.util.Map;

public class HttpService {

    private final Vertx vertx;
    private final WebClient client;
    private static final Logger log = LoggerFactory.getLogger(HttpService.class);

    public HttpService(Vertx vertx) {
        this.vertx = vertx;
        this.client = WebClient.create(vertx);
    }

    public Future<Buffer> get(String url, Map<String, String> headers) {
        Promise<Buffer> promise = Promise.promise();

        client.getAbs(url)
                .putHeaders(MultiMap.caseInsensitiveMultiMap().addAll(headers))
                .send((ar -> {
                    if (ar.succeeded()) {
                        HttpResponse<Buffer> response = ar.result();
                        if (response.statusCode() == 200) {
                            promise.complete(response.body());
                        } else {
                            log.error(String.format("Zimbra@getRequest : error received non-200 response %s", response.statusCode()));
                            promise.fail("Received non-200 response: " + response.statusCode());
                        }
                    } else {
                        promise.fail(ar.cause());
                    }
                }));

        return promise.future();
    }
}
