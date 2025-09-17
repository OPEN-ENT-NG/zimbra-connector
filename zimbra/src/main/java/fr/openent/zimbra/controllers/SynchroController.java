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

package fr.openent.zimbra.controllers;

import fr.openent.zimbra.model.synchro.SynchroInfos;
import fr.openent.zimbra.helper.AsyncHelper;
import fr.openent.zimbra.helper.JsonHelper;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.service.synchro.*;
import fr.wseduc.bus.BusAddress;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.BaseController;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.vertx.java.core.http.RouteMatcher;

import java.util.List;
import java.util.Map;

import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;
import static fr.openent.zimbra.model.constant.BusConstants.*;


public class SynchroController extends BaseController {

    private SynchroExportService synchroExportService;
    private SynchroService synchroService;
    private SynchroMailerService synchroMailerService;
    private SynchroAddressBookService synchroAddressBookService;



    private static final Logger log = LoggerFactory.getLogger(SynchroController.class);

    @Override
    public Future<Void> initAsync(Vertx vertx, JsonObject config, RouteMatcher rm,
                                    Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
        super.init(vertx, config, rm, securedActions);

        return ServiceManager.init(vertx, eb, pathPrefix)
          .compose(serviceManager -> {
            this.synchroExportService = new SynchroExportService();
            this.synchroService = serviceManager.getSynchroService();
            this.synchroMailerService = serviceManager.getSynchroMailerService();
            this.synchroAddressBookService = serviceManager.getSynchroAddressBookService();
            return Future.succeededFuture();
          });
    }

    /**
     * Update the list of deployed structures.
     * (Only deployed structures are synchronized)
     * @param request request containing Json :
     * {
     *    "structures_list" : ["UAI12345","UAI2356",...]
     * }
     */
    @Post("/synchro/structureslist")
    @SecuredAction("synchro.update.structurelist")
    public void updateStructureList(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, body -> {
            final JsonArray rawlist = body.getJsonArray("structures_list", new JsonArray());

            if(rawlist.isEmpty()) {
                log.error("Non existent or empty structures_list : " + body.toString());
                badRequest(request, "Non existent or empty structures_list");
            }else {
                try {
                    List<String> structureList = JsonHelper.getStringList(rawlist);
                    synchroService.updateDeployedStructures(structureList,
                            AsyncHelper.getJsonObjectAsyncHandler(defaultResponseHandler(request)));
                } catch (IllegalArgumentException e) {
                    log.error("Invalid format for structure UAI : " + rawlist.toString());
                    badRequest(request, "Invalid format for structure UAI");
                }
            }
        });
    }

    @Post("/synchro/users")
    @SecuredAction("synchro.update.users")
    public void updateUsers(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, body -> {
            try {
                SynchroInfos synchroInfos = new SynchroInfos(body);
                synchroService.updateUsers(synchroInfos,
                        AsyncHelper.getJsonObjectAsyncHandler(defaultResponseHandler(request)));
            } catch (IllegalArgumentException e) {
                badRequest(request, "Invalid format " + e.getMessage());
            }
        });
    }

    @Get("/synchro/test/triggreruser")
    @SecuredAction("synchro.test.triggeruser")
    public void triggerUserSynchro(final HttpServerRequest request) {
        synchroService.startSynchro(AsyncHelper.getJsonObjectAsyncHandler(defaultResponseHandler(request)));
    }



    @Get("/synchro/test/triggrerca")
    @SecuredAction("synchro.test.triggerca")
    public void triggerAddressBookSynchro(final HttpServerRequest request) {
        synchroAddressBookService.startSynchro(AsyncHelper.getJsonObjectAsyncHandler(defaultResponseHandler(request)));
    }

    @Get("/synchro/test/triggrermail")
    @SecuredAction("synchro.test.triggeruser")
    public void triggerMailing(final HttpServerRequest request) {
        ServiceManager sm = ServiceManager.getServiceManager();
        SynchroMailerService synchroMailerService = sm.getSynchroMailerService();
        synchroMailerService.startMailing(AsyncHelper.getJsonObjectAsyncHandler(defaultResponseHandler(request)));
    }


    /**
     * List Structures for Zimbra Synchronisation
     * @param request Http request
     */
    @Get("/export/structures")
    @SecuredAction("export.structure.list.all")
    public void listStructures(final HttpServerRequest request) {
        synchroExportService.listStructures(arrayResponseHandler(request));
    }

    /**
     * List users for one or more structure
     * for Zimbra Synchronisation
     * @param request Http request, containing info
     *                uai : Structure UAI,
     *                uai : Structure UAI,
     *                ...
     */
    @Get("/export/users")
    @SecuredAction("export.structure.users.all")
    public void listUsersByStructure(final HttpServerRequest request) {
        final List<String> structures = request.params().getAll("uai");
        synchroExportService.listUsersByStructure(structures, arrayResponseHandler(request));
    }

    @BusAddress(SYNCHRO_BUSADDR)
    public void handleSynchro(Message<JsonObject> message) {
        String action = message.body().getString(BUS_ACTION, "");
        JsonObject jsonResponse = new JsonObject();
        switch (action) {
            case ACTION_STARTSYNCHRO:
                log.info("Trying to start synchronization");
                synchroService.startSynchro( res -> message.reply(getDefaultResponse(res)) );
                break;
            case ACTION_MAILINGSYNCHRO:
                synchroMailerService.startMailing( res -> message.reply(getDefaultResponse(res)) );
                break;
            default:
                log.error("Zimbra synchro invalid action : " + action);
                jsonResponse
                        .put(BUS_STATUS, STATUS_ERROR)
                        .put(BUS_MESSAGE, MESSAGE_INVALID_ACTION);
                message.reply(jsonResponse);
                break;
        }
    }

    private JsonObject getDefaultResponse(AsyncResult<JsonObject> result) {
        JsonObject jsonResponse = new JsonObject();
        if(result.succeeded()) {
            jsonResponse.put(BUS_STATUS, STATUS_OK)
                    .put(BUS_MESSAGE, result.result());
        } else {
            jsonResponse.put(BUS_STATUS, STATUS_ERROR)
                    .put(BUS_MESSAGE, MESSAGE_INVALID_ACTION);
        }
        return jsonResponse;
    }
}
