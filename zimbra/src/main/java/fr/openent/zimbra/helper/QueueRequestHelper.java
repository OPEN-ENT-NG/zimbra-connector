package fr.openent.zimbra.helper;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.model.constant.SoapConstants;
import io.vertx.core.json.JsonObject;

public class QueueRequestHelper {

    public static JsonObject createQueueRequest(String type, JsonObject data) {
        return createQueueRequest(type, data, null);
    }

    public static JsonObject createQueueRequest(String type, JsonObject data, Boolean approved) {
        JsonObject queueRequest = new JsonObject()
                .put(Field.TYPE, type)
                .put(Field.DATA, data)
                .put(Field.APPROVED, approved != null ? approved : false);

        return queueRequest;
    }

    public static JsonObject createICalRequest () {
        return createICalRequest(null, null);
    }
    public static JsonObject createICalRequest(Long rangeStart, Long rangeEnd) {
        JsonObject icalRequest = new JsonObject()
                .put(Field._JSNS, SoapConstants.NAMESPACE_MAIL);

        if (rangeStart != null) { //start timestamp for ICal retrieval
            icalRequest.put("s", rangeStart);
        }

        if (rangeEnd != null) { //end timestamp for ICal retrieval
            icalRequest.put("e", rangeEnd);
        }

        JsonObject finalRequest = new JsonObject()
                .put(Field.NAME, Field.GETICALREQUEST)
                .put(Field.CONTENT, icalRequest);

        return createQueueRequest(Field.ICAL, finalRequest);
    }

}
