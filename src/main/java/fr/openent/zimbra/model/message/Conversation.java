package fr.openent.zimbra.model.message;


import fr.openent.zimbra.helper.RecipientHelper;
import fr.openent.zimbra.helper.ZimbraFlags;
import fr.openent.zimbra.model.constant.FrontConstants;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.*;

import static fr.openent.zimbra.model.constant.ZimbraConstants.*;

public class Conversation {

    private String          id;
    private String          subject;
    private Integer         nbMsg;
    private Integer         nbUnread;
    private Integer         nbTotal;
    private Boolean         isRead;
    private Long            date;
    private String          zimbraFlags;
    private List<Message>   messageList = new ArrayList<>();
    private List<ZimbraEmail>   emailsList = new ArrayList<>();
    @SuppressWarnings("FieldCanBeLocal")
    private Map<String, Recipient> userMapping = new HashMap<>();

    public String getId() { return id; }
    public String getSubject() { return subject; }
    public Integer getNbMsg() { return nbMsg; }
    public Integer getNbUnread() { return nbUnread; }
    public Boolean getRead() { return isRead; }
    public String getZimbraFlags() { return zimbraFlags; }
    public List<Message> getMessageList() { return messageList; }

    private Conversation() {
    }

    public static Conversation fromZimbra(JsonObject zimbraData) throws IllegalArgumentException {
        Conversation conversation =  new Conversation();
        conversation.id = zimbraData.getString(CONVERSATION_ID, "");
        conversation.subject = zimbraData.getString(CONVERSATION_SUBJECT, "");
        conversation.nbMsg = zimbraData.getInteger(CONVERSATION_NBMSG, 0);
        conversation.nbUnread = zimbraData.getInteger(CONVERSATION_NBUNREAD, 0);
        conversation.nbTotal = zimbraData.getInteger(CONVERSATION_NBTOTAL, 0);
        conversation.date = Long.parseLong(zimbraData.getValue(CONVERSATION_DATE, 0L).toString());
        conversation.zimbraFlags = zimbraData.getString(CONVERSATION_FLAGS, "");
        conversation.isRead = ZimbraFlags.isRead(conversation.zimbraFlags);
        conversation.generateMessageListFromZimbra(zimbraData.getJsonArray(MSG, new JsonArray()));
        conversation.generateEmailListFromZimbra(zimbraData.getJsonArray(MSG_EMAILS, new JsonArray()));
        return conversation;
    }

    public Set<String> getAllAddresses() {
        Set<String> resultSet = new HashSet<>();
        emailsList.forEach( email -> resultSet.add(email.getAddress()));
        return resultSet;
    }

    public void setUserMapping(Map<String, Recipient> mapFromEmail) {
        userMapping = mapFromEmail;
        messageList.forEach( message -> message.setUserMapping(mapFromEmail));
    }

    public JsonArray getAllMessagesJson() {
        JsonArray result = new JsonArray();
        messageList.forEach( message -> result.add(message.getJsonObject()));
        return result;
    }

    public JsonObject getJsonObject() {
        JsonObject result = new JsonObject();
        result.put(FrontConstants.THREAD_ID, id);
        result.put(FrontConstants.THREAD_DATE, date);
        result.put(FrontConstants.THREAD_SUBJECT, subject);
        result.put(FrontConstants.THREAD_NB_UNREAD, nbUnread);
        result.put(FrontConstants.MAIL_TO, getUsers(ADDR_TYPE_TO));
        result.put(FrontConstants.MAIL_CC, getUsers(ADDR_TYPE_CC));
        result.put(FrontConstants.MAIL_BCC, getUsers(ADDR_TYPE_BCC));
        JsonArray allFrom = getUsers(ADDR_TYPE_FROM);
        result.put(FrontConstants.MAIL_FROM, allFrom.isEmpty() ? "" : allFrom.getString(0));
        result.put(FrontConstants.MAIL_BCC_MOBILEAPP, getUsers(ADDR_TYPE_BCC));
        result.put(FrontConstants.MAIL_DISPLAYNAMES, getDisplayNames());
        return result;
    }

    private void generateMessageListFromZimbra(JsonArray zimbraMessageList) {
        zimbraMessageList.forEach( item -> {
            if(item instanceof JsonObject) {
                JsonObject messageJson = (JsonObject)item;
                Message message = Message.fromZimbra(messageJson, true);
                message.setConversationId(id);
                messageList.add(message);
            }
        });
    }

    private void generateEmailListFromZimbra(JsonArray zimbraEmailList) {
        zimbraEmailList.forEach( item -> {
            if(item instanceof JsonObject) {
                JsonObject emailObject = (JsonObject)item;
                emailsList.add(ZimbraEmail.fromZimbra(emailObject));
            }
        });
    }

    private JsonArray getUsers(String zimbraType) {
        return RecipientHelper.getUsersJson(emailsList, userMapping, zimbraType);
    }

    private JsonArray getDisplayNames() {
        return RecipientHelper.getDisplayNamesJson(emailsList, userMapping);
    }

}
