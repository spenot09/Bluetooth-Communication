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

    private Button start_service_button, stop_service_button, bind_bluetooth_button;
    private TextView sensor_txt,provider_instruction_textView;
    private GifImageView gif;

    private boolean bound = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provider);

        //Set up a "Back" button
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        start_service_button = findViewById(R.id.start_service_button);
        stop_service_button = findViewById(R.id.stop_service_button);
        bind_bluetooth_button = findViewById(R.id.bind_bluetooth_button);

        sensor_txt = findViewById(R.id.sensing_textView);
        provider_instruction_textView = findViewById(R.id.provider_instruction_textView);

        gif = findViewById(R.id.gifImageView);


        //Remove elements not needed currently on start-up
        sensor_txt.setVisibility(View.INVISIBLE);  // For Invisible/Disappear
        gif.setVisibility(View.INVISIBLE);

        //define fading animations
        final Animation single_out = new AlphaAnimation(1.0f, 0.0f);
        single_out.setDuration(1000);

        final AlphaAnimation fadeIn = new AlphaAnimation(0.0f , 1.0f ) ;
        fadeIn.setDuration(1000);

        final Animation out = new AlphaAnimation(1.0f, 0.0f);
        out.setRepeatCount(Animation.INFINITE);
        out.setRepeatMode(Animation.REVERSE);
        out.setDuration(1750);

        start_service_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //start the service in the background from SensorService
                startService(new Intent(getBaseContext(), SensorService.class));

                //Sort out the appearance of the View
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
                //Stop the service by implicitly calling onDestroy()
                stopService(new Intent(getBaseContext(), SensorService.class));

                //Sort out the appearance of the View
                sensor_txt.clearAnimation();
                gif.startAnimation(single_out);
                gif.setVisibility(View.INVISIBLE);
                provider_instruction_textView.startAnimation(fadeIn);
                provider_instruction_textView.setVisibility(View.VISIBLE);
            }
        });

        bind_bluetooth_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                doBindService();
            }
        });
    }

    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SensorService.MSG_SENSOR:
                    if (sensor_tpye==MSG_ACCELEROMETER) {
                        acc_textView.setText(String.format("Accelerometer value: %.1f", msg.obj));
                        speedometer.speedTo((float)msg.obj, 500);
                    }
                    if (sensor_tpye==MSG_LIGHT) {
                        light_textView.setText("Light sensor value: " + msg.obj);
                        fire_gauge.speedTo((float)msg.obj, 500);
                    }
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mService = new Messenger(service);
            textStatus.setText("Attached.");
            try {
                Message msg = Message.obtain(null, SensorService.MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mService.send(msg);
            }
            catch (RemoteException e) {
                // In this case the service has crashed before we could even do anything with it
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mService = null;
            textStatus.setText("Disconnected.");
            bound = false;
        }
    };

    private void sendMessageToService(int intvaluetosend) {
        if (bound) {
            if (mService != null) {
                try {
                    //need to check this if successful because may need to alter for use
                    Message msg = Message.obtain(null, SensorService.MSG_SENSOR, intvaluetosend, 0);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                }
                catch (RemoteException e) {
                }
            }
        }
    }

    void doBindService() {
        bindService(new Intent(this, SensorService.class), connection, Context.BIND_AUTO_CREATE);
        bound = true;
        textStatus.setText("Binding.");
    }

    void doUnbindService() {
        if (bound) {

            // Detach our existing connection.
            unbindService(connection);
            bound = false;
            //textStatus.setText("Unbinding.");
        }
    }

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

