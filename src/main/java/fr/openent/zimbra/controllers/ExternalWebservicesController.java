package fr.openent.zimbra.controllers;


import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.service.impl.UserService;
import fr.wseduc.rs.Get;
import fr.wseduc.security.ActionType;
import fr.wseduc.webutils.Utils;
import fr.wseduc.webutils.http.BaseController;
import fr.wseduc.security.SecuredAction;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.vertx.java.core.http.RouteMatcher;

import java.util.Map;

import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;

public class ExternalWebservicesController extends BaseController {

    UserService userService;

    private static final Logger log = LoggerFactory.getLogger(ExternalWebservicesController.class);

    @Override
    public void init(Vertx vertx, JsonObject config, RouteMatcher rm,
                     Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
        super.init(vertx, config, rm, securedActions);

        ServiceManager serviceManager = ServiceManager.init(vertx, config, eb, pathPrefix);
        userService = serviceManager.getUserService();
    }


    @Get("ws/getlogin")
    @SecuredAction("zimbra.ws.getlogin")
    public void getLogin(final HttpServerRequest request) {
        final String email = request.params().get("email");
        if (email == null || email.trim().isEmpty()) {
            badRequest(request);
        } else  {
            userService.getUserLogin(email, defaultResponseHandler(request));
        }
    }
}
