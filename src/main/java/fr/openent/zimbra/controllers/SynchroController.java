package fr.openent.zimbra.controllers;

import fr.openent.zimbra.model.synchro.SynchroInfos;
import fr.openent.zimbra.helper.AsyncHelper;
import fr.openent.zimbra.helper.JsonHelper;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.service.synchro.SynchroExportService;
import fr.openent.zimbra.service.synchro.SynchroService;
import fr.openent.zimbra.service.synchro.SynchroUserService;
import fr.wseduc.bus.BusAddress;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.BaseController;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.vertx.java.core.http.RouteMatcher;

import java.util.List;
import java.util.Map;

import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;
import static fr.openent.zimbra.model.constant.BusConstants.*;


public class SynchroController extends BaseController {

    private SynchroExportService synchroExportService;
    private SynchroUserService synchroUserService;
    private SynchroService synchroService;



    private static final Logger log = LoggerFactory.getLogger(SynchroController.class);

    @Override
    public void init(Vertx vertx, JsonObject config, RouteMatcher rm,
                     Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
        super.init(vertx, config, rm, securedActions);

        ServiceManager serviceManager = ServiceManager.init(vertx, config, eb, pathPrefix);

        this.synchroExportService = new SynchroExportService();
        this.synchroUserService = serviceManager.getSynchroUserService();
        this.synchroService = serviceManager.getSynchroService();

    }

    /**
     * Update the list of deployed structures.
     * (Only deployed structures are synchronized)
     * @param request request containing Json :
     * {
     *    "structures_list" : ["UAI12345","UAI2356",...]
     * }
     */
    @Post("/synchro/structureslist")
    @SecuredAction("synchro.update.structurelist")
    public void updateStructureList(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, body -> {
            final JsonArray rawlist = body.getJsonArray("structures_list", new JsonArray());

            if(rawlist.isEmpty()) {
                log.error("Non existent or empty structures_list : " + body.toString());
                badRequest(request, "Non existent or empty structures_list");
            }else {
                try {
                    List<String> structureList = JsonHelper.getStringList(rawlist);
                    synchroService.updateDeployedStructures(structureList,
                            AsyncHelper.getJsonObjectAsyncHandler(defaultResponseHandler(request)));
                } catch (IllegalArgumentException e) {
                    log.error("Invalid format for structure UAI : " + rawlist.toString());
                    badRequest(request, "Invalid format for structure UAI");
                }
            }
        });
    }

    @Post("/synchro/users")
    @SecuredAction("synchro.update.users")
    public void updateUsers(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, body -> {
            try {
                SynchroInfos synchroInfos = new SynchroInfos(body);
                synchroService.updateUsers(synchroInfos,
                        AsyncHelper.getJsonObjectAsyncHandler(defaultResponseHandler(request)));
            } catch (IllegalArgumentException e) {
                badRequest(request, "Invalid format " + e.getMessage());
            }
        });
    }

    @Get("/synchro/test/triggreruser")
    @SecuredAction("synchro.test.triggeruser")
    public void triggerUserSynchro(final HttpServerRequest request) {
        synchroUserService.syncUserFromBase(AsyncHelper.getJsonObjectAsyncHandler(defaultResponseHandler(request)));
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

    @BusAddress(SYNCHRO_BUSADDR)
    public void handleSynchro(Message<JsonObject> message) {
        String action = message.body().getString(BUS_ACTION, "");
        if(ACTION_STARTSYNCHRO.equals(action)) {
            log.info("Trying to start synchronization");
            JsonObject json = new JsonObject();
            synchroService.startSynchro( res -> {
                if(res.succeeded()) {
                    json.put(BUS_STATUS, STATUS_OK)
                            .put(BUS_MESSAGE, res.result());
                } else {
                    json.put(BUS_STATUS, STATUS_ERROR)
                            .put(BUS_MESSAGE, MESSAGE_INVALID_ACTION);
                }
                message.reply(json);
            });
        } else {
            log.error("Zimbra synchro invalid action : " + action);
            JsonObject json = new JsonObject()
                    .put(BUS_STATUS, STATUS_ERROR)
                    .put(BUS_MESSAGE, MESSAGE_INVALID_ACTION);
            message.reply(json);
        }
    }
}
