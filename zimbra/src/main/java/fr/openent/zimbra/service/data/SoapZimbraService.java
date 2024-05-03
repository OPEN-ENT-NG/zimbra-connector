/*
 * Copyright (c) Région Ile-de-France, Région Nouvelle-Aquitaine, CGI, 2016.
 * This file is part of OPEN ENT NG. OPEN ENT NG is a versatile ENT Project based on the JVM and ENT Core Project.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with OPEN ENT NG is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of OPEN ENT NG, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package fr.openent.zimbra.service.data;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.helper.AsyncHelper;
import fr.openent.zimbra.helper.ConfigManager;
import fr.openent.zimbra.helper.HttpClientHelper;
import fr.openent.zimbra.helper.PreauthHelper;
import fr.openent.zimbra.model.constant.SoapConstants;
import fr.openent.zimbra.service.impl.SlackService;
import fr.openent.zimbra.service.impl.UserInfoService;
import fr.openent.zimbra.service.impl.UserService;
import fr.openent.zimbra.service.synchro.SynchroUserService;
import fr.wseduc.webutils.Either;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.*;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.cache.CacheService;
import org.entcore.common.user.UserInfos;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static fr.openent.zimbra.model.constant.SoapConstants.COOKIE_AUTH_TOKEN;
import static fr.openent.zimbra.model.constant.SoapConstants.HEADER_COOKIE;
import static fr.openent.zimbra.model.constant.ZimbraConstants.ACCT_STATUS_ACTIVE;
import static fr.openent.zimbra.model.constant.ZimbraErrors.*;

public class SoapZimbraService {

    private static final Long LIFETIME_OFFSET = (long) 3600000; // 1h

    private String preauthKey;
    private Vertx vertx;
    private static final Logger log = LoggerFactory.getLogger(SoapZimbraService.class);
    private UserService userService;
    private SynchroUserService synchroUserService;
    private HttpClient httpClient = null;

    private static Map<String, JsonObject> authedUsers;
    private static final String MAP_AUTH_TOKEN = "authToken";
    private static final String CACHE_AUTH_TOKEN_NAME = "zimbra_authToken";
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

    private CacheService cacheService;

    private CircuitBreaker breaker;

    public SoapZimbraService(Vertx vertx, CacheService cacheService, SlackService slackService, CircuitBreakerOptions cbOptions) {
        this.userService = null;
        this.synchroUserService = null;

        ConfigManager config = Zimbra.appConfig;
        String zimbraBaseUri = config.getZimbraUri();
        this.zimbraUri = zimbraBaseUri + URI_SOAP;
        this.zimbraAdminUri = config.getZimbraAdminUri();
        this.zimbraAdminAccount = config.getZimbraAdminAccount();
        this.zimbraAdminPassword = config.getZimbraAdminPassword();
        this.preauthKey = config.getPreauthKey();
        this.vertx = vertx;

        // If cache service is null, use authedUsers map
        if (cacheService != null) {
            this.cacheService = cacheService;
        } else {
            authedUsers = new HashMap<>();
        }

        this.breaker = CircuitBreaker.create("zimbra-soap-service", vertx, cbOptions);
        this.breaker.openHandler(v -> {
            String message = "Zimbra circuit breaker " + this.breaker.name() + " opened";
            slackService.sendMessage(message);
            log.info(message);
        });
        this.breaker.closeHandler(v -> {
            String message = "Closing " + this.breaker.name() + " circuit breaker";
            slackService.sendMessage(message);
            log.info(message);
        });
    }

    public void setServices(UserService us, SynchroUserService synchroUserService) {
        this.userService = us;
        this.synchroUserService = synchroUserService;
    }

    /**
     * Add generic info to Json before sending to Zimbra
     * {
     * "Header" : {
     * "context" : {                    //or "ctxt" for zimbraAdmin requests
     * "_jsns" : "urn:zimbra",     // or "urn:zimbraAdmin" for zimbraAdmin requests
     * // If params.authToken exists, send it :
     * "authToken" : params.authToken
     * // Else send empty content
     * "_content" : [{"nosession" : {}}]
     * },
     * "format" : {
     * "type" : "js"
     * }
     * },
     * "Body" : {
     * params.name : params.content
     * }
     * }
     *
     * @param params inner data to send to zimbra
     *               {
     *               "authToken" : [optionnal] user auth token if already connected,
     *               "name" : name of the zimbra soap request,
     *               "content" : data for the request,
     *               "isAuthRequest" : [optional] boolean indicating if it is an authRequest, defaults to false
     *               "isAdmin" : boolean indicating if admin auth must be used
     *               }
     * @return Complete Json to send
     */
    private JsonObject prepareJsonRequest(JsonObject params) {

        JsonObject context = new JsonObject()
                .put("_jsns", SoapConstants.NAMESPACE_ZIMBRA);
        if (params.getBoolean(PARAM_IS_AUTH, false) || !params.containsKey(PARAM_AUTH_TOKEN)) {

            context.put("_content", new JsonArray().add(new JsonObject().put("nosession", new JsonObject())));

        } else {
            context.put(PARAM_AUTH_TOKEN, params.getString(PARAM_AUTH_TOKEN));
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
     * Asynchronously processes a Zimbra API response and returns a future containing the result.
     * The method parses the HTTP response body to a JSON object, handling both successful and error responses.
     * A successful response is marked with {@code IS_SUCCESSFUL: true}. Errors are logged and the promise is failed accordingly.
     *
     * @param httpResponse The received HTTP response from the Zimbra API.
     * @return A Future that completes with the parsed JSON object or fails in case of errors.
     */
    private Future<JsonObject> zimbraRequestFuture(HttpClientResponse httpResponse) {
        Promise<JsonObject> promise = Promise.promise();

        httpResponse.bodyHandler(body -> {
            JsonObject result;
            try {
                result = body.toJsonObject();
            } catch (DecodeException e) {
                log.debug("Zimbra response details : " + body);
                String messageToFormat = "[Zimbra@%s::zimbraRequestHandler] An error occurred while processing zimbra response: %s, status: %s";
                log.error(String.format(messageToFormat, this.getClass().getSimpleName(), e.getMessage(), httpResponse.statusMessage()));
                return;
            }
            if (httpResponse.statusCode() == 200){
                promise.complete(result.put(IS_SUCCESSFUL, true));
            } else {
                promise.complete(extractErrorDetails(result).put(IS_SUCCESSFUL, false));
            }
        });
        httpResponse.exceptionHandler(err -> {
            String messageToFormat = "[Zimbra@%s::zimbraRequestHandler] An error has occurred during request: %s";
            log.error(String.format(messageToFormat, this.getClass().getSimpleName(), err));
            promise.fail(err.getMessage());
        });

        return promise.future();
    }

    private JsonObject extractErrorDetails(JsonObject result) {
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
            return errorJson;
        } catch (Exception e) {
            String messageToFormat = "[Zimbra@%s::extractErrorDetails] An error has occurred during request parsing: %s";
            log.error(String.format(messageToFormat, this.getClass().getSimpleName(), e));
            throw e;
        }
    }

    /**
     * Call zimbra SOAP API
     *
     * @param params  inner data to send to zimbra
     *                {
     *                "authToken" : [optional] user auth token if already connected,
     *                "name" : name of the zimbra soap request,
     *                "content" : data for the request
     *                "isAuthRequest" : boolean indicating if it is an authRequest,
     *                "isAdmin" : boolean indicating if admin auth must be used
     *                }
     * @param handler handler to process request result
     */
    private void callSoapAPI(JsonObject params, String userId, String userAddress,
                             Handler<Either<String, JsonObject>> handler) {
        breaker.<JsonObject>execute(promise -> {
            if (httpClient == null) {
                httpClient = HttpClientHelper.createHttpClient(vertx);
            }
            String finalUrl = params.getBoolean(PARAM_ISADMIN) ? zimbraAdminUri : zimbraUri;

            RequestOptions requestOptions = new RequestOptions()
                    .setAbsoluteURI(finalUrl)
                    .setMethod(HttpMethod.POST)
                    .setHeaders(new HeadersMultiMap());

            if (params.getBoolean(PARAM_IS_AUTH, true) && params.containsKey(PARAM_AUTH_TOKEN)) {
                requestOptions.putHeader(HEADER_COOKIE, COOKIE_AUTH_TOKEN + "=" + params.getString(PARAM_AUTH_TOKEN));
            }

            JsonObject jsonRequest = prepareJsonRequest(params);


            httpClient.request(requestOptions)
                    .flatMap(req -> {
                        req.setChunked(true);
                        return req.send(jsonRequest.encode());
                    })
                    .compose(this::zimbraRequestFuture)
                    .onSuccess(promise::complete)
                    .onFailure(err -> {
                        log.error("Error on request: " + finalUrl + " body: " + jsonRequest.encode(), err);
                        JsonObject errorJsonFault = new JsonObject();
                        errorJsonFault.put(ERROR_MESSAGE, "Error on request: " + err.getMessage());
                        errorJsonFault.put(ERROR_CODE, ERROR_EXCEPTIONINREQ);
                        promise.fail(errorJsonFault.toString());
                    });
        }).onComplete(evt -> {
            if (evt.failed()) {
                log.error("Zimbra Soap API call failed " + evt.cause().getMessage());
                JsonObject errorJsonFault = new JsonObject();
                errorJsonFault.put(ERROR_MESSAGE, evt.cause().getMessage());
                errorJsonFault.put(ERROR_CODE, ERROR_CIRCUITBREAKER);
                handler.handle(new Either.Left<>(errorJsonFault.toString()));
            } else {
                JsonObject result = evt.result();
                if (result.getBoolean(IS_SUCCESSFUL)) {
                    handler.handle(new Either.Right<>(evt.result()));
                } else {
                    handleSoapError(result, params, userId, userAddress, AsyncHelper.getPromiseFromEither(handler));
                }
            }
        });
    }

    /**
     * Call zimbra SOAP API with regular user infos
     * If user has up to date authentication in "authedUsers" use it
     * Else authenticate user beforehand
     *
     * @param params  inner data to send to zimbra
     *                {
     *                "name" : name of the zimbra soap request,
     *                "content" : data for the request
     *                }
     * @param user    User connected
     * @param handler process result
     */
    public void callUserSoapAPI(JsonObject params, UserInfos user, Handler<Either<String, JsonObject>> handler) {
        String userId = user.getUserId();
        String userAddress = userId + "@" + Zimbra.domain;
        params.put(PARAM_ISADMIN, false);
        callSoapWithAuth(params, userId, userAddress, handler);
    }


    public void callUserSoapAPI(JsonObject params, String userId, Handler<AsyncResult<JsonObject>> handler) {
        String userAddress = userId + "@" + Zimbra.domain;
        params.put(PARAM_ISADMIN, false);
        callSoapWithAuth(params, userId, userAddress, AsyncHelper.getJsonObjectEitherHandler(handler));
    }

    /**
     * Call zimbra SOAP API with admin level
     * Use zimbra admin account
     * If admin has up to date authentication in "authedUsers" use it
     * Else authenticate as admin beforehand
     *
     * @param params  inner data to send to zimbra
     *                {
     *                "name" : name of the zimbra soap request,
     *                "content" : data for the request
     *                }
     * @param handler process result
     */
    public void callAdminSoapAPI(JsonObject params, Handler<Either<String, JsonObject>> handler) {
        params.put(PARAM_ISADMIN, true);
        callSoapWithAuth(params, zimbraAdminAccount, zimbraAdminAccount, handler);
    }

    /**
     * Call zimbra SOAP API with user infos
     * If user has up to date authentication in "authedUsers" use it
     * Else authenticate user beforehand
     * Same for admin authentication
     *
     * @param params      inner data to send to zimbra
     *                    {
     *                    "name" : name of the zimbra soap request,
     *                    "content" : data for the request,
     *                    "isAdmin" : must the request be made as admin ?
     *                    }
     * @param userId      User id
     * @param userAddress User mail address
     * @param handler     process result
     */
    private void callSoapWithAuth(JsonObject params, String userId, String userAddress,
                                  Handler<Either<String, JsonObject>> handler) {
        getAuthToken(userId, userAddress, params.getBoolean(PARAM_ISADMIN), authResult -> {
            if (authResult.isLeft()) {
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
     *
     * @param callResult  Error JsonObject
     * @param params      Initial soap call
     * @param userId      User Id
     * @param userAddress User Zimbra Address
     * @param promise     final promise
     */
    private void handleSoapError(JsonObject callResult, JsonObject params, String userId, String userAddress,
                                 Promise<JsonObject> promise) {
        String callResultStr = callResult.toString();
        Handler<Either<String, JsonObject>> soapApiHandler = AsyncHelper.getEitherFromPromise(promise);

        try {
            switch (callResult.getString(ERROR_CODE, "")) {
                case ERROR_AUTHFAILED:
                    userService.getUserAccount(userAddress, event -> {
                        if (event.failed()) {
                            JsonObject newCallResult = new JsonObject(event.cause().getMessage());
                            if (ERROR_NOSUCHACCOUNT.equals(newCallResult.getString(ERROR_CODE, ""))) {
                                synchroUserService.exportUser(userId, result -> {
                                    if (result.failed()) {
                                        promise.fail(result.cause().getMessage());
                                    } else {
                                        callSoapAPI(params, userId, userAddress, soapApiHandler);
                                    }
                                });
                            } else {
                                promise.handle(event);
                            }
                        } else {
                            String accountStatus = event.result().getString(UserInfoService.STATUS);
                            if (!ACCT_STATUS_ACTIVE.equals(accountStatus)) {
                                promise.fail("Account " + userAddress + " not active : " + accountStatus);
                                log.warn("Account " + userAddress + " not active : " + accountStatus);
                            } else {
                                promise.fail("Auth failed for " + userAddress + " with active account " + callResultStr);
                                log.error("Auth failed for " + userAddress + " with active account " + callResultStr);
                            }
                        }
                    });
                    break;
                case ERROR_AUTHEXPIRED:
                case ERROR_AUTHREQUIRED:
                    Handler<Either<String, JsonObject>> authCheckHandler = event -> {
                        if (event.isLeft()) {
                            promise.fail(event.left().getValue());
                        } else {
                            getCachedUserToken(userId, evt -> {
                                if (evt.failed()) {
                                    promise.fail(evt.cause());
                                } else {
                                    params.put(PARAM_AUTH_TOKEN, evt.result().getString(MAP_AUTH_TOKEN));
                                    callSoapAPI(params, userId, userAddress, soapApiHandler);
                                }
                            });
                        }
                    };
                    if (params.getBoolean(PARAM_ISADMIN)) {
                        adminAuth(userId, userAddress, authCheckHandler);
                    } else {
                        auth(userId, userAddress, authCheckHandler);
                    }
                    break;
                default:
                    promise.fail(callResultStr);
            }
        } catch (Exception e) {
            promise.fail(callResultStr);
        }
    }

    /**
     * Authenticate regular user. Send Json through Zimbra API
     * {
     * "name" : "AuthRequest",
     * "content" : {
     * "_jsns" : "urn:zimbraAccount",
     * "account" : {
     * "by" : "name",
     * "_content" : preauthInfos.id // email address for the account
     * },
     * "preauth" : {
     * "timestamp" : preauthInfos.timestamp, // exact timestamp used for preauth
     * "_content" : preauthInfos.preauthkey // computed preauth key
     * }
     * }
     * }
     *
     * @param userId      id of user
     * @param userAddress address of user
     * @param handler     handler after authentication
     */
    private void auth(String userId, String userAddress, Handler<Either<String, JsonObject>> handler) {
        JsonObject preauthInfos = PreauthHelper.generatePreauth(userAddress, preauthKey);
        if (preauthInfos == null) {
            handler.handle(new Either.Left<>("Error when computing preauth key"));
        } else {

            JsonObject authContent = new JsonObject();
            authContent.put("by", Field.NAME);
            authContent.put("_content", preauthInfos.getString(Field.ID));

            JsonObject preauth = new JsonObject();
            preauth.put("timestamp", preauthInfos.getString("timestamp"));
            preauth.put("_content", preauthInfos.getString("preauthkey"));

            JsonObject body = new JsonObject();
            body.put("_jsns", SoapConstants.NAMESPACE_ACCOUNT);
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
     * "name" : "AuthRequest",
     * "content" : {
     * "_jsns" : "urn:zimbraAdmin",
     * "authToken" : [
     * {
     * "_content" : authInfo.authToken // auth token from regular auth
     * }
     * ]
     * }
     * }
     *
     * @param userId      id of user
     * @param userAddress Admin user address
     * @param handler     handler after authentication
     */
    private void adminAuth(String userId, String userAddress,
                           Handler<Either<String, JsonObject>> handler) {

        JsonObject authContent = new JsonObject();
        authContent.put("by", Field.NAME);
        authContent.put("_content", userId);

        JsonObject body = new JsonObject();
        body.put("_jsns", SoapConstants.NAMESPACE_ADMIN);
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
     * "Header":{
     * "context":{
     * "change":{"token":4549},
     * "_jsns":"urn:zimbra"
     * }
     * },
     * "Body":{
     * "AuthResponse":{
     * "authToken":[{"_content":"authtokenvalue"}],
     * "lifetime":172799992,
     * "skin":[{"_content":"harmony"}],
     * "_jsns":"urn:zimbraAccount"
     * }²
     * },
     * "_jsns":"urn:zimbraSoap"
     * }
     *
     * @param userId      Id of authenticated user
     * @param userAddress Email address of authenticated user
     * @param isAdmin     was it regular or admin authentication
     * @param handler     final request handler
     * @return auth handler
     */
    private Handler<Either<String, JsonObject>> authHandler(String userId, String userAddress, boolean isAdmin,
                                                            final Handler<Either<String, JsonObject>> handler) {
        return response -> {
            if (response.isLeft()) {
                handler.handle(new Either.Left<>(response.left().getValue()));
            } else {
                JsonObject respValue = response.right().getValue();
                try {
                    String authToken = respValue
                            .getJsonObject("Body").getJsonObject("AuthResponse")
                            .getJsonArray(PARAM_AUTH_TOKEN).getJsonObject(0)
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

                    cacheUserToken(userId, authUserData, evt -> {
                        if (evt.failed()) {
                            log.error("Failed to cache auth user data : " + authUserData.encodePrettily(), evt.cause());
                        }
                    });

                    handler.handle(new Either.Right<>(authUserData));
                } catch (NullPointerException e) {
                    log.warn("Error when reading auth response", e);
                    handler.handle(new Either.Left<>("Error when reading auth response"));
                }
            }
        };
    }

    private void cacheUserToken(String userId, JsonObject authToken, Handler<AsyncResult<Void>> handler) {
        if (cacheService != null) {
            UserInfos user = new UserInfos();
            user.setUserId(userId);
            cacheService.upsertForUser(user, CACHE_AUTH_TOKEN_NAME, authToken.encode(), LIFETIME_OFFSET.intValue(),
                    evt -> handler.handle(evt.failed() ? Future.failedFuture(evt.cause()) : Future.succeededFuture()));
        } else {
            authedUsers.put(userId, authToken);
            handler.handle(Future.succeededFuture());
        }
    }

    private void getCachedUserToken(String userId, Handler<AsyncResult<JsonObject>> handler) {
        if (cacheService != null) {
            UserInfos user = new UserInfos();
            user.setUserId(userId);
            cacheService.getForUser(user, CACHE_AUTH_TOKEN_NAME, res -> {
                if (res.failed()) log.error("Failed to retrieve auth token for user " + userId, res.cause());
                if (res.failed() || !res.result().isPresent())
                    handler.handle(Future.failedFuture(res.failed() ? res.cause() : null));
                else handler.handle(Future.succeededFuture(new JsonObject(res.result().get())));
            });
        } else {
            if (!authedUsers.containsKey(userId)) handler.handle(Future.failedFuture("Auth token not found"));
            else {
                JsonObject authToken = authedUsers.get(userId);
                handler.handle(System.currentTimeMillis() < authToken.getLong(MAP_LIFETIME) ? Future.succeededFuture(authToken) : Future.failedFuture("Auth token not found"));
            }
        }
    }

    private void authentication(String userId, String userAddress, boolean isAdmin, Handler<
            Either<String, JsonObject>> handler) {
        if (isAdmin) {
            // Admin authentication
            adminAuth(userId, userAddress, handler);
            return;
        }

        // Classic user authentication
        auth(userId, userAddress, handler);
    }

    /**
     * Get authToken for a user
     * If already in map, return existing authInfo
     * Else, auth from Zimbra
     *
     * @param userId      User Id
     * @param userAddress User Zimbra address
     * @param isAdmin     Need AdminAuthToken ?
     * @param handler     result handler
     */
    private void getAuthToken(String userId, String userAddress, boolean isAdmin,
                              Handler<Either<String, JsonObject>> handler) {
        getCachedUserToken(userId, evt -> {
            if (evt.failed()) {
                log.info("Token not found for user " + userId);
                authentication(userId, userAddress, isAdmin, handler);
            } else {
                JsonObject authToken = evt.result();
                if (isAdmin && !authToken.getBoolean(MAP_ADMIN)) {
                    authentication(userId, userAddress, true, handler);
                } else {
                    handler.handle(new Either.Right<>(authToken));
                }
            }
        });
    }

    /**
     * Get auth token for connected User
     *
     * @param user    User infos
     * @param handler result handler
     */
    public void getUserAuthToken(UserInfos user, Handler<Either<String, JsonObject>> handler) {
        String userId = user.getUserId();
        String userAddress = userId + "@" + Zimbra.domain;
        getAuthToken(userId, userAddress, false, handler);
    }

    public Future<String> getUserAuthToken(UserInfos user) {
        Promise<String> promise = Promise.promise();

        getUserAuthToken(user, authTokenResponse -> {
            if (authTokenResponse.isLeft()) {
                log.error(String.format("Zimbra@getUserAuthToken : error with user token: %s", authTokenResponse.left().getValue()));
                promise.fail(authTokenResponse.left().getValue());
            } else {
                promise.complete(authTokenResponse.right().getValue().getString(Field.AUTH_TOKEN));
            }
        });

        return promise.future();
    }

}
