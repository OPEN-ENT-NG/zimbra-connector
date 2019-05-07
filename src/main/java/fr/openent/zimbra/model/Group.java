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

package fr.openent.zimbra.model;

import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.service.data.Neo4jZimbraService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static fr.openent.zimbra.service.data.Neo4jZimbraService.*;

public class Group {

    private String id;
    private String groupName;
    private String groupDisplayName;
    private Neo4jZimbraService neo4jZimbraService;

    private static Logger log = LoggerFactory.getLogger(Group.class);

    private Group(JsonObject json) throws IllegalArgumentException {
        init();
        try {
            id = json.getString(GROUP_ID, "");
            String rawName = json.getString(GROUP_NAME, "");
            if(id.isEmpty() || rawName.isEmpty()) {
                throw new IllegalArgumentException("Invalid Json for Group");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Json for Group");
        }
    }

    protected Group(String groupId) throws IllegalArgumentException {
        init();
        if(groupId == null || groupId.isEmpty()) {
            throw new IllegalArgumentException("Invalid id for Group");
        }
        id = groupId;
    }

    private void init() {
        ServiceManager sm = ServiceManager.getServiceManager();
        neo4jZimbraService = sm.getNeoService();
    }

    public String getId() {
        return id;
    }

    protected String getGroupName() {
        return groupName;
    }

    protected String getGroupDisplayName() {
        return groupDisplayName;
    }

    @SuppressWarnings("WeakerAccess")
    public static List<Group> processJsonGroups(JsonArray groupArray) throws IllegalArgumentException {
        List<Group> resultList = new ArrayList<>();
        for(Object o : groupArray) {
            if(!(o instanceof JsonObject)) {
                throw new IllegalArgumentException("JsonArray is not a Group list");
            }
            JsonObject groupJson = (JsonObject)o;
            resultList.add(new Group(groupJson));
        }
        return resultList;
    }


    protected void fetchDataFromNeo(Handler<AsyncResult<Group>> handler) {
        neo4jZimbraService.getGroupFromNeo4j(getId(), neoRes -> {
            if(neoRes.failed()) {
                handler.handle(Future.failedFuture(neoRes.cause()));
            } else {
                JsonObject jsonResult = neoRes.result();
                groupDisplayName = jsonResult.getString(GROUP_DISPLAYNAME, "");
                groupName = jsonResult.getString(GROUP_NAME, "");
                handler.handle(Future.succeededFuture(Group.this));
            }
        });
    }
}
