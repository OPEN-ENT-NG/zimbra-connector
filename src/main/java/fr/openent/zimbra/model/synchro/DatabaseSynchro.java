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

package fr.openent.zimbra.model.synchro;

import fr.openent.zimbra.model.MailAddress;
import fr.openent.zimbra.service.data.SqlSynchroService;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class DatabaseSynchro {

    private String synchroId;
    private List<MailAddress> maillingList = new ArrayList();
    private String logsContent;
    private String date;

    private static Logger log = LoggerFactory.getLogger(DatabaseSynchro.class);

    public DatabaseSynchro(JsonObject jsonData) throws IllegalArgumentException {
        if (!jsonData.containsKey(SqlSynchroService.SYNCHRO_ID)
                || !jsonData.containsKey(SqlSynchroService.SYNCHRO_MAILLINGLIST)) {
            throw new IllegalArgumentException("Missing data");
        }

        try {
            synchroId = jsonData.getInteger(SqlSynchroService.SYNCHRO_ID).toString();
            processMaillingList(jsonData.getString(SqlSynchroService.SYNCHRO_MAILLINGLIST));
            logsContent = jsonData.getString(SqlSynchroService.SYNCHRO_AGG_LOGS, "");
            date = jsonData.getString(SqlSynchroService.SYNCHRO_DATE, "");
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public String getSynchroId() { return synchroId;}
    public String getLogsContent() { return logsContent;}
    public List<MailAddress> getMaillingList() { return maillingList;}
    public String getDate() { return date;}

    private void processMaillingList(String rawList) {
        String[] rawMails = rawList.split(",");
        for( String mail : rawMails ) {
            try {
                MailAddress addr = MailAddress.createFromRawAddress(mail);
                maillingList.add(addr);
            } catch (IllegalArgumentException e) {
                log.warn("Synchro : Invalid email address : " + mail);
            }
        }
    }

}
