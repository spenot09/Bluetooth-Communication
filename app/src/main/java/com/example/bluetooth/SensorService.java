package com.example.bluetooth;

import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import static android.hardware.Sensor.TYPE_ACCELEROMETER;
import static android.hardware.Sensor.TYPE_LIGHT;

public class SensorService extends Service implements SensorEventListener {

    private static final String TAG = "SensorService";

    private Sensor sensor;
    private SensorManager sensorManager;

    // Keeps track of all current registered clients
    Messenger mClient;

    // Target we publish for clients to send messages to IncomingHandler
    final Messenger mMessenger = new Messenger(new IncomingHandler());

    //      Service Functions       //

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        return START_STICKY;
    }

    // Handler of incoming messages from clients.
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Constants.MSG_REGISTER_CLIENT:
                    mClient=(msg.replyTo);
                    break;
                // Select and register the correct sensor based on incoming messages
                case Constants.MSG_SENSOR:
                    sensorManager.unregisterListener(SensorService.this, sensor);
                    if (msg.arg1==Constants.MSG_ACCELEROMETER) {
                        sensor = sensorManager.getDefaultSensor(TYPE_ACCELEROMETER);
                    }
                    if (msg.arg1==Constants.MSG_LIGHT) {
                        sensor = sensorManager.getDefaultSensor(TYPE_LIGHT);
                    }
                    sensorManager.registerListener(SensorService.this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    // Procedure to send messages back to Activity that instantiated it
    private void sendMessageToUI(float sensor_val) {
            try {
                // Send data as an int, this will need to be a float but just testing atm with a static value passed as a parameter
                mClient.send(Message.obtain(null, Constants.MSG_SENSOR, sensor_val));
            }
            catch (RemoteException e) {
                // The client is dead. Remove it from the list; we are going through the list from back to front so this is safe to do inside the loop.
                Log.i(TAG, "Client is disconnected");
            }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Toast.makeText(this, "Service Binding...", Toast.LENGTH_SHORT).show();
        return mMessenger.getBinder();
    }

    @Override
    public void onDestroy() {
        Toast.makeText(this, "Service Ended", Toast.LENGTH_SHORT).show();
        sensorManager.unregisterListener(this);
        super.onDestroy();
    }

            //      Sensor      //

    @Override
    public void onSensorChanged(SensorEvent event) {
        // grab the values -- off the main thread
        new SensorEventTask().execute(event);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }

            //      Async Task      //
    //Writing data to disk is a blocking operation and should be performed on a
    // background thread. A simple way of doing this is through an AsyncTask.
    // Within the Service, we can add an AsyncTask and pass it the SensorEvent
    // for handling

    protected class SensorEventTask extends AsyncTask<SensorEvent,Float,Float> {

        @Override
        protected void onProgressUpdate(Float... progress) {
            Log.i(TAG, Float.toString(progress[0]));
            if (mClient!=null) {
                sendMessageToUI(progress[0]);
            }
        }

        @Override
        protected Float doInBackground(SensorEvent... events) {
            publishProgress(events[0].values[0]);
            return null;
        }
    }

    private final IBinder binder = new SensorBinder();

    class SensorBinder extends Binder {
        SensorService getService() {
            return SensorService.this;
        }
    }
}
