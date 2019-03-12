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
import android.widget.Button;
import android.widget.TextView;

import com.github.anastr.speedviewlib.ImageLinearGauge;
import com.github.anastr.speedviewlib.PointerSpeedometer;

public class RequesterActivity extends AppCompatActivity {

    private Button  acc_button, light_button, unbind_button, bind_button;
    private TextView light_textView, acc_textView, textStatus;
    private PointerSpeedometer speedometer;
    private ImageLinearGauge fire_gauge;

    Messenger mService = null;
    final Messenger mMessenger = new Messenger(new IncomingHandler());

    private boolean bound = false;

    private int sensor_type;
    static final int MSG_ACCELEROMETER = 1;
    static final int MSG_LIGHT = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_requester);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        acc_button = findViewById(R.id.acc_sensor_button);
        light_button = findViewById(R.id.light_sensor_button);
        unbind_button = findViewById(R.id.unbind_button);
        bind_button = findViewById(R.id.bind_button);

        acc_textView = findViewById(R.id.acc_textView);
        light_textView = findViewById(R.id.light_textView);
        textStatus = findViewById(R.id.textStatus);

        fire_gauge = findViewById(R.id.fireGauge);

        speedometer = findViewById(R.id.accGauge);
        speedometer.speedTo(0);

        acc_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sensor_type = MSG_ACCELEROMETER;
                sendMessageToService(MSG_ACCELEROMETER);
            }
        });

        light_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sensor_type = MSG_LIGHT;
                sendMessageToService(MSG_LIGHT);
            }
        });

        unbind_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                doUnbindService();
            }
        });

        bind_button.setOnClickListener(new View.OnClickListener() {
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
                    if (sensor_type==MSG_ACCELEROMETER) {
                        acc_textView.setText(String.format("Accelerometer value: %.1f", msg.obj));
                        speedometer.speedTo((float)msg.obj, 500);
                    }
                    if (sensor_type==MSG_LIGHT) {
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
            textStatus.setText("Unbinding.");
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

