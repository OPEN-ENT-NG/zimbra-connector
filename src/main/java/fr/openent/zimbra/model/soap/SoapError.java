package fr.openent.zimbra.model.soap;

import fr.openent.zimbra.service.data.SoapZimbraService;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;

public class SoapError {

    private String message;
    private String code;

    public SoapError(String jsonStr) throws DecodeException {
        JsonObject errorData = new JsonObject(jsonStr);
        message = errorData.getString(SoapZimbraService.ERROR_MESSAGE, "");
        code = errorData.getString(SoapZimbraService.ERROR_CODE, "");
    }

    public String getMessage() {
        return message;
    }

    public String getCode() {
        return code;
    }
}
