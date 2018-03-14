package com.example.ben.helloworld;
import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
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

import java.util.Arrays;
import java.util.UUID;
import java.util.List;
import java.util.ListIterator;

public class MainActivity extends AppCompatActivity{
    public final int REQUEST_ENABLE_BT = 1;
    public static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    public final int STATE_DISCONNECTED = 0;
    public final int STATE_CONNECTED = 2;
    public final static UUID UUID_NOTIFY =
            UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    public final static UUID UUID_SERVICE =
            UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    //$,M,<,<data size, 1 byte>,<command, 1 byte>, data, <checksum, 1 byte>
    //checksum = all data bytes, data size, and command byte XORed\
    //A confusing aspect of this is that bytes are constrained to -128, 127, so we can't just write in the actual positive hex values. Can do that in code though (as bits will be the same)
    public  final static byte[] arm_string = {0x24, 0x4d, 0x3c, 0x08, -56, -36, 0x05, -36, 0x05, -48, 0x07, -24, 0x03, -4};
    public  final static byte[] disarm_string = {0x24, 0x4d, 0x3c, 0x08, -56, -36, 0x05, -36, 0x05, -24, 0x03, -24, 0x03, -64};
    public  final static byte[] base_raw_data_string = {0x24, 0x4d, 0x3c, 0x08, -56, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, -64}; // checksum base is just 0xC8 (AKA -56) ^ 0x08 - XOR with data bytes
    public  final static int    checksum_pos = 13;
    public  final static int    data_pos = 5;
    public  final static int    message_size = 14;

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
    private boolean ble_connected = false;
    private boolean att_to_connect = false;

    //1000-2000, gonna keep centered at 1500
    private int yaw = 1500;
    private int pitch = 1500;
    private int roll = 1500;
    private int throttle = 1000;
    private boolean armed = false;

    ToggleButton armToggle;
    ToggleButton connectToggle;

    TextView tvProgressLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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

    }

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
    final private Runnable sendData = new Runnable() {
        public void run(){
            if(armed){
                send_raw_rc_data();
                mHandler.postDelayed(sendData, 25);
            }
        }
    };
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
                    if(writeCharacteristic(arm_string)) {
                        armed = true;
                        mHandler.postDelayed(sendData, 5000);

                    }else{
                        armToggle.setOnCheckedChangeListener(null);
                        armToggle.setChecked(false);
                        armToggle.setOnCheckedChangeListener(toggle_listener);
                        Toast.makeText(context, "Make sure you're connected before attempting to arm",Toast.LENGTH_SHORT).show();
                    }
                } else {
                    //unarm drone
                    if(writeCharacteristic(disarm_string))
                        armed = false;
                    else{

                        armToggle.setOnCheckedChangeListener(null);
                        armToggle.setChecked(true);
                        armToggle.setOnCheckedChangeListener(toggle_listener);
                        Toast.makeText(context, "Make sure you're connected before attempting to disarm",Toast.LENGTH_SHORT).show();
                    }

                }
                break;
            case R.id.connectButton:
                if (isChecked) {
                    ble_scan();
                } else {
                    if(mBluetoothAdapter != null && btGatt != null){
                        btGatt.disconnect();
                        close();
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
            throttle = progress + 1000;
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
                /* this isnt right most of the time */
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
                    if(tmp.getName().equals("Crazepony") && !att_to_connect) {
                        target = result.getDevice();
                        //Toast.makeText(context, "Found Crazepony drone, id " + target.getAddress() +" attempting to connect", Toast.LENGTH_SHORT).show();

                        connect(target);

                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run(){
                                if(ble_connected){
                                    mScanning = false;
                                    mHandler.postAtFrontOfQueue(stopScanner);
                                    att_to_connect = false;
                                }
                            }
                        }, 5000);


                    }
                }
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

    public boolean writeCharacteristic(byte[] data)
    {
        if(mBluetoothAdapter != null && btGatt != null) {

            main_characteristic.setValue(data);
            btGatt.writeCharacteristic(main_characteristic);

            mHandler.postDelayed(new Runnable() {
                @Override
                public void run(){
                    btGatt.readCharacteristic(main_characteristic);
                    //could confirm that theyre identical here
                }
            }, 10);
            return true;
        }else
            return false;
    }
    /*
    public void delay_milli(int milliseconds){
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run(){
                btGatt.readCharacteristic(main_characteristic);
                //could confirm that theyre identical here
            }
        }, milliseconds);
    }
    */
    public BluetoothGattCallback btGattCb = new BluetoothGattCallback(){
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState)
        {
            super.onConnectionStateChange(gatt, status, newState);
            if(newState== STATE_CONNECTED && status == 0)
            {
                System.out.println("Successfully connected to Crazepony, status "+status +" new state: " + newState);
                ble_connected = true;
                gatt.discoverServices();
            }
            else if (newState == STATE_DISCONNECTED && status == 0)
            {
                System.out.println("Successfully disconnected from Crazepony, status "+status);
                ble_connected = false;
                //close();
            }
            else{
                System.out.println("Neither connect nor disconnected, status "+status);
                ble_connected = false;
                close();
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
        public void onDescriptorRead (BluetoothGatt gatt,
                               BluetoothGattDescriptor descriptor,
                               int status){
            System.out.println("READING OUT: " + descriptor.getValue());
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
        att_to_connect = true;
        return true;
    }

    public void close() {
        if (btGatt == null) {
            return;
        }
        btGatt.close();
        btGatt = null;
    }

    //4 16-bit channels (little endian) - Roll, pitch, yaw, throttle (from low to high index)
    public void send_raw_rc_data()
    {
        byte[] msg = Arrays.copyOf(base_raw_data_string, message_size);

        //incredible code
        msg[data_pos]  |= (byte)(roll      & 0x00FF);
        msg[data_pos+1]|= (byte)(roll >> 8 & 0x00FF);
        msg[data_pos+2]|= (byte)(pitch     & 0x00FF);
        msg[data_pos+3]|= (byte)(pitch >> 8 & 0x00FF);
        msg[data_pos+4]|= (byte)(yaw      & 0x00FF);
        msg[data_pos+5]|= (byte)(yaw  >>8    & 0x00FF);
        msg[data_pos+6]|= (byte)(throttle & 0x00FF);
        msg[data_pos+7]|= (byte)(throttle >> 8 & 0x00FF);

        //more incredible code
        msg[checksum_pos] ^= (byte)(msg[data_pos] ^ msg[data_pos+1] ^ msg[data_pos+2] ^ msg[data_pos+3] ^ msg[data_pos+4]
                            ^ msg[data_pos+5] ^ msg[data_pos+6] ^ msg[data_pos+7]);
        for(int i = 0; i < msg.length; i++){
            System.out.print(msg[i] + " ");
        }
        System.out.println();
           writeCharacteristic(msg);
    }
}
