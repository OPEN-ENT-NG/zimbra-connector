package fr.openent.zimbra.model;

import io.vertx.core.json.JsonObject;
import static fr.openent.zimbra.service.data.Neo4jZimbraService.*;

public class Group {
    private String id;
    private String rawName;
    private String translatedName = "";

    @SuppressWarnings("WeakerAccess")
    public Group(JsonObject json) throws IllegalArgumentException {
        try {
            id = json.getString(GROUP_ID, "");
            rawName = json.getString(GROUP_NAME, "");
            if(id.isEmpty() || rawName.isEmpty()) {
                throw new IllegalArgumentException("Invalid Json for Group");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Json for Group");
        }
    }

    public String getId() {
        return id;
    }

    public String getRawName() {
        return rawName;
    }
}
