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

package fr.openent.zimbra.model.soap.model;

import fr.openent.zimbra.model.constant.SoapConstants;
import fr.openent.zimbra.model.soap.SoapRequest;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import static fr.openent.zimbra.model.constant.SoapConstants.*;
import static fr.openent.zimbra.model.constant.ZimbraConstants.*;

public class SoapContactFolder {
    private static Logger log = LoggerFactory.getLogger(SoapContactFolder.class);

    public static void importContactsFromCsv(String userId, String folderId, String csvContent,
                                             Handler<AsyncResult<JsonObject>> handler) {
        SoapRequest importContactsRequest = SoapRequest.MailSoapRequest(SoapConstants.IMPORT_CONTACTS_REQUEST, userId);
        JsonObject content = new JsonObject()
                .put(IMPORT_CONTENT_TYPE, IMPORT_CT_CSV)
                .put(IMPORT_FOLDER_ID, folderId)
                .put(IMPORT_CONTACTS_DATA, new JsonObject()
                    .put(ATTR_VALUE, csvContent));
        importContactsRequest.setContent(content);
        importContactsRequest.start(handler);
    }
}
