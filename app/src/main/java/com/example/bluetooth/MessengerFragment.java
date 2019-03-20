package com.example.bluetooth;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
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
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.anastr.speedviewlib.ImageLinearGauge;
import com.github.anastr.speedviewlib.PointerSpeedometer;

import java.util.Arrays;
import java.util.List;

import static android.content.ContentValues.TAG;


public class MessengerFragment extends Fragment {

    // Set messages to be initiated during BT connection
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;

    // Lists used for "chatbot" to allow possible associations with sensor type commands
    private List<String> acc_terms = Arrays.asList("accelerometer", "speed", "tilt", "TYPE_ACCELEROMETER");
    private List<String> light_terms = Arrays.asList("light", "Light", "light sensor", "TYPE_LIGHT");

    // Sensor_type will need to be set by the incoming request from the client
    private int sensor_type;

    // Initialise the variable that will act as the container for the incoming sensor values
    private float sens_val_receiver_end;

    // Set up the chat components and buttons to interact with the app
    private ListView chatWindow;
    private EditText editMessage;
    private Button sendButton, start_service, client_button, receiver_btn, visualise_btn;

    private String connectedDeviceName = null;

    // Initialise container to hold sensor value from service locally
    private float sensor_val;

    // Initialise ArrayAdapter that will allow for scrolling chatWindow
    private ArrayAdapter<String> chatAdapter;

    // Initialise BT components and chat buffer
    private StringBuffer messageOutBuffer;
    private BluetoothAdapter bluetoothAdapter;

    // An instance of ChatService
    private ChatService chatService;

    // Initialise booleans that allow us to keep track of states
    private boolean bound, receive, visualise;

    // Initialise speedometer and linearGauge for visualisations
    private PointerSpeedometer speedometer;
    private ImageLinearGauge fire_gauge;

    // Instantiations to allow inter-service communication
    Messenger mService = null;
    final Messenger mMessenger = new Messenger(new IncomingHandler());

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        // Get local Bluetooth adapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (bluetoothAdapter == null) {
            FragmentActivity activity = getActivity();
            Toast.makeText(activity, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            activity.finish();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // Enable BT adapter here
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else if (chatService == null) {
            setupChat();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (chatService != null) {
            chatService.stop();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (chatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (chatService.getState() == ChatService.STATE_NONE) {
                // Start the Bluetooth chat services
                chatService.start();
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.messanger_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        chatWindow = view.findViewById(R.id.chat_window);
        editMessage = view.findViewById(R.id.edit_message);
        sendButton = view.findViewById(R.id.send_btn);
        start_service = view.findViewById(R.id.service_btn);
        client_button = view.findViewById(R.id.client_btn);
        receiver_btn = view.findViewById(R.id.receiver_btn);
        visualise_btn = view.findViewById(R.id.graphs_btn);

        fire_gauge = view.findViewById(R.id.fireGauge);
        speedometer = view.findViewById(R.id.accGauge);

        // set base position to 0 of speedometer and allow movement from there
        speedometer.speedTo(0);
    }

    private void setupChat() {
               // Initialize the array adapter for the conversation thread
        chatAdapter = new ArrayAdapter<String>(getActivity(), R.layout.message);

        chatWindow.setAdapter(chatAdapter);

        // Initialize the send button with a listener that for click events
        sendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                View view = getView();
                if (null != view) {
                    TextView textView = view.findViewById(R.id.edit_message);
                    String message = textView.getText().toString();
                    sendMessage(message);
                }
            }
        });

        // Launch the ProviderActivity from which the SensorService can be started
        start_service.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                doUnbindService();
                Intent intent = new Intent(getActivity(), ProviderActivity.class);
                startActivity(intent);
            }
        });

        // Bind to the service locally and communicate to SensorService which Sensor to sample
        client_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                doBindService();
                Log.i(TAG, "VALUE OF SENSOR_TYPE IS: " + Integer.toString(sensor_type));
                if (sensor_type == Constants.MSG_ACCELEROMETER) {
                    sendMessageToService(Constants.MSG_ACCELEROMETER);
                }
                if (sensor_type == Constants.MSG_LIGHT) {
                    sendMessageToService(Constants.MSG_LIGHT);
                }
            }
        });

        // State change to signal client is ready to receive data
        receiver_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                receive = true;
            }
        });

        // State change to visualise incoming "received" data
        visualise_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                visualise = true;
            }
        });

        // Initialize the ChatService to perform bluetooth connections
        chatService = new ChatService(getActivity(), chatHandler);

        // Initialize the buffer for outgoing messages
        messageOutBuffer = new StringBuffer("");
    }

    private void ensureDiscoverable() {
        // Enables device to be discovered by other BT devices in range
        if (bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    // Procedure used to send messages to the chatWindow and across BT
    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (chatService.getState() != ChatService.STATE_CONNECTED) {
            Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the ChatService to write
            byte[] send = message.getBytes();
            chatService.write(send);

            // Reset out string buffer to zero and clear the edit text field
            messageOutBuffer.setLength(0);
            editMessage.setText(messageOutBuffer);
        }
    }

    // Item selection from drop down menu and initiates the correct Intent
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent serverIntent = null;
        switch (item.getItemId()) {
            case R.id.secure_connect_scan:
                serverIntent = new Intent(getActivity(), ConnectionActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
                return true;
            case R.id.insecure_connect_scan:
                serverIntent = new Intent(getActivity(), ConnectionActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);
                return true;
            case R.id.discoverable:
                ensureDiscoverable();
                return true;
        }
        return false;
    }

    private void setStatus(int resId) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(resId);
    }

    private void setStatus(CharSequence subTitle) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(subTitle);
    }

    // Handler to check for incoming messages from SensorService
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Constants.MSG_SENSOR:
                    // Extract float value from incoming message object
                    sensor_val = (float) (msg.obj);

                    // Function to send incoming sensor values across the connected BT chat
                    runnable_message.run();
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    private Runnable runnable_message = new Runnable() {
        @Override
        public void run() {
            // Send messages on the main thread
            sendMessage(Float.toString(sensor_val));
            Log.d("Handlers", "Called on main thread");
        }
    };

    // ServiceConnection to manage the connection between the activity and the service.
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mService = new Messenger(service);
            try {
                Message msg = Message.obtain(null, Constants.MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mService.send(msg);
            } catch (RemoteException e) {
                // In this case the service has crashed before we could even do anything with it
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mService = null;
        }
    };

    // Procedure to communicate with the SensorService running locally
    private void sendMessageToService(int intvaluetosend) {
        if (bound) {
            if (mService != null) {
                try {
                    // Extract ID to reply to and send "intvaluetosend" to SensorService, ready to
                    // caught by an Handler on the other side
                    Message msg = Message.obtain(null, Constants.MSG_SENSOR, intvaluetosend, 0);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                } catch (RemoteException e) {
                }
            }
        }
    }

    // Procedure that encompasses binding and registers state change via boolean "bound" variable
    void doBindService() {
        getActivity().bindService(new Intent(getActivity(), SensorService.class), connection, Context.BIND_AUTO_CREATE);
        bound = true;
    }

    void doUnbindService() {
        if (bound) {

            // Detach our existing connection.
            getActivity().unbindService(connection);
            bound = false;
        }
    }

    // Handler that interfaces with the ChatService
    private final Handler chatHandler = new Handler() {
        @SuppressLint("StringFormatInvalid")
        @Override
        public void handleMessage(Message msg) {
            FragmentActivity activity = getActivity();
            switch (msg.what) {
                // A change of state has occurred that needs to be handled gracefully
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case ChatService.STATE_CONNECTED:
                            setStatus(getString(R.string.title_connected_to, connectedDeviceName));
                            chatAdapter.clear();
                            chatAdapter.add("Hi there! Welcome to SensX :)\nPlease select the " +
                                    "sensor you'd like to use by typing in the appropriate command. " +
                                    "We currently support use of the Accelerometer and Light sensors.");
                            break;
                        case ChatService.STATE_CONNECTING:
                            setStatus(R.string.title_connecting);
                            break;
                        case ChatService.STATE_LISTEN:
                        case ChatService.STATE_NONE:
                            setStatus(R.string.title_not_connected);
                            break;
                    }
                    break;

                // A message is to be written to the chatAdapter and thus eventually
                // the chatWindow
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);

                    // Determine which sensor has been selected based on outgoing message. This is used to
                    // synchronize the sensor_type with the Provider device and allow correct visualisation
                    if (acc_terms.contains(writeMessage)) {
                        Log.e(TAG, "ACCELEROMETER SELECTED");
                        sensor_type = Constants.MSG_ACCELEROMETER;
                    }

                    if (light_terms.contains(writeMessage)) {
                        Log.e(TAG, "LIGHT SELECTED");
                        sensor_type = Constants.MSG_LIGHT;
                    }

                    chatAdapter.add("Me:  " + writeMessage);
                    break;

                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);

                    // Check if incoming message is from the Client device. If so then set sensor_type
                    // to the correct value on the Client device, which will then be passed to SensorService.
                    if (acc_terms.contains(readMessage)) {
                        Log.e(TAG, "ACCELEROMETER SELECTED");
                        sensor_type = Constants.MSG_ACCELEROMETER;
                    }

                    if (light_terms.contains(readMessage)) {
                        Log.e(TAG, "LIGHT SELECTED");
                        sensor_type = Constants.MSG_LIGHT;
                    }

                    // If Client is ready to receive then parse and do so. Visualise if state requires it also
                    if (receive) {
                        sens_val_receiver_end = Float.parseFloat(readMessage);
                        Log.e(TAG, "Value of sens_val_receiver_end is " + sens_val_receiver_end);
                        if (visualise) {
                            Log.e(TAG, "Value of sensor_type is: " + sensor_type);
                            if (sensor_type == Constants.MSG_ACCELEROMETER) {
                                speedometer.speedTo(sens_val_receiver_end, 500);
                            }
                            if (sensor_type == Constants.MSG_LIGHT) {
                                fire_gauge.speedTo(sens_val_receiver_end, 500);
                            }
                        }
                    }
                    chatAdapter.add(connectedDeviceName + ":  " + readMessage);
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    connectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    if (null != activity) {
                        Toast.makeText(activity, "Connected to " + connectedDeviceName, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case Constants.MESSAGE_TOAST:
                    if (null != activity) {
                        Toast.makeText(activity, msg.getData().getString(Constants.TOAST), Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_SECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, true);
                }
                break;
            case REQUEST_CONNECT_DEVICE_INSECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, false);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    setupChat();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Toast.makeText(getActivity(), R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                    getActivity().finish();
                }
        }
    }

    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        String address = data.getExtras().getString(ConnectionActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        chatService.connect(device, secure);
    }
}
