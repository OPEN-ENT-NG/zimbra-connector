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

import { model, idiom as lang } from "entcore";

import http from "axios";

class Quota {
    max: number;
    used: number;
    unit: string;

    constructor() {
        this.max = 1;
        this.used = 0;
        this.unit = "Mo";
    }

    appropriateDataUnit(bytes: number) {
        var order = 0;
        var orders = {
            0: lang.translate("byte"),
            1: "Ko",
            2: "Mo",
            3: "Go",
            4: "To"
        };
        var finalNb = bytes;
        while (finalNb >= 1024 && order < 4) {
            finalNb = finalNb / 1024;
            order++;
        }
        return {
            nb: finalNb,
            order: orders[order]
        };
    }

    async refresh() {
        const response = await http.get("/zimbra/quota");
        const data = response.data;
        data.quota = data.quota / (1024 * 1024);
        data.storage = data.storage / (1024 * 1024);

        if (data.quota > 2000) {
            data.quota = Math.round((data.quota / 1024) * 10) / 10;
            data.storage = Math.round((data.storage / 1024) * 10) / 10;
            this.unit = "Go";
        } else {
            data.quota = Math.round(data.quota);
            data.storage = Math.round(data.storage);
        }

        this.max = data.quota;
        this.used = data.storage;
    }
}

export let quota = new Quota();
