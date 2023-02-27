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

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.helper.JsonHelper;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.model.constant.SoapConstants;
import fr.openent.zimbra.model.synchro.Structure;
import fr.openent.zimbra.service.DbMailService;
import fr.openent.zimbra.service.data.Neo4jZimbraService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;
import io.vertx.core.Handler;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static fr.openent.zimbra.model.constant.SynchroConstants.*;

@SuppressWarnings("FieldCanBeLocal")
public class EntUser {

    @SuppressWarnings("WeakerAccess")
    protected Neo4jZimbraService neoService;
    protected DbMailService dbMailService;

    private String userId;
    private String externalId = "";
    private String lastName = "";
    private String firstName = "";
    private String displayName = "";
    private String login = "";
    private String email = "";
    private String emailAcademy = "";
    private List<Group> groups = new ArrayList<>();
    private String profile = "";
    private List<Structure> structures;
    private MailAddress userZimbraAddress;


    public EntUser(UserInfos userInfos) throws IllegalArgumentException {
        initUser(userInfos.getUserId());
    }

    public EntUser(String userId) throws IllegalArgumentException {
        initUser(userId);
    }

    private void initUser(String userId) throws IllegalArgumentException {
        this.userId = userId;
        userZimbraAddress = MailAddress.createFromLocalpartAndDomain(userId, Zimbra.domain);
        ServiceManager sm = ServiceManager.getServiceManager();
        this.neoService = sm.getNeoService();
        this.dbMailService = sm.getDbMailServiceApp();
    }

    public String getUserId() {
        return userId;
    }

    public String getUserStrAddress() {
        return userZimbraAddress.toString();
    }

    @SuppressWarnings("WeakerAccess")
    public MailAddress getUserMailAddress() {
        return userZimbraAddress;
    }

    protected List<Group> getGroups() {
        return groups;
    }

    @SuppressWarnings("WeakerAccess")
    public String getLogin() {
        return login;
    }

    public void fetchDataFromNeo(Handler<AsyncResult<Void>> handler) {
        neoService.getUserFromNeo4j(userId, neoResult -> {
            if(neoResult.failed()) {
                handler.handle(Future.failedFuture(neoResult.cause()));
            } else {
                JsonObject jsonUser = neoResult.result();
                try {
                    applyJson(jsonUser);
                } catch (Exception e) {
                    handler.handle(Future.failedFuture(e));
                    return;
                }
                handler.handle(Future.succeededFuture());
            }
        });
    }

    private void applyJson(JsonObject neoUser) throws IllegalArgumentException {
        externalId = neoUser.getString("externalId", "");
        lastName = neoUser.getString("lastName", "");
        firstName = neoUser.getString("firstName", "");
        displayName = neoUser.getString("displayName", "");
        login = neoUser.getString("login", "");
        emailAcademy = neoUser.getString("emailAcademy", "");
        email = neoUser.getString("email", "");
        profile = neoUser.getString("profiles", "");
        groups = Group.processJsonGroups(neoUser.getJsonArray("groups", new JsonArray()));
        structures = neoUser
                .getJsonArray(Field.STRUCTURES, new JsonArray()).stream()
                .filter(structUai -> structUai instanceof String)
                .map(structUai -> new Structure(null, null, (String) structUai))
                .collect(Collectors.toList());

        if(login.isEmpty()) {
            throw new IllegalArgumentException("Login is mandatory for user : " + userId);
        }
    }


    @SuppressWarnings("WeakerAccess")
    public JsonArray getSoapData() {
        return getSoapData(false);
    }

    private JsonArray getSoapData(boolean delete) {
        List<String> uaiList = structures.stream().map(Structure::getUai).sorted().collect(Collectors.toList());
        StringBuilder structuresStr = new StringBuilder();
        for(String uai : uaiList) {
            structuresStr.append(uai);
            structuresStr.append(" ");
        }

        JsonArray attributes = new JsonArray();
        if(!delete) {
            attributes.add(new JsonObject()
                    .put(SoapConstants.ATTR_NAME, FIRSTNAME)
                    .put(SoapConstants.ATTR_VALUE, firstName))
                    .add(new JsonObject()
                            .put(SoapConstants.ATTR_NAME, LASTNAME)
                            .put(SoapConstants.ATTR_VALUE, lastName))
                    .add(new JsonObject()
                            .put(SoapConstants.ATTR_NAME, DISPLAYNAME)
                            .put(SoapConstants.ATTR_VALUE, displayName))
                    .add(new JsonObject()
                            .put(SoapConstants.ATTR_NAME, LOGIN)
                            .put(SoapConstants.ATTR_VALUE, login));
        }
        attributes.add(new JsonObject()
                        .put(SoapConstants.ATTR_NAME, PROFILE)
                        .put(SoapConstants.ATTR_VALUE, delete ? SoapConstants.EMPTY_VALUE : profile))
                .add(new JsonObject()
                        .put(SoapConstants.ATTR_NAME, STRUCTURES)
                        .put(SoapConstants.ATTR_VALUE, delete ? SoapConstants.EMPTY_VALUE : structuresStr.toString()))
                .add(new JsonObject()
                        .put(SoapConstants.ATTR_NAME, ACCOUNT_STATUS)
                        .put(SoapConstants.ATTR_VALUE, delete ? ACCT_STATUS_LOCKED : ACCT_STATUS_ACTIVE))
                .add(new JsonObject()
                        .put(SoapConstants.ATTR_NAME, HIDEINSEARCH)
                        .put(SoapConstants.ATTR_VALUE, delete ? SoapConstants.TRUE_VALUE : SoapConstants.FALSE_VALUE))
                .add(new JsonObject()
                        .put(SoapConstants.ATTR_NAME, DATE_MODIFICATION)
                        .put(SoapConstants.ATTR_VALUE, new SimpleDateFormat("yyyyMMdd").format(new Date().getTime())));

        if(delete) {
            attributes.add(new JsonObject()
                    .put(SoapConstants.ATTR_NAME, GROUPID)
                    .put(SoapConstants.ATTR_VALUE, SoapConstants.EMPTY_VALUE));
        } else {
            for (Group group : groups) {
                attributes
                        .add(new JsonObject()
                                .put(SoapConstants.ATTR_NAME, GROUPID)
                                .put(SoapConstants.ATTR_VALUE, group.getId()));
            }
        }

        return attributes;
    }

    public List<Structure> getStructures() {
        return structures;
    }

    public void setStructures(List<Structure> structures) {
        this.structures = structures;
    }

    @SuppressWarnings("WeakerAccess")
    public JsonArray getDeleteSoapData() {
        return getSoapData(true);
    }
}
