#Drone Control

The purpose of this application and server combination is to use the gyroscope
and acceleration sensors of an Android device to control the movements of a
drone. The Android front-end is constructed to run on any device with Android
API 19 and above. For now, the nodeJS server must be run on a local machine.
Finally, Bluetooth and Wi-Fi capabilities must be present in the Android
device.

##How to Run the Code

###Android
	1. Pull Github Repository
	2. Import Android App into Android Studio
	3. Create an emulator with minimum Android API 19, or connect a compatible
	device with USB debugging enabled.
	4. Use build to push the Android application APK file to the intended device.
	5. Complete server setup and start server before continuing.
	6. Start the Android application on the device.
	7. Choose Connect
	8. Control Drone!!!!
	9. Disconnect when finished
	
###NodeJS Server
	1. Open TCP port 3000 in computer's firewall
	2. Install NodeJS
	3. In a terminal window, navigate to the folder where index.js is stored.
	4. Run 'nodejs index.js' to start the server
	5. Use the Android App!
	6. End the server with CTRL-C when finished.
	