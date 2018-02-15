package nothawk.red5;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity implements Orientation.Listener {

    private Orientation mOrientation;
    private AttitudeIndicator mAttitudeIndicator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mOrientation = new Orientation(this);
        mAttitudeIndicator = (AttitudeIndicator) findViewById(R.id.attitudeIndicator);
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
