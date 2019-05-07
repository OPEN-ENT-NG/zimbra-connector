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

import { Behaviours, http, notify, _ } from "entcore";

Behaviours.register("zimbra", {
    rights: {
        workflow: {
            draft: "fr.openent.zimbra.controllers.ZimbraController|createDraft",
            read: "fr.openent.zimbra.controllers.ZimbraController|view",
            expert: "fr.openent.zimbra.controllers.ZimbraController|preauth",
            outsideCommunication: "fr.openent.zimbra.controllers.ZimbraController|zimbraOutside"
        }
    },
    sniplets: {
        ml: {
            title: "sniplet.ml.title",
            description: "sniplet.ml.description",
            controller: {
                init: function() {
                    this.message = {};
                },
                initSource: function() {
                    this.setSnipletSource({});
                },
                send: function() {
                    this.message.to = _.map(
                        this.snipletResource.shared,
                        function(shared) {
                            return shared.userId || shared.groupId;
                        }
                    );
                    this.message.to.push(this.snipletResource.owner.userId);
                    http()
                        .postJson("/zimbra/send", this.message)
                        .done(function() {
                            notify.info("ml.sent");
                        })
                        .e401(function() {});
                    this.message = {};
                }
            }
        }
    }
});
