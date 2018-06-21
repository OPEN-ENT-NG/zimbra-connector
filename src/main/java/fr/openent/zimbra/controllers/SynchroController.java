package fr.openent.zimbra.controllers;

import fr.openent.zimbra.service.synchro.SynchroExportService;
import fr.wseduc.rs.Get;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.BaseController;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import org.vertx.java.core.http.RouteMatcher;

import java.util.List;
import java.util.Map;

import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;


public class SynchroController extends BaseController {

    private SynchroExportService synchroExportService;

    @Override
    public void init(Vertx vertx, JsonObject config, RouteMatcher rm,
                     Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
        super.init(vertx, config, rm, securedActions);
        this.synchroExportService = new SynchroExportService();
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
}
