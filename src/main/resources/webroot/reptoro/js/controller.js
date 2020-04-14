'use strict';

var appControllers = angular.module('appControllers', []);


reptoroApp.controller('AppIndexCtrl', ['$scope', '$http', '$templateCache', '$routeParams',
function($scope, $http) {
    $scope.users = [];

    $http.get('https://api.randomuser.me/0.4/?results=10')
    .then(function(resp) {
        $scope.users = resp.data.results;
    }, function(resp) {
        $scope.users = [];
    });
    $scope.showDetail = function(u) {
        $scope.active = u;
    };
}]);

reptoroApp.controller('ReposCtrl', ['$scope', '$http', '$templateCache', '$routeParams',
function($scope, $http) {
    $scope.repos = [];
    $scope.cause = "";

    $http.get('/reptoro/repository')
    .then(function(resp) {
        $scope.repos = resp.data.results;
        $scope.cause = resp.data.cause;
    }, function(resp) {
        $scope.repos = [];
        $scope.cause = resp.cause;
    });
    $scope.showDetail = function(u) {
        $scope.active = u;
    };
}]);

reptoroApp.controller('SharedCtrl', ['$scope', '$http', '$templateCache', '$routeParams',
function($scope, $http) {
    $scope.sharedimports = [];
    $scope.cause = "";

    $http.get('/reptoro/sharedimport')
    .then(function(resp) {  
        $scope.sharedimports = resp.data.results;
        $scope.cause = resp.data.cause;
    }, function(resp) {
        $scope.sharedimports = [];
        $scope.cause = resp.cause;
    });
    $scope.showDetail = function(u) {
        $scope.active = u;
    };
}]);

reptoroApp.controller("HeaderCtrl",['$scope', '$http', '$rootScope', '$location', '$log',
    function($scope, $http, $rootScope, $location,$log) {
        $scope.authUrl = '/reptoro/login';
        $scope.user = {};
        $rootScope.user = {};

        $scope.logout = function () {
            $http.post('/reptoro/logout', {})
            .then(function(response) {
              $rootScope.authenticated = false;
              $scope.user = {};
              $rootScope.user = {};
              $location.path("/");
              $location.reload($location.path);
              $rootScope.$broadcast('logout', "update");
            },function(response) {
              $scope.user = {};
              $rootScope.$broadcast('logout', "update");
            });
          };
      
          var fetchUser = function () {
            $http({method: 'GET',url: $scope.authUrl})
            .then(function(response) {
              $scope.user = response.data;
              $rootScope.authenticated = true;
              $rootScope.user = response.data;
              $log.log(response.data);
            },function(response) {
              scope.user = {};
              $rootScope.authenticated = false;
            });
          };
      
          fetchUser();   
    }
]);