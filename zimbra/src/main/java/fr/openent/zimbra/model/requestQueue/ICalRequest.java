package fr.openent.zimbra.model.requestQueue;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.helper.IModelHelper;
import fr.openent.zimbra.model.IModel;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ICalRequest extends RequestQueue implements IModel<ICalRequest> {
    public String name;
    public JsonObject content;
    public String jsns;

    public ICalRequest(JsonObject icalRequest) {
        this.name = icalRequest.getString(Field.NAME,null);
        this.content = icalRequest.getJsonObject(Field.CONTENT, new JsonObject());
        this.jsns = content.getString(Field._JSNS, null);
    }

    public String getName() {
        return name;
    }

    public JsonObject getContent() {
        return content;
    }

    public String getJsns() {
        return jsns;
    }

    @Override
    public void addTaskToAction() {
        //todo
    }


    @Override
    public JsonObject toJson() {
        return IModelHelper.toJson(this, false, true);
    }
}


