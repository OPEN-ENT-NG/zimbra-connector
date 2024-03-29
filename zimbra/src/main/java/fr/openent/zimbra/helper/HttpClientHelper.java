/*
 * Copyright (c) Région Ile-de-France, Région Nouvelle-Aquitaine, CGI, 2016.
 * This file is part of OPEN ENT NG. OPEN ENT NG is a versatile ENT Project based on the JVM and ENT Core Project.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with OPEN ENT NG is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of OPEN ENT NG, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package fr.openent.zimbra.helper;

import fr.openent.zimbra.Zimbra;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.ProxyOptions;
import io.vertx.ext.web.client.WebClientOptions;

public class HttpClientHelper {
    private static final Logger log = LoggerFactory.getLogger(HttpClientHelper.class);

    /**
     * get default Proxy options
     * @return proxyOptions
     */
    public static ProxyOptions proxyOptions() {
        return new ProxyOptions()
                .setHost(System.getProperty("httpclient.proxyHost"))
                .setPort(Integer.parseInt(System.getProperty("httpclient.proxyPort")))
                .setUsername(System.getProperty("httpclient.proxyUsername"))
                .setPassword(System.getProperty("httpclient.proxyPassword"));
    }

    /**
     * Create default WebClientOptions
     * @return new WebClientOptions
     */
    public static WebClientOptions getWebClientOptions() {
        WebClientOptions options = new WebClientOptions();
        if (System.getProperty("httpclient.proxyHost") != null) {
            options.setProxyOptions(HttpClientHelper.proxyOptions());
        }
        int maxPoolSize = Zimbra.appConfig != null ? Zimbra.appConfig.getHttpClientMaxPoolSize() : 0;
        if(maxPoolSize > 0) {
            options.setMaxPoolSize(maxPoolSize);
        }
        return options;
    }

    /**
     * Create default HttpClient
     * @return new HttpClient
     */
    public static HttpClient createHttpClient(Vertx vertx) {
        final HttpClientOptions options = new HttpClientOptions();
        if (System.getProperty("httpclient.proxyHost") != null) {
            ProxyOptions proxyOptions = proxyOptions();
            options.setProxyOptions(proxyOptions);
        }
        int maxPoolSize = Zimbra.appConfig.getHttpClientMaxPoolSize();
        if(maxPoolSize > 0) {
            options.setMaxPoolSize(maxPoolSize);
        }
        return vertx.createHttpClient(options);
    }

}
