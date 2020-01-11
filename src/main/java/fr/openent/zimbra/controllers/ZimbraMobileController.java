package fr.openent.zimbra.controllers;

import fr.openent.zimbra.filters.DevLevelFilter;
import fr.openent.zimbra.helper.AsyncHelper;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.service.messages.MobileThreadService;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.rs.Put;
import fr.wseduc.security.ActionType;
import fr.wseduc.webutils.Utils;
import fr.wseduc.webutils.http.BaseController;
import fr.wseduc.webutils.security.SecuredAction;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.user.UserUtils;
import org.vertx.java.core.http.RouteMatcher;

import java.util.List;
import java.util.Map;

import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;
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

    @Get("/threads/list")
    @fr.wseduc.security.SecuredAction(value = "zimbra.list", type = ActionType.AUTHENTICATED)
    public void listThreads(HttpServerRequest request) {
        final String pageStr = Utils.getOrElse(request.params().get("page"), "0", false);

        getUserInfos(eb, request, user -> {
            if (user != null) {
                int page;
                try {
                    page = Integer.parseInt(pageStr);
                } catch (NumberFormatException e) { page = 0; }
                mobileThreadService.listThreads(user, page,
                        AsyncHelper.getJsonArrayAsyncHandler(arrayResponseHandler(request)));
            } else {
                unauthorized(request);
            }
        });
    }

    @Post("/thread/toggleUnread")
    @fr.wseduc.security.SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(DevLevelFilter.class)
    public void toggleUnreadThread(final HttpServerRequest request) {
        final List<String> ids = request.params().getAll("id");
        final String unread = request.params().get("unread");

        if (ids == null || ids.isEmpty() || unread == null || (!unread.equals("true") && !unread.equals("false"))) {
            badRequest(request);
            return;
        }
        UserUtils.getUserInfos(eb, request, user -> {
            if (user != null) {
                mobileThreadService.toggleUnreadThreads(ids, Boolean.parseBoolean(unread), user,
                        AsyncHelper.getJsonObjectAsyncHandler(defaultResponseHandler(request)));
            } else {
                unauthorized(request);
            }
        });
    }


    @Put("/thread/trash")
    @fr.wseduc.security.SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(DevLevelFilter.class)
    public void trashThread(final HttpServerRequest request) {
        final List<String> threadIds = request.params().getAll("id");
        if(threadIds == null || threadIds.size() == 0){
            badRequest(request);
            return;
        }
        UserUtils.getUserInfos(eb, request, user -> {
            if(user == null){
                unauthorized(request);
                return;
            }
            mobileThreadService.trashThreads(threadIds, user,
                    AsyncHelper.getJsonObjectAsyncHandler(defaultResponseHandler(request)));
        });
    }
}
