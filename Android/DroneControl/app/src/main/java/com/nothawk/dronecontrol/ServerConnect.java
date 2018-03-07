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
}
