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

package fr.openent.apizimbra.controller;


import fr.openent.apizimbra.ApiZimbra;

import fr.openent.apizimbra.manager.ServiceManager;
import fr.openent.apizimbra.service.NotificationService;
import fr.openent.apizimbra.service.CommunicationService;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.BaseController;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.vertx.java.core.http.RouteMatcher;

import java.util.Map;

import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;
import static org.entcore.common.user.UserUtils.getUserInfos;

public class ApiController extends BaseController {
    private NotificationService notificationService;
    private CommunicationService communicationService;

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);

    @Override
    public void init(Vertx vertx, JsonObject config, RouteMatcher rm,
                     Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
        super.init(vertx, config, rm, securedActions);

        ServiceManager serviceManager = ServiceManager.init(vertx, eb);
        notificationService = serviceManager.getNotificationService();
        communicationService = serviceManager.getCommunicationService();
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
     * Configure notification
     * Only used for rights management, should not be called
     */
    @Get("/rights/notification")
    @SecuredAction("zimbra.notification.configuration")
    public void configureNotification(final HttpServerRequest request) {
        renderError(request);
    }


    /**
     * Create notification in timeline when receiving a mail
     * Respond to the request immediatly to free it, then send the notification internally
     * Return empty Json Object if params are well formatted
     * @param request request containing data :
     *                sender : mail address of the sender
     *                recipient : neo4j id of the recipient
     *                messageId : essage_id in the mailbox of recipient
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

    @Get("config")
    @SecuredAction("apizimbra.manage.config")
    public void getConfig(HttpServerRequest request) {
        renderJson(request, ApiZimbra.appConfig.getPublicConfig());
    }
}
