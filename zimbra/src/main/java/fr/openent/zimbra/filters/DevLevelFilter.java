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

package fr.openent.zimbra.filters;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.helper.ConfigManager;
import fr.wseduc.webutils.http.Binding;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.http.filter.ResourcesProvider;
import org.entcore.common.user.UserInfos;

public class DevLevelFilter implements ResourcesProvider {

    private static final Logger log = LoggerFactory.getLogger(DevLevelFilter.class);


    @Override
    public void authorize(HttpServerRequest httpServerRequest, Binding binding, UserInfos userInfos, Handler<Boolean> handler) {
        if(Zimbra.appConfig.isActionBlocked(ConfigManager.UPDATE_ACTION)) {
            handler.handle(false);
            log.error("action blocked : " + binding.getUriPattern());
        } else {
            handler.handle(userInfos != null);
        }
    }
}
