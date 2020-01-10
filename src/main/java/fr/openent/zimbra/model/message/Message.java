package fr.openent.zimbra.model.message;

import fr.openent.zimbra.helper.ZimbraFlags;
import fr.openent.zimbra.model.constant.FrontConstants;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

import static fr.openent.zimbra.model.constant.ZimbraConstants.*;

public class Message {

    private String      id;
    private String      subject;
    private String      body;
    private String      zimbraFolder;
    private String      frontFolder;
    private String      conversationId;
    private String      zimbraFlags;
    private Long        date;
    private Boolean     hasAttachment;
    private Boolean     isRead;
    private Boolean     isDraft;
    private Boolean     isReplied;
    private Multipart   multipart;
    private List<ZimbraEmail> emailAdresses = new ArrayList<>();

    private boolean     zimbraEmails = false;
    private boolean     frontEmails = false;

    public String getId() { return id; }
    public Long getDate() { return date; }
    public String getSubject() { return subject; }
    public String getConversationId() { return conversationId; }

    public String getFrontState() {
        return zimbraFlags.contains(MSG_FLAG_DRAFT)? FrontConstants.STATE_DRAFT : FrontConstants.STATE_SENT;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    private Message() {}

    private void setFrontFolder() {
        if(ZimbraFlags.isDraft(zimbraFlags)){
            frontFolder = "DRAFT";
        }else if (ZimbraFlags.isSentByMe(zimbraFlags)) {
            frontFolder = "OUTBOX";
        }else {
            frontFolder = "INBOX";
        }
    }

    private void loadEmailAdresses(JsonArray rawEmails) {
        rawEmails.forEach( item -> {
            if(item instanceof JsonObject) {
                JsonObject emailObject = (JsonObject)item;
                emailAdresses.add(ZimbraEmail.fromZimbra(emailObject));
            }
        });
    }

    public static Message fromZimbra(JsonObject zimbraData) throws IllegalArgumentException {
        Message message = new Message();
        message.id = zimbraData.getString(MSG_ID, "");
        message.subject = zimbraData.getString(MSG_SUBJECT, "");
        message.zimbraFolder = zimbraData.getString(MSG_LOCATION, "");
        message.conversationId = zimbraData.getString(MSG_CONVERSATION_ID, "");
        message.zimbraFlags = zimbraData.getString(MSG_FLAGS, "");
        message.setFrontFolder();
        message.isRead = ZimbraFlags.isRead(message.zimbraFlags);
        message.hasAttachment = ZimbraFlags.hasAttachment(message.zimbraFlags);
        message.multipart = new Multipart(message.id, zimbraData.getJsonArray(MSG_MULTIPART));
        message.body = message.multipart.getBody();
        message.loadEmailAdresses(zimbraData.getJsonArray(MSG_EMAILS, new JsonArray()));
        message.zimbraEmails = true;
        return message;
    }
}
