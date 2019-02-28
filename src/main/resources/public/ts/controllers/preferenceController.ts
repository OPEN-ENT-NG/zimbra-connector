import {$, _, Document, moment, ng, notify, skin, template} from "entcore";
import { User} from "../model";


import {Preference} from "../model/preferences";

export let preferenceController = ng.controller("preferenceController", [
    "$location",
    "$scope",
    "$timeout",
    "$compile",
    "$sanitize",
    "model",
    "route",
    function($location, $scope, $timeout, $compile, $sanitize, model, route) {
        route({
            preferences: async () => {
                template.open("main", "setting/main");
                template.open("body", "setting/expert");
                model.me.workflow.load(['timeline','directory']);
                $scope.pickTheme = skin.pickSkin;
                $scope.account = new User();
                await $scope.account.findMe();
                $scope.preference = await new Preference();
                $scope.$apply();
            }
        });

        $scope.hasNotificationWorkflow = () =>{
            return model.me.hasWorkflow('org.entcore.timeline.controllers.TimelineController|mixinConfig')
        };
        $scope.hasHistoryWorkflow = () =>{
            return model.me.hasWorkflow('org.entcore.timeline.controllers.TimelineController|historyView')
        };
        $scope.hasSwitchThemeWorkflow = () =>{
            return model.me.hasWorkflow('org.entcore.directory.controllers.UserBookController|userBookSwitchTheme')
        };
        $scope.saveModeExpert = async (preference: Preference) => {
          let result = await preference.save();
        }
    }]);