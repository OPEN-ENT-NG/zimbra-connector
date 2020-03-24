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

function AdminController($location, $scope, $timeout, $compile, $sanitize, model, route) {

    route({
        outSide:  function() {
            template.open("body", "out-side-communication");
            $scope.selectLeafMenu($scope.leafMenu[0]);
        }
    });
    $scope.structures = model.structures;
    $scope.template = template;
    $scope.lang = lang;


    $scope.themes = [
        {
            name: "pink",
            path: "default"
        },
        {
            name: "orange",
            path: "orange"
        },
        {
            name: "blue",
            path: "blue"
        },
        {
            name: "purple",
            path: "purple"
        },
        {
            name: "red",
            path: "red"
        },
        {
            name: "green",
            path: "green"
        },
        {
            name: "grey",
            path: "grey"
        }
    ];
    $scope.setTheme = function(theme){
        ui.setStyle('/public/admin/'+theme.path+'/');
        http.put('/userbook/preference/admin', {
            name: theme.name,
            path: theme.path
        })
    };
    $scope.getCurrentLeaf = function(){
        return _.findWhere($scope.leafMenu, { name: $scope.currentLeaf })
    };
    $scope.filterChildren = function(struct){
        if(struct === $scope.structure)
            return struct.children;

        var parents = [];

        var recursivelyTagParents = function(struct){
            if(typeof struct.parents === 'undefined')
                return;

            _.forEach(struct.parents, function(p){
                if(parents.indexOf(p) < 0)
                    parents.push(p);
                recursivelyTagParents(p)
            })
        };
        recursivelyTagParents(struct);

        //Prevents infinite loops when parents are contained inside children.
        return _.filter(struct.children, function(child){
            return !child.selected || !_.findWhere(parents, {id: child.id})
        })
    };
    $scope.refreshScope = function(){ $scope.$apply() };
    $scope.viewStructure = function(structure){
        $scope.structure = structure;
        $scope.structure.groups.sync(function(){
            $scope.linkedGroupsOpts.reorderGroups();
            $scope.$apply()
        });


    };
    $scope.setShowWhat = function(what){
        $scope.showWhat = what
    };
    $scope.selectOnly = function(structure, structureList){
        /*
            _.forEach(structure.children, function(s){ s.selected = false })
            _.forEach(structureList, function(s){ s.selected = s.id === structure.id ? true : false })
        */
        $scope.structures.forEach(function(structure){
            structure.selected = false
        });

        var recursivelySelectParents = function(structure){
            //Prevent infinite loops
            if(structure.selected)
                return;

            structure.selected = true;

            if(!structure.parents)
                return;

            _.forEach(structure.parents, function(parent){
                var parentLocated = $scope.structures.findWhere({id: parent.id });
                if(parentLocated)
                    recursivelySelectParents(parentLocated)
            })
        };
        recursivelySelectParents(structure)

    };
    $scope.linkedGroupsOpts = {
        showLinked: false,
        orderLinked: false,
        filterLinked: function(){
            if(!this.showLinked)
                return function(){ return true };
            return function(group){
                return $scope.isLinked(group)
            }
        },
        orderByLinked: function(){
            if(!this.orderLinked)
                return function(group){
                    var score = 3;
                    if(group._order){
                        score += group._order.structGroup ? -1 : 0;
                        score += group._order.linked ? -2 : 0
                    }
                    return score
                };
            return function(group){
                return $scope.isLinked(group) ? 0 : 1
            }
        },
        reorderGroups: function(){
            $scope.structure.groups.all.forEach(function(group){
                if(!group)
                    return;
                group._order = {
                    linked: $scope.isLinked(group),
                    structGroup: group.name.indexOf($scope.structure.name) > -1
                }
            })
        }
    };
    $scope.switchExternalAppGroupLink = function(group){
        if( !group)
            return;
        if($scope.isLinked(group)){
            var idx = group.roles.indexOf($scope.role.id);
            group.roles.splice(idx, 1);
            group.removeLink($scope.role.id).error(function(){
                group.roles.push($scope.role.id);
                notify.error('appregistry.notify.attribution.error');
                $scope.$apply()
            })
        } else {
            group.roles.push($scope.role.id);
            group.addLink($scope.role.id).error(function(){
                group.roles.splice(group.roles.indexOf($scope.role.id), 1);
                notify.error('appregistry.notify.attribution.error');
                $scope.$apply()
            })
        }
    };
    $scope.isLinked = function(group){
        if(!group)
            return false;
        var roleId = $scope.role.id;
        return group.roles.indexOf(roleId) >= 0
    };

    $scope.filterTopStructures = function(structure){
        return !structure.parents
    };
    $scope.leafMenu = [
        {
            name: "CommunicationExtérieure",
            text: lang.translate("communication.outside"),
            templateName: 'out-side-communication',
            onClick: function(){
                $scope.currentLeaf = "CommunicationExtérieure";
                $scope.role = new Role();
                $scope.role.sync(function(role){
                    $scope.role = role;
                });
            },
            onStructureClick: function(structure){
                $scope.viewStructure(structure);

            }
        }
    ];
    $scope.selectLeafMenu = function (menuItem) {
        template.open('body', menuItem.templateName);
        menuItem.onClick();
    }

}