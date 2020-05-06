'use strict';

var reptoroApp = angular.module('ReptoroApp', [
    'ngRoute',
    'patternfly',
    'patternfly.charts',
    'appControllers',
    'patternfly.modals',
    'patternfly.notification'
]);

/**
 * Config routes
 */
reptoroApp.config(['$routeProvider', function ($routeProvider) {
    $routeProvider
    .when('/', {
      templateUrl: '/reptoro/admin.html',
      controller: 'AdminCtrl'
    })
    .when('/remoterepos', {
      templateUrl: '/reptoro/repos.html',
      controller: 'ReposCtrl'
    })
    .when('/sharedimports', {
      templateUrl: '/reptoro/imports.html',
      controller: 'SharedCtrl'
    })
    .when('/admin', {
      templateUrl: '/reptoro/admin.html',
      controller: 'AdminCtrl'
    })
    .when('/downloads/:id', {
      templateUrl: '/reptoro/downloads.html',
      controller: 'DownloadsCtrl'
    })
    .when('/404', {
      templateUrl: '/404.html'
    })
    .otherwise({
      redirectTo: '/404'
    })
  }]);

// startFrom Filter for Pagination
reptoroApp.filter('startFrom', function() {
  return function(input, start) {
      start = +start;
      return input.slice(start);
  }
});

reptoroApp.filter('split', function() {
  return function(input, splitChar, splitIndex) {
    // do some bounds checking here to ensure it has that index
    return input.split(splitChar)[splitIndex];
  }
});

reptoroApp.filter('splitLast', function() {
    return function(input, splitChar) {
      var split = input.split(splitChar);
      return split[split.length-1];
    }
});
