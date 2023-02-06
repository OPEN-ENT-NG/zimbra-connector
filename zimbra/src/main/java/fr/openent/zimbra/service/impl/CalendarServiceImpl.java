package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.model.constant.SoapConstants;
import fr.openent.zimbra.model.soap.model.SoapICalResponse;
import fr.openent.zimbra.service.CalendarService;
import fr.openent.zimbra.service.data.SoapZimbraService;
import fr.wseduc.webutils.http.Renders;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.user.UserInfos;

public class CalendarServiceImpl implements CalendarService {
    protected static final Logger log = LoggerFactory.getLogger(Renders.class);

    private final SoapZimbraService soapService;

    public CalendarServiceImpl(SoapZimbraService soapService) {
        this.soapService = soapService;
    }

    public Future<String> getICal(UserInfos user) {
        return getICal(user, null, null);
    }

    public Future<String> getICal(UserInfos user, Long rangeStart, Long rangeEnd) {
        Promise<String> promise = Promise.promise();

        JsonObject icalRequest = new JsonObject()
                .put(Field._JSNS, SoapConstants.NAMESPACE_MAIL);

        if (rangeStart != null) {
            icalRequest.put("s", rangeStart);
        }

        if (rangeEnd != null) {
            icalRequest.put("e", rangeEnd);
        }

        JsonObject searchRequest = new JsonObject()
                .put(Field.NAME, Field.GETICALREQUEST)
                .put(Field.CONTENT, icalRequest);

        soapService.callUserSoapAPI(searchRequest, user, getICalResponse -> {
            if (getICalResponse.isLeft()) {
                log.error(getICalResponse.left().getValue());
                promise.fail("zimbra.error.ical.not.retrieved");
            } else {
                String ical = new SoapICalResponse(getICalResponse.right().getValue()).getContent();
                promise.complete(ical);
            }
        });

        return promise.future();
    }

}
