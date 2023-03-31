package fr.openent.zimbra.model;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class TransactionElement implements IModel<TransactionElement> {
    private String query;
    private JsonArray params;
    private JsonArray result;

    public TransactionElement(JsonObject jsonObject) {
        throw new RuntimeException("Not implemented");
    }

    public TransactionElement(String query, JsonArray params) {
        this.query = query;
        this.params = params;
    }

    @Override
    public JsonObject toJson() {
        return new JsonObject()
                .put("action", "prepared")
                .put("statement", query)
                .put("values", params);
    }

    public String getQuery() {
        return query;
    }

    public TransactionElement setQuery(String query) {
        this.query = query;
        return this;
    }

    public JsonArray getParams() {
        return params;
    }

    public TransactionElement setParams(JsonArray params) {
        this.params = params;
        return this;
    }

    public JsonArray getResult() {
        return result;
    }

    public TransactionElement setResult(JsonArray result) {
        this.result = result;
        return this;
    }
}
