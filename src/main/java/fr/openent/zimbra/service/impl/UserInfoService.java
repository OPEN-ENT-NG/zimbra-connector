package fr.openent.zimbra.service.impl;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class UserInfoService {

    static final String QUOTA = "quota";
    static final String ALIAS = "alias";

    static void processQuota(JsonObject getInfoRequest, JsonObject data) {
        try {
            Long quotaUsed = getInfoRequest.getLong("used");
            String totalQuota = getInfoRequest.getJsonObject("attrs")
                    .getJsonObject("_attrs")
                    .getString("zimbraMailQuota");

            data.put(QUOTA, new JsonObject()
                    .put("storage", quotaUsed)
                    .put("quota", totalQuota));
        } catch (NullPointerException e) {
            data.remove(QUOTA);
        }
    }

    static void processAliases(JsonObject getInfoRequest, JsonObject data) {
        try {
            String name = getInfoRequest.getString("name");
            JsonObject attrs = getInfoRequest.getJsonObject("attrs")
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
