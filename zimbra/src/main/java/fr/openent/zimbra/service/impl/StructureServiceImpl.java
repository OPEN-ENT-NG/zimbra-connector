package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.ErrorEnum;
import fr.openent.zimbra.helper.IModelHelper;
import fr.openent.zimbra.helper.JsonHelper;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.helper.StructureHelper;
import fr.openent.zimbra.model.synchro.Structure;
import fr.openent.zimbra.service.StructureService;
import fr.openent.zimbra.service.data.Neo4jZimbraService;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class StructureServiceImpl implements StructureService {
    private final Neo4jZimbraService neoService;
    private static final Logger log = LoggerFactory.getLogger(StructureServiceImpl.class);

    public StructureServiceImpl(Neo4jZimbraService neo4jZimbraService) {
        this.neoService = neo4jZimbraService;
    }

    @Override
    public Future<List<Structure>> getStructuresAndAdmls(List<String> structuresId) {
        Promise<List<Structure>> promise = Promise.promise();

        neoService.listAdml(structuresId)
                        .onSuccess(structData -> {
                            try {
                                promise.complete(
                                        IModelHelper.toList(
                                                structData,
                                                struct -> new Structure(struct
                                                        .getString(Field.STRUCTURE))
                                                        .setADMLS(JsonHelper.getStringList(struct.getJsonArray(Field.ADMLS))))
                                );
                            } catch (Exception e) {
                                String errMessage = String.format("[Zimbra@%s::getStructuresAndAdmls]:  " +
                                                "error while fetching structures model: %s",
                                        this.getClass().getSimpleName(), e.getMessage());
                                log.error(errMessage);
                                promise.fail(ErrorEnum.ERROR_FETCHING_STRUCTURE.method());
                            }})
                        .onFailure(err -> {
                            String errMessage = String.format("[Zimbra@%s::getStructuresAndAdmls]:  " +
                                            "error while fetching structures ADMLs: %s",
                                    this.getClass().getSimpleName(), err.getMessage());
                            log.error(errMessage);
                            promise.fail(ErrorEnum.FAIL_NOTIFY_ADML.method());
                        });

        return promise.future();
    }
}
