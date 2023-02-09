package fr.openent.zimbra.model.task;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.TaskStatus;
import fr.openent.zimbra.helper.IModelHelper;
import fr.openent.zimbra.model.IModel;
import fr.openent.zimbra.model.action.Action;
import fr.openent.zimbra.model.constant.SoapConstants;
import fr.wseduc.webutils.http.Renders;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class ICalTask extends Task implements IModel<ICalTask> {
    protected static final Logger log = LoggerFactory.getLogger(Renders.class);

    public String type;
    public JsonObject data;
    public long actionId;
    public String name;
    public JsonObject body;
    public String jsns;

    public ICalTask(JsonObject icalRequest) {
        super(icalRequest);
        this.type = icalRequest.getString(Field.TYPE, null);
        this.data = icalRequest.getJsonObject(Field.DATA, new JsonObject());
        this.actionId = icalRequest.getLong(Field.ACTIONID, null);
        this.name = data.getString(Field.NAME, null);
        this.body = data.getJsonObject(Field.CONTENT, new JsonObject());
        this.jsns = data.getString(Field._JSNS, null);
    }

    public ICalTask (Action action, TaskStatus status, Long rangeStart, Long rangeEnd) {
        super(status, action);
        this.jsns = SoapConstants.NAMESPACE_MAIL;
        this.body = new JsonObject();

        if (rangeStart != null) { // start timestamp for ICal retrieval
            this.body.put("s", rangeStart);
        }

        if (rangeEnd != null) { // end timestamp for ICal retrieval
            this.body.put("e", rangeEnd);
        }
    }

    public String getType() {
        return type;
    }

    public JsonObject getData() {
        return data;
    }

    public long getActionId() {
        return actionId;
    }

    public String getName() {
        return name;
    }

    public JsonObject getBody() {
        return body;
    }

    public String getJsns() {
        return jsns;
    }

    @Override
    public JsonObject toJson() {
        return IModelHelper.toJson(this, false, true);
    }
}


