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

package fr.openent.apizimbra.manager;

import fr.openent.apizimbra.ApiZimbra;
import fr.openent.apizimbra.service.CommunicationService;
import fr.openent.apizimbra.service.NotificationService;
import fr.openent.apizimbra.service.data.DbMailService;
import fr.openent.apizimbra.service.data.DbMailServiceFactory;
import fr.openent.apizimbra.service.data.Neo4jZimbraService;
import fr.wseduc.webutils.email.EmailSender;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import org.entcore.common.email.EmailFactory;
import org.entcore.common.notification.TimelineHelper;

@SuppressWarnings("unused")
public class ServiceManager {

    private static ServiceManager serviceManager = null;

    private TimelineHelper timelineHelper;
    private EmailSender emailSender;
    private DbMailService dbMailServiceSync;
    private DbMailService dbMailServiceApp;
    private NotificationService notificationService;
    private CommunicationService communicationService;
    private Neo4jZimbraService neoService;



    private ServiceManager(Vertx vertx, EventBus eb) {
        ConfigManager appConfig = ApiZimbra.appConfig;
        JsonObject rawConfig = appConfig.getRawConfig();
        String pathPrefix = appConfig.getPathPrefix();

        timelineHelper = new TimelineHelper(vertx, eb, rawConfig);
        EmailFactory emailFactory = EmailFactory.getInstance();
        emailSender = emailFactory.getSender();

        initDbMailService(appConfig);
        this.neoService = new Neo4jZimbraService();
        this.notificationService = new NotificationService(pathPrefix, timelineHelper);
        this.communicationService = new CommunicationService();
    }

    private void initDbMailService(ConfigManager appConfig) {
        DbMailServiceFactory dbFactory = new DbMailServiceFactory();
        this.dbMailServiceApp = dbFactory.getDbMailService(appConfig.getAppSynchroType());
        if(appConfig.getSyncSynchroType().equals(appConfig.getAppSynchroType())) {
            this.dbMailServiceSync = this.dbMailServiceApp;
        } else {
            this.dbMailServiceSync = dbFactory.getDbMailService(appConfig.getAppSynchroType());
        }
    }

    public static ServiceManager init(Vertx vertx, EventBus eb) {
        if(serviceManager == null) {
            serviceManager = new ServiceManager(vertx, eb);
        }
        return serviceManager;
    }

    public static ServiceManager getServiceManager() {
        return serviceManager;
    }

    public TimelineHelper getTimelineHelper() {
        return timelineHelper;
    }

    public EmailSender getEmailSender() {
        return emailSender;
    }

    public DbMailService getDbMailServiceSync() {
        return dbMailServiceSync;
    }

    public DbMailService getDbMailServiceApp() {
        return dbMailServiceApp;
    }

    public NotificationService getNotificationService() {
        return notificationService;
    }

    public CommunicationService getCommunicationService() {
        return communicationService;
    }

    public Neo4jZimbraService getNeoService() {
        return neoService;
    }
}
