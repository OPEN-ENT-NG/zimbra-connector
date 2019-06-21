/*
 * Copyright (c) Région Ile-de-France, Région Nouvelle-Aquitaine, CGI, 2016.
 * This file is part of OPEN ENT NG. OPEN ENT NG is a versatile ENT Project based on the JVM and ENT Core Project.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with OPEN ENT NG is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of OPEN ENT NG, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package fr.openent.zimbra.model.soap.model;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.model.constant.SoapConstants;
import fr.openent.zimbra.model.constant.ZimbraConstants;
import fr.openent.zimbra.model.soap.SoapRequest;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import static fr.openent.zimbra.model.constant.SoapConstants.*;
import static fr.openent.zimbra.model.constant.ZimbraConstants.*;

public class SoapAccount {

    private String name;
    private String id;


    private static Logger log = LoggerFactory.getLogger(SoapAccount.class);

    public static void getUserAccount(String userId, Handler<AsyncResult<SoapAccount>> handler) {
        String accountName = userId + "@" + Zimbra.appConfig.getZimbraDomain();
        JsonObject content = new JsonObject()
                .put(SoapConstants.ACCOUNT, new JsonObject()
                    .put(SoapConstants.ID_BY, ZimbraConstants.ACCT_NAME)
                    .put(SoapConstants.ATTR_VALUE, accountName));


        SoapRequest getAccountRequest = SoapRequest.AdminSoapRequest(SoapConstants.GET_ACCOUNT_REQUEST);
        getAccountRequest.setContent(content);
        getAccountRequest.start(processAccountHandler(handler));
    }

    private static Handler<AsyncResult<JsonObject>> processAccountHandler(Handler<AsyncResult<SoapAccount>> handler) {
        return res -> {
            if(res.failed()) {
                handler.handle(Future.failedFuture(res.cause()));
            } else {
                JsonObject jsonResponse = res.result();
                try {
                    JsonObject jsonAccount = jsonResponse.getJsonObject(BODY)
                            .getJsonObject(GET_ACCOUNT_RESPONSE)
                            .getJsonArray(ACCT_INFO_ACCOUNT).getJsonObject(0);

                    SoapAccount account = createFromJson(jsonAccount);
                    handler.handle(Future.succeededFuture(account));
                } catch (Exception e) {
                    handler.handle(Future.failedFuture(e));
                }
            }
        };
    }

    private static SoapAccount createFromJson(JsonObject accountData) throws IllegalArgumentException {
        SoapAccount account = new SoapAccount();
        account.id = accountData.getString(ZIMBRA_ID, "");
        account.name = accountData.getString(FOLDER_NAME, "");
        // todo get attributes
        return account;
    }
}
