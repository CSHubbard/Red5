package com.nothawk.dronecontrol;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Toast;
import android.widget.ToggleButton;
import java.net.URISyntaxException;
import io.socket.client.IO;
import io.socket.client.Socket;
import com.google.gson.Gson;

//ben imports
import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.widget.Toast;
import java.util.UUID;
import java.util.List;
import java.util.ListIterator;
//end ben imports

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class ServerConnect extends Activity implements Orientation.Listener{
    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;

    private View mContentView;
    private View mControlsView;
    private boolean mVisible;
    private Orientation mOrientation;
    private AttitudeIndicator mAttitudeIndicator;

    //ben global stuff
    public  final int REQUEST_ENABLE_BT = 1;
    public  static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    public  final int STATE_DISCONNECTED = 0;
    public final int STATE_CONNECTED = 2;
    public final static UUID UUID_NOTIFY =
            UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    public final static UUID UUID_SERVICE =
            UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    public final static byte[] arm_string = {0x24, 0x4d, 0x3c, 0x08, -56, -36, 0x05, -36, 0x05, -48, 0x07, -24, 0x03, -4};
    public final static byte[] disarm_string = {0x24, 0x4d, 0x3c, 0x08, -56, -36, 0x05, -36, 0x05, -24, 0x03, -24, 0x03, -64};
    private Context context;

    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler = new Handler();
    private static final long SCAN_PERIOD = 30000;
    private BluetoothLeScanner scanner;

    private BluetoothDevice target;
    private BluetoothGatt btGatt;
    private BluetoothGattService main_service;
    private BluetoothGattCharacteristic main_characteristic;

    //1000-2000, gonna keep centered at 1500
    private int yaw = 1500;
    private int pitch = 1500;
    private int roll = 1500;

    ToggleButton armToggle;
    ToggleButton connectToggle;

    TextView tvProgressLabel;
    //end ben globals


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_server_connect);

        mVisible = true;
        mControlsView = findViewById(R.id.fullscreen_content_controls);
        mContentView = findViewById(R.id.fullscreen_content);
        mOrientation = new Orientation(this);
        mAttitudeIndicator = findViewById(R.id.fullscreen_content);

        // Set up the user interaction to manually show or hide the system UI.
        mContentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggle();
            }
        });

        //Getting the button
        ToggleButton toggle = findViewById(R.id.serverStartEnd);
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    connectServer();
                } else {
                    disconnectServer();
                }
            }
        });

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        findViewById(R.id.serverStartEnd).setOnTouchListener(mDelayHideTouchListener);

        //ben on create stuff
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }
        context = this;
        armToggle = findViewById(R.id.armButton);
        connectToggle = findViewById(R.id.connectButton);

        //Toast.makeText(this, "Hello?",Toast.LENGTH_SHORT).show();
        if(armToggle!=null) {
            armToggle.setOnCheckedChangeListener(toggle_listener);
        }
        if(connectToggle != null ){
            connectToggle.setOnCheckedChangeListener(toggle_listener);
        }

        SeekBar seekBar = findViewById(R.id.seekBar);
        seekBar.setOnSeekBarChangeListener(seekBarChangeListener);
        tvProgressLabel = findViewById(R.id.textView2);
        tvProgressLabel.setText("Throttle: " + (seekBar.getProgress()+1000));

        if(!init_ble()){
            Toast.makeText(this, "Bluetooth must be enabled ",Toast.LENGTH_SHORT).show();
        }

        // Make sure we have access coarse location enabled, if not, prompt the user to enable it
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
        }
        //end ben on create stuff

    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mControlsView.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };

    @SuppressLint("InlinedApi")
    private void show() {
        // Show the system bar
        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            mControlsView.setVisibility(View.VISIBLE);
        }
    };

    private final Handler mHideHandler = new Handler();
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    /**
     * Schedules a call to hide() in delay milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mOrientation.startListening(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mOrientation.stopListening();
    }

    @Override
    public void onOrientationChanged(float pitch, float roll) {
        roll = -1 * roll;
        mAttitudeIndicator.setAttitude(pitch, roll);
    }

    //Create Socket
    public Socket mSocket = null;
    private void createSocket(){
        try {
                //To make this work open your local port 3000 to TCP connections
                //and then find your host name.
                //The format below is http://YOUR_PC's_HOSTNAME:PORT_NUM
                mSocket = IO.socket("http://Normandy:3000");
            } catch (URISyntaxException e) {mSocket = null;}
    }


    //Connect Socket to Server
    private void connectServer(){
        // Connection Start Toast
        Toast.makeText(getApplicationContext(), "Connecting to Server", Toast.LENGTH_SHORT)
                .show();
        //Create Socket
        createSocket();
        //Connect to nodeJS server here
        mSocket.connect();
        mSocket.emit("join", "Android"); //Join Android room
        //Send data to nodeJS Server every half-second
        handler.postDelayed(runnable, 500);
        //Send data to nodeJS Server on connect
        //sendData(mAttitudeIndicator.getPitch(), mAttitudeIndicator.getRoll());
    }

    //Initialize handler to send data every second
    Handler handler = new Handler();
    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            sendData(mAttitudeIndicator.getPitch(), mAttitudeIndicator.getRoll());
            handler.postDelayed(this, 500);
        }
    };

    //Package data into JSON and send
    private void sendData(float pitch, float roll){
        //Collect Data
        OrientationData data = new OrientationData(pitch, roll);
        Gson gson = new Gson();
        String json = gson.toJson(data);
        Log.d("test", json);
        //Send JSON to server
        mSocket.emit("pushData", json);
    }

    //Disconnect Socket from Server
    private void disconnectServer(){
        //Disconnect Toast
        Toast.makeText(getApplicationContext(), "Disconnecting Server", Toast.LENGTH_SHORT)
                .show();
        //Stop sending data
        handler.removeCallbacks(runnable);
        //Disconnect from nodeJS server here
        mSocket.emit("leave", "Android"); //Leave Android room
        mSocket.disconnect();
    }


    //EVERYTHING BELOW THIS POINT IS DRONE CODE

    /* Need coarse location data for BLE scans, requires user permission. This is the callback for the request (done in onCreate) */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    System.out.println("coarse location permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }

                    });
                    builder.show();
                }
                return;
            }
        }
    }

    /* Listener for the toggle buttons (currently just arm and connect) */
    private final CompoundButton.OnCheckedChangeListener toggle_listener = new CompoundButton.OnCheckedChangeListener()
    {
        @Override
        public void onCheckedChanged (CompoundButton buttonView,boolean isChecked){
            //Toast.makeText(context, "The Switch is " + (isChecked ? "on" : "off"),Toast.LENGTH_SHORT).show();
            switch (buttonView.getId()) {
                case R.id.armButton:
                    if (isChecked) {
                        //arm drone
                        writeCharacteristic(arm_string);
                    } else {
                        //unarm drone
                        writeCharacteristic(disarm_string);
                    }
                    break;
                case R.id.connectButton:
                    if (isChecked) {
                        ble_scan();
                    } else {
                        if(mBluetoothAdapter != null && btGatt != null){
                            btGatt.disconnect();
                        }
                        mHandler.postAtFrontOfQueue(stopScanner);
                    }
                    break;
            }
        }
    };

    /* Listener for the throttle slider */
    SeekBar.OnSeekBarChangeListener seekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            // updated continuously as the user slides the thumb
            tvProgressLabel.setText("Throttle: " + (progress+1000));
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            // called when the user first touches the SeekBar
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // called after the user finishes moving the SeekBar
        }
    };

    /* Initialize the BLE adapter, requires user permission */
    public boolean init_ble()
    {

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return false;
        }else {
            scanner = mBluetoothAdapter.getBluetoothLeScanner();
            return true;
        }
    }

    /* Callback for the Bluetooth activation request */
    protected void onActivityResult (int requestCode, int resultCode, Intent data)
    {
        if(requestCode == REQUEST_ENABLE_BT){
            if(resultCode == RESULT_OK){
                Toast.makeText(this, "Bluetooth has been enabled, scanning",Toast.LENGTH_SHORT).show();
                scanner = mBluetoothAdapter.getBluetoothLeScanner();
            }
        }
    }

    /* Asynchronous stop of the BLE scan, done when Crazepony is found, connect is pressed again, or there is a timeout (10s) */
    final private Runnable stopScanner = new Runnable() {
        public void run() {
            if(mScanning) {
                Toast.makeText(context, "Stopping scan, either timeout, Crazepony found, or manual", Toast.LENGTH_SHORT).show();
                mScanning = false;
                scanner.stopScan(scanCallback);
                connectToggle.setOnCheckedChangeListener(null);
                connectToggle.setChecked(false);
                connectToggle.setOnCheckedChangeListener(toggle_listener);
            }
        }
    };

    /* Callback for the BLE scan - checks if a Crazepony is found */
    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            BluetoothDevice tmp;
            if(result != null) {
                tmp = result.getDevice();

                if(tmp != null && tmp.getName()!= null){
                    if(tmp.getName().equals("Crazepony")) {
                        target = result.getDevice();
                        mHandler.postAtFrontOfQueue(stopScanner);
                        Toast.makeText(context, "Found Crazepony drone, id " + target.getAddress() +" attempting to connect", Toast.LENGTH_SHORT).show();
                        connect(target);
                    }else
                    {
                        Toast.makeText(context, "Found other thing, id " + tmp.getName(), Toast.LENGTH_SHORT).show();
                    }
                }
                //

            }
        }

    };

    /* Initiate a BLE scan, max 10s. Have to remove Callbacks or it could be cut short by a hanging postDelayed */
    public void ble_scan()
    {
        Toast.makeText(context, "Starting attempt to connect to Crazepony",Toast.LENGTH_SHORT).show();
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {

            mHandler.removeCallbacks(stopScanner);
            scanner.startScan(scanCallback);
            mScanning = true;
            mHandler.postDelayed(stopScanner, SCAN_PERIOD);

        }else{
            init_ble();
        }
    }

    public void writeCharacteristic(byte[] data)
    {
        if(mBluetoothAdapter != null && btGatt != null) {
            main_characteristic.setValue(data);
            btGatt.writeCharacteristic(main_characteristic);
        }
    }

    public BluetoothGattCallback btGattCb = new BluetoothGattCallback(){
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState)
        {
            super.onConnectionStateChange(gatt, status, newState);
            if(newState== STATE_CONNECTED)
            {
                System.out.println("Successfully connected to Crazepony, status "+status +" new state: " + newState);
                gatt.discoverServices();
            }
            else if (newState == STATE_DISCONNECTED)
            {
                System.out.println("Successfully disconnected from Crazepony, status "+status);
            }
            else{
                System.out.println("Neither connect nor disconnected, status "+status);

            }
        }
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status)
        {
            super.onServicesDiscovered(gatt, status);

            String uuid = null;
            List<BluetoothGattService> services = gatt.getServices();
            for(BluetoothGattService gatt_service : services){

                uuid = gatt_service.getUuid().toString();
                System.out.println("Discovered " + uuid);
                List<BluetoothGattCharacteristic> gatt_characteristics = gatt_service.getCharacteristics();

                for(BluetoothGattCharacteristic gatt_char : gatt_characteristics){

                    uuid = gatt_char.getUuid().toString();
                    if(uuid.equalsIgnoreCase(UUID_NOTIFY.toString())){
                        main_characteristic = gatt_char;
                        btGatt.setCharacteristicNotification(gatt_char,true);
                    }
                }
            }
        }
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic)
        {
            super.onCharacteristicChanged(gatt, characteristic);
        }
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic, int status)
        {
            super.onCharacteristicRead(gatt, characteristic, status);
        }
        //Response from write operation
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic, int status)
        {
            super.onCharacteristicWrite(gatt, characteristic, status);
        }
    };

    public boolean connect(BluetoothDevice device){
        btGatt = device.connectGatt(this, true, btGattCb);
        return true;
    }

    public void close() {
        if (btGatt == null) {
            return;
        }
        btGatt.close();
        btGatt = null;
    }
/*
4 16-bit channels (little endian) - Roll, pitch, yaw, throttle (from low to high index)
    public void send_raw_rc_data()
    {

    }

    public byte calculate_checksum(byte[] message, int len)
    {

    }
    */
}

