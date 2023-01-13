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

package fr.openent.apizimbra.model;

import fr.openent.apizimbra.ApiZimbra;
import fr.openent.apizimbra.manager.ServiceManager;
import fr.openent.apizimbra.service.data.DbMailService;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class MailAddress {

    /**
     * The local part of an email address is the part before the '@'
     * See RFC 5322 Chapter 3.4.1 https://tools.ietf.org/html/rfc5322
     */
    private String localPart = "";
    private String domain = "";
    private String completeCleanAddress = "";
    private String rawAddress = "";
    private String neoId = "";
    private boolean isLocal;

    private DbMailService dbMailService;

    private static Logger log = LoggerFactory.getLogger(MailAddress.class);



    static public MailAddress createFromRawAddress(String rawAddress) throws IllegalArgumentException {
        return new MailAddress(rawAddress);
    }

    @SuppressWarnings({"unused"})
    static public MailAddress createFromLocalpartAndDomain(String localPart, String domain)
            throws IllegalArgumentException {
        return new MailAddress(localPart, domain);
    }

    public boolean isExternal() {
        return !isLocal;
    }
    public String getNeoId() {
        return neoId;
    }

    public MailAddress(String rawAddress) throws IllegalArgumentException {
        this.rawAddress = rawAddress;
        processRawAddress();
        initServices();
    }

    @Override
    public String toString() {
        return this.localPart + "@" + this.domain;
    }

    private void processRawAddress() throws IllegalArgumentException {
        if(rawAddress.isEmpty()) {
            throw new IllegalArgumentException("Empty address can't be processed");
        }
        String addr = rawAddress.replaceAll(".*<","");
        addr = addr.replaceAll(">.*","");
        completeCleanAddress = addr;
        processCleanAddress();
    }

    private void processCleanAddress() throws IllegalArgumentException {
        if(completeCleanAddress.isEmpty()) {
            throw new IllegalArgumentException("Empty address can't be processed");
        }
        try {
            setLocalPart(completeCleanAddress.substring(0, completeCleanAddress.indexOf('@')));
            setDomain(completeCleanAddress.substring(completeCleanAddress.indexOf('@') + 1));
        } catch (StringIndexOutOfBoundsException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public MailAddress(String localPart, String domain) throws IllegalArgumentException {
        setLocalPart(localPart);
        setDomain(domain);
        initServices();
    }

    private void setLocalPart(String localPart) {
        //todo check valid localPart
        this.localPart = localPart;
    }

    @SuppressWarnings("unused")
    String getLocalPart() {
        return localPart;
    }

    private void setDomain(String domain) {
        //todo check valid domain
        this.domain = domain;
        isLocal = ApiZimbra.appConfig.getZimbraDomain().equals(domain);
    }

    private void initServices() {
        ServiceManager serviceManager = ServiceManager.getServiceManager();
        dbMailService = serviceManager.getDbMailServiceApp();
    }

    public void fetchNeoId(Handler<String> handler) {
        if(!neoId.isEmpty()) {
            handler.handle(neoId);
            return;
        }
        if(!isLocal) {
            handler.handle(completeCleanAddress);
            return;
        }

        dbMailService.getNeoIdFromMail(completeCleanAddress, sqlResponse -> {
            if(sqlResponse.isLeft() || sqlResponse.right().getValue().isEmpty()) {
                log.error("no user in database for address : " + completeCleanAddress);
                // Do not fetch Zimbra inside apizimbra
                handler.handle(null);
            } else {
                JsonArray results = sqlResponse.right().getValue();
                if(results.size() > 1) {
                    log.warn("More than one user id for address : " + completeCleanAddress);
                }
                this.neoId = results.getJsonObject(0).getString(DbMailService.NEO4J_UID);
                handler.handle(this.neoId);
            }
        });
    }
}
