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
import fr.openent.zimbra.model.soap.model.SoapContactFolder;
import fr.openent.zimbra.model.soap.model.SoapFolder;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static fr.openent.zimbra.model.constant.ZimbraConstants.*;
import static fr.openent.zimbra.model.constant.ZimbraErrors.*;
import static fr.openent.zimbra.service.data.SoapZimbraService.ERROR_CODE;

class AddressBookZimbraSynchro {

    private String userId;
    private String uai;
    private String structureRootFolderPath;
    private String rootFolderName;
    private static Logger log = LoggerFactory.getLogger(AddressBookZimbraSynchro.class);

    AddressBookZimbraSynchro(String userId, String uai) {
        this.userId = userId;
        this.uai = uai;
        this.rootFolderName = Zimbra.appConfig.getSharedFolderName();
        this.structureRootFolderPath = rootFolderName + "/" + uai;
    }

    void initSync(Handler<AsyncResult<JsonObject>> handler) {
        getFolder(userId, rootFolderName, res -> {
           if(res.failed()) {
               handler.handle(Future.failedFuture(res.cause()));
           } else {
               getFolder(userId, structureRootFolderPath, resSubfolder -> {
                   if(resSubfolder.failed()) {
                       handler.handle(Future.failedFuture(resSubfolder.cause()));
                   } else {
                       SoapFolder zimbraFolder = resSubfolder.result();
                       zimbraFolder.emptyFolder(userId, handler);
                   }
               });
           }
        });
    }

    private void getFolder(String userId, String path, Handler<AsyncResult<SoapFolder>> handler) {
        SoapFolder.getFolderByPath(userId, path, VIEW_CONTACT, 0, res ->  {
            if(res.failed()) {
                try  {
                    JsonObject error = new JsonObject(res.cause().getMessage());
                    String errorCode = error.getString(ERROR_CODE, "");
                    if(ERROR_NOSUCHFOLDER.equals(errorCode)) {
                        SoapFolder.createFolderByPath(userId, path, VIEW_CONTACT, handler);
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

    public void sync(Map<String,AddressBookFolder> folders, Handler<AsyncResult<JsonObject>> handler) {
        syncSubFolders(structureRootFolderPath, folders, res -> {
            if(res.failed()) {
                log.error("AddrBookSync : Error when syncing etab " + uai + " for user " + userId, res.cause());
            }
            handler.handle(res);
        });
    }

    private void syncSubFolders(String path, Map<String,AddressBookFolder> subFolders,
                                Handler<AsyncResult<JsonObject>> handler) {
        List<Future> folderFutures = new ArrayList<>();
        subFolders.forEach( (name, folder) -> {
            String subFolderPath = path + "/" + name;
            Future<JsonObject> folderFuture = Future.future();
            folderFutures.add(folderFuture);
            syncFolder(subFolderPath, folder, folderFuture.completer());
        });
        CompositeFuture.all(folderFutures).setHandler( res -> {
            if(res.failed()) {
                handler.handle(Future.failedFuture(res.cause()));
            } else {
                handler.handle(Future.succeededFuture(new JsonObject()));
            }
        });
    }

    private void syncFolder(String path, AddressBookFolder folder, Handler<AsyncResult<JsonObject>> handler) {
        SoapFolder.createFolderByPath(userId, path, VIEW_CONTACT, resCreateFolder -> {
            if(resCreateFolder.failed()) {
                handler.handle(Future.failedFuture(resCreateFolder.cause()));
            } else {
                String folderId = resCreateFolder.result().getId();
                SoapContactFolder.importContactsFromCsv(userId, folderId, folder.getCsv(), res -> {
                    if(res.failed()) {
                        handler.handle(res);
                    } else {
                        syncSubFolders(path, folder.getSubFolders(), handler);
                    }
                });
            }
        });
    }
}
