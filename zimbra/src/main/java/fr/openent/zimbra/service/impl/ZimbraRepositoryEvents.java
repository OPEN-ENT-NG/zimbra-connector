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

package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.service.synchro.AddressBookService;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.RepositoryEvents;

/**
 * Created by sjol on 01/10/2020.
 */
public class ZimbraRepositoryEvents implements RepositoryEvents {

    private final AddressBookService addressBookService;
    public ZimbraRepositoryEvents() {
        ServiceManager serviceManager = ServiceManager.getServiceManager();
        this.addressBookService = serviceManager.getAddressBookService();
    }

    @Override
    public void deleteGroups(JsonArray jsonArray) {

    }

    @Override
    public void deleteUsers(JsonArray jsonArray) {

    }

    @Override
    public void transition(JsonObject structure) {
        addressBookService.truncatePurgeTable();
    }
}
