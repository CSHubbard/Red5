package com.nothawk.dronecontrol;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity  extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Getting the button
        Button btn = findViewById(R.id.btn_startControl);
        //Defining action when button is clicked
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(getApplicationContext(), "Starting Drone Control", Toast.LENGTH_SHORT)
                        .show();
                openServerConnect();
            }
        });
    }

    public void openServerConnect(){
        Intent intent = new Intent(this, ServerConnect.class);
        startActivity(intent);
    }
}
