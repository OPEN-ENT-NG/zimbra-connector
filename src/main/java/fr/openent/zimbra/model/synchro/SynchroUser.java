package fr.openent.zimbra.model.synchro;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.helper.JsonHelper;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.model.EntUser;
import fr.openent.zimbra.model.soap.SoapRequest;
import fr.openent.zimbra.model.ZimbraUser;
import fr.openent.zimbra.model.constant.SoapConstants;
import fr.openent.zimbra.model.constant.ZimbraConstants;
import fr.openent.zimbra.service.data.SoapZimbraService;
import fr.openent.zimbra.service.data.SqlSynchroService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;

import static fr.openent.zimbra.model.constant.SynchroConstants.*;
import static fr.openent.zimbra.model.constant.SoapConstants.*;
import static fr.openent.zimbra.model.constant.ZimbraConstants.*;


public class SynchroUser extends EntUser {

    private int idRowBdd;
    private String sync_action;
    private ZimbraUser zimbraData = null;


    private static Logger log = LoggerFactory.getLogger(SynchroUser.class);

    public SynchroUser(String userid) throws IllegalArgumentException {
        super(userid);
    }

    public void synchronize(String sync_action, Handler<AsyncResult<JsonObject>> handler) {
        synchronize(0, sync_action, handler);
    }

    public void synchronize(int idRow, String sync_action, Handler<AsyncResult<JsonObject>> handler) {
        this.idRowBdd = idRow;
        this.sync_action = sync_action;
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
        if(ACTION_CREATION.equals(sync_action)
                || ACTION_MODIFICATION.equals(sync_action)) {
            super.fetchDataFromNeo(handler);
        } else {
            handler.handle(Future.succeededFuture());
        }
    }

    private void getZimbraId(Handler<AsyncResult<String>> handler) {
        if(ACTION_CREATION.equals(sync_action)) {
            handler.handle(Future.succeededFuture(""));
        } else {
            SoapRequest getInfoRequest = SoapRequest.AdminSoapRequest(GET_ACCOUNT_INFO_REQUEST);
            getInfoRequest.setContent(new JsonObject()
                    .put(ACCT_INFO_ACCOUNT, new JsonObject()
                        .put(SoapConstants.ID_BY, ZimbraConstants.ACCT_NAME)
                        .put(SoapConstants.ATTR_VALUE, getUserStrAddress())));
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
        if(sync_action == null) {
            handler.handle(Future.failedFuture("No defined modification type for synchronisation"));
            return;
        }
        switch(sync_action) {
            case ACTION_CREATION:
                createUserIfNotExists(handler);
                syncGroups();
                break;
            case ACTION_MODIFICATION:
                updateUser(zimbraId, handler);
                syncGroups();
                break;
            case ACTION_DELETION:
                deleteUser(zimbraId, handler);
                break;
            default:
                handler.handle(Future.failedFuture("Unknown sync_action : " + sync_action));
                log.error("Unknown sync_action : " + sync_action);
        }
    }

    private void syncGroups() {
        sqlZimbraService.checkGroupsExistence(getGroups(), sqlResult -> {
            if(sqlResult.failed()) {
                log.error("Error when getting unsynced groups : " + sqlResult.cause().getMessage());
            } else {
                try {
                    List<String> unsyncedGroupIds = JsonHelper.extractValueFromJsonObjects(sqlResult.result(), "id");
                    for(String groupId : unsyncedGroupIds) {
                        SynchroGroup group = new SynchroGroup(groupId);
                        group.synchronize( v -> {
                            if(v.failed()) {
                                log.error("Group synchronisation failed for group : " + groupId
                                        + ", Error : " + v);
                            }
                        });
                    }
                } catch (IllegalArgumentException e) {
                    log.error("Error when trying to process sql groups result : " + sqlResult.result().toString());
                }
            }
        });
    }


    private void createUserIfNotExists(Handler<AsyncResult<JsonObject>> handler) {
        ZimbraUser user = new ZimbraUser(getUserMailAddress());
        user.checkIfExists(userResponse -> {
            if(userResponse.failed()) {
                handler.handle(Future.failedFuture(userResponse.cause()));
            } else {
                if(user.existsInZimbra()) {
                    updateUser(user.getZimbraID(), handler);
                } else {
                    createUser(0, handler);
                }
            }
        });
    }

    private void createUser(int increment, Handler<AsyncResult<JsonObject>> handler) {
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

    private String getZimbraIdFromGetUserInfoResponse(JsonObject getUserInfoResponse) throws Exception{

        JsonArray attrs = getUserInfoResponse
                .getJsonObject(BODY)
                .getJsonObject(GET_ACCOUNT_INFO_RESPONSE)
                .getJsonArray(ACCT_ATTRIBUTES);
        for(Object o : attrs) {
            if(!(o instanceof JsonObject)) {
                continue;
            }
            JsonObject attr = (JsonObject)o;
            if(ACCT_INFO_ZIMBRA_ID.equals(attr.getString(ACCT_ATTRIBUTES_NAME))) {
                return attr.getString(ACCT_ATTRIBUTES_CONTENT);
            }
        }
        throw new Exception("No zimbraId in attributes");
    }

    private void addAlias(String zimbraId, Handler<AsyncResult<JsonObject>> handler) {
        SoapRequest addAliasRequest = SoapRequest.AdminSoapRequest(ADD_ALIAS_REQUEST);
        addAliasRequest.setContent(new JsonObject()
                .put(ACCT_ID, zimbraId)
                .put(ACCT_ALIAS, getUserStrAddress()));
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
        ServiceManager sm = ServiceManager.getServiceManager();
        SqlSynchroService sqlSynchroService = sm.getSqlSynchroService();
        String status = STATUS_DONE;
        String logs = "";
        if(result.failed()) {
            status = STATUS_ERROR;
            logs = result.cause().getMessage();
        }
        if(idRowBdd == 0) {
            handler.handle(Future.succeededFuture(new JsonObject()));
        } else {
            sqlSynchroService.updateSynchroUser(idRowBdd, status, logs, handler);
        }
    }
}
