package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.helper.PreauthHelper;
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

    private String preauthKey;
    private Vertx vertx;
    private static final Logger log = LoggerFactory.getLogger(Renders.class);
    private UserService userService;
    private HttpClient httpClient = null;
    private Map<String, JsonObject> authedUsers;

    private String zimbraUri;

    public SoapZimbraService(Vertx vertx, JsonObject config, UserService us) {
        this.userService = us;
        this.zimbraUri = config.getString("zimbra-uri", "");
        this.vertx = vertx;
        this.authedUsers = new HashMap<>();

        preauthKey = config.getString("preauth-key","");
        if( preauthKey.isEmpty() ) {
            log.fatal("Zimbra : No preauth key in conf");
        }
    }

    /**
     * Add generic info to Json before sending to Zimbra
     * {
     *     "Header" : {
     *         "context" : {
     *              "_jsns" : "urn:zimbra",
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
     *      "content" : data for the request
     * }
     * @return Complete Json to send
     */
    private JsonObject prepareJsonRequest(JsonObject params) {

        JsonObject context = new JsonObject().put("_jsns", "urn:zimbra");
        if(params.containsKey("authToken")) {
            context.put("authToken", params.getString("authToken"));
        } else {
            context.put("_content", new JsonArray().add(new JsonObject().put("nosession", new JsonObject())));
        }
        JsonObject header = new JsonObject();
        header.put("context", context);
        header.put("format", new JsonObject().put("type", "js"));

        JsonObject body = new JsonObject();
        body.put(params.getString("name"),
                params.getJsonObject("content"));

        return new JsonObject()
                .put("Header", header)
                .put("Body", body);
    }

    /**
     * Create default HttpClient
     * @return new HttpClient
     */
    private HttpClient createHttpClient() {
        final HttpClientOptions options = new HttpClientOptions();
        if (System.getProperty("httpclient.proxyHost") != null) {
            ProxyOptions proxyOptions = new ProxyOptions()
                    .setHost(System.getProperty("httpclient.proxyHost"))
                    .setPort(Integer.parseInt(System.getProperty("httpclient.proxyPort")))
                    .setUsername(System.getProperty("httpclient.proxyUsername"))
                    .setPassword(System.getProperty("httpclient.proxyPassword"));
            options.setProxyOptions(proxyOptions);
        }
        return vertx.createHttpClient(options);
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
     *      "authToken" : [optionnal] user auth token if already connected,
     *      "name" : name of the zimbra soap request,
     *      "content" : data for the request
     * }
     * @param handler handler to process request result
     */
    private void callSoapAPI(JsonObject params, Handler<Either<String,JsonObject>> handler) {
        if(httpClient == null) {
            httpClient = createHttpClient();
        }
        HttpClientRequest request;
        Handler<HttpClientResponse> handlerRequest = zimbraRequestHandler(handler);
        if( params == null ) {
            request = httpClient.getAbs(zimbraUri, handlerRequest);
        } else {
            request = httpClient.postAbs(zimbraUri, handlerRequest);
            request.setChunked(true);
            JsonObject jsonRequest = prepareJsonRequest(params);
            request.write(jsonRequest.encode());
        }
        request.end();
    }

    /**
     * Authenticate user. Send Json through Zimbra API
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
     * @param user user information (first name, last name
     * @param handler handler after authentication
     */
    public void auth(UserInfos user, Handler<Either<String, JsonObject>> handler) {
        String userId = user.getUserId();
        String userDomain = userService.getUserDomain(user);

        JsonObject preauthInfos = PreauthHelper.generatePreauth(userId, userDomain, preauthKey);
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
            body.put("_jsns", "urn:zimbraAccount");
            body.put("account", authContent);
            body.put("preauth", preauth);

            JsonObject params = new JsonObject()
                    .put("name", "AuthRequest")
                    .put("content", body);

            callSoapAPI(params, authHandler(user, handler));
        }
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
     * @param user User infos to know which user has been authenticated
     * @param handler final request handler
     * @return auth handler
     */
    private Handler<Either<String,JsonObject>> authHandler(UserInfos user,
                                                           final Handler<Either<String, JsonObject>> handler) {
        return response -> {
            if(response.isLeft()) {
                handler.handle(new Either.Left<>(response.left().getValue()));
            } else {
                JsonObject respValue = response.right().getValue();
                try  {
                    String userId = user.getUserId();
                    String userDomain = userService.getUserDomain(user);
                    String userAddress = userId + "@" + userDomain;

                    String authToken = respValue
                            .getJsonObject("Body").getJsonObject("AuthResponse")
                            .getJsonArray("authToken").getJsonObject(0)
                            .getString("_content");
                    Long lifetime = respValue
                            .getJsonObject("Body").getJsonObject("AuthResponse")
                            .getLong("lifetime");
                    JsonObject authUserData = new JsonObject()
                            .put("authToken", authToken)
                            .put("lifetime", lifetime)
                            .put("mailAddress", userAddress);

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
