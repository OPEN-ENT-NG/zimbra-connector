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

package fr.openent.zimbra;

import fr.openent.zimbra.controllers.*;
import fr.openent.zimbra.filters.RequestErrorFilter;
import fr.openent.zimbra.helper.ConfigManager;
import fr.openent.zimbra.helper.JsonHelper;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.model.constant.BusConstants;
import fr.openent.zimbra.service.impl.ReturnedMailService;
import fr.openent.zimbra.service.impl.ZimbraRepositoryEvents;
import fr.openent.zimbra.service.synchro.SynchroTask;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.http.BaseServer;
import fr.wseduc.cron.CronTrigger;

import java.text.ParseException;
import java.util.List;


public class Zimbra extends BaseServer {

    public final static int DEFAULT_FOLDER_DEPTH = 3;
    public static final String URL = "/zimbra";
    public static String domain;
    public static String synchroLang;
    public static ConfigManager appConfig;
    public static String zimbraSchema;
    private ReturnedMailService returnedMailService;


    private static Logger log = LoggerFactory.getLogger(Zimbra.class);

    @Override
    public void start() throws Exception {
        super.start();

        appConfig = new ConfigManager(config);
        zimbraSchema = config.getString("db-schema");
        Zimbra.domain = appConfig.getZimbraDomain();
        Zimbra.synchroLang = appConfig.getSynchroLang();
        addController(new ZimbraController());
        addController(new ZimbraMobileController());
        addController(new SynchroController());
        addController(new ExternalWebservicesController());
        addController(new ZimbraAdminController());
        addFilter(new RequestErrorFilter());
        ServiceManager serviceManager = ServiceManager.init(vertx, vertx.eventBus(), "");
        this.returnedMailService = serviceManager.getReturnedMailService();
        // Repository Events
        setRepositoryEvents(new ZimbraRepositoryEvents());

        try {
            SynchroTask syncLauncherTask = new SynchroTask(vertx.eventBus(), BusConstants.ACTION_STARTSYNCHRO);
            new CronTrigger(vertx, appConfig.getSynchroCronDate()).schedule(syncLauncherTask);
            log.info("Cron launched with date : " + appConfig.getSynchroCronDate());
            returnedMailService.deleteMailsProgress(event -> {
                if(event.isLeft()) {
                 log.error("Failed to remove returned mails in progress : " + event.left().getValue());
                }
            });
        } catch (ParseException e) {
            log.fatal(e);
        }
        try {
            SynchroTask syncMailerTask = new SynchroTask(vertx.eventBus(), BusConstants.ACTION_MAILINGSYNCHRO);
            new CronTrigger(vertx, appConfig.getMailerCron()).schedule(syncMailerTask);
        } catch (ParseException e) {
            log.warn("Mailer Cron deactivated");
        }
    }
}
