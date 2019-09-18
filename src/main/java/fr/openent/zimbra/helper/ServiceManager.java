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
import fr.openent.zimbra.service.DbMailService;
import fr.openent.zimbra.service.DbMailServiceFactory;
import fr.openent.zimbra.service.data.*;
import fr.openent.zimbra.service.impl.*;
import fr.openent.zimbra.service.synchro.*;
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
    private Vertx vertx;

    private SoapZimbraService soapService;
    private UserService userService;
    private FolderService folderService;
    private AttachmentService attachmentService;
    private MessageService messageService;
    private SignatureService signatureService;
    private DbMailService dbMailServiceSync;
    private DbMailService dbMailServiceApp;
    private SearchService searchService;
    private NotificationService notificationService;
    private CommunicationService communicationService;
    private GroupService groupService;
    private Neo4jZimbraService neoService;
    private ExpertModeService expertModeService;


    private SynchroUserService synchroUserService;
    private SynchroUserService synchroUserServiceApp;
    private SynchroService synchroService;
    private SqlSynchroService sqlSynchroService;
    private SynchroGroupService synchroGroupService;
    private SynchroMailerService synchroMailerService;
    private SynchroAddressBookService synchroAddressBookService;
    private Neo4jAddrbookService neo4jAddrbookService;

    private SynchroLauncher synchroLauncher;



    private ServiceManager(Vertx vertx, EventBus eb, String pathPrefix) {

        this.vertx = vertx;
        ConfigManager appConfig = Zimbra.appConfig;
        JsonObject rawConfig = appConfig.getRawConfig();

        timelineHelper = new TimelineHelper(vertx, eb, rawConfig);
        EmailFactory emailFactory = new EmailFactory(vertx, rawConfig);
        emailSender = emailFactory.getSender();

        this.sqlSynchroService = new SqlSynchroService(appConfig.getDbSchema());
        initDbMailService(appConfig);
        this.searchService = new SearchService(vertx);
        this.soapService = new SoapZimbraService(vertx);
        this.neoService = new Neo4jZimbraService();
        this.synchroAddressBookService = new SynchroAddressBookService(sqlSynchroService);
        this.synchroUserService = new SynchroUserService(dbMailServiceSync, sqlSynchroService);
        this.userService = new UserService(soapService, synchroUserService, dbMailServiceApp, synchroAddressBookService);
        this.folderService = new FolderService(soapService);
        this.signatureService = new SignatureService(userService, soapService);
        this.messageService = new MessageService(soapService, folderService,
                dbMailServiceApp, userService, synchroUserService);
        this.attachmentService = new AttachmentService(soapService, messageService, vertx, rawConfig);
        this.notificationService = new NotificationService(pathPrefix, timelineHelper);
        this.communicationService = new CommunicationService();
        this.groupService = new GroupService(soapService, dbMailServiceApp, synchroUserService);
        this.expertModeService = new ExpertModeService();

        this.synchroLauncher = new SynchroLauncher(synchroUserService, sqlSynchroService);
        this.synchroService = new SynchroService(sqlSynchroService, synchroLauncher);
        this.synchroGroupService = new SynchroGroupService(soapService, synchroUserService);
        this.synchroMailerService = new SynchroMailerService(sqlSynchroService);
        this.neo4jAddrbookService = new Neo4jAddrbookService();

        soapService.setServices(userService, synchroUserService);
        synchroUserService.setUserService(userService);

    }

    private void initDbMailService(ConfigManager appConfig) {
        DbMailServiceFactory dbFactory = new DbMailServiceFactory(vertx, sqlSynchroService);
        this.dbMailServiceApp = dbFactory.getDbMailService(appConfig.getAppSynchroType());
        if(appConfig.getSyncSynchroType().equals(appConfig.getAppSynchroType())) {
            this.dbMailServiceSync = this.dbMailServiceApp;
        } else {
            this.dbMailServiceSync = dbFactory.getDbMailService(appConfig.getAppSynchroType());
        }
    }

    public static ServiceManager init(Vertx vertx, EventBus eb, String pathPrefix) {
        if(serviceManager == null) {
            serviceManager = new ServiceManager(vertx, eb, pathPrefix);
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

    public SoapZimbraService getSoapService() {
        return soapService;
    }

    public DbMailService getDbMailServiceSync() {
        return dbMailServiceSync;
    }

    public DbMailService getDbMailServiceApp() {
        return dbMailServiceApp;
    }

    public SearchService getSearchService() {
        return searchService;
    }

    public UserService getUserService() {
        return userService;
    }

    public FolderService getFolderService() {
        return folderService;
    }

    public SignatureService getSignatureService() {
        return signatureService;
    }

    public MessageService getMessageService() {
        return messageService;
    }

    public AttachmentService getAttachmentService() {
        return attachmentService;
    }

    public NotificationService getNotificationService() {
        return notificationService;
    }

    public CommunicationService getCommunicationService() {
        return communicationService;
    }

    public GroupService getGroupService() {
        return groupService;
    }

    public Neo4jZimbraService getNeoService() {
        return neoService;
    }

    public ExpertModeService getExpertModeService() {
        return expertModeService;
    }

    public SynchroUserService getSynchroUserService() {
        return synchroUserService;
    }

    public SynchroService getSynchroService() {
        return synchroService;
    }

    public SqlSynchroService getSqlSynchroService() {
        return sqlSynchroService;
    }

    public SynchroGroupService getSynchroGroupService() {
        return synchroGroupService;
    }

    public SynchroMailerService getSynchroMailerService() {
        return synchroMailerService;
    }

    public SynchroAddressBookService getSynchroAddressBookService() {
        return synchroAddressBookService;
    }

    public Neo4jAddrbookService getNeo4jAddrbookService() {
        return neo4jAddrbookService;
    }

    public SynchroLauncher getSynchroLauncher() {
        return synchroLauncher;
    }
}
