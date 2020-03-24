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
import fr.openent.zimbra.helper.JsonHelper;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class SynchroInfos {

    private static final String MAILLINGLIST = "maillingList";
    private static final String USERS_CREATED = "createdUsers";
    private static final String USERS_MODIFIED = "modifiedUsers";
    private static final String USERS_DELETED = "deletedUsers";

    private int id;
    private String maillingListRaw;
    private List<String> users_created;
    private List<String> users_modified;
    private List<String> users_deleted;

    private static Logger log = LoggerFactory.getLogger(SynchroInfos.class);

    public SynchroInfos(JsonObject jsonData) throws IllegalArgumentException {
        if(!jsonData.containsKey(USERS_CREATED)
            || !jsonData.containsKey(USERS_MODIFIED)
            || !jsonData.containsKey(USERS_DELETED)) {
            throw new IllegalArgumentException("Missing data");
        }
        users_created = JsonHelper.getStringList(jsonData.getJsonArray(USERS_CREATED));
        users_modified = JsonHelper.getStringList(jsonData.getJsonArray(USERS_MODIFIED));
        users_deleted = JsonHelper.getStringList(jsonData.getJsonArray(USERS_DELETED));
        if(jsonData.containsKey(MAILLINGLIST)) {
            maillingListRaw = jsonData.getString(MAILLINGLIST, "");
        }
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public String getMaillinglistRaw() {
        return maillingListRaw;
    }

    public List<String> getUsersCreated() {
        return users_created;
    }

    public List<String> getUsersModified() {
        return users_modified;
    }

    public List<String> getUsersDeleted() {
        return users_deleted;
    }
}
