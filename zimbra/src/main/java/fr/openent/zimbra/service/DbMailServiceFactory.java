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

package fr.openent.zimbra.service;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.helper.ConfigManager;
import fr.openent.zimbra.service.data.NeoDbMailService;
import fr.openent.zimbra.service.data.SqlDbMailService;
import fr.openent.zimbra.service.data.SqlSynchroService;
import io.vertx.core.Vertx;

public class DbMailServiceFactory {

    private Vertx vertx;
    private SqlSynchroService sqlSynchroService;

    public DbMailServiceFactory(Vertx vertx, SqlSynchroService sqlSynchroService) {
        this.vertx = vertx;
        this.sqlSynchroService = sqlSynchroService;
    }

    public DbMailService getDbMailService(String serviceName) {
        switch (serviceName) {
            case ConfigManager.SYNC_SQL:
                return new SqlDbMailService(Zimbra.appConfig != null ? Zimbra.appConfig.getDbSchema() : "");
            case ConfigManager.SYNC_NEO:
            default:
                return new NeoDbMailService(vertx, sqlSynchroService);
        }
    }
}
