package fr.openent.zimbra.service;

import io.vertx.core.Future;
import org.entcore.common.user.UserInfos;

public interface CalendarService {
    /**
     * Gets ICal text from Zimbra API
     * @param user {@link UserInfos} the user
     * @return {@link String} the ICal text
     */
    Future<String> getICal(UserInfos user);

    /**
     * Gets ICal text from Zimbra API
     * @param user {@link UserInfos} the user
     * @param rangeStart {@link Long} the range start in milliseconds to get the events
     * @param rangeEnd {@link Long} the range end in milliseconds to get the events
     * @return {@link String} the ICal text
     */
    Future<String> getICal(UserInfos user, Long rangeStart, Long rangeEnd);

}
