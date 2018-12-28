import { ng, _ } from "entcore";
export const ngBottomScroll = ng.directive('ngBottomScroll',  () => {
    return {
        restrict: 'A',
        link: function (scope, element, attrs) {
            let MyElement = element[0];
            element.bind('scroll', function () {
                if (MyElement.scrollTop + MyElement.offsetHeight >= MyElement.scrollHeight) {
                    scope.$apply(attrs.ngBottomScroll)
                }
            })
        }
    }
});