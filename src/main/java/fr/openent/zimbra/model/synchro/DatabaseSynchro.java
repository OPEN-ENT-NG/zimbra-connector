package fr.openent.zimbra.model.synchro;

import fr.openent.zimbra.model.MailAddress;
import fr.openent.zimbra.service.data.SqlSynchroService;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class DatabaseSynchro {

    private String synchroId;
    private List<MailAddress> maillingList = new ArrayList();
    private String logsContent;
    private String date;

    private static Logger log = LoggerFactory.getLogger(DatabaseSynchro.class);

    public DatabaseSynchro(JsonObject jsonData) throws IllegalArgumentException {
        if (!jsonData.containsKey(SqlSynchroService.SYNCHRO_ID)
                || !jsonData.containsKey(SqlSynchroService.SYNCHRO_MAILLINGLIST)) {
            throw new IllegalArgumentException("Missing data");
        }

        try {
            synchroId = jsonData.getInteger(SqlSynchroService.SYNCHRO_ID).toString();
            processMaillingList(jsonData.getString(SqlSynchroService.SYNCHRO_MAILLINGLIST));
            logsContent = jsonData.getString(SqlSynchroService.SYNCHRO_AGG_LOGS, "");
            date = jsonData.getString(SqlSynchroService.SYNCHRO_DATE, "");
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public String getSynchroId() { return synchroId;}
    public String getLogsContent() { return logsContent;}
    public List<MailAddress> getMaillingList() { return maillingList;}
    public String getDate() { return date;}

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

}
