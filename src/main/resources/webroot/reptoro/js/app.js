'use strict';

var reptoroApp = angular.module('ReptoroApp', [
    'ngRoute',
    'appControllers'
]);

/**
 * Config routes
 */
reptoroApp.config(['$routeProvider', function ($routeProvider) {
    $routeProvider
    .when('/', {
      templateUrl: '/reptoro/repos.html',
      controller: 'ReposCtrl'
    })
    .when('/remoterepos', {
      templateUrl: '/reptoro/repos.html',
      controller: 'ReposCtrl'
    })
    .when('/sharedimports', {
      templateUrl: '/reptoro/imports.html',
      controller: 'SharedCtrl'
    })
    // .when('/cart', {
    //   templateUrl: 'app/view/cart.html',
    //   controller: 'CartCtrl'
    // })
    // .when('/account', {
    //   templateUrl: 'app/view/account.html',
    //   controller: 'AccountCtrl'
    // })
    // .when('/orders', {
    //   templateUrl: 'app/view/orders.html',
    //   controller: 'UserOrderCtrl'
    // })
    // .when('/orders/:orderId', {
    //   templateUrl: 'app/view/order-detail.html',
    //   controller: 'OrderDetailCtrl'
    // })
    .when('/404', {
      templateUrl: '/404.html'
    })
    .otherwise({
      redirectTo: '/404'
    })
  }]);