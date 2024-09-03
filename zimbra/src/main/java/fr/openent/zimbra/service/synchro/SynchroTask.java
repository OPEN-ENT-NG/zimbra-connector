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

package fr.openent.zimbra.service.synchro;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import static fr.openent.zimbra.model.constant.BusConstants.*;

public class SynchroTask implements Handler<Long> {

    private final EventBus eb;
    private final Logger log = LoggerFactory.getLogger(SynchroTask.class);

    private String action;


    public SynchroTask(EventBus eb, String action) {
        this.eb = eb;
        this.action = action;
    }


    @Override
    public void handle(Long event) {
        log.info("Zimbra cron started : " + action);
        eb.request(SYNCHRO_BUSADDR,
                new JsonObject().put(BUS_ACTION, action),
                res -> {
                    if(res.succeeded()) {
                        log.info("Cron launch successful with action " + action);
                    } else {
                        log.error("Cron launch failed with action " + action);
                    }
                });
    }
}
