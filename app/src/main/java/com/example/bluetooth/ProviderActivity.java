package com.example.bluetooth;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.TextView;

import pl.droidsonroids.gif.GifImageView;

public class ProviderActivity extends AppCompatActivity {

    private Button start_service_button, stop_service_button;
    private TextView sensor_txt, provider_instruction_textView;

    // "Sensing" GIF
    private GifImageView gif;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provider);

        // Set up a "Back" button
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        start_service_button = findViewById(R.id.start_service_button);
        stop_service_button = findViewById(R.id.stop_service_button);

        sensor_txt = findViewById(R.id.sensing_textView);
        provider_instruction_textView = findViewById(R.id.provider_instruction_textView);

        gif = findViewById(R.id.gifImageView);

        // Remove elements not needed currently on start-up
        sensor_txt.setVisibility(View.INVISIBLE);  // For Invisible/Disappear
        gif.setVisibility(View.INVISIBLE);

        // Define fading animations
        final Animation single_out = new AlphaAnimation(1.0f, 0.0f);
        single_out.setDuration(1000);

        final AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(1000);

        final Animation out = new AlphaAnimation(1.0f, 0.0f);
        out.setRepeatCount(Animation.INFINITE);
        out.setRepeatMode(Animation.REVERSE);
        out.setDuration(1750);

        start_service_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Start the service in the background from SensorService
                startService(new Intent(getBaseContext(), SensorService.class));

                // Sort out the appearance of the View
                provider_instruction_textView.startAnimation(single_out);

                provider_instruction_textView.setVisibility(View.INVISIBLE);
                gif.startAnimation(fadeIn);
                gif.setVisibility(View.VISIBLE);

                sensor_txt.startAnimation(out);
            }
        });

        stop_service_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Stop the service by implicitly calling onDestroy()
                stopService(new Intent(getBaseContext(), SensorService.class));

                // Sort out the appearance of the View
                sensor_txt.clearAnimation();
                gif.startAnimation(single_out);
                gif.setVisibility(View.INVISIBLE);
                provider_instruction_textView.startAnimation(fadeIn);
                provider_instruction_textView.setVisibility(View.VISIBLE);
            }
        });
    }


    // Method for setting up "back" button
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }
}

