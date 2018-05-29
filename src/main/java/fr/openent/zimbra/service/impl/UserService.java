package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.helper.ZimbraConstants;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

public class UserService {

    private SoapZimbraService soapService;

    public UserService(SoapZimbraService soapService) {
        this.soapService = soapService;
    }

    private String getUserDomain(UserInfos user) {
        return "";
    }

    public String getUserZimbraAddress(UserInfos user) {
        //todo replace placeholder
        return "thomas.lecocq2@ng.preprod-ent.fr";
        //return user.getUserId() + "@" + getUserDomain(user);
    }


    /**
     * Get used and max quota of user from Zimbra
     * @param user User infos
     * @param handler Result handler
     */
    public void getQuota(UserInfos user,
                         Handler<Either<String, JsonObject>> handler) {

        JsonObject getInfoRequest = new JsonObject()
                .put("name", "GetInfoRequest")
                .put("content", new JsonObject()
                        .put("_jsns", ZimbraConstants.NAMESPACE_ACCOUNT));

        soapService.callUserSoapAPI(getInfoRequest, user, response -> {
            if(response.isLeft()) {
                handler.handle(new Either.Left<>(response.left().getValue()));
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
        try {
            Long quotaUsed = jsonResponse.getJsonObject("Body")
                    .getJsonObject("GetInfoResponse")
                    .getLong("used");

            String totalQuota = jsonResponse.getJsonObject("Body")
                    .getJsonObject("GetInfoResponse")
                    .getJsonObject("attrs")
                    .getJsonObject("_attrs")
                    .getString("zimbraMailQuota");

            JsonObject resultQuotas = new JsonObject()
                    .put("storage", quotaUsed)
                    .put("quota", totalQuota);

            handler.handle(new Either.Right<>(resultQuotas));
        } catch (NullPointerException e) {
            handler.handle(new Either.Left<>("Error when reading response"));
        }
    }

}
