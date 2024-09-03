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

package fr.openent.zimbra.service.synchro;


import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.helper.JsonHelper;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.model.constant.SynchroConstants;
import fr.openent.zimbra.model.synchro.DatabaseSynchro;
import fr.openent.zimbra.service.data.SqlSynchroService;
import fr.wseduc.webutils.email.EmailSender;
import io.vertx.core.*;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.http.request.JsonHttpServerRequest;

import java.util.ArrayList;
import java.util.List;

public class SynchroMailerService {

    private final SqlSynchroService sqlSynchroService;

    private static final Logger log = LoggerFactory.getLogger(SynchroMailerService.class);

    public SynchroMailerService(SqlSynchroService sqlSynchroService) {
        this.sqlSynchroService = sqlSynchroService;
    }


    public void startMailing(Handler<AsyncResult<JsonObject>> handler) {
        sqlSynchroService.updateSynchros(
                SynchroConstants.STATUS_DONE,
                SynchroConstants.STATUS_MAILLING_DONE,
                res -> {
                    if(res.failed()) {
                        handler.handle(Future.failedFuture(res.cause()));
                    } else {
                        try {
                            List<String> idsSynchros = JsonHelper.extractValueFromJsonObjects(res.result(),
                                    SqlSynchroService.SYNCHRO_ID);
                            startMailingForSyncList(idsSynchros, handler);
                        } catch (IllegalArgumentException e) {
                            log.error("Error when getting synchros ids");
                            handler.handle(Future.failedFuture("Error when getting synchros ids"));
                        }
                    }
                }
        );
    }

    private void startMailingForSyncList(List<String> syncIdList, Handler<AsyncResult<JsonObject>> handler) {
        List<Future<?>> allSyncProcessed = new ArrayList<>();
        for(String syncId : syncIdList) {
            sqlSynchroService.getSynchroInfos(syncId, dbres -> {
                Promise<JsonObject> syncEnded = Promise.promise();
                if(dbres.failed()) {
                    syncEnded.fail(dbres.cause());
                    log.error("Error when getting synchro " + syncId + " error : " + dbres.cause());
                } else  {
                    processDatabaseSynchroResult(dbres.result(), syncEnded);
                }
                allSyncProcessed.add(syncEnded.future());
            });
        }
        Future.join(allSyncProcessed).onComplete( res -> {
            if(res.succeeded()) {
                handler.handle(Future.succeededFuture(new JsonObject()));
            } else {
                handler.handle(Future.failedFuture(res.cause()));
            }
        });
    }

    private void processDatabaseSynchroResult(JsonObject dbResult,
                                              Handler<AsyncResult<JsonObject>> handler) {
        try {
            DatabaseSynchro synchroData = new DatabaseSynchro(dbResult);
            sendMailling(synchroData, handler);
        } catch (IllegalArgumentException e) {
            log.error("Could not process Synchro info from database : " + dbResult.toString());
            handler.handle(Future.failedFuture(e));
        }
    }

    private void sendMailling(DatabaseSynchro synchroData, Handler<AsyncResult<JsonObject>> handler) {
        //todo get logs and send mailling
        String syncId = synchroData.getSynchroId();
        String syncDate = synchroData.getDate();
        String logsContent = synchroData.getLogsContent();
        ServiceManager sm = ServiceManager.getServiceManager();
        EmailSender emailSender = sm.getEmailSender();

        String subject = String.format("Zimbra synchronization report #%s", syncId);
        String from = Zimbra.appConfig.getSynchroFromMail();
        ArrayList<Object> to = new ArrayList<>(synchroData.getMaillingList());
        ArrayList<Object> cc = new ArrayList<>();
        ArrayList<Object> bcc = new ArrayList<>();
        JsonArray headers = new JsonArray();

        String mailContent = String.format("Here is Zimbra synchronization report #%s<br/>\r\n", syncId)
                + String.format("Date launched : %s<br/>\r\n", syncDate)
                + "Errors : <br/>\r\n"
                + logsContent;
        try{
            final HttpServerRequest request = new JsonHttpServerRequest(new JsonObject());
            emailSender.sendEmail(request,
                    to,
                    from,
                    cc,
                    bcc,
                    subject,
                    mailContent,
                    null,
                    false, headers,
                    res -> {
                        if(res.failed()) {
                            handler.handle(Future.failedFuture(res.cause()));
                        } else {
                            handler.handle(Future.succeededFuture(res.result().body()));
                        }
                    });
        }catch(Exception e){
            log.error("Failed to send Zimbra synchronization report : ", e);
        }
    }
}
