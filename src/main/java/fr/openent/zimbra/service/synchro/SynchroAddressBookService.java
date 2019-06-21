package fr.openent.zimbra.service.synchro;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.helper.AsyncHelper;
import fr.openent.zimbra.model.soap.model.SoapAccount;
import fr.openent.zimbra.model.synchro.addressbook.AddressBookSynchro;
import fr.openent.zimbra.service.data.SqlSynchroService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;

public class SynchroAddressBookService {

    private SqlSynchroService sqlSynchroService;
    private static Logger log = LoggerFactory.getLogger(SynchroAddressBookService.class);

    private boolean synchroStarted = false;

    public SynchroAddressBookService(SqlSynchroService sqlSynchroService) {
        this.sqlSynchroService = sqlSynchroService;
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
