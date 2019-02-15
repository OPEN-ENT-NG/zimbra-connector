package fr.openent.zimbra.controllers;

import fr.openent.zimbra.service.impl.ZimbraAdminService;
import fr.wseduc.rs.*;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.http.BaseController;
import io.vertx.core.json.JsonArray;
import org.entcore.common.http.filter.AdminFilter;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.security.ActionType;
import org.entcore.common.http.filter.ResourceFilter;
import io.vertx.core.http.HttpServerRequest;
import org.entcore.common.user.UserUtils;

import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.leftToResponse;


public class ZimbraAdminController extends BaseController {

    @Get("/admin-console")
    @ResourceFilter(AdminFilter.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void adminView(HttpServerRequest request) {
        renderView(request);
    }

    @Get("/groups/roles")
    @ResourceFilter(AdminFilter.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void listGroupsWithRoles(final HttpServerRequest request) {
        String structureId = request.params().get("structureId");
        ZimbraAdminService.listGroupsWithRole(structureId, true, r -> {
            if (r.isRight()) {
                JsonArray res = r.right().getValue();
                UserUtils.translateGroupsNames(res, I18n.acceptLanguage(request));
                renderJson(request, res);
            } else {
                leftToResponse(request, r.left());
            }
        });
    }
    @Get("/role")
    @ResourceFilter(AdminFilter.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void zimbraRole(final HttpServerRequest request) {
        ZimbraAdminService.getTheRole( defaultResponseHandler(request));
    }
}
