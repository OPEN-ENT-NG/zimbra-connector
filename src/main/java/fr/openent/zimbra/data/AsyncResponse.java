package fr.openent.zimbra.data;

public abstract class AsyncResponse {

    protected boolean responseOK = true;

    public boolean isOK() {
        return responseOK;
    }

    public boolean isKO() {
        return !responseOK;
    }
}
