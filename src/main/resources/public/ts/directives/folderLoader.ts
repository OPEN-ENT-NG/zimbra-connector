
import { ng, idiom as lang } from 'entcore';

export let folderLoader = ng.directive('folderLoader', function() {
    return {
        restrict: 'E',
        transclude: true,
        scope: {
            folderName: '=',
            display: '='
        },
        template:
        '<div id="folder-loader" ng-show="display">'+
            '<div class="spinner">'+
            '<i class="mail bounce1"></i>'+
            '<i class="mail bounce2"></i>'+
            '<i class="mail bounce3"></i>'+
            '</div>'+
            '</div>',
        replace: true
    };
});