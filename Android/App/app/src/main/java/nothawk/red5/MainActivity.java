package nothawk.red5;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import java.net.Socket;


public class MainActivity extends AppCompatActivity implements Orientation.Listener {

    private Orientation mOrientation;
    private AttitudeIndicator mAttitudeIndicator;
    private Client mSocket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mOrientation = new Orientation(this);
        mAttitudeIndicator = (AttitudeIndicator) findViewById(R.id.attitudeIndicator);

        //mSocket = new Client("192.168.0.8", 1234);
        mSocket = new Client("10.0.2.15", 1234);

        mSocket.setClientCallback(new Client.ClientCallback () {
            @Override
            public void onMessage(String message) {
            }

            @Override
            public void onConnect(Socket mSocket) {
                //mSocket.send("Hello World!\n");
                //mSocket.disconnect();
            }

            @Override
            public void onDisconnect(Socket socket, String message) {
            }

            @Override
            public void onConnectError(Socket socket, String message) {
            }
        });

        mSocket.connect();
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
        mAttitudeIndicator.setAttitude(pitch, -1*roll);
    }
}