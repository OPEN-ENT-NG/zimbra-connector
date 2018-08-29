package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.helper.HttpClientHelper;
import fr.openent.zimbra.helper.PreauthHelper;
import fr.openent.zimbra.service.synchro.SynchroUserService;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.http.Renders;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.user.UserInfos;

import java.util.HashMap;
import java.util.Map;

import static fr.openent.zimbra.helper.ZimbraConstants.*;

public class SoapZimbraService {

    private static final Long LIFETIME_OFFSET = (long)3600000; // 1h

    private String preauthKey;
    private Vertx vertx;
    private static final Logger log = LoggerFactory.getLogger(Renders.class);
    private UserService userService;
    private SynchroUserService synchroUserService;
    private HttpClient httpClient = null;

    private static Map<String, JsonObject> authedUsers = new HashMap<>();
    private static final String MAP_AUTH_TOKEN = "authToken";
    private static final String MAP_LIFETIME = "lifetime";
    private static final String MAP_ADDRESS = "emailAddress";
    private static final String MAP_ADMIN = "isAdmin";


    private static final String PARAM_ISADMIN = "isAdmin";
    private static final String PARAM_NAME = "name";
    private static final String PARAM_CONTENT = "content";
    private static final String PARAM_AUTH_TOKEN = "authToken";
    private static final String PARAM_IS_AUTH = "isAuthRequest";

    public static final String ERROR_CODE = "code";
    public static final String ERROR_MESSAGE = "message";

    private String zimbraUri;
    private String zimbraAdminUri;
    private static final String URI_SOAP = "/service/soap";

    private String zimbraAdminAccount;
    private String zimbraAdminPassword;

    public SoapZimbraService(Vertx vertx, JsonObject config) {
        this.userService = null;
        this.synchroUserService = null;

        String zimbraBaseUri = config.getString("zimbra-uri", "");
        this.zimbraUri = zimbraBaseUri + URI_SOAP;
        this.zimbraAdminUri = config.getString("zimbra-admin-uri", "");
        this.zimbraAdminAccount = config.getString("admin-account","");
        this.zimbraAdminPassword = config.getString("admin-password","");
        this.preauthKey = config.getString("preauth-key","");
        this.vertx = vertx;

        if(zimbraUri.isEmpty() || zimbraAdminAccount.isEmpty() || zimbraAdminUri.isEmpty()
                || zimbraAdminPassword.isEmpty() || preauthKey.isEmpty() || Zimbra.domain.isEmpty()) {
            log.fatal("Zimbra : Missing configuration in conf.properties");
        }
    }

    public void setServices(UserService us, SynchroUserService synchroUserService) {
        this.userService = us;
        this.synchroUserService = synchroUserService;
    }

    /**
     * Add generic info to Json before sending to Zimbra
     * {
     *     "Header" : {
     *         "context" : {                    //or "ctxt" for zimbraAdmin requests
     *              "_jsns" : "urn:zimbra",     // or "urn:zimbraAdmin" for zimbraAdmin requests
     *              // If params.authToken exists, send it :
     *              "authToken" : params.authToken
     *              // Else send empty content
     *              "_content" : [{"nosession" : {}}]
     *         },
     *         "format" : {
     *             "type" : "js"
     *         }
     *     },
     *     "Body" : {
     *         params.name : params.content
     *     }
     * }
     * @param params inner data to send to zimbra
     * {
     *      "authToken" : [optionnal] user auth token if already connected,
     *      "name" : name of the zimbra soap request,
     *      "content" : data for the request,
     *      "isAuthRequest" : [optional] boolean indicating if it is an authRequest, defaults to false
     *      "isAdmin" : boolean indicating if admin auth must be used
     * }
     * @return Complete Json to send
     */
    private JsonObject prepareJsonRequest(JsonObject params) {

        JsonObject context = new JsonObject()
                .put("_jsns", NAMESPACE_ZIMBRA);
        if(params.getBoolean(PARAM_IS_AUTH, false) || !params.containsKey(PARAM_AUTH_TOKEN)) {

            context.put("_content", new JsonArray().add(new JsonObject().put("nosession", new JsonObject())));

        } else {
            context.put("authToken", params.getString(PARAM_AUTH_TOKEN));
        }

        JsonObject header = new JsonObject();
        header.put("context", context);

        header.put("format", new JsonObject().put("type", "js"));

        JsonObject body = new JsonObject();
        body.put(params.getString(PARAM_NAME),
                params.getJsonObject(PARAM_CONTENT));

        return new JsonObject()
                .put("Header", header)
                .put("Body", body);
    }


    /**
     * Create default response handler for zimbra api requests
     * Add error code and message if request did not succeed
     * @param handler final response handler
     * @return default handler
     */
    private Handler<HttpClientResponse> zimbraRequestHandler(JsonObject params, String userId, String userAddress,
                                                             final Handler<Either<String,JsonObject>> handler) {
        return response ->
            response.bodyHandler( body -> {
                JsonObject result = body.toJsonObject();
                if(response.statusCode() == 200) {
                    handler.handle(new Either.Right<>(result));
                } else {
                    try {
                        JsonObject errorJson = new JsonObject();
                        errorJson.put(ERROR_MESSAGE, result.getJsonObject("Body")
                                .getJsonObject("Fault")
                                .getJsonObject("Reason")
                                .getString("Text"));
                        errorJson.put(ERROR_CODE, result.getJsonObject("Body")
                                .getJsonObject("Fault")
                                .getJsonObject("Detail")
                                .getJsonObject("Error")
                                .getString("Code"));
                        handleSoapError(errorJson, params, userId, userAddress, handler);
                    } catch (Exception e) {
                        handler.handle(new Either.Left<>(response.statusMessage()));
                    }
                }
            });
    }

    /**
     * Call zimbra SOAP API
     * @param params inner data to send to zimbra
     * {
     *      "authToken" : [optional] user auth token if already connected,
     *      "name" : name of the zimbra soap request,
     *      "content" : data for the request
     *      "isAuthRequest" : boolean indicating if it is an authRequest,
     *      "isAdmin" : boolean indicating if admin auth must be used
     * }
     * @param handler handler to process request result
     */
    private void callSoapAPI(JsonObject params, String userId, String userAddress,
                             Handler<Either<String,JsonObject>> handler) {
        if(httpClient == null) {
            httpClient = HttpClientHelper.createHttpClient(vertx);
        }
        String finalUrl = params.getBoolean(PARAM_ISADMIN) ? zimbraAdminUri : zimbraUri;
        HttpClientRequest request;
        Handler<HttpClientResponse> handlerRequest = zimbraRequestHandler(params, userId, userAddress, handler);
        request = httpClient.postAbs(finalUrl, handlerRequest);
        request.setChunked(true);

        JsonObject jsonRequest = prepareJsonRequest(params);

        request.write(jsonRequest.encode());
        request.end();
    }

    /**
     * Call zimbra SOAP API with regular user infos
     * If user has up to date authentication in "authedUsers" use it
     * Else authenticate user beforehand
     * @param params inner data to send to zimbra
     *  {
     *      "name" : name of the zimbra soap request,
     *      "content" : data for the request
     *  }
     * @param user User connected
     * @param handler process result
     */
    void callUserSoapAPI(JsonObject params, UserInfos user, Handler<Either<String,JsonObject>> handler) {
        String userId = user.getUserId();
        String userAddress = userId + "@" + Zimbra.domain;
        params.put(PARAM_ISADMIN, false);
        callSoapWithAuth(params, userId, userAddress, handler);
    }

    /**
     * Call zimbra SOAP API with admin level
     * Use zimbra admin account
     * If admin has up to date authentication in "authedUsers" use it
     * Else authenticate as admin beforehand
     * @param params inner data to send to zimbra
     *  {
     *      "name" : name of the zimbra soap request,
     *      "content" : data for the request
     *  }
     * @param handler process result
     */
    public void callAdminSoapAPI(JsonObject params, Handler<Either<String,JsonObject>> handler) {
        params.put(PARAM_ISADMIN, true);
        callSoapWithAuth(params, zimbraAdminAccount, zimbraAdminAccount, handler);
    }

    /**
     * Call zimbra SOAP API with user infos
     * If user has up to date authentication in "authedUsers" use it
     * Else authenticate user beforehand
     * Same for admin authentication
     * @param params inner data to send to zimbra
     *  {
     *      "name" : name of the zimbra soap request,
     *      "content" : data for the request,
     *      "isAdmin" : must the request be made as admin ?
     *  }
     * @param userId User id
     * @param userAddress User mail address
     * @param handler process result
     */
    private void callSoapWithAuth(JsonObject params, String userId, String userAddress,
                                 Handler<Either<String,JsonObject>> handler) {
        getAuthToken(userId, userAddress, params.getBoolean(PARAM_ISADMIN), authResult -> {
            if(authResult.isLeft()) {
                handler.handle(authResult);
            } else {
                params.put(PARAM_AUTH_TOKEN, authResult.right().getValue().getString(MAP_AUTH_TOKEN));
                callSoapAPI(params, userId, userAddress, handler);
            }
        });
    }

    /**
     * Handle some soap Errors.
     * If the error can be handled, continue with initial soap call
     * Else, return the error.
     * Auth expired and required attempt one more authentification without the map.
     * Auth failed usually means account does not exists, since we use preauth, then try to create account
     * @param callResult Error JsonObject
     * @param params Initial soap call
     * @param userId User Id
     * @param userAddress User Zimbra Address
     * @param handler final handler
     */
    private void handleSoapError(JsonObject callResult, JsonObject params, String userId, String userAddress,
                                 Handler<Either<String,JsonObject>> handler) {
        String callResultStr = callResult.toString();
        try {
            switch(callResult.getString(ERROR_CODE, "")) {
                case ERROR_AUTHFAILED:
                    userService.getUserAccount(userId, event -> {
                        if(event.isLeft()) {
                            JsonObject newCallResult = new JsonObject(event.left().getValue());
                            if(ERROR_NOSUCHACCOUNT.equals(newCallResult.getString(ERROR_CODE, ""))) {
                                synchroUserService.exportUser(userId, result -> {
                                    if (result.isLeft()) {
                                        handler.handle(result);
                                    } else {
                                        callSoapAPI(params, userId, userAddress, handler);
                                    }
                                });
                            } else {
                                handler.handle(event);
                            }
                        } else {
                            String accountStatus = event.right().getValue().getString(UserInfoService.STATUS);
                            if( ! ACCT_STATUS_ACTIVE.equals(accountStatus)) {
                                handler.handle(new Either.Left<>(
                                        "Account " + userAddress + " not active : " + accountStatus));
                                log.warn("Account not active : " + accountStatus);
                            } else {
                                handler.handle(new Either.Left<>("" +
                                        "Auth failed for " + userAddress + " with active account " + callResultStr));
                                log.error("Auth failed with active account " + callResultStr);
                            }
                        }
                    });
                    break;
                case ERROR_AUTHEXPIRED:
                case ERROR_AUTHREQUIRED:
                    Handler<Either<String,JsonObject>> authCheckHandler = event -> {
                        if(event.isLeft()) {
                            handler.handle(event);
                        } else {
                            params.put(PARAM_AUTH_TOKEN, authedUsers.get(userId).getString(MAP_AUTH_TOKEN));
                            callSoapAPI(params, userId, userAddress, handler);
                        }
                    };
                    if(params.getBoolean(PARAM_ISADMIN)) {
                        adminAuth(userId, userAddress, authCheckHandler);
                    } else {
                        auth(userId, userAddress, authCheckHandler);
                    }
                    break;
                default:
                    handler.handle(new Either.Left<>(callResultStr));
            }
        } catch (Exception e) {
            handler.handle(new Either.Left<>(callResultStr));
        }
    }

    /**
     * Authenticate regular user. Send Json through Zimbra API
     * {
     *     "name" : "AuthRequest",
     *     "content" : {
     *         "_jsns" : "urn:zimbraAccount",
     *         "account" : {
     *             "by" : "name",
     *             "_content" : preauthInfos.id // email address for the account
     *         },
     *         "preauth" : {
     *             "timestamp" : preauthInfos.timestamp, // exact timestamp used for preauth
     *             "_content" : preauthInfos.preauthkey // computed preauth key
     *         }
     *     }
     * }
     * @param userId id of user
     * @param userAddress address of user
     * @param handler handler after authentication
     */
    private void auth(String userId, String userAddress, Handler<Either<String, JsonObject>> handler) {
        JsonObject preauthInfos = PreauthHelper.generatePreauth(userAddress, preauthKey);
        if(preauthInfos == null) {
            handler.handle(new Either.Left<>("Error when computing preauth key"));
        } else {

            JsonObject authContent = new JsonObject();
            authContent.put("by", "name");
            authContent.put("_content", preauthInfos.getString("id"));

            JsonObject preauth = new JsonObject();
            preauth.put("timestamp", preauthInfos.getString("timestamp"));
            preauth.put("_content", preauthInfos.getString("preauthkey"));

            JsonObject body = new JsonObject();
            body.put("_jsns", NAMESPACE_ACCOUNT);
            body.put("account", authContent);
            body.put("preauth", preauth);

            JsonObject params = new JsonObject()
                    .put(PARAM_NAME, "AuthRequest")
                    .put(PARAM_CONTENT, body)
                    .put(PARAM_ISADMIN, false);

            callSoapAPI(params, userId, userAddress, authHandler(userId, userAddress, false, handler));
        }
    }

    /**
     * Authenticate regular user. Send Json through Zimbra API
     * {
     *     "name" : "AuthRequest",
     *     "content" : {
     *         "_jsns" : "urn:zimbraAdmin",
     *         "authToken" : [
     *          {
     *             "_content" : authInfo.authToken // auth token from regular auth
     *          }
     *         ]
     *     }
     * }
     * @param userId id of user
     * @param userAddress Admin user address
     * @param handler handler after authentication
     */
    private void adminAuth(String userId, String userAddress,
                           Handler<Either<String, JsonObject>> handler) {

        JsonObject authContent = new JsonObject();
        authContent.put("by", "name");
        authContent.put("_content", userId);

        JsonObject body = new JsonObject();
        body.put("_jsns", NAMESPACE_ADMIN);
        body.put("account", authContent);
        body.put("password", new JsonObject()
                .put("_content", zimbraAdminPassword));

        JsonObject params = new JsonObject()
                .put(PARAM_NAME, "AuthRequest")
                .put(PARAM_CONTENT, body)
                .put(PARAM_ISADMIN, true);

        callSoapAPI(params, userId, userAddress, authHandler(userId, userAddress, true, handler));
    }

    /**
     * Create auth handler
     * If authentication is successful add infos to authedUsers
     * Example auth Response :
     * {
     *      "Header":{
     *          "context":{
     *              "change":{"token":4549},
     *              "_jsns":"urn:zimbra"
     *           }
     *       },
     *       "Body":{
     *          "AuthResponse":{
     *              "authToken":[{"_content":"authtokenvalue"}],
     *              "lifetime":172799992,
     *              "skin":[{"_content":"harmony"}],
     *              "_jsns":"urn:zimbraAccount"
     *          }²
     *       },
     *       "_jsns":"urn:zimbraSoap"
     * }
     * @param userId Id of authenticated user
     * @param userAddress Email address of authenticated user
     * @param isAdmin was it regular or admin authentication
     * @param handler final request handler
     * @return auth handler
     */
    private Handler<Either<String,JsonObject>> authHandler(String userId, String userAddress, boolean isAdmin,
                                                           final Handler<Either<String, JsonObject>> handler) {
        return response -> {
            if(response.isLeft()) {
                handler.handle(new Either.Left<>(response.left().getValue()));
            } else {
                JsonObject respValue = response.right().getValue();
                try  {
                    String authToken = respValue
                            .getJsonObject("Body").getJsonObject("AuthResponse")
                            .getJsonArray("authToken").getJsonObject(0)
                            .getString("_content");
                    Long lifetime = respValue
                            .getJsonObject("Body").getJsonObject("AuthResponse")
                            .getLong("lifetime");
                    lifetime = lifetime - LIFETIME_OFFSET + System.currentTimeMillis();
                    JsonObject authUserData = new JsonObject()
                            .put(MAP_AUTH_TOKEN, authToken)
                            .put(MAP_LIFETIME, lifetime)
                            .put(MAP_ADDRESS, userAddress)
                            .put(MAP_ADMIN, isAdmin);

                    authedUsers.put(userId, authUserData);
                    handler.handle(new Either.Right<>(authUserData));

                } catch(NullPointerException e) {
                    log.warn("Error when reading auth response", e);
                    handler.handle(new Either.Left<>("Error when reading auth response"));
                }
            }
        };
    }

    /**
     * Get authToken for a user
     * If already in map, return existing authInfo
     * Else, auth from Zimbra
     * @param userId User Id
     * @param userAddress User Zimbra address
     * @param isAdmin Need AdminAuthToken ?
     * @param handler result handler
     */
    private void getAuthToken(String userId, String userAddress, boolean isAdmin,
                              Handler<Either<String,JsonObject>> handler) {
        boolean authed = false;
        boolean adminAuthed = false;
        if( authedUsers.containsKey(userId) ) {
            JsonObject authInfo = authedUsers.get(userId);
            Long timestamp = System.currentTimeMillis();
            if(timestamp < authInfo.getLong(MAP_LIFETIME)) {
                authed = true;
                if(authInfo.getBoolean(MAP_ADMIN)) {
                    adminAuthed = true;
                }
            }
        }
        if((isAdmin && adminAuthed) || (!isAdmin && authed)) {
            generateAuthResponse(userId, handler);
        } else {
            if(isAdmin) {
                adminAuth(userId, userAddress, response -> {
                    if(response.isLeft()) {
                        handler.handle(response);
                    } else {
                        generateAuthResponse(userId, handler);
                    }
                });
            } else {
                auth(userId, userAddress, response -> {
                    if(response.isLeft()) {
                        handler.handle(response);
                    } else {
                        generateAuthResponse(userId, handler);
                    }
                });
            }
        }
    }

    /**
     * Return authInfos for specified user from Map data
     * @param userId User id
     * @param handler result Handler
     */
    private void generateAuthResponse(String userId, Handler<Either<String, JsonObject>> handler) {
        JsonObject authInfo = authedUsers.get(userId);
        handler.handle(new Either.Right<>(authInfo));
    }

    /**
     * Get auth token for connected User
     * @param user User infos
     * @param handler result handler
     */
    void getUserAuthToken(UserInfos user, Handler<Either<String,JsonObject>> handler) {
        String userId = user.getUserId();
        String userAddress = userId + "@" + Zimbra.domain;
        getAuthToken(userId, userAddress, false, handler);
    }


}
