package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.helper.HttpClientHelper;
import fr.openent.zimbra.model.SlackConfiguration;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class SlackService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SlackService.class);

    private final Vertx vertx;
    private final SlackConfiguration config;

    public SlackService(Vertx vertx, SlackConfiguration config) {
        this.vertx = vertx;
        this.config = config;
    }

    public void sendMessage(String text) {
        HttpClient httpClient = HttpClientHelper.createHttpClient(vertx);
        String message = "[" + this.config.host() + "] " + text;
        String address = this.config.uri() + "chat.postMessage?token=" + this.config.apiToken()
                + "&channel=" + encodeParam(this.config.channel()) + "&text=" + encodeParam(message)
                + "&username=" + encodeParam(this.config.botUsername())
                + "&pretty=1";
        RequestOptions requestOptions = new RequestOptions()
                .setAbsoluteURI(address)
                .setMethod(HttpMethod.POST)
                .setHeaders(new HeadersMultiMap())
                .putHeader(Field.CONTENT_TYPE, "application/json");

        httpClient.request(requestOptions)
                .flatMap(HttpClientRequest::send)
                .onSuccess(response -> {
                    if (response.statusCode() != 200) {
                        LOGGER.error("An error occurred when notify slack");
                    }
                })
                .onFailure(err -> LOGGER.error("An error occurred when sending slack message", err));
    }

    private String encodeParam(String param) {
        try {
            return URLEncoder.encode(param, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            LOGGER.error("An error occurred when encoding param", e);
            return "";
        }
    }
}
