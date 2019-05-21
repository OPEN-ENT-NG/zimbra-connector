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

import fr.openent.zimbra.model.Group;
import fr.openent.zimbra.model.ZimbraUser;
import fr.wseduc.webutils.Either;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;

public abstract class DbMailService {

    public static final String ZIMBRA_NAME = "mailzimbra";
    public static final String NEO4J_UID = "uuidneo";

    protected String getUserNameFromMail(String mail) {
        return mail.split("@")[0];
    }

    /**
     * Get user uuid from mail in database
     * @param mail Zimbra mail
     * @param handler result handler
     */
    public abstract void getNeoIdFromMail(String mail, Handler<Either<String, JsonArray>> handler);

    /**
     * Get user mail from uuid in database
     * @param uuid User uuid
     * @param handler result handler
     */
    public abstract void getUserMailFromId(String uuid, Handler<Either<String, JsonArray>> handler);

    /**
     * Get group mail from uuid in database
     * @param uuid Group uuid
     * @param handler result handler
     */
    public abstract void getGroupMailFromId(String uuid, Handler<Either<String, JsonArray>> handler);

    /**
     * Remove user from base
     * @param userId user id
     * @param userMail user mail
     * @param handler final handler
     */
    public abstract void removeUserFrombase(String userId, String userMail, Handler<Either<String, JsonObject>> handler);

    public abstract void updateUserAsync(ZimbraUser user);

    public abstract void updateUsers(JsonArray users, Handler<Either<String, JsonObject>> handler);

    public abstract void checkGroupsExistence(List<Group> groups, Handler<AsyncResult<JsonArray>> handler);

    public abstract void updateGroup(String groupId, String groupAddr, Handler<AsyncResult<JsonObject>> handler);
}
