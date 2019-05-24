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

package fr.openent.zimbra.model.synchro.addressbook;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.model.soap.model.SoapFolder;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import static fr.openent.zimbra.model.constant.ZimbraConstants.*;
import static fr.openent.zimbra.model.constant.ZimbraErrors.*;
import static fr.openent.zimbra.service.data.SoapZimbraService.ERROR_CODE;

class AddressBookZimbraSynchro {

    private static Logger log = LoggerFactory.getLogger(AddressBookZimbraSynchro.class);

    void initSync(String userId, Handler<AsyncResult<JsonObject>> handler) {
        String rootFolderName = Zimbra.appConfig.getSharedFolderName();
        getFolder(userId, rootFolderName, res -> {
           if(res.failed()) {
               handler.handle(Future.failedFuture(res.cause()));
           } else {
               SoapFolder zimbraFolder = res.result();
               zimbraFolder.emptyFolder(userId, handler);
           }
        });
    }

    private void getFolder(String userId, String folderName, Handler<AsyncResult<SoapFolder>> handler) {
        SoapFolder.getFolderByPath(userId, folderName, VIEW_CONTACT, 0, res ->  {
            if(res.failed()) {
                try  {
                    JsonObject error = new JsonObject(res.cause().getMessage());
                    String errorCode = error.getString(ERROR_CODE, "");
                    if(ERROR_NOSUCHFOLDER.equals(errorCode)) {
                        SoapFolder.createFolderByPath(userId, folderName, VIEW_CONTACT, handler);
                    } else {
                        handler.handle(res);
                    }
                } catch (Exception e) {
                    log.warn("ABSync : Unable to decode Zimbra error : " + res.cause().getMessage());
                    handler.handle(res);
                }
            } else {
                handler.handle(res);
            }
        });
    }
}
