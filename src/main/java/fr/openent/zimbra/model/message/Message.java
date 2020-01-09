package fr.openent.zimbra.model.message;

import io.vertx.core.json.JsonObject;

public class Message {

    private Message() {}

    public static Message fromZimbra(JsonObject zimbraData) throws IllegalArgumentException {
        return new Message();
    }
}
