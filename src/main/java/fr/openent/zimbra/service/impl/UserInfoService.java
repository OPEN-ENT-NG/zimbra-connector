package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.helper.ZimbraConstants;
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

    /**
     * Process aliases information from Zimbra GetInfoResponse
     * Add Json to data :
     * {
     *      "quota" : {quota_infos}, // see UserInfoService.processQuota
     *      "alias" : {alias_infos}  // see UserInfoService.processAliases
     * }
     * @param getAccountResponse Zimbra GetInfoResponse Json object
     * @param data JsonObject where result must be added
     */
    static void processAccountInfo(JsonObject getAccountResponse, JsonObject data) {
        JsonObject alias = new JsonObject();
        JsonObject account;
        try {
            account = getAccountResponse.getJsonArray("account").getJsonObject(0);
        } catch (Exception e) {
            account = new JsonObject();
        }
        alias.put("name", account.getString(ZimbraConstants.ACCT_NAME, ""));
        alias.put("aliases", new JsonArray());

        JsonArray attributes = account.getJsonArray(ZimbraConstants.ACCT_ATTRIBUTES, new JsonArray());
        for(Object o : attributes) {
            if(!(o instanceof JsonObject)) continue;
            JsonObject attr = (JsonObject)o;
            String key = attr.getString(ZimbraConstants.ACCT_ATTRIBUTES_NAME, "");
            String value = attr.getString(ZimbraConstants.ACCT_ATTRIBUTES_CONTENT, "");

            switch (key) {
                case "zimbraMailQuota":
                    data.put(QUOTA, new JsonObject()
                            .put("quota", value));
                    break;
                case "zimbraMailAlias":
                    JsonArray aliases = alias.getJsonArray("aliases").add(value);
                    alias.put("aliases", aliases);
                    break;
            }
        }
        data.put(ALIAS, alias);
    }
}
