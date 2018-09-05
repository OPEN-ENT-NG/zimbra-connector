package fr.openent.zimbra.controllers;

import fr.openent.zimbra.service.impl.SoapZimbraService;
import fr.openent.zimbra.service.impl.SqlZimbraService;
import fr.openent.zimbra.service.impl.UserService;
import fr.openent.zimbra.service.synchro.SynchroExportService;
import fr.openent.zimbra.service.synchro.SynchroUserService;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Put;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.BaseController;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import org.vertx.java.core.http.RouteMatcher;

import java.util.List;
import java.util.Map;

import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;


public class SynchroController extends BaseController {

    private SynchroExportService synchroExportService;
    private SynchroUserService synchroUserService;

    @Override
    public void init(Vertx vertx, JsonObject config, RouteMatcher rm,
                     Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
        super.init(vertx, config, rm, securedActions);
        SqlZimbraService sqlService = new SqlZimbraService(vertx, config.getString("db-schema", "zimbra"));
        SoapZimbraService soapService = new SoapZimbraService(vertx, config);
        this.synchroExportService = new SynchroExportService();
        this.synchroUserService = new SynchroUserService(soapService, sqlService);
        UserService userService = new UserService(soapService, synchroUserService, sqlService);

        soapService.setServices(userService, synchroUserService);
        synchroUserService.setUserService(userService);
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
}
