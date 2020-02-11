package fr.openent.zimbra.model.message;

import fr.openent.zimbra.helper.RecipientHelper;
import fr.openent.zimbra.helper.ZimbraFlags;
import fr.openent.zimbra.model.constant.FrontConstants;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.*;

import static fr.openent.zimbra.model.constant.ZimbraConstants.*;

public class Message {

    private String      id;
    private String      subject;
    private String      body;
    private String      zimbraFolder;
    private String      frontFolder;
    private String      conversationId;
    private String      zimbraFlags;
    private String      mailId;
    private Long        date;
    private Boolean     hasAttachment;
    private Boolean     isRead;
    private Boolean     isDraft;
    private Boolean     isTrashed;
    private Boolean     isReplied;
    private Multipart   multipart;
    private List<ZimbraEmail> emailAdresses = new ArrayList<>();
    private Map<String, Recipient> userMapping = new HashMap<>();

    public String getId() { return id; }
    public Long getDate() { return date; }
    public String getSubject() { return subject; }
    public String getMailId() { return mailId; }

    public String getFrontState() {
        return zimbraFlags.contains(MSG_FLAG_DRAFT)? FrontConstants.STATE_DRAFT : FrontConstants.STATE_SENT;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public void setUserMapping(Map<String, Recipient> userMapping) {
        this.userMapping = userMapping;
    }

    public Set<String> getAllAddresses() {
        Set<String> result = new HashSet<>();
        emailAdresses.forEach( email -> result.add(email.getAddress()));
        return result;
    }

    private Message() {}

    public JsonObject getJsonObject() {
        JsonObject result = new JsonObject();
        result.put(FrontConstants.MESSAGE_ID, id);
        result.put(FrontConstants.MESSAGE_BODY, body);
        result.put(FrontConstants.MESSAGE_DATE, date);
        result.put(FrontConstants.MESSAGE_SUBJECT, subject);
        result.put(FrontConstants.MESSAGE_FOLDER_ID, zimbraFolder);
        result.put(FrontConstants.MESSAGE_THREAD_ID, conversationId);
        result.put(FrontConstants.MESSAGE_STATE, getFrontState());
        result.put(FrontConstants.MESSAGE_UNREAD, !isRead);
        result.put(FrontConstants.MESSAGE_RESPONSE, isReplied);
        result.put(FrontConstants.MESSAGE_HAS_ATTACHMENTS, hasAttachment);
        result.put(FrontConstants.MESSAGE_SYSTEM_FOLDER, frontFolder);
        JsonArray allFrom = getUsers(ADDR_TYPE_FROM);
        result.put(FrontConstants.MAIL_FROM, allFrom.isEmpty() ? "" : allFrom.getString(0));
        result.put(FrontConstants.MAIL_TO, getUsers(ADDR_TYPE_TO));
        result.put(FrontConstants.MAIL_CC, getUsers(ADDR_TYPE_CC));
        result.put(FrontConstants.MAIL_BCC, getUsers(ADDR_TYPE_BCC));
        result.put(FrontConstants.MAIL_BCC_MOBILEAPP, getUsers(ADDR_TYPE_BCC));
        result.put(FrontConstants.MAIL_DISPLAYNAMES, getDisplayNames());
        result.put(FrontConstants.MESSAGE_ATTACHMENTS, multipart.getAttachmentsJson());
        result.put(FrontConstants.MESSAGE_IS_DRAFT, isDraft);
        result.put(FrontConstants.MESSAGE_IS_TRASHED, isTrashed);
        return result;
    }

    public static Message fromZimbra(JsonObject zimbraData) throws IllegalArgumentException {
        return fromZimbra(zimbraData, false);
    }

    public static Message fromZimbra(JsonObject zimbraData, boolean fromConv) throws IllegalArgumentException {
        Message message = new Message();
        message.id = zimbraData.getString(MSG_ID, "");
        message.mailId = zimbraData.getString(MSG_EMAILID, "");
        message.subject = zimbraData.getString(MSG_SUBJECT, "");
        message.zimbraFolder = zimbraData.getString(MSG_LOCATION, "");
        message.conversationId = zimbraData.getString(MSG_CONVERSATION_ID, "");
        message.zimbraFlags = zimbraData.getString(MSG_FLAGS, "");
        message.date = Long.parseLong(zimbraData.getValue(MSG_DATE, 0L).toString());
        message.setFrontFolder();
        message.isRead = ZimbraFlags.isRead(message.zimbraFlags);
        message.hasAttachment = ZimbraFlags.hasAttachment(message.zimbraFlags);
        message.multipart = new Multipart(message.id, zimbraData.getJsonArray(MSG_MULTIPART), fromConv);
        message.body = message.multipart.getBody();
        message.loadEmailAdresses(zimbraData.getJsonArray(MSG_EMAILS, new JsonArray()));
        message.isReplied = ZimbraFlags.isReplied(message.zimbraFlags);
        message.isDraft = ZimbraFlags.isDraft(message.zimbraFlags);
        message.isTrashed = message.zimbraFolder.equals(FOLDER_TRASH_ID);
        return message;
    }

    private JsonArray getUsers(String zimbraType) {
        return RecipientHelper.getUsersJson(emailAdresses, userMapping, zimbraType);
    }

    private JsonArray getDisplayNames() {
        return RecipientHelper.getDisplayNamesJson(emailAdresses, userMapping);
    }

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
}
