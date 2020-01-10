package fr.openent.zimbra.controllers;

import fr.openent.zimbra.helper.AsyncHelper;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.service.messages.MobileThreadService;
import fr.wseduc.rs.Get;
import fr.wseduc.security.ActionType;
import fr.wseduc.webutils.http.BaseController;
import fr.wseduc.webutils.security.SecuredAction;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import org.vertx.java.core.http.RouteMatcher;

import java.util.Map;

import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.user.UserUtils.getUserInfos;

public class ZimbraMobileController extends BaseController {

    private MobileThreadService mobileThreadService;

    @Override
    public void init(Vertx vertx, JsonObject config, RouteMatcher rm,
                     Map<String, SecuredAction> securedActions) {
        super.init(vertx, config, rm, securedActions);
        ServiceManager serviceManager = ServiceManager.init(vertx, eb, pathPrefix);
        this.mobileThreadService = serviceManager.getMobileThreadService();
    }

    @Get("/thread/messages/:threadId")
    @fr.wseduc.security.SecuredAction(value = "zimbra.message", type = ActionType.AUTHENTICATED)
    public void getThreadMessages(final HttpServerRequest request) {
        final String threadId = request.params().get("threadId");
        if (threadId == null || threadId.trim().isEmpty()) {
            badRequest(request);
            return;
        }
        getUserInfos(eb, request, user -> {
            if (user != null) {
                mobileThreadService.getMessages(threadId, user,
                        AsyncHelper.getJsonArrayAsyncHandler(arrayResponseHandler(request)));
            } else {
                unauthorized(request);
            }
        });
    }
}
