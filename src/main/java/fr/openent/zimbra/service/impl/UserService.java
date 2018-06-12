package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.helper.ZimbraConstants;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.user.UserInfos;

public class UserService {

    private SoapZimbraService soapService;
    private SqlZimbraService sqlService;
    private static Logger log = LoggerFactory.getLogger(UserService.class);

    public UserService(SoapZimbraService soapService, SqlZimbraService sqlService) {
        this.soapService = soapService;
        this.sqlService = sqlService;
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
    private void getUserInfo(UserInfos user,
                             Handler<Either<String, JsonObject>> handler) {
        JsonObject getInfoRequest = new JsonObject()
                .put("name", "GetInfoRequest")
                .put("content", new JsonObject()
                        .put("_jsns", ZimbraConstants.NAMESPACE_ACCOUNT));

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
    void getAliases(String account, Handler<Either<String, JsonObject>> handler) {

        getUserAccount(account, response -> {
            if(response.isLeft()) {
                handler.handle(response);
            } else {
                processGetAliases(response.right().getValue(), handler);
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
     * Get account info from specified user from Zimbra
     * @param account Zimbra account name or alias
     * @param handler result handler
     */
    private void getUserAccount(String account,
                             Handler<Either<String, JsonObject>> handler) {

        JsonObject acct = new JsonObject()
                .put("by", "name")
                .put("_content", account);

        JsonObject getInfoRequest = new JsonObject()
                .put("name", "GetAccountRequest")
                .put("content", new JsonObject()
                        .put("_jsns", ZimbraConstants.NAMESPACE_ADMIN)
                        .put("account", acct));

        soapService.callAdminSoapAPI(getInfoRequest, response -> {
            if(response.isLeft()) {
                handler.handle(response);
            } else {
                processGetAccountInfo(response.right().getValue(), handler);
            }
        });
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
    private void processGetAccountInfo(JsonObject jsonResponse,
                                    Handler<Either<String, JsonObject>> handler) {

        JsonObject getInfoResp = jsonResponse.getJsonObject("Body")
                .getJsonObject("GetAccountResponse");
        JsonObject frontData = new JsonObject();

        UserInfoService.processAccountInfo(getInfoResp, frontData);

        sqlService.updateUsers(new JsonArray().add(frontData.getJsonObject(UserInfoService.ALIAS)),
                sqlResponse -> {
                    if(sqlResponse.isLeft()) {
                        log.error("Error when updating Zimbra users : " + sqlResponse.left().getValue());
                    }
                });

        handler.handle(new Either.Right<>(frontData));
    }



}
