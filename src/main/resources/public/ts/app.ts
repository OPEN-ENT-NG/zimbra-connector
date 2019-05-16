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

import { routes, ng , ui } from "entcore";

import * as controllers from './controllers';
import * as directives from './directives';

for (let controller in controllers) {
    ng.controllers.push(controllers[controller]);
}

for (let directive in directives) {
    ng.directives.push(directives[directive]);
}



routes.define(function($routeProvider) {
    if(location.pathname==="/zimbra/zimbra"){
        $routeProvider
            .when("/read-mail/:mailId", {
                action: "readMail"
            })
            .when("/write-mail/:userId", {
                action: "writeMail"
            })
            .when("/write-mail", {
                action: "writeMail"
            })
            .when("/inbox", {
                action: "inbox"
            })
            .when("/printMail/:mailId", {
                action: "viewPrint"
            })

            .otherwise({
                redirectTo: "/inbox"
            });
    }else if(location.pathname==="/zimbra/print"){
        $routeProvider
            .when("/printMail/:mailId", {
                action: "viewPrint"
            })
            .otherwise({
                action: "redirectToZimbra"
            });
    }else if(location.pathname==="/zimbra/preferences"){
        $routeProvider
            .otherwise({
                action: "preferences"
            });
    }
    else if(location.pathname==="zimbra/admin-console"){
        $routeProvider
            .otherwise({
                action: "outSide"
            })
    }


});