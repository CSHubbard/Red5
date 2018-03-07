//Initialize Dependencies
var express = require('express');
var path = require('path');
var app = express();
var server = require('http').Server(app);
var io = require('socket.io')(server);

var port = 3000;

//Start server
server.listen(port, function(){
	console.log('Server listening at port %s', port);
});

var ConnectionData = {
	phoneConnectionStatus: "Disconnected",
	droneConnectionStatus: "Disconnected",
	recievedData: "None"
}
//Server send HTML file to client
//This piece is only for testing the server with browsers
//set static path
app.use(express.static(path.join(__dirname, 'public')));
app.set('views', path.join(__dirname, 'views'));
app.set('view engine', 'ejs');

function renderData(req, res) {
    res.render('pages/index', {
		ConnectionData: ConnectionData
	})
}

app.get('/', renderData);

//Action that happens when the connection status changes
io.on('connection', function(client){
	//handles the connection of a client
	console.log('Client connected...');
	//handles the disconnection of a client
	client.on('disconnect', function(){
		console.log('Client disconnected...');
	});
	
	//Updates index.ejs when connections happen	
	client.on('join', function(room){
		client.join(room);
		switch(room) {
			case "Android":
				ConnectionData.phoneConnectionStatus= "Connected";
				client.to('Server').emit('update', {type:"connection-phone", conData: ConnectionData});
				break;
			case "Drone":			
				ConnectionData.droneConnectionStatus= "Connected";
				client.to('Server').emit('update', {type:"connection-drone", conData: ConnectionData});
				break;
		}
		console.log(room + ' client joined!');
	});
	
	//Updates index.ejs when disconnections happen	
	client.on('leave', function(room){
		switch(room) {
			case "Android":
				ConnectionData.phoneConnectionStatus= "Disconnected";
				client.to('Server').emit('update', {type:"disconnection-phone", conData: ConnectionData});
				break;
			case "Drone":
				ConnectionData.droneConnectionStatus= "Disconnected";
				client.to('Server').emit('update', {type:"disconnection-drone", conData: ConnectionData});
				break;
		}
		console.log(room + ' client left!');
	});
	
	//Receive data from clients in "Android" room and sends it to index.ejs
	client.on('pushData', function(data){	
		ConnectionData.recievedData = data;
		client.to('Server').emit('update', {type: "push", conData: ConnectionData});

		//Send data to clients "Drone" room
		//INSERT DRONE STUFF HERE
		//io.sockets.in(Drone).emit(droneData);
	});
});
	

