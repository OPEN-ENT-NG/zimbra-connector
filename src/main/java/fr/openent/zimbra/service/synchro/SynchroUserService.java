package fr.openent.zimbra.service.synchro;

import fr.openent.zimbra.model.soap.SoapRequest;
import fr.openent.zimbra.model.synchro.SynchroUser;
import fr.openent.zimbra.model.constant.SoapConstants;
import fr.openent.zimbra.model.constant.SynchroConstants;
import fr.openent.zimbra.service.data.SqlSynchroService;
import fr.openent.zimbra.service.data.SqlZimbraService;
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

import static fr.openent.zimbra.model.constant.ZimbraConstants.*;
import static fr.openent.zimbra.service.data.SoapZimbraService.ERROR_CODE;

public class SynchroUserService {

    private UserService userService;
    private SqlZimbraService sqlService;
    private SqlSynchroService sqlSynchroService;
    private static Logger log = LoggerFactory.getLogger(SynchroUserService.class);

    public SynchroUserService(SqlZimbraService sqlService,
                              SqlSynchroService sqlSynchroService){
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


    /**
     * Get the first user to synchronize from bdd (if any) and synchronize its information in Zimbra
     * @param handler synchronization result
     */
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
                    String sync_action = bddRes.getString(SqlSynchroService.USER_SYNCACTION);
                    try {
                        SynchroUser user = new SynchroUser(idUser);
                        user.synchronize(idRow, sync_action, handler);
                    } catch (IllegalArgumentException e) {
                        handler.handle(Future.failedFuture(e));
                    }
                }
            }
        });
    }

    /**
     * Export a user to Zimbra
     * Get model from Neo4j, then create user in Zimbra
     * @param userId userId
     * @param handler result handler
     */
    public void exportUser(String userId, Handler<AsyncResult<JsonObject>> handler) {
        try {
            SynchroUser user = new SynchroUser(userId);
            user.synchronize(SynchroConstants.ACTION_CREATION, handler);
        } catch (IllegalArgumentException e) {
            handler.handle(Future.failedFuture(e));
        }
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

            Handler<AsyncResult<JsonObject>> addGroupHandler = resAdd -> {
                if(resAdd.failed()) {
                    log.error("Error when adding group " + groupId
                            + " to user " + userId + " : " + resAdd.cause().getMessage());
                }
                if(nbRemaining.decrementAndGet() == 0) {
                    handler.handle(new Either.Right<>(new JsonObject()));
                }
            };

            userService.getUserAccountById(userId, result -> {
                if(result.succeeded()) {
                    JsonObject accountInfo = result.result();
                    addGroup(groupId, accountInfo, addGroupHandler);
                } else {
                    JsonObject newCallResult = new JsonObject(result.cause().getMessage());
                    if(ERROR_NOSUCHACCOUNT.equals(newCallResult.getString(ERROR_CODE, ""))) {
                        exportUser(userId, resultCreate -> {
                            if (resultCreate.failed()) {
                                log.error("Error when creating account " + userId + " : "
                                        + resultCreate.cause().getMessage());
                                if(nbRemaining.decrementAndGet() == 0) {
                                    handler.handle(new Either.Right<>(new JsonObject()));
                                }
                            } else {
                                JsonObject accountData = resultCreate.result();
                                JsonObject accountInfo = new JsonObject();
                                UserInfoService.processAccountInfo(accountData, accountInfo);
                                addGroup(groupId, accountInfo, addGroupHandler);
                            }
                        });
                    } else {
                        log.error("Error when getting user account " + userId + " : " + result.cause().getMessage());
                        if(nbRemaining.decrementAndGet() == 0) {
                            handler.handle(new Either.Right<>(new JsonObject()));
                        }
                    }
                }
            });
        }
    }


    private void addGroup(String groupId, JsonObject accountInfo,
                          Handler<AsyncResult<JsonObject>> handler) {
        JsonArray attrs = new JsonArray().add(
                new JsonObject()
                        .put(SynchroConstants.ADDGROUPID, groupId));

        SoapRequest modifyAccountRequest = SoapRequest.AdminSoapRequest(SoapConstants.MODIFY_ACCOUNT_REQUEST);
        modifyAccountRequest.setContent(new JsonObject()
                .put(ACCT_ID, accountInfo.getValue(UserInfoService.ZIMBRA_ID))
                .put(ACCT_INFO_ATTRIBUTES, attrs));
        modifyAccountRequest.start(handler);
    }

}
