//Initialize Dependencies
var app = require('express')();
var server = require('http').Server(app);
var io = require('socket.io')(server);

var port = 3000;

//Start server
server.listen(port, function(){
	console.log('Server listening at port %s', port);
});


//Server send HTML file to client
//This piece is only for testing the server with browsers
app.get('/', function(req, res){
	res.sendFile(__dirname + '/index.html');
});

//Action that happens when a client is connected to the server
io.on('connection', function(client){
	console.log('Client connected...');
	client.on('disconnect', function(){
		console.log('Client disconnected...');
	});
	client.on('join', function(room){
		client.join(room);
		console.log(room + ' client joined!');
	});
	//Receive data from clients in "Android" room
	client.on('pushData', function(data){
		console.log(data); //Prints gyroscope data
		//DO SOMETHING WITH THE DATA FROM GYROSCOPE HERE
	});
	//Send data to clients "Drone" room
		//INSERT DRONE STUFF HERE
		//io.sockets.in(Drone).emit(droneData);
});
