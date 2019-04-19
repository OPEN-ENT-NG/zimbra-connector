package fr.openent.zimbra.controllers;


import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.service.impl.CommunicationService;
import fr.openent.zimbra.service.impl.NotificationService;
import fr.openent.zimbra.service.synchro.SynchroUserService;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.rs.Put;
import fr.wseduc.webutils.http.BaseController;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.vertx.java.core.http.RouteMatcher;

import java.util.Map;

import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;

public class ExternalWebservicesController extends BaseController {

    private SynchroUserService synchroUserService;
    private NotificationService notificationService;
    private CommunicationService communicationService;

    private static final Logger log = LoggerFactory.getLogger(ExternalWebservicesController.class);

    @Override
    public void init(Vertx vertx, JsonObject config, RouteMatcher rm,
                     Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
        super.init(vertx, config, rm, securedActions);

        ServiceManager serviceManager = ServiceManager.init(vertx, config, eb, pathPrefix);
        synchroUserService = serviceManager.getSynchroUserService();
        notificationService = serviceManager.getNotificationService();
        communicationService = serviceManager.getCommunicationService();
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
}
