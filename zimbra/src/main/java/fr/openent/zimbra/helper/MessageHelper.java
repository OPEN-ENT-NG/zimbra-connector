package fr.openent.zimbra.helper;

import fr.openent.zimbra.model.message.Message;
import fr.openent.zimbra.model.message.ZimbraEmail;
import fr.openent.zimbra.service.impl.RecipientService;
import io.vertx.core.Future;
import org.apache.commons.lang3.NotImplementedException;
import org.entcore.common.user.UserInfos;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static fr.openent.zimbra.model.constant.ZimbraConstants.ADDR_TYPE_FROM;

public class MessageHelper {
    /**
     * Helps you to get sender email.
     * @param message Current zimbra message.
     * @return Sender's email if it exists, null otherwise.
     */
    public static ZimbraEmail getSenderEmail(Message message) {
        return message.getEmailAddresses().stream().filter(mail -> mail.getAddrType().equals(ADDR_TYPE_FROM)).findFirst().orElse(null);
    }

    /**
     * Helps you to determine if current user is the sender of current mail.
     * @param message   Zimbra message
     * @param user      Current user
     * @return          True if user is the sender, false otherwise.
     */
    public static boolean isUserMailSender(Message message, UserInfos user) {
        ZimbraEmail senderEmail = getSenderEmail(message);
        return senderEmail != null && message.getUserMapping().get(senderEmail.getAddress()).getUserId().equals(user.getUserId());
    }
}
