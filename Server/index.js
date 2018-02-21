var express = require('express');
var path = require('path');

var app = express();
//port for communication
const PORT = 5000;

//set static path
app.use(express.static(path.join(__dirname, 'public')));
app.set('views', path.join(__dirname, 'views'));
app.set('view engine', 'ejs');

app.get('/', (req, res) => res.render('pages/index'));

app.listen(PORT, () => console.log(`Listening on ${ PORT }`));


//server stuff from https://causeyourestuck.io/2016/04/27/node-js-android-tcpip/
var net = require('net');
var sockets = [];

// creates the server (no way) and takes as a parameter a callback function called
// every time a new client connects to the server. The client's socket is contained in sock.
var svr = net.createServer(function(sock) {
    console.log('Connected: ' + sock.remoteAddress + ':' + sock.remotePort);
    sockets.push(sock);

    //sock.write('Welcome to the server!\n');
	
	// a callback called every time the client sends a message. 
    sock.on('data', function(data) {
        // for (var i=0; i<sockets.length ; i++) {
            // if (sockets[i] != sock) {
                // if (sockets[i]) {
                    // sockets[i].write(data);
                // }
            // }
        // }
		
		//doing stuff with the data revived from the client will go here!
		console.log(data);
    });
	
	// a callback called when the client disconnects from the server.
    sock.on('end', function() {
        console.log('Disconnected: ' + sock.remoteAddress + ':' + sock.remotePort);
        var idx = sockets.indexOf(sock);
        if (idx != -1) {
            delete sockets[idx];
        }
    });
});

//var svraddr = '127.0.0.1';
var svraddr = '192.168.0.8';
var svrport = 1234;

svr.listen(svrport, svraddr);
console.log('Server Created at ' + svraddr + ':' + svrport + '\n');