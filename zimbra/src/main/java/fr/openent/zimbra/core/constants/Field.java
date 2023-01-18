package fr.openent.zimbra.core.constants;

public class Field {

    private Field() {
        throw new IllegalStateException("Utility class");
    }

    public static final String CONTENT_TYPE = "Content-Type";
    public static final String CONTENT_DISPOSITION = "Content-Disposition";
    public static final String COOKIE = "Cookie";
    public static final String AUTH_TOKEN = "authToken";
    public static final String NAME = "name";
    public static final String ERROR = "error";
    public static final String STATUS = "status";
    public static final String ID = "id";
    public static final String SIZE = "size";
    public static final String APP = "zimbra";
    public static final String FALSE = "false";
    public static final String INLINE = "inline";
    public static final String HTTPCLIENT = "httpClient";
    public static final String ZIMBRARESPONSE = "zimbraResponse";
    public static final String USERNAME = "username";
    public static final String UNAUTHORIZED = "unauthorized";

    public static final String MAIL_ATTACHMENT_TOO_BIG = "mail.ATTACHMENT_TOO_BIG";
    public static final String MAIL_INVALID_REQUEST = "mail.INVALID_REQUEST";
}
