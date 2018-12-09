package fr.openent.zimbra.service.synchro;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.data.EntUser;
import fr.openent.zimbra.data.SoapRequest;
import fr.openent.zimbra.data.synchro.SynchroUser;
import fr.openent.zimbra.helper.SoapConstants;
import fr.openent.zimbra.helper.ZimbraConstants;
import fr.openent.zimbra.service.impl.*;
import fr.wseduc.webutils.Either;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

import static fr.openent.zimbra.helper.AsyncHelper.getJsonObjectFinalFuture;
import static fr.openent.zimbra.helper.ZimbraConstants.*;
import static fr.openent.zimbra.helper.SoapConstants.*;
import static fr.openent.zimbra.service.impl.SoapZimbraService.ERROR_CODE;

public class SynchroUserService {

    private SoapZimbraService soapService;
    private UserService userService;
    private SqlZimbraService sqlService;
    private SqlSynchroService sqlSynchroService;
    private static Logger log = LoggerFactory.getLogger(SynchroUserService.class);

    public SynchroUserService(SoapZimbraService soapZimbraService, SqlZimbraService sqlService,
                              SqlSynchroService sqlSynchroService){
        this.soapService = soapZimbraService;
        this.sqlService = sqlService;
        this.sqlSynchroService = sqlSynchroService;
    }

    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    /**
     * Remove outdated user info from base
     * @param userId User ID (potentially obsolete)
     * @param userMail User Mail (potentially obsolete)
     * @param handler Final handler
     */
    public void removeUserFromBase(String userId, String userMail, Handler<Either<String,JsonObject>> handler) {
        sqlService.removeUserFrombase(userId, userMail, handler);
    }


    public void syncUserFromBase(Handler<AsyncResult<JsonObject>> handler) {
        Future<JsonObject> startFuture = Future.future();
        startFuture.setHandler(handler);


        sqlSynchroService.fetchUserToSynchronize(res -> {
            if(res.failed()) {
                handler.handle(res);
            } else {
                JsonObject bddRes = res.result();
                if(bddRes.isEmpty()) {
                    handler.handle(res);
                } else {
                    int idRow = bddRes.getInteger(SqlSynchroService.USER_IDROW);
                    String idUser = bddRes.getString(SqlSynchroService.USER_IDUSER);
                    String modType = bddRes.getString(SqlSynchroService.USER_MODTYPE);
                    SynchroUser user = new SynchroUser(idUser);
                    user.synchronize(idRow, modType, handler);
                }
            }
        });
    }

    /**
     * Export a user to Zimbra
     * Get data from Neo4j, then create user in Zimbra
     * @param userId userId
     * @param handler result handler
     */
    /*public void exportUser(String userId, Handler<Either<String, JsonObject>> handler) {

        neoService.getUserFromNeo4j(userId, neoResponse -> {
            if(neoResponse.isLeft()) {
                handler.handle(neoResponse);
            } else {
                createUserOld(userId, 0, neoResponse.right().getValue(), handler);
            }
        });
    }*/

    public void exportUser(String userId, Handler<Either<String, JsonObject>> handler) {
        try {
            EntUser user = new EntUser(userId);
            Future<JsonObject> finalFuture = getJsonObjectFinalFuture(handler);

            Future<Void> fetchDataFuture = Future.future();
            user.fetchDataFromNeo(fetchDataFuture.completer());

            fetchDataFuture.compose( v -> {
                createUser(0, user, finalFuture.completer());
            }, finalFuture);
        } catch (IllegalArgumentException e) {
            handler.handle(new Either.Left<>(e.getMessage()));
        }
    }

    /**
     * Create user in Zimbra
     *      login@domain
     * If user login already exists in Zimbra, use deduplication :
     *      login-<increment>@domain
     * @param user User
     * @param increment Increment to use (if > 0)
     * @param handler result handler
     *                if right, contains account info
     */
    private void createUser(int increment, EntUser user, Handler<AsyncResult<JsonObject>> handler) {
        String login = user.getLogin();

        String accountName = increment > 0
                ? login + "-" + increment + "@" + Zimbra.domain
                : login + "@" + Zimbra.domain;

        SoapRequest createAccountRequest = SoapRequest.AdminSoapRequest(CREATE_ACCOUNT_REQUEST);
        createAccountRequest.setContent(
                new JsonObject()
                    .put(ACCT_NAME, accountName)
                    .put(ATTR_LIST, user.getSoapData()));

        createAccountRequest.start( res -> {
            if(res.succeeded()) {
                JsonObject zimbraResponse = res.result();
                try {
                    String zimbraId = getZimbraIdFromCreateUserResponse(zimbraResponse);
                    addAlias(user, zimbraId, handler);
                } catch (Exception e) {
                    handler.handle(Future.failedFuture("Could not get account id from Zimbra response " + zimbraResponse.toString()));
                }
            } else {
                try {
                    JsonObject callResult = new JsonObject(res.cause().getMessage());
                    if(callResult.getString(SoapZimbraService.ERROR_CODE,"").equals(ERROR_ACCOUNTEXISTS)) {
                        createUser(increment+1, user, handler);
                    } else {
                        handler.handle(res);
                    }
                } catch (Exception e) {
                    handler.handle(res);
                }
            }
        });
    }

    private String getZimbraIdFromCreateUserResponse(JsonObject createUserResponse) throws Exception{
        JsonObject account = createUserResponse
                .getJsonObject(BODY)
                .getJsonObject(CREATE_ACCOUNT_RESPONSE)
                .getJsonArray(ACCOUNT).getJsonObject(0);
        return account.getString(ACCT_ID);
    }

    private void addAlias(EntUser user, String zimbraId, Handler<AsyncResult<JsonObject>> handler) {
        SoapRequest addAliasRequest = SoapRequest.AdminSoapRequest(ADD_ALIAS_REQUEST);
        addAliasRequest.setContent(new JsonObject()
                .put(ACCT_ID, zimbraId)
                .put(ACCT_ALIAS, user.getUserAddress()));
        addAliasRequest.start(handler);
    }

    /**
     * Add Manual Group to users asynchronously
     * @param groupId Group Id
     * @param groupMembersId Users Id
     * @param handler result handler, always succeeding
     */
    void addGroupToMembers(String groupId, JsonArray groupMembersId,
                           Handler<Either<String, JsonObject>> handler) {

        AtomicInteger nbRemaining = new AtomicInteger(groupMembersId.size());

        for(Object o : groupMembersId) {
            if(!(o instanceof JsonObject)) {
                log.error("addGroupToMembers : invalid group member from Neo4j");
                if(nbRemaining.decrementAndGet() == 0) {
                    handler.handle(new Either.Right<>(new JsonObject()));
                }
                continue;
            }
            String userId = ((JsonObject)o).getString("id");

            Handler<Either<String, JsonObject>> addGroupHandler = resAdd -> {
                if(resAdd.isLeft()) {
                    log.error("Error when adding group " + groupId
                            + " to user " + userId + " : " + resAdd.left().getValue());
                }
                if(nbRemaining.decrementAndGet() == 0) {
                    handler.handle(new Either.Right<>(new JsonObject()));
                }
            };

            userService.getUserAccountById(userId, result -> {
                if(result.isRight()) {
                    JsonObject accountInfo = result.right().getValue();
                    addGroup(groupId, accountInfo, addGroupHandler);
                } else {
                    JsonObject newCallResult = new JsonObject(result.left().getValue());
                    if(ERROR_NOSUCHACCOUNT.equals(newCallResult.getString(ERROR_CODE, ""))) {
                        exportUser(userId, resultCreate -> {
                            if (result.isLeft()) {
                                log.error("Error when creating account " + userId + " : "
                                        + resultCreate.left().getValue());
                                if(nbRemaining.decrementAndGet() == 0) {
                                    handler.handle(new Either.Right<>(new JsonObject()));
                                }
                            } else {
                                JsonObject accountData = resultCreate.right().getValue();
                                JsonObject accountInfo = new JsonObject();
                                UserInfoService.processAccountInfo(accountData, accountInfo);
                                addGroup(groupId, accountInfo, addGroupHandler);
                            }
                        });
                    } else {
                        log.error("Error when getting user account " + userId + " : " + result.left().getValue());
                        if(nbRemaining.decrementAndGet() == 0) {
                            handler.handle(new Either.Right<>(new JsonObject()));
                        }
                    }
                }
            });
        }
    }


    private void addGroup(String groupId, JsonObject accountInfo,
                          Handler<Either<String, JsonObject>> handler) {
        JsonArray attrs = new JsonArray().add(
                new JsonObject()
                        .put("+ou", groupId));

        JsonObject modifyAccountRequest = new JsonObject()
                .put("name", "ModifyAccountRequest")
                .put("content", new JsonObject()
                        .put("_jsns", SoapConstants.NAMESPACE_ADMIN)
                        .put("id", accountInfo.getValue(UserInfoService.ZIMBRA_ID))
                        .put("_attrs", attrs));

        soapService.callAdminSoapAPI(modifyAccountRequest, handler);
    }

}
