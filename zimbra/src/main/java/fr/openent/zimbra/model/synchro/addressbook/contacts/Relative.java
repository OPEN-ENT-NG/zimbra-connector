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

package fr.openent.zimbra.model.synchro.addressbook.contacts;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import static fr.openent.zimbra.service.data.Neo4jAddrbookService.*;

public class Relative extends Contact{

    public Relative(JsonObject json, String uai) {
        super(json, uai);
        JsonArray neoClasses = json.getJsonArray(CLASSES, new JsonArray());
        String concatClasses = concatField(neoClasses);
        if(!concatClasses.isEmpty()) {
            classes = concatClasses;
            displayName = displayName + " (" + getProfileFolderName() + "; " + concatClasses + ")";
        }
    }

    @Override
    public String getProfile() {
        return PROFILE_RELATIVE;
    }
}
