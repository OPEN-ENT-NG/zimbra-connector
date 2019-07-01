package fr.openent.zimbra.service.synchro;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.helper.AsyncContainer;
import fr.openent.zimbra.helper.AsyncHandler;
import fr.openent.zimbra.helper.AsyncHelper;
import fr.openent.zimbra.model.soap.model.SoapAccount;
import fr.openent.zimbra.model.soap.model.SoapFolder;
import fr.openent.zimbra.model.soap.model.SoapMountpoint;
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
     * @param userId id of user to sync
     * @param uaiList list of structures' uai
     * @param handler result handler (no data)
     */
    public void syncUser(String userId, List<String> uaiList, Handler<AsyncResult<String>> handler) {

        Future<String> finalFuture = Future.future();
        finalFuture.setHandler(handler);
        Future<JsonObject> roleFetched = Future.future();

        neoZimbraService.hasExternalCommunicationRole(userId, roleFetched.completer());

        AsyncContainer<AsyncHandler<String>> asyncContainer = new AsyncContainer<>();
        roleFetched.compose( neoResult -> {
            AsyncHandler<String> userHandler = getHandlerFromCommunicationRole(userId, uaiList, neoResult);
            asyncContainer.setValue(userHandler);

            Future<SoapFolder> folderInitiated = Future.future();
            SoapFolder.getOrCreateFolderByPath(userId, Zimbra.appConfig.getSharedFolderName(), VIEW_CONTACT,
                    folderInitiated.completer());
            return folderInitiated;
        }).compose( soapFolder ->  {
            Future<JsonObject> folderEmptied = Future.future();
            soapFolder.emptyFolder(userId, folderEmptied.completer());
            return folderEmptied;
        }).compose( v ->
                AsyncHelper.processListSynchronously(uaiList, asyncContainer.getValue(),
                        finalFuture.completer()),finalFuture);
    }

    private AsyncHandler<String> getHandlerFromCommunicationRole(String userId, List<String> uaiList,
                                                                 JsonObject neoResult) {
        AsyncHandler<String> handler;
        if (neoResult.getBoolean(HAS_EXTERNAL_ROLE, false)) {
            handler = (uai, handlerStructure) -> shareAddressBook(userId, uaiList.get(0), handlerStructure);
        } else {
            handler = (uai, handlerStructure) -> {
                AddressBookSynchro absync = new AddressBookSynchroVisibles(uaiList.get(0), userId);
                absync.load( res -> {
                    if(res.failed()) {
                        handlerStructure.handle(Future.failedFuture(res.cause()));
                    } else {
                        absync.sync(userId, ressync -> {
                            if(ressync.failed()) {
                                handlerStructure.handle(Future.failedFuture(res.cause()));
                            } else {
                                // fixme weird handler
                                handlerStructure.handle(Future.succeededFuture(""));
                            }
                        });
                    }
                });
            };
        }
        return handler;
    }

    private void shareAddressBook(String userId, String uai, Handler<AsyncResult<String>> handler) {
        String adminName = Zimbra.appConfig.getAddressBookAccountName();
        String rootFolderPath = Zimbra.appConfig.getSharedFolderName();
        String adminMail = adminName + "@" + Zimbra.appConfig.getZimbraDomain();

        SoapFolder.getFolderByPath(adminName, rootFolderPath, VIEW_CONTACT, 0, resFolder -> {
            if(resFolder.failed()) {
                handler.handle(Future.failedFuture(resFolder.cause()));
            } else {
                String folderId = resFolder.result().getId();

                SoapFolder.getOrCreateFolderByPath(userId, rootFolderPath, VIEW_CONTACT, resRootFolder -> {
                    if(resRootFolder.failed()) {
                        handler.handle(Future.failedFuture(resRootFolder.cause()));
                    } else {
                        String rootFolderId = resRootFolder.result().getId();

                        resFolder.result().shareReadFolder(adminName, userId, resShare -> {
                            SoapMountpoint.getOrCreateMountpoint(userId, uai, rootFolderId,  VIEW_CONTACT, adminMail,
                                    folderId, resLink -> {
                                        if (resLink.failed()) {
                                            handler.handle(Future.failedFuture(resLink.cause()));
                                        } else {
                                            // fixme weird handler
                                            handler.handle(Future.succeededFuture(""));
                                        }
                                    });
                        });
                    }
                });

            }
        } );
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
            AsyncHelper.processListSynchronously(structureList, (structure, hand) -> {
                log.info("Synchronizing addressbook for structure "+ structure);
                synchronizeStructure(structure, v -> hand.handle(Future.succeededFuture(structure)));
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

    private void synchronizeStructure(String structureUAI, Handler<AsyncResult<JsonObject>> handler) {
        AddressBookSynchro addressBook;
        try {
            addressBook = new AddressBookSynchro(structureUAI);
        } catch (NullPointerException e) {
            log.error("Empty UAI in ABook sync");
            handler.handle(Future.succeededFuture(new JsonObject()));
            return;
        }
        addressBook.load(res -> {
            if (res.failed()) {
                handler.handle(Future.failedFuture(res.cause()));
            } else {
                addressBook.sync(Zimbra.appConfig.getAddressBookAccountName(), handler);
            }
        });
    }
}
