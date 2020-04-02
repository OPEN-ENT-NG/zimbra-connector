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

import {$, _, Document, moment, ng, notify, skin, template} from "entcore";
import { User} from "../model";


import {Preference} from "../model/preferences";
import http from '../model/http';

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
                $scope.mailConfig = await $scope.getMailConfig();
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
        };

        $scope.getMailConfig = async () => {
            let config = await http.get("/zimbra/mailconfig");
            return config.data;
        };
    }]);