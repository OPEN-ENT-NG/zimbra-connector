package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.model.soap.SoapRequest;
import fr.openent.zimbra.model.ZimbraUser;
import fr.openent.zimbra.helper.AsyncHelper;
import fr.openent.zimbra.model.constant.SoapConstants;
import fr.openent.zimbra.model.constant.ZimbraConstants;
import fr.openent.zimbra.service.data.Neo4jZimbraService;
import fr.openent.zimbra.service.data.SoapZimbraService;
import fr.openent.zimbra.service.data.SqlZimbraService;
import fr.openent.zimbra.service.synchro.SynchroUserService;
import fr.wseduc.webutils.Either;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;

import java.util.concurrent.atomic.AtomicInteger;

import static fr.openent.zimbra.service.data.Neo4jZimbraService.TYPE_GROUP;
import static fr.openent.zimbra.service.data.Neo4jZimbraService.TYPE_USER;

public class UserService {

    private SoapZimbraService soapService;
    private SqlZimbraService sqlService;
    private SynchroUserService synchroUserService;
    private Neo4jZimbraService neoService;
    private GroupService groupService;
    private static Logger log = LoggerFactory.getLogger(UserService.class);

    public UserService(SoapZimbraService soapService, SynchroUserService synchroUserService,
                       SqlZimbraService sqlService) {
        this.soapService = soapService;
        this.synchroUserService = synchroUserService;
        this.sqlService = sqlService;
        this.neoService = new Neo4jZimbraService();
        this.groupService = new GroupService(soapService, sqlService, synchroUserService);
    }

    /**
     * Get used and max quota of user from Zimbra
     * @param user User infos
     * @param handler Result handler
     */
    public void getQuota(UserInfos user,
                         Handler<Either<String, JsonObject>> handler) {

        getUserInfo(user, response -> {
            if(response.isLeft()) {
                handler.handle(response);
            } else {
                processGetQuota(response.right().getValue(), handler);
            }
        });
    }


    /**
     * Process response from Zimbra API to get Quotas details of user logged in
     * In case of success, return a Json Object :
     * {
     * 	    "storage" : "quotaUsed"
     * 	    "quota" : "quotaTotalAllowed"
     * }
     * @param jsonResponse Zimbra API Response
     * @param handler Handler result
     */
    private void processGetQuota(JsonObject jsonResponse,
                                 Handler<Either<String,JsonObject>> handler) {

        if(jsonResponse.containsKey(UserInfoService.QUOTA)) {
            handler.handle(new Either.Right<>(jsonResponse.getJsonObject(UserInfoService.QUOTA)));
        } else {
            handler.handle(new Either.Left<>("Could not get Quota from GetInfoRequest"));
        }
    }

    /**
     * Get info from connected user
     * @param user Connected user
     * @param handler result handler
     */
    void getUserInfo(UserInfos user,
                             Handler<Either<String, JsonObject>> handler) {
        JsonObject getInfoRequest = new JsonObject()
                .put("name", "GetInfoRequest")
                .put("content", new JsonObject()
                        .put("_jsns", SoapConstants.NAMESPACE_ACCOUNT));

        soapService.callUserSoapAPI(getInfoRequest, user, response -> {
            if(response.isLeft()) {
                handler.handle(response);
            } else {
                processGetUserInfo(response.right().getValue(), handler);
            }
        });
    }

    /**
     * Process response from Zimbra API to get Info of user logged in
     * Return Json :
     * {
     *     "quota" : {quota_infos}, // see UserInfoService.processQuota
     *     "alias" : {alias_infos}  // see UserInfoService.processAliases
     * }
     * @param jsonResponse Zimbra API Response
     * @param handler result handler
     */
    private void processGetUserInfo(JsonObject jsonResponse,
                                    Handler<Either<String, JsonObject>> handler) {

        JsonObject getInfoResp = jsonResponse.getJsonObject("Body")
              .getJsonObject("GetInfoResponse");
        JsonObject frontData = new JsonObject();

        UserInfoService.processQuota(getInfoResp, frontData);
        UserInfoService.processAliases(getInfoResp, frontData);
        UserInfoService.processSignaturePref(getInfoResp, frontData);

        sqlService.updateUsers(new JsonArray().add(frontData.getJsonObject(UserInfoService.ALIAS)),
                                sqlResponse -> {
            if(sqlResponse.isLeft()) {
                log.error("Error when updating Zimbra users : " + sqlResponse.left().getValue());
            }
        });

        handler.handle(new Either.Right<>(frontData));
    }

    /**
     * Get name and aliases of specified user from Zimbra
     * @param account Zimbra account name or alias
     * @param handler Result handler
     */
    public void getAliases(String account, Handler<AsyncResult<JsonObject>> handler) {

        getUserAccount(account, response -> {
            if(response.failed()) {
                handler.handle(response);
            } else {
                processGetAliases(response.result(), AsyncHelper.getJsonObjectEitherHandler(handler));
            }
        });
    }

    /**
     * Process response from Zimbra API to get alias details of specified user
     * In case of success, return a Json Object :
     * {
     * 	    "name" : "user mail address"
     * 	    "aliases" :
     * 	    [
     * 	        "alias"
     * 	    ]
     * }
     * @param jsonResponse Zimbra API Response
     * @param handler Handler result
     */
    private void processGetAliases(JsonObject jsonResponse,
                                 Handler<Either<String,JsonObject>> handler) {

        if(jsonResponse.containsKey(UserInfoService.ALIAS)) {
            handler.handle(new Either.Right<>(jsonResponse.getJsonObject(UserInfoService.ALIAS)));
        } else {
            handler.handle(new Either.Left<>("Could not get Quota from GetInfoRequest"));
        }
    }

    /**
     * Process response from Zimbra API to get email address of specified user
     * In case of success, return a String with the address
     * @param jsonResponse Zimbra API Response
     * @param handler Handler result
     */
    private void processGetAddress(JsonObject jsonResponse,
                                   Handler<Either<String,String>> handler) {

        if(jsonResponse.containsKey(UserInfoService.ALIAS)) {
            handler.handle(new Either.Right<>(jsonResponse.getJsonObject(UserInfoService.ALIAS).getString("name")));
        } else {
            handler.handle(new Either.Left<>("Could not get Quota from GetInfoRequest"));
        }
    }

    /**
     * Get account info from specified user from Zimbra
     * @param account Zimbra account name or alias
     * @param handler result handler
     */
    public void getUserAccount(String account,
                               Handler<AsyncResult<JsonObject>> handler) {

        JsonObject acct = new JsonObject()
                .put(SoapConstants.ID_BY, ZimbraConstants.ACCT_NAME)
                .put(SoapConstants.ATTR_VALUE, account);

        SoapRequest getAccountRequest = SoapRequest.AdminSoapRequest(SoapConstants.GET_ACCOUNT_REQUEST);
        getAccountRequest.setContent(new JsonObject().put(SoapConstants.ACCOUNT, acct));
        getAccountRequest.start(handler);
    }

    /**
     * Get a user account by its neo id
     * @param userId Neo4j id of the account to get
     * @param handler result handler
     */
    public void getUserAccountById(String userId,
                                   Handler<AsyncResult<JsonObject>> handler) {
        if(userId == null || userId.isEmpty()) {
            handler.handle(Future.failedFuture("Empty userId, can't get account"));
            return;
        }
        String account = userId + "@" + Zimbra.domain;
        getUserAccount(account, handler);
    }

    /**
     * Process response from Zimbra API to get Info of specified user
     * Return Json :
     * {
     *     "quota" : {quota_infos}, // see UserInfoService.processQuota
     *     "alias" : {alias_infos}  // see UserInfoService.processAliases
     * }
     * @param jsonResponse Zimbra API Responsek
     * @param handler result handler
     */
    private void processGetAccountInfo(JsonObject jsonResponse, Handler<JsonObject> handler) {

        JsonObject getInfoResp = jsonResponse.getJsonObject(SoapConstants.BODY)
                .getJsonObject(SoapConstants.GET_ACCOUNT_RESPONSE);
        JsonObject frontData = new JsonObject();

        UserInfoService.processAccountInfo(getInfoResp, frontData);

        sqlService.updateUsers(new JsonArray().add(frontData.getJsonObject(UserInfoService.ALIAS)),
                sqlResponse -> {
                    if(sqlResponse.isLeft()) {
                        log.error("Error when updating Zimbra users : " + sqlResponse.left().getValue());
                    }
                });

        handler.handle(frontData);
    }

    /**
     * Get a user adress
     * First query database
     * If not present, query Zimbra
     * If not existing in Zimbra, try to create it
     * @param userId User Id
     * @param handler result handler
     */
    private void getUserAddress(String userId, Handler<Either<String,String>> handler) {
        sqlService.getUserMailFromId(userId, result -> {
            if(result.isLeft() || result.right().getValue().isEmpty()) {
                log.debug("no user in database for id : " + userId);
                String account = userId + "@" + Zimbra.domain;
                getUserAccount(account, response -> {
                    if(response.failed()) {
                        JsonObject callResult = new JsonObject(response.cause().getMessage());
                        if(ZimbraConstants.ERROR_NOSUCHACCOUNT
                                .equals(callResult.getString(SoapZimbraService.ERROR_CODE, ""))) {
                            synchroUserService.exportUser(userId, resultSync -> {
                                if (resultSync.failed()) {
                                    handler.handle(new Either.Left<>(resultSync.cause().getMessage()));
                                } else {
                                    getUserAddress(userId, handler);
                                }
                            });
                        } else {
                            handler.handle(new Either.Left<>(response.cause().getMessage()));
                        }
                    } else {
                        processGetAccountInfo(response.result(), resInfo ->
                                processGetAddress(resInfo, handler)
                        );
                    }
                });
            } else {
                JsonArray results = result.right().getValue();
                if(results.size() > 1) {
                    log.warn("More than one address for user id : " + userId);
                }
                String mail = results.getJsonObject(0).getString(SqlZimbraService.ZIMBRA_NAME);
                handler.handle(new Either.Right<>(mail));
            }
        });
    }

    /**
     * Get addresses for a list of users or groupes
     * Return a Json Object :
     * {
     *     "userId1" : "userAddress1",
     *     "groupId1" : "groupAddress1",
     *     "userId2" : "userAddress2",
     *     ...
     * }
     * @param idList Array with the list of Ids
     * @param handler result handler
     */
    void getMailAddresses(JsonArray idList, Handler<JsonObject> handler) {
        if(idList == null || idList.isEmpty()) {
            handler.handle(new JsonObject());
            return;
        }
        neoService.getIdsType(idList, neoResult -> {
            if(neoResult.isLeft()) {
                handler.handle(new JsonObject());
                log.error("Could not get recipient ids from Neo4j");
                return;
            }
            JsonArray idListWithTypes = neoResult.right().getValue();
            final AtomicInteger processedIds = new AtomicInteger(idListWithTypes.size());
            JsonObject addressList = new JsonObject();
            for(Object o : idListWithTypes) {
                if(!(o instanceof JsonObject)) continue;
                JsonObject idInfos = (JsonObject)o;
                JsonObject elemInfos = new JsonObject();
                if(!idInfos.getString("displayName", "").isEmpty()) {
                    elemInfos.put("displayName", idInfos.getString("displayName"));
                }
                String elemId = idInfos.getString("id");
                switch (idInfos.getString("type", "")) {
                    case TYPE_USER:
                        getUserAddress(elemId, result -> {
                            if(result.isRight()) {
                                elemInfos.put("email", result.right().getValue());
                                addressList.put(elemId, elemInfos);
                            }
                            if(processedIds.decrementAndGet() == 0) {
                                handler.handle(addressList);
                            }
                        });
                        break;
                    case TYPE_GROUP:
                        groupService.getGroupAddress(elemId, result -> {
                            if(result.isRight()) {
                                elemInfos.put("email", result.right().getValue());
                                elemInfos.put("displayName",
                                        UserUtils.groupDisplayName(
                                                idInfos.getString("groupName", ""),
                                                idInfos.getString("displayName"),
                                                Zimbra.synchroLang));
                                addressList.put(elemId, elemInfos);
                            }
                            if(processedIds.decrementAndGet() == 0) {
                                handler.handle(addressList);
                            }
                        });
                        break;
                    default:
                        if(processedIds.decrementAndGet() == 0) {
                            handler.handle(addressList);
                        }
                }
            }
        });
    }

}
