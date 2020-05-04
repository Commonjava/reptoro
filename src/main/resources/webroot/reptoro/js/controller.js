'use strict';

var appControllers = angular.module('appControllers', []);


reptoroApp.controller('AppIndexCtrl', ['$scope', '$http', '$templateCache', '$routeParams',
  function ($scope, $http) {
    $scope.users = [];

    $http.get('https://api.randomuser.me/0.4/?results=10')
      .then(function (resp) {
        $scope.users = resp.data.results;
      }, function (resp) {
        $scope.users = [];
      });
    $scope.showDetail = function (u) {
      $scope.active = u;
    };
  }]);

reptoroApp.controller('AdminCtrl', ['$scope', '$http', '$templateCache', '$routeParams',
  function ($scope, $http) {
    $scope.header = "ADMIN PANEL";
    $scope.viewType = 'admin';
    $scope.adminPanelLoading = true;
    $scope.lastRepoAction = "";
    $scope.lastRepoActionTimestamp = Date.now();
    $scope.lastImportAction = "";
    $scope.lastImportActionTimestamp = Date.now();
    $scope.eventsPublished = 0;
    $scope.importsNotCompared = 0;
    $scope.reposNotCompared = 0;
    $scope.appLogs =
      sessionStorage.length > 0 && sessionStorage.getItem("appLogs").length > 0 ? sessionStorage.getItem("appLogs") : "";

    $scope.remoteRepoContentCount = 0;
    $scope.remoteReposCount = 0;

    $scope.sharedImportsContentCount = 0;
    $scope.sharedImportsCount = 0;

    var repoStart = {
      method: 'POST',
      url: '/reptoro/start/repos',
      headers: {
        'Content-Type': "application/json"
      },
      data: {
        "eventType": "START_REPO_PROCESS",
        "userId": $scope.user.id
      }
    };
    $scope.startRepoProcess = function () {
      $http(repoStart)
        .then(function (resp) {
          console.log(resp);
          $scope.lastRepoAction = "START";
          $scope.lastRepoActionTimestamp = Date.now();
        }, function (err) {
          console.log(err);
          $scope.lastRepoAction = "ERROR";
          $scope.lastRepoActionTimestamp = Date.now();
        });
    }

    var importsStart = {
      method: 'POST',
      url: '/reptoro/start/imports',
      headers: {
        'Content-Type': "application/json"
      },
      data: {
        "eventType": "START_IMPORTS_PROCESS",
        "userId": $scope.user.id
      }
    };
    $scope.startImportsProcess = function () {
      $http(importsStart)
        .then(function (resp) {
          console.log(resp);
          $scope.lastImportAction = "START";
          $scope.lastImportActionTimestamp = Date.now();
        }, function (err) {
          console.log(err);
          $scope.lastImportAction = "ERROR";
          $scope.lastImportActionTimestamp = Date.now();
        });
    }

    var reposStop = {
      method: 'POST',
      url: '/reptoro/stop/repos',
      headers: {
        'Content-Type': "application/json"
      },
      data: {
        "eventType": "STOP_REPO_PROCESS",
        "userId": $scope.user.id
      }
    };
    $scope.stopRepoProcess = function () {
      $http(reposStop)
        .then(function (resp) {
          // console.log(resp);
          $scope.lastRepoAction = "STOP";
          $scope.lastRepoActionTimestamp = Date.now();
        }, function (err) {
          console.log(err);
          $scope.lastRepoAction = "ERROR";
          $scope.lastRepoActionTimestamp = Date.now();
        });
    }

    var importsStop = {
      method: 'POST',
      url: '/reptoro/stop/imports',
      headers: {
        'Content-Type': "application/json"
      },
      data: {
        "eventType": "STOP_IMPORTS_PROCESS",
        "userId": $scope.user.id
      }
    };
    $scope.stopImportsProcess = function () {
      $http(importsStop)
        .then(function (resp) {
          console.log(resp);
          $scope.lastImportAction = "STOP";
          $scope.lastImportActionTimestamp = Date.now();
        }, function (err) {
          console.log(err);
          $scope.lastImportAction = "ERROR";
          $scope.lastImportActionTimestamp = Date.now();
        });
    }

    $http.get('/reptoro/imports/notvalidated/count')
      .then(function (resp) {
        if(resp.data.failed) {
          console.log(resp.data.failed);
          $scope.importsNotCompared = resp.data.failed;
        } else {
          // console.log(resp.data.result);
          $scope.importsNotCompared = resp.data.result.count;
        }
      }, function (resp) {
        console.log(err);
      });

    $http.get('/reptoro/repos/notvalidated/count')
      .then(function (resp) {
        if(resp.data.failed) {
          console.log(resp.data.failed);
          $scope.reposNotCompared = resp.data.failed;
        } else {
          // console.log(resp.data.result);
          $scope.reposNotCompared = resp.data.result.count;
        }
      }, function (err) {
        console.log(err);
      });

    $http.get('/reptoro/repos/contents/count')
      .then(function (resp) {
        if(resp.data.failed) {
          console.log(resp.data.failed);
          $scope.remoteRepoContentCount = resp.data.failed;
        } else {
          // console.log(resp.data.result);
          $scope.remoteRepoContentCount = resp.data.result.count;
        }
      }, function (err) {
        console.log(err);
      });

    $http.get('/reptoro/imports/contents/count')
      .then(function (resp) {
        if(resp.data.failed) {
          console.log(resp.data.failed);
          $scope.sharedImportsContentCount = resp.data.failed;
          $scope.adminPanelLoading = false;
        } else {
          console.log(resp.data.result);
          $scope.sharedImportsContentCount = resp.data.result.count;
          $scope.adminPanelLoading = false;
        }
      }, function (resp) {
        console.log(err);
        $scope.adminPanelLoading = false;
      });

    $http.get('/reptoro/repos/count')
      .then(function (resp) {
        if(resp.data.failed) {
          console.log(resp.data.failed);
          $scope.remoteReposCount = resp.data.failed;
        } else {
          // console.log(resp.data.result);
          $scope.remoteReposCount = resp.data.result.count;
        }
      }, function (err) {
        console.log(err);
      });

    $http.get('/reptoro/imports/count')
      .then(function (resp) {
        if(resp.data.failed) {
          console.log(resp.data.failed);
          $scope.sharedImportsCount = resp.data.failed;
        } else {
          // console.log(resp.data.result);
          $scope.sharedImportsCount = resp.data.result.count;
        }
      }, function (resp) {
        console.log(err);
      });

    var eventbus = new EventBus('/eventbus');
    eventbus.onopen = () => {
      eventbus.registerHandler('publish.to.client', (err, message) => {
        var event = message.body;
        // console.log(message);
        if (event != null) {
          var time = new Date();
          var timeMilis = time.getTime();
          $scope.appLogs = $scope.appLogs + "["+ time.toGMTString() +"] " + JSON.stringify(event.msg) + "\n";
          // store application logs to session storage...
          if(sessionStorage.getItem('appLogs') == '') {
            sessionStorage.setItem('appLogs',$scope.appLogs);
          }
          $scope.$apply();
        }
      });
      eventbus.registerHandler('monitor.metrics', (err, message) => {
        var res = message.body;

         // console.log(res);

        if (res != null) {
          if(res.count) {
            if(res.failed) {
              console.log(res);
            } else {
              if(res.repos) {
                // repo count
                $scope.reposNotCompared = res.count;
              } else {
                // import count
                $scope.importsNotCompared = res.count;
              }
            }
          } else {
            if(res['vertx.verticles'] || res['vertx.timers']) {
              $scope.metrics = res;
              var time = (new Date()).getTime();
              $scope.eventsPublished = res['vertx.eventbus.messages.published'].count;
//            $scope.eventsPublished = res['vertx.pools.worker.vert.x-internal-blocking.usage'].count ;
              $scope.eventsData.yData.push($scope.eventsPublished);
              $scope.eventsData.xData.push(Date.now());
              $scope.discardedMesgs = res['vertx.eventbus.messages.discarded'].count;
              $scope.lineChartData.xData.push(Date.now());
              $scope.lineChartData.yData0.push($scope.discardedMesgs);
            } else {
              console.log("Unknown msg:");
              console.log(res);
            }
          }

          $scope.$apply();
        }
      });
    }

    // Trend Chart
    $scope.eventsConfig = {
      chartId      : 'reptoroTrendsChart',
      title        : 'Reptoro Internal Blocking usage',
      layout       : 'large',
      trendLabel   : 'Reptoro Internal Blocking usage',
      valueType    : 'actual',
      timeFrame    : 'Last 15 Minutes',
      units        : 'events/seconds',
      tooltipType  : 'events',
      compactLabelPosition  : 'left'
    };
    var today = new Date();
    var dates = ['dates'];
    var values = ['used'];
    $scope.dates = dates;
    $scope.eventsData = {
      dataAvailable: true,
      total: 250,
      xData: dates,
      yData: values
    };
    $scope.eventsShowXAxis = false;
    $scope.eventsShowYAxis = false;

    // Line Chart
    $scope.lineConfig = {
      chartId: 'exampleLine',
      grid: {y: {show: false}},
      point: {r: 1},
      // color: {pattern: [pfUtils.colorPalette.blue, pfUtils.colorPalette.green]}
    };

    var today = new Date();
    var dates = ['dates'];
    $scope.lineChartData = {
      dataAvailable: true,
      xData: dates,
      yData0: ['Discarded']
    };

    $scope.custShowXAxis = false;
    $scope.custShowYAxis = false;
    $scope.custAreaChart = false;

    $scope.addDataPoint = function () {
      $scope.lineChartData.xData.push(new Date($scope.lineChartData.xData[$scope.data.xData.length - 1].getTime() + (24 * 60 * 60 * 1000)));
      $scope.lineChartData.yData0.push(Math.round(Math.random() * 100));
    };

    $scope.resetData = function () {
      $scope.lineChartData = {
        xData: dates,
        yData0: ['Discarded']
      };
    };

  }]);

reptoroApp.controller('ReposCtrl', ['$scope', '$http', '$templateCache', '$routeParams', '$filter','$window','$location',
  function ($scope, $http, $route, $routeParams, $location, $window) {
    $scope.repos = [];
    $scope.cause = "";
    $scope.viewType = 'repos';
    $scope.header = "Remote Repositories";
    $scope.pageSize = 10;
    $scope.pageNumber = 1;
    $scope.numTotalItems = $scope.repos.length;
    $scope.pageSizeIncrements = [10, 20, 30, 40, 50]
    $scope.maxPages = 1;

    $http.get('/reptoro/repository')
      .then(function (resp) {
        $scope.repos = resp.data.results;
        $scope.cause = resp.data.cause;
        $scope.numTotalItems = $scope.repos.length;
        $scope.maxPages = Math.ceil($scope.numTotalItems / $scope.pageSize);
      }, function (resp) {
        $scope.repos = [];
        $scope.cause = resp.cause;
      });

    $scope.showDetail = function (u) {
      $scope.active = u;
    };

    $scope.changeRepoProtocol = function(repo) {
      console.log(repo);
      var repoChangeProtocol = {
        method: 'POST',
        url: '/reptoro/repo/change',
        headers: {
          'Content-Type': "application/json"
        },
        data: {
          "eventType": "CHANGE_REPO_PROTOCOL",
          "repo": repo
        }
      };
      $http(repoChangeProtocol)
        .then(function (resp) {
          var result = resp.data;
          if(result.operation == 'success') {

            console.log(resp);
            $window.location.reload();

          } else {
            console.log(resp);
          }
        }, function (err) {
          console.log(err);
        });
    }

    $scope.reverseRepoProtocol = function(repo) {
      console.log(repo);
      var repoReverseProtocol = {
        method: 'POST',
        url: '/reptoro/repo/reverse',
        headers: {
          'Content-Type': "application/json"
        },
        data: {
          "eventType": "REVERSE_REPO_PROTOCOL",
          "repo": repo
        }
      };
      $http(repoReverseProtocol)
        .then(function (resp) {
          var result = resp.data;
          if(result.operation == 'success') {

            console.log(resp);
            $window.location.reload();

          } else {
            console.log(resp);
          }
        }, function (err) {
          console.log(err);
        });
    }

    $scope.scanRepo = function(repo) {
      console.log(repo);
      repo['stage'] = 'START';

      var rescanRepo = {
        method: 'POST',
        url: '/reptoro/repo/scan',
        headers: {
          'Content-Type': "application/json"
        },
        data: {
          "eventType": "RESCAN_REPO",
          "repo": repo
        }
      };
      $http(rescanRepo)
        .then(function (resp) {
          console.log(resp);
        }, function (err) {
          console.log(err);
        });
    }
  }]);

reptoroApp.controller('SharedCtrl', ['$scope', '$http', '$templateCache', '$routeParams', '$filter', '$log',
  function ($scope, $http, $log) {
    $scope.sharedimports = [];
    $scope.cause = "";
    $scope.viewType = 'imports';
    $scope.header = "Shared Imports";
    $scope.pageSize = 10;
    $scope.pageNumber = 1;
    $scope.numTotalItems = $scope.sharedimports.length;
    $scope.pageSizeIncrements = [5, 10, 20, 40, 80, 100,500];

    $http.get('/reptoro/sharedimport')
      .then(function (resp) {
        $scope.sharedimports = resp.data.results;
        $scope.cause = resp.data.cause;
        $scope.numTotalItems = $scope.sharedimports.length;
        // console.log($scope.sharedimports);
      }, function (resp) {
        $scope.sharedimports = [];
        $scope.cause = resp.cause;
      });
    $scope.showDetail = function (u) {
      $scope.active = u;
    };


    $scope.rescanSharedImport = function (sharedImport) {
      console.log(sharedImport);

      var rescanSharedImport = {
        method: 'POST',
        url: '/reptoro/import/scan',
        headers: {
          'Content-Type': "application/json"
        },
        data: {
          "eventType": "RESCAN_SHARED_IMPORT",
          "import": sharedImport
        }
      };
      $http(rescanSharedImport)
        .then(function (resp) {
          console.log(resp);

        }, function (err) {
          console.log(err);
        });
    }
  }]);

reptoroApp.controller("HeaderCtrl", ['$scope', '$http', '$rootScope', '$location', '$log', '$window',
  function ($scope, $http, $rootScope, $location, $log,$window) {
    $scope.authUrl = '/reptoro/login';
    $scope.user = {};
    $rootScope.user = {};
    $scope.viewType = 'admin';

    $scope.logout = function () {
      $http.post('/reptoro/logout', {})
        .then(function (response) {
          $rootScope.authenticated = false;
          $scope.user = {};
          $rootScope.user = {};
          $location.path("/");
          $window.location.reload();
          $rootScope.$broadcast('logout', "update");
        }, function (response) {
          $scope.user = {};
          $rootScope.$broadcast('logout', "update");
        });
    };

    var fetchUser = function () {
      $http({method: 'GET', url: $scope.authUrl})
        .then(function (response) {
          $scope.user = response.data;
          $rootScope.authenticated = true;
          $rootScope.user = response.data;
          $log.log(response.data);
        }, function (response) {
          scope.user = {};
          $rootScope.authenticated = false;
        });
    };

    fetchUser();
  }
]);

reptoroApp.controller('DownloadsCtrl',['$scope', '$http', '$templateCache', '$routeParams', '$location', '$route',
  function ($scope, $http,$routeParams,$location,$route) {
    $scope.downloads = [];
    $scope.pageSize = 10;
    $scope.pageNumber = 1;
    $scope.numTotalItems = $scope.downloads.length;
    $scope.pageSizeIncrements = [5, 10, 20, 40, 80, 100,500,1000];
    $scope.buildId = $location.id;
    $scope.header = "DOWNLOADS FOR " + $scope.buildId ;


    $http.get('/reptoro/import/downloads/' + $scope.buildId)
      .then(function (resp) {
        $scope.downloads = resp.data.results;
        $scope.cause = resp.data.cause || '';
        $scope.numTotalItems = $scope.downloads.length;
        // console.log($scope.downloads);
      }, function (resp) {
        $scope.downloads = [];
        $scope.status = resp.status;
      });

    $scope.redownload = function (download) {
      console.log(download);
    }

    $scope.openModalHeaders = function(headers) {
      console.log(JSON.parse(headers));
    }

    $scope.showSourceHeaders = function (headers) {
      $scope.sourceheaders = JSON.parse(headers);
    }
  }
]);
