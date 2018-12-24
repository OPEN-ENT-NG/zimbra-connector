package fr.openent.zimbra.model.synchro;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.helper.AsyncHelper;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.model.Group;
import fr.openent.zimbra.model.MailAddress;
import fr.openent.zimbra.model.constant.SoapConstants;
import fr.openent.zimbra.model.constant.ZimbraConstants;
import fr.openent.zimbra.model.soap.SoapError;
import fr.openent.zimbra.model.soap.SoapRequest;
import fr.openent.zimbra.service.synchro.SynchroGroupService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class SynchroGroup extends Group {


    private MailAddress ddlAddress;

    private SynchroGroupService synchroGroupService;


    private static Logger log = LoggerFactory.getLogger(SynchroGroup.class);

    SynchroGroup(String groupId) throws IllegalArgumentException {
        super(groupId);
        init();
    }

    private void init() {
        ddlAddress = new MailAddress(getId(), Zimbra.domain);
        ServiceManager sm = ServiceManager.getServiceManager();
        synchroGroupService = sm.getSynchroGroupService();
    }



    private void checkIfExists(Handler<AsyncResult<Boolean>> handler) {
        getZimbraInfos(result -> {
            if(result.succeeded()) {
                handler.handle(Future.succeededFuture(Boolean.TRUE));
            } else {
                String errorStr = result.cause().getMessage();
                try {
                    SoapError error = new SoapError(errorStr);
                    if(ZimbraConstants.ERROR_NOSUCHDLIST.equals(error.getCode())) {
                        handler.handle(Future.succeededFuture(Boolean.FALSE));
                    } else {
                        handler.handle(Future.failedFuture(errorStr));
                    }
                } catch (DecodeException e) {
                    log.error("Unknown error when trying to fetch group info : " + errorStr);
                    handler.handle(Future.failedFuture("Unknown Zimbra error"));
                }
            }
        });
    }

    public void synchronize(Handler<AsyncResult<JsonObject>> handler) {
        // todo finalize synchronization
        createIfNotExists(handler);
    }

    private void createIfNotExists(Handler<AsyncResult<JsonObject>> handler) {
        checkIfExists( res -> {
            if(res.succeeded() && res.result()) {
                handler.handle(Future.succeededFuture(new JsonObject()));
            } else {
                createInZimbra(handler);
            }
        });
    }

    private void updateInZimbra(Handler<AsyncResult<JsonObject>> handler) {

    }



    private void createInZimbra(Handler<AsyncResult<JsonObject>> handler) {
        synchroGroupService.exportGroup(getId(), AsyncHelper.getJsonObjectEitherHandler(handler));
    }



    private void getZimbraInfos(Handler<AsyncResult<JsonObject>> handler) {
        SoapRequest getDListRequest = SoapRequest.AdminSoapRequest(SoapConstants.GET_DISTRIBUTIONLIST_REQUEST);
        getDListRequest.setContent(new JsonObject()
                .put(ZimbraConstants.DLIST_LIMIT_MEMBERS, 1)
                .put(SoapConstants.ID_BY, ZimbraConstants.ACCT_NAME)
                .put(SoapConstants.ATTR_VALUE, ddlAddress.getRawCleanAddress()));
        getDListRequest.start(handler);
    }
}
