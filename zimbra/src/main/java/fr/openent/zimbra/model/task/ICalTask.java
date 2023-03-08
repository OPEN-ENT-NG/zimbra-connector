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

public class ICalTask extends Task<ICalTask> implements IModel<ICalTask> {
    protected static final Logger log = LoggerFactory.getLogger(Renders.class);

    public long id;
    public long actionId;
    public String name;
    public JsonObject body;

    public ICalTask(JsonObject icalRequest) throws Exception {
        super(icalRequest);

        initICalTask(icalRequest);
    }

    public ICalTask(Action<ICalTask> action, JsonObject icalRequest) throws Exception {
        super(icalRequest, action);

        initICalTask(icalRequest);
    }

    public ICalTask(Action<ICalTask> action, TaskStatus status, Long rangeStart, Long rangeEnd) {
        super(status, action);
        this.name = Field.GETICALREQUEST;
        this.body = new JsonObject().put(Field._JSNS, SoapConstants.NAMESPACE_MAIL);

        if (rangeStart != null) { // start timestamp for ICal retrieval
            this.body.put("s", rangeStart);
        }

        if (rangeEnd != null) { // end timestamp for ICal retrieval
            this.body.put("e", rangeEnd);
        }
    }

    @Override
    public long getId() {
        return id;
    }

    public long getActionId() {
        return actionId;
    }

    public void setActionId(long actionId) {
        this.actionId = actionId;
    }

    @Override
    public TaskStatus getStatus() {
        return status;
    }

    public String getName() {
        return name;
    }

    public JsonObject getBody() {
        return body;
    }


    @Override
    public JsonObject toJson() {
        return IModelHelper.toJson(this, false, true);
    }

    private void initICalTask(JsonObject icalRequest) throws Exception {
        try {
            this.id = icalRequest.getLong(Field.ID, null);
            this.actionId = icalRequest.getLong(Field.ACTION_ID, null);
            this.status = TaskStatus.fromString(icalRequest.getString(Field.STATUS, null));
            this.name = icalRequest.getString(Field.NAME, null);
            this.body = new JsonObject(icalRequest.getString(Field.BODY, new JsonObject().toString()));
        } catch (Exception e) {
            throw new Exception(String.format("[Zimbra@%s::ICalTask] Bad field format", this.getClass().getSimpleName()));
        }
    }
}


