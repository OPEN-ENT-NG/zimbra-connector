package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.helper.ZimbraConstants;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

import static fr.openent.zimbra.helper.ZimbraConstants.NAMESPACE_ACCOUNT;

public class SignatureService {

    private UserService userService;
    private SoapZimbraService soapService;
    private static final String DEFAULT_SIGNATURE_NAME = "SignatureENT";
    private static final String DEFAULT_SIGNATURE_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

    public SignatureService(UserService userService, SoapZimbraService soapService) {
        this.soapService = soapService;
        this.userService = userService;
    }

    /**
     * Get a signature
     * @param user User
     * @param result result handler
     */
    public void getSignature(UserInfos user,
                             Handler<Either<String,JsonObject>> result) {

        JsonObject getSignaturesRequest = new JsonObject()
                .put("name", "GetSignaturesRequest")
                .put("content", new JsonObject()
                        .put("_jsns", NAMESPACE_ACCOUNT));

        soapService.callUserSoapAPI(getSignaturesRequest, user, response -> {
            if(response.isLeft()) {
                result.handle(response);
            } else {
                processGetSignature(user, response.right().getValue(), result);
            }
        });
    }

    /**
     * Process response from Zimbra API to get a signature
     * Check If some signatures already exists and if "SignatureENT" exists
     * In case of success, return Json Object :
     * {
     * 	    "preference" : {
     * 	        "useSignature": boolean,
     * 	        "signature": signature Body
     * 	    },
     * 	    "id" : signatureID,
     * 	    "zimbraENTSignatureExists" : boolean
     * }
     * @param user User
     * @param jsonResponse Zimbra API Response
     * @param result Handler result
     */
    private void processGetSignature(UserInfos user, JsonObject jsonResponse,
                                     Handler<Either<String, JsonObject>> result) {
        try {
            Boolean signatureENTExists = false;
            if (jsonResponse.getJsonObject("Body")
                    .getJsonObject("GetSignaturesResponse").containsKey("signature")) {

                for(Object o : jsonResponse.getJsonObject("Body")
                        .getJsonObject("GetSignaturesResponse")
                        .getJsonArray("signature")) {

                    if(!(o instanceof JsonObject)) continue;
                    JsonObject attr = (JsonObject)o;
                    String name = attr.getString("name", "");
                    String id = attr.getString("id", "");

                    if (name.equals(DEFAULT_SIGNATURE_NAME)) {
                        String signatureBody = jsonResponse.getJsonObject("Body")
                                .getJsonObject("GetSignaturesResponse")
                                .getJsonArray("signature").getJsonObject(0)
                                .getJsonArray("content").getJsonObject(0)
                                .getString("_content");

                        String signatureId = jsonResponse.getJsonObject("Body")
                                .getJsonObject("GetSignaturesResponse")
                                .getJsonArray("signature").getJsonObject(0)
                                .getString("id");

                        signatureENTExists = true;

                        getPrefSignature(user, isPrefered -> {
                            JsonObject finalResponse = new JsonObject()
                                    .put("preference", new JsonObject()
                                            .put("useSignature", isPrefered)
                                            .put ("signature", signatureBody)
                                            .toString())
                                    .put("zimbraENTSignatureExists", true)
                                    .put("id", signatureId);
                            result.handle(new Either.Right<>(finalResponse));
                        });

                    }
                }
            }
            if (!signatureENTExists) {
                JsonObject finalResponse = new JsonObject()
                        .put("preference", new JsonObject()
                                .put("useSignature", false)
                                .put ("signature", "")
                                .toString())
                        .put("zimbraENTSignatureExists", false)
                        .put("id", "");

                result.handle(new Either.Right<>(finalResponse));
            }

        } catch (NullPointerException e) {
            result.handle(new Either.Left<>("Error when reading response get signature"));
        }
    }



    /**
     * Create a signature
     * @param user User
     * @param signatureBody Body of the signature
     * @param useSignature Boolean signature usage
     * @param result result handler
     */
    public void createSignature(UserInfos user, String signatureBody, Boolean useSignature,
                                  Handler<Either<String,JsonObject>> result) {

        JsonObject signatureReq = new JsonObject()
                .put("id", DEFAULT_SIGNATURE_ID)
                .put("name", DEFAULT_SIGNATURE_NAME)
                .put("content", new JsonObject()
                    .put("type", "text/plain")
                    .put("_content", signatureBody));

        JsonObject createSignatureRequest = new JsonObject()
                .put("name", "CreateSignatureRequest")
                .put("content", new JsonObject()
                        .put("_jsns", ZimbraConstants.NAMESPACE_ACCOUNT)
                        .put("signature", signatureReq));

        soapService.callUserSoapAPI(createSignatureRequest, user, response -> {
            if(response.isLeft()) {
                result.handle(response);
            } else {
                processCreateSignature(response.right().getValue(), signatureBody, result);
            }
        })  ;
    }

    /**
     * Process response from Zimbra API to create a signature
     * In case of success, return Json Object :
     * {
     * 	    "preference" : {
     * 	        "useSignature": boolean,
     * 	        "signature": signatureBody
     * 	    },
     * 	    "id" : signatureID
     * }
     * @param jsonResponse Zimbra API Response
     * @param signatureBody Body of the signature
     * @param result Handler result
     */
    private void processCreateSignature(JsonObject jsonResponse, String signatureBody,
                                          Handler<Either<String, JsonObject>> result) {
        try {

            String signatureName = jsonResponse.getJsonObject("Body")
                    .getJsonObject("CreateSignatureResponse")
                    .getJsonArray("signature").getJsonObject(0)
                    .getString("name");

            String signatureId = jsonResponse.getJsonObject("Body")
                    .getJsonObject("CreateSignatureResponse")
                    .getJsonArray("signature").getJsonObject(0)
                    .getString("id");

            JsonObject finalResponse = new JsonObject()
                    .put("preference", new JsonObject()
                           .put("useSignature", true)
                           .put ("signature", signatureBody)
                            .toString())
                    .put("id", signatureId);

            result.handle(new Either.Right<>(finalResponse));

        } catch (NullPointerException e) {
            result.handle(new Either.Left<>("Error when reading response create signature"));
        }
    }



    /**
     * Modify a signature
     * @param user User
     * @param signatureBody Body of the signature
     * @param useSignature Boolean signature usage
     * @param result result handler
     */
    public void modifySignature(UserInfos user, String signatureBody, Boolean useSignature,
                                Handler<Either<String,JsonObject>> result) {

        JsonObject signatureReq = new JsonObject()
                .put("id", DEFAULT_SIGNATURE_ID)
                .put("name", DEFAULT_SIGNATURE_NAME)
                .put("content", new JsonObject()
                        .put("type", "text/plain")
                        .put("_content", signatureBody));

        JsonObject modifySignatureRequest = new JsonObject()
                .put("name", "ModifySignatureRequest")
                .put("content", new JsonObject()
                        .put("_jsns", ZimbraConstants.NAMESPACE_ACCOUNT)
                        .put("signature", signatureReq));

        soapService.callUserSoapAPI(modifySignatureRequest, user, response -> {
            if(response.isLeft()) {
                result.handle(response);
            } else {
                processModifySignature(response.right().getValue(), signatureBody, result);
            }
        });
    }

    /**
     * Process response from Zimbra API to modify a signature
     * In case of success, return Json Object :
     * {
     * 	    "preference" : {
     * 	        "useSignature": boolean,
     * 	        "signature": signatureBody
     * 	    },
     * 	    "id" : signatureID
     * }
     * @param jsonResponse Zimbra API Response
     * @param signatureBody Body of the signature
     * @param result Handler result
     */
    private void processModifySignature(JsonObject jsonResponse, String signatureBody,
                                        Handler<Either<String, JsonObject>> result) {
        try {

            JsonObject finalResponse = new JsonObject()
                    .put("preference", new JsonObject()
                            .put("useSignature", true)
                            .put("signature", signatureBody)
                            .toString())
                    .put("id", DEFAULT_SIGNATURE_ID);

            result.handle(new Either.Right<>(finalResponse));

        } catch (NullPointerException e) {
            result.handle(new Either.Left<>("Error when reading response modify signature"));
        }
    }


    /**
     * Process response from Zimbra API to get signature profil configuration of user logged in
     * In case of success, return a Json Object :
     * {
     * 	    "signatureUsage" : boolean
     * 	    "signatureName" : "signatureName"
     * }
     * @param user User infos
     * @param handler Result handler
     */
    private void getPrefSignature(UserInfos user,
                             Handler<Boolean> handler) {
        userService.getUserInfo(user, response -> {
            if(response.isLeft()) {
                handler.handle(false);
            } else {
                JsonObject jsonResponse = response.right().getValue();
                if(jsonResponse.containsKey(UserInfoService.SIGN_PREF)) {

                    JsonObject jsonPref = jsonResponse.getJsonObject(UserInfoService.SIGN_PREF);

                    handler.handle(DEFAULT_SIGNATURE_ID.equals(jsonPref.getString("id")));
                } else {
                    handler.handle(false);
                }
            }
        });
    }


    //TODO : modifyAccount pour modifier les pref zimbra signature checked/uncheked signature
    private void modifySignatureUsage(JsonObject jsonResponse, String signatureBody,
                                      Handler<Either<String, JsonObject>> result) {

    }


}
