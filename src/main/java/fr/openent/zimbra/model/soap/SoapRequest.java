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

package fr.openent.zimbra.model.soap;

import fr.openent.zimbra.helper.AsyncHelper;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.service.data.SoapZimbraService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

import static fr.openent.zimbra.model.constant.SoapConstants.*;

public class SoapRequest {

    private String name;
    private String namespace;
    private boolean isAdmin;
    private String userId;
    private JsonObject content = null;

    @SuppressWarnings("SameParameterValue")
    private SoapRequest(String name, String namespace, boolean isAdmin) {
        this.name = name;
        this.namespace = namespace;
        this.isAdmin = isAdmin;
    }

    private SoapRequest(String name, String namespace, boolean isAdmin, String userId) {
        this.name = name;
        this.namespace = namespace;
        this.isAdmin = isAdmin;
        this.userId = userId;
    }

    public static SoapRequest AccountSoapRequest(String name, String userId) {
        return new SoapRequest(name, NAMESPACE_ACCOUNT, false, userId);
    }

    public static SoapRequest AdminSoapRequest(String name) {
        return new SoapRequest(name, NAMESPACE_ADMIN, true);
    }

    public void setContent(JsonObject content) {
        if(content == null) {
            content = new JsonObject();
        }
        content.put(REQ_NAMESPACE, namespace);
        this.content = content;
    }

    public void start(Handler<AsyncResult<JsonObject>> handler) {
        ServiceManager sm = ServiceManager.getServiceManager();
        SoapZimbraService soapService = sm.getSoapService();

        if(name == null || name.isEmpty() || content == null || content.isEmpty()) {
            handler.handle(Future.failedFuture("Incomplete request"));
            return;
        }

        JsonObject reqParams = new JsonObject()
                .put(REQ_NAME, name)
                .put(REQ_CONTENT, content);

        if(isAdmin) {
            soapService.callAdminSoapAPI(reqParams, AsyncHelper.getJsonObjectEitherHandler(handler));
        } else {
            if(userId.isEmpty()) {
                handler.handle(Future.failedFuture("Can't launch user request without userid"));
            } else {
                soapService.callUserSoapAPI(reqParams, userId, handler);
            }
        }

    }
}
