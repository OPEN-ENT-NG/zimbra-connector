package fr.openent.zimbra.model.synchro;

import fr.openent.zimbra.model.MailAddress;
import fr.openent.zimbra.helper.JsonHelper;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class SynchroInfos {

    private static final String MAILLINGLIST = "maillingList";
    private static final String USERS_CREATED = "createdUsers";
    private static final String USERS_MODIFIED = "modifiedUsers";
    private static final String USERS_DELETED = "deletedUsers";

    private int id;
    private List<MailAddress> maillingList = new ArrayList<>();
    private List<String> users_created;
    private List<String> users_modified;
    private List<String> users_deleted;

    private static Logger log = LoggerFactory.getLogger(SynchroInfos.class);

    public SynchroInfos(JsonObject jsonData) throws IllegalArgumentException {
        if(!jsonData.containsKey(USERS_CREATED)
            || !jsonData.containsKey(USERS_MODIFIED)
            || !jsonData.containsKey(USERS_DELETED)) {
            throw new IllegalArgumentException("Missing data");
        }
        users_created = JsonHelper.getStringList(jsonData.getJsonArray(USERS_CREATED));
        users_modified = JsonHelper.getStringList(jsonData.getJsonArray(USERS_MODIFIED));
        users_deleted = JsonHelper.getStringList(jsonData.getJsonArray(USERS_DELETED));
        if(jsonData.containsKey(MAILLINGLIST)) {
            processMaillingList(jsonData.getString(MAILLINGLIST, ""));
        }
    }

    private void processMaillingList(String rawList) {
        String[] rawMails = rawList.split(",");
        for( String mail : rawMails ) {
            try {
                MailAddress addr = MailAddress.createFromRawAddress(mail);
                maillingList.add(addr);
            } catch (IllegalArgumentException e) {
                log.warn("Synchro : Invalid email address : " + mail);
            }
        }
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public String getMaillinglistRaw() {
        StringBuilder rawlist = new StringBuilder();
        boolean first = true;
        for(MailAddress mail : maillingList) {
            if(!first)
                rawlist.append(",");
            first = false;
            rawlist.append(mail.toString());
        }
        return rawlist.toString();
    }

    public List<String> getUsersCreated() {
        return users_created;
    }

    public List<String> getUsersModified() {
        return users_modified;
    }

    public List<String> getUsersDeleted() {
        return users_deleted;
    }
}
