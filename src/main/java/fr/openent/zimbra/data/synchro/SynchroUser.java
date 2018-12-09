package fr.openent.zimbra.data.synchro;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.data.EntUser;
import fr.openent.zimbra.data.SoapRequest;
import fr.openent.zimbra.data.ZimbraUser;
import fr.openent.zimbra.helper.SoapConstants;
import fr.openent.zimbra.helper.ZimbraConstants;
import fr.openent.zimbra.service.impl.SoapZimbraService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import static fr.openent.zimbra.helper.SynchroConstants.*;
import static fr.openent.zimbra.helper.SoapConstants.*;
import static fr.openent.zimbra.helper.ZimbraConstants.*;


public class SynchroUser extends EntUser {

    private int idRowBdd;
    private String modType;
    private ZimbraUser zimbraData = null;


    private static Logger log = LoggerFactory.getLogger(SynchroUser.class);

    public SynchroUser(String userid) {
        super(userid);
    }

    public void synchronize(int idRow, String modType, Handler<AsyncResult<JsonObject>> handler) {
        this.idRowBdd = idRow;
        this.modType = modType;
        Future<JsonObject> updateDbFuture = Future.future();
        updateDbFuture.setHandler(result ->
            updateDatabase(result, handler)
        );

        Future<Void> updatedFromNeo = Future.future();
        fetchDataFromNeo(updatedFromNeo.completer());
        updatedFromNeo.compose( v -> {
            Future<String> updatedFromZimbra = Future.future();
            getZimbraId(updatedFromZimbra.completer());
            return updatedFromZimbra;
        }).compose( zimbraId ->
            updateZimbra(zimbraId, updateDbFuture.completer())
        , updateDbFuture);
    }

    @Override
    public void fetchDataFromNeo(Handler<AsyncResult<Void>> handler) {
        if(MODTYPE_CREATION.equals(modType)
                || MODTYPE_MODIFICATION.equals(modType)) {
            super.fetchDataFromNeo(handler);
        } else {
            handler.handle(Future.succeededFuture());
        }
    }

    private void getZimbraId(Handler<AsyncResult<String>> handler) {
        if(MODTYPE_CREATION.equals(modType)) {
            handler.handle(Future.succeededFuture(""));
        } else {
            SoapRequest getInfoRequest = SoapRequest.AccountSoapRequest(GET_ACCOUNT_INFO_REQUEST, getUserId());
            getInfoRequest.setContent(new JsonObject()
                    .put(ACCT_INFO_ACCOUNT, new JsonObject()
                        .put(SoapConstants.ID_BY, ZimbraConstants.ACCT_NAME)
                        .put(SoapConstants.ATTR_VALUE, getUserAddress())));
            getInfoRequest.start( result -> {
                if(result.failed()) {
                    handler.handle(Future.failedFuture(result.cause()));
                } else {
                    try {
                        String zimbraId = getZimbraIdFromGetUserInfoResponse(result.result());
                        handler.handle(Future.succeededFuture(zimbraId));
                    } catch (Exception e) {
                        handler.handle(Future.failedFuture("Error when processing getInfoRequest : " + e.getMessage()));
                    }
                }
            });
        }
    }

    private void updateZimbra(String zimbraId, Handler<AsyncResult<JsonObject>> handler) {
        // todo check groups existence
        if(modType == null) {
            handler.handle(Future.failedFuture("No defined modification type for synchronisation"));
            return;
        }
        switch(modType) {
            case MODTYPE_CREATION:
                createUser(0, handler);
                syncGroups();
                break;
            case MODTYPE_MODIFICATION:
                updateUser(zimbraId, handler);
                syncGroups();
                break;
            case MODTYPE_DELETION:
                deleteUser(zimbraId, handler);
                break;
            default:
                handler.handle(Future.failedFuture("Unknown modType : " + modType));
                log.error("Unknown modType : " + modType);
        }
    }

    private void syncGroups() {

    }

    private void createUser(int increment, Handler<AsyncResult<JsonObject>> handler) {
        // todo check user does not exists
        String login = getLogin();

        String accountName = increment > 0
                ? login + "-" + increment + "@" + Zimbra.domain
                : login + "@" + Zimbra.domain;

        SoapRequest createAccountRequest = SoapRequest.AdminSoapRequest(CREATE_ACCOUNT_REQUEST);
        createAccountRequest.setContent(
                new JsonObject()
                        .put(ACCT_NAME, accountName)
                        .put(ATTR_LIST, getSoapData()));

        createAccountRequest.start( res -> {
            if(res.succeeded()) {
                JsonObject zimbraResponse = res.result();
                try {
                    String zimbraId = getZimbraIdFromCreateUserResponse(zimbraResponse);
                    addAlias(zimbraId, handler);
                } catch (Exception e) {
                    log.error("Could not add alias to account : " + accountName);
                    handler.handle(Future.failedFuture("Could not get account id from Zimbra response " + zimbraResponse.toString()));
                }
            } else {
                try {
                    JsonObject callResult = new JsonObject(res.cause().getMessage());
                    if(callResult.getString(SoapZimbraService.ERROR_CODE,"").equals(ERROR_ACCOUNTEXISTS)) {
                        createUser(increment+1, handler);
                    } else {
                        handler.handle(res);
                    }
                } catch (Exception e) {
                    handler.handle(res);
                }
            }
        });
    }

    @SuppressWarnings("RedundantThrows")
    private String getZimbraIdFromCreateUserResponse(JsonObject createUserResponse) throws Exception{
        JsonObject account = createUserResponse
                .getJsonObject(BODY)
                .getJsonObject(CREATE_ACCOUNT_RESPONSE)
                .getJsonArray(ACCOUNT).getJsonObject(0);
        return account.getString(ACCT_ID);
    }

    @SuppressWarnings("RedundantThrows")
    private String getZimbraIdFromGetUserInfoResponse(JsonObject getUserInfoResponse) throws Exception{
        return getUserInfoResponse
                .getJsonObject(BODY)
                .getJsonObject(GET_ACCOUNT_INFO_RESPONSE)
                .getJsonObject(ACCT_INFO_ATTRIBUTES)
                .getString(ACCT_INFO_ZIMBRA_ID, "");
    }

    private void addAlias(String zimbraId, Handler<AsyncResult<JsonObject>> handler) {
        SoapRequest addAliasRequest = SoapRequest.AdminSoapRequest(ADD_ALIAS_REQUEST);
        addAliasRequest.setContent(new JsonObject()
                .put(ACCT_ID, zimbraId)
                .put(ACCT_ALIAS, getUserAddress()));
        addAliasRequest.start(handler);
    }

    private void updateUser(String zimbraId, Handler<AsyncResult<JsonObject>> handler) {
        SoapRequest modifyAccountRequest = SoapRequest.AdminSoapRequest(MODIFY_ACCOUNT_REQUEST);
        modifyAccountRequest.setContent(
                new JsonObject()
                        .put(ATTR_LIST, getSoapData())
                        .put(ACCT_ID, zimbraId));
        modifyAccountRequest.start(handler);
    }

    private void deleteUser(String zimbraId, Handler<AsyncResult<JsonObject>> handler) {
        SoapRequest modifyAccountRequest = SoapRequest.AdminSoapRequest(MODIFY_ACCOUNT_REQUEST);
        modifyAccountRequest.setContent(
                new JsonObject()
                        .put(ATTR_LIST, getDeleteSoapData())
                        .put(ACCT_ID, zimbraId));
        modifyAccountRequest.start(handler);
    }

    private void updateDatabase(AsyncResult<JsonObject> result, Handler<AsyncResult<JsonObject>> handler) {
        // TODO
        /*ServiceManager sm = ServiceManager.getServiceManager();
        SqlSynchroService sqlSynchroService = sm.getSqlSynchroService();
        sqlSynchroService.updateSynchroUser(idRowBdd, STATUS_DONE, "", handler);*/
        handler.handle(Future.succeededFuture(new JsonObject()));
    }
}
