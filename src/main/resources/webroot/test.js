

window.addEventListener('load' , function(evt) {

  var eb = new EventBus('/eventbus');
  console.log("EventBus connection is open!")
  eb.onopen = function(evt) {
    eb.registerHandler('listings.urls', function (error, message) {
      console.log("LU: " ,message);
    });

    eb.registerHandler("open.circuit.browsed.stores", function(error,message) {
      console.log("OCBS: " , message);
    });

    eb.registerHandler("vertx.circuit-breaker", function(error,message) {
      console.log("CB: " , message);
    });
    eb.registerHandler("error.processing",function(error,message) {
      console.log("ERR: " , message);
    })
  }
});
