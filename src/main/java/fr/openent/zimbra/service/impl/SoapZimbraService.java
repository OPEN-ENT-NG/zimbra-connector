package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.helper.HttpClientHelper;
import fr.openent.zimbra.helper.PreauthHelper;
import fr.openent.zimbra.helper.ZimbraConstants;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.http.Renders;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.ProxyOptions;
import org.entcore.common.user.UserInfos;

import java.util.HashMap;
import java.util.Map;

public class SoapZimbraService {

    private static final Long LIFETIME_OFFSET = (long)3600000; // 1h

    private String preauthKey;
    private Vertx vertx;
    private static final Logger log = LoggerFactory.getLogger(Renders.class);
    private UserService userService;
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

    private String zimbraUri;
    private String zimbraAdminUri;

    private String zimbraAdminAccount;
    private String zimbraAdminPassword;

    public SoapZimbraService(Vertx vertx, JsonObject config) {
        this.userService = null;
        this.zimbraUri = config.getString("zimbra-uri", "");
        this.zimbraAdminUri = config.getString("zimbra-admin-uri", "");
        this.zimbraAdminAccount = config.getString("admin-account","");
        this.zimbraAdminPassword = config.getString("admin-password","");
        this.preauthKey = config.getString("preauth-key","");
        this.vertx = vertx;

        if(zimbraUri.isEmpty() || zimbraAdminAccount.isEmpty() || zimbraAdminUri.isEmpty()
                || zimbraAdminPassword.isEmpty() || preauthKey.isEmpty()) {
            log.fatal("Zimbra : Missing configuration in conf.properties");
        }
    }

    public void setUserService(UserService us) {
        this.userService = us;
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
                .put("_jsns", ZimbraConstants.NAMESPACE_ZIMBRA);
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
     * @param handler final response handler
     * @return default handler
     */
    private Handler<HttpClientResponse> zimbraRequestHandler(final Handler<Either<String,JsonObject>> handler) {
        return response -> {
            if(response.statusCode() == 200) {
                response.bodyHandler( body -> {
                    JsonObject result = body.toJsonObject();
                    handler.handle(new Either.Right<>(result));
                });
            } else {
                handler.handle(new Either.Left<>(response.statusMessage()));
            }
        };
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
    private void callSoapAPI(JsonObject params, Handler<Either<String,JsonObject>> handler) {
        if(httpClient == null) {
            httpClient = HttpClientHelper.createHttpClient(vertx);
        }
        String finalUrl = params.getBoolean(PARAM_ISADMIN) ? zimbraAdminUri : zimbraUri;
        HttpClientRequest request;
        Handler<HttpClientResponse> handlerRequest = zimbraRequestHandler(handler);
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
        String userAddress = userService.getUserZimbraAddress(user);
        params.put(PARAM_ISADMIN, false);
        callUserSoapAPI(params, userId, userAddress, handler);
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
    void callAdminSoapAPI(JsonObject params, Handler<Either<String,JsonObject>> handler) {
        params.put(PARAM_ISADMIN, true);
        callUserSoapAPI(params, zimbraAdminAccount, zimbraAdminAccount, handler);
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
    private void callUserSoapAPI(JsonObject params, String userId, String userAddress,
                                 Handler<Either<String,JsonObject>> handler) {
        boolean authed = false;
        boolean adminAuthed = false;
        JsonObject authInfo = new JsonObject();
        if( authedUsers.containsKey(userId) ) {
            authInfo = authedUsers.get(userId);
            Long timestamp = System.currentTimeMillis();
            if(timestamp < authInfo.getLong(MAP_LIFETIME)) {
                String authToken = authInfo.getString(MAP_AUTH_TOKEN);
                params.put(PARAM_AUTH_TOKEN, authToken);
                authed = true;
                if(authInfo.getBoolean(MAP_ADMIN)) {
                    adminAuthed = true;
                }
            }
        }
        if(params.getBoolean(PARAM_ISADMIN)) {
            if(adminAuthed) {
                callSoapAPI(params, handler);
            } else {
                adminAuth(userId, userAddress, authInfo, response -> {
                    if(response.isLeft()) {
                        handler.handle(response);
                    } else {
                        callUserSoapAPI(params, userId, userAddress, handler);
                    }
                });
            }
        } else  {
            if(authed) {
                callSoapAPI(params, handler);
            } else {
                auth(userId, userAddress, response -> {
                    if(response.isLeft()) {
                        handler.handle(response);
                    } else {
                        callUserSoapAPI(params, userId, userAddress, handler);
                    }
                });
            }
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
    public void auth(String userId, String userAddress, Handler<Either<String, JsonObject>> handler) {
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
            body.put("_jsns", ZimbraConstants.NAMESPACE_ACCOUNT);
            body.put("account", authContent);
            body.put("preauth", preauth);

            JsonObject params = new JsonObject()
                    .put(PARAM_NAME, "AuthRequest")
                    .put(PARAM_CONTENT, body)
                    .put(PARAM_ISADMIN, false);

            callSoapAPI(params, authHandler(userId, userAddress, false, handler));
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
     * @param authInfo user auth info from map
     * @param handler handler after authentication
     */
    private void adminAuth(String userId, String userAddress, JsonObject authInfo,
                           Handler<Either<String, JsonObject>> handler) {

        JsonObject authContent = new JsonObject();
        authContent.put("by", "name");
        authContent.put("_content", userId);

        JsonObject body = new JsonObject();
        body.put("_jsns", ZimbraConstants.NAMESPACE_ADMIN);
        body.put("account", authContent);
        body.put("password", new JsonObject()
                .put("_content", zimbraAdminPassword));

        JsonObject params = new JsonObject()
                .put(PARAM_NAME, "AuthRequest")
                .put(PARAM_CONTENT, body)
                .put(PARAM_ISADMIN, true);

        callSoapAPI(params, authHandler(userId, userAddress, true, handler));
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
}
