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


//Server send HTML file to client
//This piece is only for testing the server with browsers
//set static path
app.use(express.static(path.join(__dirname, 'public')));
app.set('views', path.join(__dirname, 'views'));
app.set('view engine', 'ejs');

app.get('/', (req, res) => res.render('pages/index'));

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
		console.log(data); //Prints orientation data
		//DO SOMETHING WITH THE DATA FROM phone HERE
	});
	
	//Send data to clients "Drone" room
		//INSERT DRONE STUFF HERE
		//io.sockets.in(Drone).emit(droneData);
});
