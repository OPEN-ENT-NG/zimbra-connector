import { routes, ng } from "entcore";
import { zimbraController } from "./controllers/controller";
import { printController } from "./controllers/printController";
import { recipientList, switchSearch, ngBottomScroll } from "./directives/index";


routes.define(function($routeProvider) {
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
});

ng.controllers.push(zimbraController);
ng.controllers.push(printController);
ng.directives.push(recipientList);
ng.directives.push(switchSearch);
ng.directives.push(ngBottomScroll);
