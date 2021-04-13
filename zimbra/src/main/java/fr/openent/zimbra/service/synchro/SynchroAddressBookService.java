package fr.openent.zimbra.service.synchro;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.helper.AsyncContainer;
import fr.openent.zimbra.helper.AsyncHandler;
import fr.openent.zimbra.helper.AsyncHelper;
import fr.openent.zimbra.model.soap.model.SoapAccount;
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
        if(synchroStarted) {
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
     * @param userId id of user to sync
     * @param structuresList list of structures
     * @param handler result handler (no data)
     */
    public void syncUser(String userId, List<Structure> structuresList, Handler<AsyncResult<Structure>> handler) {

        Future<Structure> finalFuture = Future.future();
        finalFuture.setHandler(handler);
        Future<JsonObject> roleFetched = Future.future();

        AsyncContainer<Boolean> hasRoleContainer = new AsyncContainer<>();

        neoZimbraService.hasExternalCommunicationRole(userId, roleFetched.completer());

        AsyncContainer<AsyncHandler<Structure>> userHandlerContainer = new AsyncContainer<>();
        roleFetched.compose( neoResult -> {
            Boolean hasExternalRole = neoResult.getBoolean(HAS_EXTERNAL_ROLE, false);
            hasRoleContainer.setValue(hasExternalRole);
            AsyncHandler<Structure> userHandler = getHandlerFromCommunicationRole(userId, hasExternalRole);
            userHandlerContainer.setValue(userHandler);

            Future<SoapFolder> folderInitiated = Future.future();
            SoapFolder.getOrCreateFolderByPath(userId, Zimbra.appConfig.getSharedFolderName(), VIEW_CONTACT,
                    folderInitiated.completer());
            return folderInitiated;
        }).compose( soapFolder ->  {
            Future<JsonObject> folderEmptied = Future.future();
            if(hasRoleContainer.getValue()) {
                folderEmptied.complete(new JsonObject());
            } else {
                soapFolder.emptyFolder(userId, folderEmptied.completer());
            }
            return folderEmptied;
        }).compose( v ->
                AsyncHelper.processListSynchronously(structuresList, userHandlerContainer.getValue(),
                        finalFuture.completer()),finalFuture);
    }

    /**
     * Get the process for each structures oget shared folder from admin account
     *      * - Else : get all accessiblef the user
     * - If he has external role :  contacts from structure
     * @param userId user neo4j id
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
                    if(ressync.failed()) {
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
        String adminName = Zimbra.appConfig.getAddressBookAccountName();
        String rootFolderPath = Zimbra.appConfig.getSharedFolderName();
        String adminMail = adminName + "@" + Zimbra.appConfig.getZimbraDomain();

        Future<Structure> finalFuture = Future.future();
        finalFuture.setHandler(handler);
        AsyncContainer<SoapFolder> sharedFolderContainer = new AsyncContainer<>();
        AsyncContainer<SoapFolder> rootFolderContainer = new AsyncContainer<>();

        Future<SoapFolder> sharedFolderFetched = Future.future();
        SoapFolder.getOrCreateFolderByPath(adminName, rootFolderPath + "/" + structure.getUai(),
                VIEW_CONTACT, sharedFolderFetched.completer());
        sharedFolderFetched.compose(resFolder -> {
            sharedFolderContainer.setValue(resFolder);

            Future<SoapFolder> folderCreated = Future.future();
            SoapFolder.getOrCreateFolderByPath(userId, rootFolderPath, VIEW_CONTACT, folderCreated.completer());
            return folderCreated;
        }).compose(resRootFolder -> {
            rootFolderContainer.setValue(resRootFolder);

            SoapFolder sharedFolder = sharedFolderContainer.getValue();

            Future<JsonObject> folderShared = Future.future();
            sharedFolder.shareFolderReadonly(adminName, userId, folderShared.completer());
            return folderShared;
        }).compose( resShare -> {
            String rootFolderId = rootFolderContainer.getValue().getId();
            String sharedFolderId = sharedFolderContainer.getValue().getId();

            Future<SoapMountpoint> mountpointCreated = Future.future();
            SoapMountpoint.getOrCreateMountpoint(userId, structure.getName(), rootFolderId, VIEW_CONTACT, adminMail,
                    sharedFolderId, mountpointCreated.completer());
            return mountpointCreated;
        }).compose( res ->
                synchronizeStructure(structure, resSync ->
                        finalFuture.complete(structure)), finalFuture);
    }

    private void start(Handler<AsyncResult<JsonObject>> handler) {
        Future<String> finalFuture = Future.future();
        finalFuture.setHandler(getFinalSyncHandler(handler));

        Future<SoapAccount> abookAccountFetched = Future.future();
        SoapAccount.getUserAccount(Zimbra.appConfig.getAddressBookAccountName(), abookAccountFetched.completer());

        abookAccountFetched.compose( soapAccount -> {
                Future<List<String>> deployedStructuresFetched = Future.future();
                sqlSynchroService.getDeployedStructures(deployedStructuresFetched.completer());
                return deployedStructuresFetched;
        }).compose( structureList ->
            AsyncHelper.processListSynchronously(structureList, (uai, hand) -> {
                log.info("Synchronizing addressbook for structure "+ uai);
                Structure structure = new Structure(new JsonObject().put(Structure.UAI, uai));
                synchronizeStructure(structure, v -> hand.handle(Future.succeededFuture(uai)));
            },
            finalFuture.completer())
        , finalFuture);

    }

    private Handler<AsyncResult<String>> getFinalSyncHandler(Handler<AsyncResult<JsonObject>> handler) {
        return json -> {
            if(json.failed()) {
                handler.handle(Future.failedFuture(json.cause()));
            } else {
                handler.handle(Future.succeededFuture(new JsonObject().put("data", json.result())));
            }
        };
    }

    private void synchronizeStructure(Structure structure, Handler<AsyncResult<JsonObject>> handler) {
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
            if(res.failed()) {
                handler.handle(res);
            } else {
                if(res.result().isEmpty()) {
                    log.info("Giving up sync struct " + structure.getUai());
                    handler.handle(Future.succeededFuture(new JsonObject()));
                } else {
                    log.info("Sycing struct " + structure.getUai());
                    addressBook.synchronize(Zimbra.appConfig.getAddressBookAccountName(), true, handler);
                }
            }
        });
    }
}
