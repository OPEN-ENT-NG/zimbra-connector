package fr.openent.zimbra.service.impl;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class UserInfoService {

    static final String QUOTA = "quota";
    static final String ALIAS = "alias";

    /**
     * Process quota information from Zimbra GetInfoResponse
     * Add Json to data :
     * {
     *     "storage" : used data,
     *     "quota" : max data usage allowed
     * }
     * @param getInfoResponse Zimbra GetInfoResponse Json object
     * @param data JsonObject where result must be added under "quota" entry
     */
    static void processQuota(JsonObject getInfoResponse, JsonObject data) {
        try {
            Long quotaUsed = getInfoResponse.getLong("used");
            String totalQuota = getInfoResponse.getJsonObject("attrs")
                    .getJsonObject("_attrs")
                    .getString("zimbraMailQuota");

            data.put(QUOTA, new JsonObject()
                    .put("storage", quotaUsed)
                    .put("quota", totalQuota));
        } catch (NullPointerException e) {
            data.remove(QUOTA);
        }
    }

    /**
     * Process aliases information from Zimbra GetInfoResponse
     * Add Json to data :
     * {
     *     "name" : user mail address from Zimbra,
     *     "aliases" :
     *      [
     *          "alias"
     *      ]
     * }
     * @param getInfoResponse Zimbra GetInfoResponse Json object
     * @param data JsonObject where result must be added under "quota" entry
     */
    static void processAliases(JsonObject getInfoResponse, JsonObject data) {
        try {
            String name = getInfoResponse.getString("name");
            JsonObject attrs = getInfoResponse.getJsonObject("attrs")
                    .getJsonObject("_attrs");
            JsonArray aliases = new JsonArray();
            if(attrs.containsKey("zimbraMailAlias")) {
                try {
                    aliases.add(attrs.getString("zimbraMailAlias"));
                } catch (ClassCastException cce) {
                    aliases = attrs.getJsonArray("zimbraMailAlias");
                }
            }

            data.put(ALIAS, new JsonObject()
                    .put("name", name)
                    .put("aliases", aliases));
        } catch (NullPointerException|ClassCastException e) {
            data.remove(ALIAS);
        }
    }
}
