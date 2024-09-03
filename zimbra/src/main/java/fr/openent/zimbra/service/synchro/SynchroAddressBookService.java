package fr.openent.zimbra.service.synchro;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.helper.AsyncContainer;
import fr.openent.zimbra.helper.AsyncHandler;
import fr.openent.zimbra.helper.AsyncHelper;
import fr.openent.zimbra.model.soap.model.SoapFolder;
import fr.openent.zimbra.model.soap.model.SoapMountpoint;
import fr.openent.zimbra.model.synchro.Structure;
import fr.openent.zimbra.model.synchro.addressbook.AddressBookSynchro;
import fr.openent.zimbra.model.synchro.addressbook.AddressBookSynchroVisibles;
import fr.openent.zimbra.service.data.Neo4jZimbraService;
import fr.openent.zimbra.service.data.SqlSynchroService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;

import static fr.openent.zimbra.model.constant.ZimbraConstants.*;
import static fr.openent.zimbra.service.impl.CommunicationService.HAS_EXTERNAL_ROLE;

public class SynchroAddressBookService {

    private SqlSynchroService sqlSynchroService;
    private Neo4jZimbraService neoZimbraService;

    private static Logger log = LoggerFactory.getLogger(SynchroAddressBookService.class);

    private boolean synchroStarted = false;

    public SynchroAddressBookService(SqlSynchroService sqlSynchroService) {
        this.sqlSynchroService = sqlSynchroService;
        neoZimbraService = new Neo4jZimbraService();
    }

    public void startSynchro(Handler<AsyncResult<JsonObject>> handler) {
        if (synchroStarted) {
            log.warn("Address Book Synchronization already started");
            handler.handle(Future.failedFuture("Address Book Synchronization already started"));
        } else {
            synchroStarted = true;
            start(res -> {
                synchroStarted = false;
                handler.handle(res);
            });
        }
    }

    /**
     * Sync contacts for all structures for a user
     * Do not empty user folder if he has external communication role
     *
     * @param userId         id of user to sync
     * @param structuresList list of structures
     * @param handler        result handler (no data)
     */
    public void syncUser(String userId, List<Structure> structuresList, Handler<AsyncResult<Structure>> handler) {

        Promise<Structure> finalPromise = Promise.promise();
        finalPromise.future().onComplete(handler);
        Promise<JsonObject> roleFetched = Promise.promise();

        AsyncContainer<Boolean> hasRoleContainer = new AsyncContainer<>();

        neoZimbraService.hasExternalCommunicationRole(userId, roleFetched);

        AsyncContainer<AsyncHandler<Structure>> userHandlerContainer = new AsyncContainer<>();
        roleFetched.future().compose(neoResult -> {
                    Boolean hasExternalRole = neoResult.getBoolean(HAS_EXTERNAL_ROLE, false);
                    hasRoleContainer.setValue(hasExternalRole);
                    AsyncHandler<Structure> userHandler = getHandlerFromCommunicationRole(userId, hasExternalRole);
                    userHandlerContainer.setValue(userHandler);

                    Promise<SoapFolder> folderInitiated = Promise.promise();
                    SoapFolder.getOrCreateFolderByPath(userId, Zimbra.appConfig.getSharedFolderName(), VIEW_CONTACT,
                            folderInitiated);
                    return folderInitiated.future();
                }).compose(soapFolder -> {
                    Promise<JsonObject> folderEmptied = Promise.promise();
                    if (hasRoleContainer.getValue()) {
                        folderEmptied.complete(new JsonObject());
                    } else {
                        soapFolder.emptyFolder(userId, folderEmptied);
                    }
                    return folderEmptied.future();
                }).compose(v -> AsyncHelper.processListSynchronously(structuresList, userHandlerContainer.getValue()))
                .onSuccess(finalPromise::complete)
                .onFailure(err -> {
                    log.error("Error processing list synchronously", err);
                    finalPromise.fail(err);
                });
    }

    /**
     * Get the process for each structures oget shared folder from admin account
     * * - Else : get all accessiblef the user
     * - If he has external role :  contacts from structure
     *
     * @param userId  user neo4j id
     * @param hasRole hasExternalCommunicationRole result
     * @return Handler with appropriate process for each structure
     */
    private AsyncHandler<Structure> getHandlerFromCommunicationRole(String userId, Boolean hasRole) {
        AsyncHandler<Structure> handler;
        if (hasRole || Zimbra.appConfig.getForceSyncAdressBooks()) {
            handler = (structure, handlerStructure) -> shareAddressBook(userId, structure, handlerStructure);
        } else {
            handler = (structure, handlerStructure) -> {
                AddressBookSynchro absync = new AddressBookSynchroVisibles(structure, userId);
                absync.synchronize(userId, ressync -> {
                    if (ressync.failed()) {
                        handlerStructure.handle(Future.failedFuture(ressync.cause()));
                    } else {
                        handlerStructure.handle(Future.succeededFuture(structure));
                    }
                });
            };
        }
        return handler;
    }

    private void shareAddressBook(String userId, Structure structure, Handler<AsyncResult<Structure>> handler) {
        String adminName = structure.getUai() + Zimbra.appConfig.getAddressBookAccountName();
        String rootFolderPath = Zimbra.appConfig.getSharedFolderName();
        String adminMail = adminName + "@" + Zimbra.appConfig.getZimbraDomain();

        Promise<Structure> finalPromise = Promise.promise();
        finalPromise.future().onComplete(handler);
        AsyncContainer<SoapFolder> sharedFolderContainer = new AsyncContainer<>();
        AsyncContainer<SoapFolder> rootFolderContainer = new AsyncContainer<>();

        Promise<SoapFolder> sharedFolderFetched = Promise.promise();
        SoapFolder.getOrCreateFolderByPath(adminName, rootFolderPath + "/" + structure.getUai(),
                VIEW_CONTACT, sharedFolderFetched);
        sharedFolderFetched.future().compose(resFolder -> {
                    sharedFolderContainer.setValue(resFolder);

                    Promise<SoapFolder> folderCreated = Promise.promise();
                    SoapFolder.getOrCreateFolderByPath(userId, rootFolderPath, VIEW_CONTACT, folderCreated);
                    return folderCreated.future();
                }).compose(resRootFolder -> {
                    rootFolderContainer.setValue(resRootFolder);

                    SoapFolder sharedFolder = sharedFolderContainer.getValue();

                    Promise<JsonObject> folderShared = Promise.promise();
                    sharedFolder.shareFolderReadonly(adminName, userId, folderShared);
                    return folderShared.future();
                }).compose(resShare -> {
                    String rootFolderId = rootFolderContainer.getValue().getId();
                    String sharedFolderId = sharedFolderContainer.getValue().getId();

                    Promise<SoapMountpoint> mountpointCreated = Promise.promise();
                    SoapMountpoint.getOrCreateMountpoint(userId, structure.getName(), rootFolderId, VIEW_CONTACT, adminMail,
                            sharedFolderId, mountpointCreated);
                    return mountpointCreated.future();
                }).compose(resMountpoint -> synchronizeStructureFuture(structure))
                .onSuccess(resSync -> finalPromise.complete(structure))
                .onFailure(err -> {
                    log.error("Error in sharing address book", err);
                    finalPromise.fail(err);
                });
    }

    private void start(Handler<AsyncResult<JsonObject>> handler) {
        // Create a promise for the final sync result
        Promise<String> finalPromise = Promise.promise();
        finalPromise.future().onComplete(getFinalSyncHandler(handler));

        // Create a promise for fetching deployed structures
        Promise<List<String>> deployedStructuresFetched = Promise.promise();
        sqlSynchroService.getDeployedStructures(deployedStructuresFetched);

        // Compose future chains and synchronize address books
        deployedStructuresFetched.future().compose(structureList ->
                AsyncHelper.processListSynchronously(structureList, (uai, hand) -> {
                    log.info("Synchronizing address book for structure " + uai);
                    Structure structure = new Structure(new JsonObject().put(Structure.UAI, uai));
                    synchronizeStructure(structure, v -> hand.handle(Future.succeededFuture(uai)));
                })
        ).onComplete(finalPromise);

        finalPromise.future().onComplete(getFinalSyncHandler(handler));
    }

    private Handler<AsyncResult<String>> getFinalSyncHandler(Handler<AsyncResult<JsonObject>> handler) {
        return json -> {
            if (json.failed()) {
                handler.handle(Future.failedFuture(json.cause()));
            } else {
                handler.handle(Future.succeededFuture(new JsonObject().put("data", json.result())));
            }
        };
    }

    public void synchronizeStructure(Structure structure, Handler<AsyncResult<JsonObject>> handler) {
        log.info("Trying to sync struct " + structure.getUai());
        AddressBookSynchro addressBook;
        try {
            addressBook = new AddressBookSynchro(structure);
        } catch (NullPointerException e) {
            log.error("Empty UAI in ABook sync");
            handler.handle(Future.succeededFuture(new JsonObject()));
            return;
        }
        sqlSynchroService.updateStructureForAbSync(structure.getUai(), res -> {
            if (res.failed()) {
                handler.handle(res);
            } else {
                if (res.result().isEmpty()) {
                    log.info("Giving up sync struct " + structure.getUai());
                    handler.handle(Future.succeededFuture(new JsonObject()));
                } else {
                    log.info("Sycing struct " + structure.getUai());
                    addressBook.synchronize(structure.getUai() + Zimbra.appConfig.getAddressBookAccountName(),
                            true, handler);
                }
            }
        });
    }

    public Future<JsonObject> synchronizeStructureFuture(Structure structure) {
        Promise<JsonObject> promise = Promise.promise();
        log.info("Trying to sync struct " + structure.getUai());

        AddressBookSynchro addressBook;
        try {
            addressBook = new AddressBookSynchro(structure);
        } catch (NullPointerException e) {
            log.error("Empty UAI in ABook sync");
            promise.complete(new JsonObject());
            return promise.future();
        }

        sqlSynchroService.updateStructureForAbSync(structure.getUai(), res -> {
            if (res.failed()) {
                promise.fail(res.cause());
            } else {
                if (res.result().isEmpty()) {
                    log.info("Giving up sync struct " + structure.getUai());
                    promise.complete(new JsonObject());
                } else {
                    log.info("Syncing struct " + structure.getUai());
                    addressBook.synchronize(structure.getUai() + Zimbra.appConfig.getAddressBookAccountName(), true, abRes -> {
                        if (abRes.succeeded()) {
                            promise.complete(abRes.result());
                        } else {
                            promise.fail(abRes.cause());
                        }
                    });
                }
            }
        });

        return promise.future();
    }

}
