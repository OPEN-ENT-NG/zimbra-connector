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
import fr.openent.zimbra.model.synchro.Structure;
import fr.openent.zimbra.model.synchro.addressbook.contacts.Contact;
import fr.openent.zimbra.model.synchro.addressbook.contacts.GroupContact;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.user.UserUtils;

import static fr.openent.zimbra.service.data.Neo4jAddrbookService.*;

public class AddressBookSynchroVisibles extends AddressBookSynchro {

    private String userId;

    private static Logger log = LoggerFactory.getLogger(AddressBookSynchroVisibles.class);

    public AddressBookSynchroVisibles(Structure structure, String userId) throws NullPointerException {
        super(structure);
        this.userId = userId;
        if(userId == null || userId.isEmpty()) {
            throw new NullPointerException("AddrBookVisibles : Empty UserId");
        }
    }

    @Override
    protected void load(Handler<AsyncResult<AddressBookSynchro>> handler) {
        neo4jAddrbookService.getVisibles( userId, uai, res -> {
            if (res.failed()) {
                handler.handle(Future.failedFuture(res.cause()));
            } else {
                loaded = true;
                processVisibles(res.result(), handler);
            }
        });
    }


    private void processVisibles(JsonArray visibles, Handler<AsyncResult<AddressBookSynchro>> handler) {
        UserUtils.translateGroupsNames(visibles, Zimbra.appConfig.getSynchroLang());
        for(Object o : visibles) {
            if(!(o instanceof JsonObject)) continue;
            try{
                JsonObject visible = ((JsonObject)o);
                if(visible.getString(GROUPNAME,"").isEmpty()) {
                    processUser(visible);
                } else {
                    Contact groupContact = new GroupContact(visible, uai);
                    addToProfileFolder(groupContact);
                }
            }catch (Exception e) {
                log.error("ABSync : Unknown Error when processing user : " + o, e);
            }
        }
        if(folders.isEmpty()) {
            handler.handle(Future.failedFuture("no address book generated"));
        } else {
            handler.handle(Future.succeededFuture(this));
        }
    }
}
