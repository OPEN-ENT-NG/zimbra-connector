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


import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.model.synchro.Structure;
import fr.openent.zimbra.model.synchro.addressbook.AddressBookSynchro;
import fr.openent.zimbra.model.synchro.addressbook.AddressBookSynchroVisibles;
import fr.openent.zimbra.service.data.SqlAddressBookService;
import fr.openent.zimbra.service.data.SqlSynchroService;
import fr.openent.zimbra.service.impl.CommunicationService;
import fr.openent.zimbra.service.impl.NotificationService;
import fr.openent.zimbra.service.impl.UserService;
import fr.openent.zimbra.service.synchro.SynchroAddressBookService;
import fr.openent.zimbra.service.synchro.SynchroUserService;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.rs.Put;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.BaseController;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.user.UserInfos;
import org.vertx.java.core.http.RouteMatcher;

import java.util.Map;

import static fr.openent.zimbra.Zimbra.appConfig;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;
import static org.entcore.common.user.UserUtils.getUserInfos;

public class ExternalWebservicesController extends BaseController {

    private SynchroUserService synchroUserService;
    private NotificationService notificationService;
    private CommunicationService communicationService;
    private UserService userService;
    private SqlSynchroService sqlSynchroService;
    private SqlAddressBookService sqlAddressBookService;
    private SynchroAddressBookService synchroAddressBookService;

    private static final Logger log = LoggerFactory.getLogger(ExternalWebservicesController.class);

    @Override
    public void init(Vertx vertx, JsonObject config, RouteMatcher rm,
                     Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
        super.init(vertx, config, rm, securedActions);

        ServiceManager serviceManager = ServiceManager.init(vertx, eb, pathPrefix);
        synchroUserService = serviceManager.getSynchroUserService();
        notificationService = serviceManager.getNotificationService();
        communicationService = serviceManager.getCommunicationService();
        userService = serviceManager.getUserService();
        sqlSynchroService = serviceManager.getSqlSynchroService();
        sqlAddressBookService = serviceManager.getSqlAddressBookService();
        synchroAddressBookService = serviceManager.getSynchroAddressBookService();
    }


    /**
     * A user id has been modified, mark it for update.
     * The user is removed from the base and will be resynchronized on next connection
     * @param request Http request, containing info
     *                entid : User ID as in Neo4j,
     *                zimbramail : Zimbra email address
     */
    @Put("/export/updateid")
    @SecuredAction("export.update.id")
    public void updateUserId(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, body -> {
            final String userId = body.getString("entid");
            final String userMail = body.getString("zimbramail");

            if(userId == null || userId.isEmpty()
                    || userMail == null || userMail.isEmpty()) {
                badRequest(request);
            } else {
                synchroUserService.removeUserFromBase(userId, userMail, defaultResponseHandler(request));
            }
        });
    }

    /**
     * Oauth connection
     * Secured action validate expert access to Zimbra for connected user
     * Trigger on-connection processes
     * @param request http request
     */
    @Get("/oauth/validate")
    @SecuredAction("zimbra.expert.validateoauth")
    public void oauthConnection(final HttpServerRequest request) {
        //todo oauthConnection : trigger on-connection processes
        log.info("call to oauthConnection");
        getUserInfos(eb, request, user -> {
            if (user != null) {

                renderJson(request, new JsonObject());
            } else {
                unauthorized(request);
            }
        });
    }


    /**
     * Create notification in timeline when receiving a mail
     * Respond to the request immediatly to free it, then send the notification internally
     * Return empty Json Object if params are well formatted
     * @param request request containing data :
     *                sender : mail address of the sender
     *                recipient : neo4j id of the recipient
     *                messageId : message_id in the mailbox of recipient
     *                subject : message subject
     */
    @Post("notification")
    @SecuredAction("zimbra.notification.send")
    public void sendNotification(HttpServerRequest request) {
        RequestUtils.bodyToJson(request, body -> {
            final String zimbraSender = body.getString("sender");
            final String zimbraRecipient = body.getString("recipient");
            final String messageId = body.getString("messageId");
            final String subject = body.getString("subject");

            if(zimbraSender == null || zimbraSender.isEmpty()
                    || zimbraRecipient == null || zimbraRecipient.isEmpty()
                    || messageId == null || messageId.isEmpty()) {
                badRequest(request);
            } else {
                renderJson(request, new JsonObject());
                notificationService.sendNewMailNotification(zimbraSender, zimbraRecipient, messageId, subject,
                        v -> {});
            }
        });
    }


    /**
     * Indicates if a sender (user or external address) can send a mail to a receiver (user, group or external address)
     * Returns JsonObject :
     * {
     *     can_communicate : true/false
     * } Check if communication is allowed between two mail addresses
     * @param request
     * 		from : mail address for the sender
     * 		to : mail address for the recipient
     */
    @Get("communication")
    @SecuredAction("zimbra.communication.all")
    public void canCommunicate(final HttpServerRequest request) {
        String sender = request.params().get("from");
        String receiver = request.params().get("to");
        if( sender != null && !sender.isEmpty() && receiver != null && !receiver.isEmpty()) {
            communicationService.canCommunicate(sender, receiver, defaultResponseHandler(request));
        } else {
            badRequest(request);
        }
    }

    @Get("wstest")
    @SecuredAction("zimbra.ws.test")
    public void testWs(final HttpServerRequest request) {
        String action = request.params().get("action");
        if(action == null || action.isEmpty()) {
            badRequest(request);
        } else {
            String userid = request.params().get("userid");
            String uai = request.params().get("uai");
            Structure structure = new Structure(new JsonObject().put(Structure.UAI, uai));
            switch (action) {
                case "conversationEB":
                    String subject = request.params().get("subject");
                    String body = request.params().get("body");
                    String to = request.params().get("to");
                    JsonObject message = new JsonObject().put("subject",subject)
                            .put("body",body)
                            .put("to",new JsonArray().add(to));
                    eb.send("org.entcore.conversation",
                            new JsonObject().put("action", "send")
                                    .put("userId", userid).put("message",message),
                            res -> renderJson(request, (JsonObject)res.result().body()));
                    return;
                case "getmail":
                    eb.send("fr.openent.zimbra",
                            new JsonObject().put("action", "getMailUser")
                                    .put("idList", new JsonArray().add(userid)),
                            res -> renderJson(request, (JsonObject)res.result().body()));
                    return;
                case "syncuserab":
                    String visibles = request.params().get("visibles");
                    AddressBookSynchro absync = "true".equals(visibles)
                            ? new AddressBookSynchroVisibles(structure, userid)
                            : new AddressBookSynchro(structure);
                    absync.synchronize(userid, ressync -> {
                        if(ressync.failed()) {
                            renderError(request);
                        } else {
                            renderJson(request, ressync.result());
                        }
                    });
                    break;
                case "forceSynchUserAddressBook":
                    UserInfos user = new UserInfos();
                    user.setUserId(userid);
                    sqlAddressBookService.purgeUserSyncAddressBook(userid, done -> {
                        if (done.isLeft()) {
                            renderError(request);
                        } else {
                            userService.syncAddressBookAsync(user);
                            ok(request);
                        }
                    });
                    break;
                case "purgeUserAddressBook":
                    sqlAddressBookService.purgeUserSyncAddressBook(userid, done ->{
                        if(done.isLeft()) {
                            renderError(request);
                        } else {
                            ok(request);
                        }
                    });
                    break;
                case "purgeStructureAddressBook":
                    sqlSynchroService.purgeStructureSyncAddressBook(uai, done -> {
                        if(done.isLeft()) {
                            renderError(request);
                        } else {
                            ok(request);
                        }
                    });
                    break;
                case "forceSynchStructureAddressBook":
                    sqlSynchroService.purgeStructureSyncAddressBook(uai, done -> {
                        if (done.isLeft()) {
                            renderError(request);
                        } else {
                            synchroAddressBookService.synchronizeStructure(structure, structureSynch -> {
                                if(structureSynch.failed()) {
                                    renderError(request);
                                } else {
                                    ok(request);
                                }
                            });
                        }
                    });
                    break;
                default:
                    badRequest(request);
            }
        }
    }

    @Get("config")
    @SecuredAction("zimbra.manage.config")
    public void getConfig(HttpServerRequest request) {
        renderJson(request, appConfig.getPublicConfig());
    }
}
