package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.helper.ZimbraConstants;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class UserInfoService {

    static final String QUOTA = "quota";
    static final String STATUS = "status";
    static final String ALIAS = "alias";
    static final String GROUPS = "groups";
    public static final String ZIMBRA_ID = "zimbra_id";
    static final String SIGN_PREF = "signature";

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
     * Process signature preference information from Zimbra GetInfoResponse
     * Add Json to data :
     * {
     *     "name" : user mail address from Zimbra,
     *     "aliases" : name
     * }
     * @param getInfoResponse Zimbra GetInfoResponse Json object
     * @param data JsonObject where result must be added under "quota" entry
     */
    static void processSignaturePref(JsonObject getInfoResponse, JsonObject data) {
        try {

            JsonObject attrs = getInfoResponse.getJsonObject("prefs")
                    .getJsonObject("_attrs");

            if(attrs.containsKey("zimbraPrefDefaultSignatureId")) {
                data.put(SIGN_PREF, new JsonObject()
                        .put("prefered", true)
                        .put("id", attrs.getString("zimbraPrefDefaultSignatureId")));
            }
            else {
                data.put(SIGN_PREF, new JsonObject()
                        .put("prefered", false)
                        .put("id", ""));
            }

        } catch (NullPointerException|ClassCastException e) {
            data.remove(SIGN_PREF);
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
    public static void processAccountInfo(JsonObject getAccountResponse, JsonObject data) {
        JsonObject alias = new JsonObject();
        JsonObject account;
        try {
            account = getAccountResponse.getJsonArray("account").getJsonObject(0);
        } catch (Exception e) {
            account = new JsonObject();
        }

        data.put(ZIMBRA_ID, account.getString(ZimbraConstants.ACCT_ID, ""));

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
                case "zimbraAccountStatus":
                    data.put(STATUS, value);
                    break;
                case "ou":
                    JsonArray groups = data.getJsonArray(GROUPS, new JsonArray());
                    groups.add(value);
                    data.put(GROUPS, groups);
                    break;
            }
        }
        data.put(ALIAS, alias);
    }
}
