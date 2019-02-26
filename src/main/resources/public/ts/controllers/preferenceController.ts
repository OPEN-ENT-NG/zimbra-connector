import {$, _, Document, idiom as lang, moment, ng, notify, skin, template} from "entcore";
import {DISPLAY, Mail, quota, SCREENS, SystemFolder, User, UserFolder, Zimbra,} from "../model";

import {Mix} from "entcore-toolkit";
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
        $scope.state = {
            selectAll: false,
            filterUnread: false,
            searching: false,
            current: undefined,
            newItem: undefined,
            draftError: false,
            dragFolder: undefined,
            emptyMessage: lang.translate("folder.empty"),
            searchFailed: false,
            draftSaveDate: null
        };
        $scope.display = new DISPLAY();
        $scope.viewMode = $scope.display.LIST;
        $scope.zimbra = Zimbra.instance;
        $scope.displayLightBox = {
            readMail : false
        };
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