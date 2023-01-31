package fr.openent.zimbra.model.requestQueue;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.helper.IModelHelper;
import fr.openent.zimbra.model.IModel;
import fr.openent.zimbra.service.impl.QueueServiceImpl;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ICalRequest extends RequestQueue implements IModel<ICalRequest> {
    public String type;
    public JsonObject data;
    public Integer actionId;
    public String name;
    public JsonObject content;
    public String jsns;

    public ICalRequest(JsonObject icalRequest) {
        this.type = icalRequest.getString(type, null);
        this.data = icalRequest.getJsonObject(Field.DATA, new JsonObject());
        this.actionId = icalRequest.getInteger(Field.ACTIONID, null);
        this.name = data.getString(Field.NAME,null);
        this.content = data.getJsonObject(Field.CONTENT, new JsonObject());
        this.jsns = data.getString(Field._JSNS, null);
    }

    public String getType() {
        return type;
    }

    public JsonObject getData() {
        return data;
    }

    public Integer getActionId() {
        return actionId;
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
        RequestQueue.queueService.createTask(this.actionId)
                .compose(taskId -> {
                    //create icalrequest
                })
                .onSuccess()
                .onFailure();
    }


    @Override
    public JsonObject toJson() {
        return IModelHelper.toJson(this, false, true);
    }
}


