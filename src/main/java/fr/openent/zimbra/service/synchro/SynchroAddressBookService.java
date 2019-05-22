package fr.openent.zimbra.service.synchro;

import fr.openent.zimbra.helper.AsyncHelper;
import fr.openent.zimbra.model.synchro.addressbook.AddressBookSynchro;
import fr.openent.zimbra.model.synchro.addressbook.DefaultAddressBookSynchroImpl;
import fr.openent.zimbra.model.synchro.addressbook.AddressBookSynchroZimbra;
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

        Future<List<String>> structureListFetched = Future.future();
        sqlSynchroService.getDeployedStructures(structureListFetched.completer());
        structureListFetched.compose( structureList -> {
            AsyncHelper.processListSynchronously(structureList, (structure, hand) -> {
                log.info("Synchronizing addressbook for structure "+ structure);
                synchronizeStructure(structure, v -> hand.handle(Future.succeededFuture(structure)));
            },
            finalFuture.completer());
        }, finalFuture);

    }

    private Handler<AsyncResult<String>> getFinalSyncHandler(Handler<AsyncResult<JsonObject>> handler) {
        return json -> {
            // todo final addr book sync handler
            handler.handle(Future.succeededFuture(new JsonObject()));
        };
    }

    private void synchronizeStructure(String structureUAI, Handler<AsyncResult<JsonObject>> handler) {
        AddressBookSynchro addressBook;
        AddressBookSynchro addressBookZimbra;
        try {
            addressBook = new DefaultAddressBookSynchroImpl(structureUAI);
            addressBookZimbra = new AddressBookSynchroZimbra(structureUAI);
        } catch (NullPointerException e) {
            // todo handle and log error
            handler.handle(Future.succeededFuture(new JsonObject()));
            return;
        }
        addressBook.load(vNeo -> {
            addressBookZimbra.load(vZimbra -> {
                handler.handle(Future.succeededFuture(new JsonObject()));
            });
        });
    }
}
