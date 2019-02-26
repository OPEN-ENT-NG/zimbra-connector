import { routes, ng , ui } from "entcore";
import { zimbraController, printController, preferenceController } from "./controllers/index";
import { recipientList, switchSearch, ngBottomScroll, folderLoader } from "./directives/index";



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
    else {
        $routeProvider
            .when("", {
                action: "outSide"
            })
            .otherwise({
                action: "outSide"
            });
    }


});

ng.controllers.push(zimbraController);
ng.controllers.push(printController);
ng.controllers.push(preferenceController);
ng.directives.push(recipientList);
ng.directives.push(switchSearch);
ng.directives.push(ngBottomScroll);
ng.directives.push(folderLoader);