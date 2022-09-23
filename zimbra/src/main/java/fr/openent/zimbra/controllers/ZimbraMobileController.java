package fr.openent.zimbra.controllers;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.filters.DevLevelFilter;
import fr.openent.zimbra.helper.AsyncHelper;
import fr.openent.zimbra.helper.JsonHelper;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.service.messages.MobileThreadService;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.rs.Put;
import fr.wseduc.security.ActionType;
import fr.wseduc.webutils.Utils;
import fr.wseduc.webutils.http.BaseController;
import fr.wseduc.webutils.request.RequestUtils;
import fr.wseduc.webutils.security.SecuredAction;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
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

    /**
     * Get all messages from a thread
     * @param request http request
     *                  threadId (URL param) : Id of the thread to fetch
     *        Response : array of messages
     *        Error 400 : threadId is empty
     *        Error 401 : User not logged in
     */
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

    /**
     * Get paginated list of threads for connected user
     * @param request http request
     *                  page (Optional GET param) : Page number, default to 0
     *        Response : array of threads
     *        Error 401 : User not logged in
     */
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

    /**
     * Toggle threads read or unread
     * @param request http request
     *                Body :
     *                {
     *                  "id" : list of thread ids to change,
     *                  "unread" : if true, set threads as unread, else as read
     *                }
     *        Error 400 : Malformed body
     *        Error 401 : User not logged in
     */
    @Post("/thread/toggleUnread")
    @fr.wseduc.security.SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(DevLevelFilter.class)
    public void toggleUnreadThread(final HttpServerRequest request) {

        UserUtils.getUserInfos(eb, request, user -> {
            if (user != null) {
                RequestUtils.bodyToJson(request, body -> {
                    try {
                        List<String> ids = JsonHelper.getStringList(body.getJsonArray(Field.ID, new JsonArray()));
                        boolean unread = body.getBoolean("unread");
                        if (ids.isEmpty()) {
                            badRequest(request);
                            return;
                        }
                        mobileThreadService.toggleUnreadThreads(ids, unread, user,
                                AsyncHelper.getJsonObjectAsyncHandler(defaultResponseHandler(request)));
                    } catch (Exception e) {
                        badRequest(request);
                    }
                });
            } else {
                unauthorized(request);
            }
        });
    }

    /**
     * Move threads to trash
     * @param request http request
     *                Body :
     *                {
     *                  "id" : list of thread ids to change
     *                }
     *        Error 400 : Malformed body
     *        Error 401 : User not logged in
     */
    @Put("/thread/trash")
    @fr.wseduc.security.SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(DevLevelFilter.class)
    public void trashThread(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user != null) {
                RequestUtils.bodyToJson(request, body -> {
                    try {
                        List<String> threadIds = JsonHelper.getStringList(body.getJsonArray(Field.ID, new JsonArray()));
                        if (threadIds.isEmpty()) {
                            badRequest(request);
                            return;
                        }
                        mobileThreadService.trashThreads(threadIds, user,
                                AsyncHelper.getJsonObjectAsyncHandler(defaultResponseHandler(request)));
                    } catch (Exception e) {
                        badRequest(request);
                    }
                });
            } else {
                unauthorized(request);
            }
        });
    }

    /**
     * Get paginated list of messages for a thread
     * @param request http request
     *                  threadId (GET param) : Thread id to fetch
     *                  page (Optional GET param) : Page number, default to 0
     *        Response : array of messages
     *        Error 400 : empty thread id
     *        Error 401 : User not logged in
     */
    @Get("/thread/get-page/:threadId")
    @fr.wseduc.security.SecuredAction(value = "zimbra.message", type = ActionType.AUTHENTICATED)
    public void getThreadPageMessages(final HttpServerRequest request) {
        final String threadId = request.params().get("threadId");
        final String pageStr = Utils.getOrElse(request.params().get("page"), "0", false);

        if (threadId == null || threadId.trim().isEmpty()) {
            badRequest(request);
            return;
        }

		getUserInfos(eb, request, user -> {
			if (user != null) {
                int page;
                try {
                    page = Integer.parseInt(pageStr);
                } catch (NumberFormatException e) { page = 0; }
                mobileThreadService.getMessages(threadId, user, page,
                        AsyncHelper.getJsonArrayAsyncHandler(arrayResponseHandler(request)));
			} else {
				unauthorized(request);
			}
		});
    }
}
