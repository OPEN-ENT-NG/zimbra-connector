package fr.openent.zimbra.model;

import io.vertx.core.json.JsonObject;

/**
 * ⚠ Classes implementing this model must have a public constructor with JsonObject parameter
 */
public interface IModel<I extends IModel<I>> {
    /**
     * Convert object to jsonObject
     * @return jsonObject
     */
    JsonObject toJson();
}