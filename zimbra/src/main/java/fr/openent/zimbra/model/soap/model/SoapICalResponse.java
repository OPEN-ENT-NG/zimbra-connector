package fr.openent.zimbra.model.soap.model;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.SoapRequestFields;
import fr.openent.zimbra.model.IModel;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class SoapICalResponse implements IModel<SoapICalResponse> {

    private final JsonObject header;
    private final JsonObject context;
    private final JsonObject change;
    private final Long token;
    private final String jsnsContext;
    private final String jsnsGetICalResponse;
    private final String jsns;
    private final JsonObject body;
    private final JsonObject getICalResponse;
    private final JsonArray ical;
    private final String content;

    public SoapICalResponse(JsonObject icalSoapResponse) {
        this.header = icalSoapResponse.getJsonObject(Field.HEADER, new JsonObject());
        this.context = this.header.getJsonObject(Field.CONTEXT, new JsonObject());
        this.change = this.context.getJsonObject(Field.CHANGE, new JsonObject());
        this.token = this.change.getLong(Field.TOKEN, null);
        this.jsnsContext = this.context.getString(Field._JSNS, null);

        this.body = icalSoapResponse.getJsonObject(SoapRequestFields.BODY.method(), new JsonObject());
        this.getICalResponse = this.body.getJsonObject(Field.GETICALRESPONSE, new JsonObject());
        this.ical = this.getICalResponse.getJsonArray(Field.ICAL, new JsonArray());
        this.content = this.ical.size() != 0 ? this.ical.getJsonObject(0).getString(Field._CONTENT, "") : "";
        this.jsnsGetICalResponse = this.getICalResponse.getString(Field._JSNS, null);

        this.jsns = icalSoapResponse.getString(Field._JSNS, null);
    }

    @Override
    public JsonObject toJson() {
        JsonObject change = new JsonObject().put(Field.TOKEN, this.token);
        JsonObject context = new JsonObject().put(Field.CHANGE, change).put(Field._JSNS, this.jsnsContext);
        JsonObject header = new JsonObject().put(Field.CONTEXT, context);

        JsonArray ical = new JsonArray().add(new JsonObject().put(Field._CONTENT, this.content));
        JsonObject getICalResponse = new JsonObject().put(Field.ICAL, ical).put(Field._JSNS, this.jsnsGetICalResponse);
        JsonObject body = new JsonObject().put(Field.GETICALRESPONSE, getICalResponse);

        JsonObject result = new JsonObject().put(Field.HEADER, header).put(Field.BODY, body).put(Field._JSNS, this.jsns);

        return result;
    }

    public JsonObject getHeader() {
        return header;
    }

    public JsonObject getContext() {
        return context;
    }

    public JsonObject getChange() {
        return change;
    }

    public Long getToken() {
        return token;
    }

    public String getJsnsContext() {
        return jsnsContext;
    }

    public String getJsnsGetICalResponse() {
        return jsnsGetICalResponse;
    }

    public String getJsns() {
        return jsns;
    }

    public JsonObject getBody() {
        return body;
    }

    public JsonObject getGetICalResponse() {
        return getICalResponse;
    }

    public JsonArray getIcal() {
        return ical;
    }

    public String getContent() {
        return content;
    }

}
