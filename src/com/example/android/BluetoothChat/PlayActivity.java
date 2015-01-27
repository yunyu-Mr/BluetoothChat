package com.example.android.BluetoothChat;

import com.example.android.BluetoothChat.Pad.PartitionEventListener;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.KeyEvent;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.inputmethod.EditorInfo;
import android.view.MotionEvent;

import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RelativeLayout;

import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.Sensor;
import android.hardware.SensorManager;

import java.io.*;

public class PlayActivity extends Activity implements 
PartitionEventListener, OnTouchListener, SensorEventListener{
    // Debugging
    private static final String TAG = "BluetoothChat";
    private static final boolean D = true;

    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;
    
    // Name of the connected device
    private String mConnectedDeviceName = null;
//    // Array adapter for the conversation thread
//    private ArrayAdapter<String> mConversationArrayAdapter;
    // String buffer for outgoing messages
    private StringBuffer mOutStringBuffer;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the chat services
    private BluetoothChatService mChatService = null;
    
    // Layout Views
    private ListView mConversationView;
    //Sensor Views
    private TextView mAccXText;
    private TextView mAccYText;
    
    private EditText mOutEditText;
    private Button mSendButton;
    
    private Button mKeyUpButton;
    private Button mKeyDownButton;
    private Button mKeyLeftButton;
    private Button mKeyRightButton;
    
    private RelativeLayout mRelativeLayout = null;
    private Pad padLeft = null;
    private int viewWidth = 0;
    private int viewHeight = 0;
    
    //Sensor
    private SensorManager mSensorManager;
    private Sensor mOrientationSensor;
    
//    //Sensor
//    private SensorManager mSensorManager;
//    private Sensor mOrientationSensor;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_play);
		
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        //Sensor
        this.mAccXText = (TextView) findViewById(R.id.accelerometer_x);
        this.mAccYText = (TextView) findViewById(R.id.accelerometer_y);
        
        this.mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        this.mOrientationSensor = this.mSensorManager
        		.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
	}
	
    @Override
    public void onStart() {
        super.onStart();
        if(D) Log.e(TAG, "++ ON START ++");

        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        // Otherwise, setup the chat session
        } else {
            if (mChatService == null) ;
            this.setUpBluetooth();
        }
    }
    
    @Override
    public synchronized void onResume() {
        super.onResume();
        if(D) Log.e(TAG, "+ ON RESUME +");

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
              // Start the Bluetooth chat services
              mChatService.start();
            }
        }
        //Sensor
        this.mSensorManager.registerListener(this,this.mOrientationSensor,
        		SensorManager.SENSOR_DELAY_UI);
    }

    private void setUpBluetooth() {
        //Pad controller
        this.mRelativeLayout = (RelativeLayout) findViewById(R.id.playRelativeLayout);
//        WindowManager wm = this.getWindowManager();
        this.viewHeight = 350;
        this.viewWidth = 350;
        int padSize = this.viewHeight * 2 / 5;
        padLeft = new Pad(this);
		padLeft.setAlpha((float) 0.5);
		padLeft.setPartition(12);
		padLeft.setPartitionEventListener(this);
		padLeft.setOnTouchListener(this);
		padLeft.setDrawPartitionAll(false);
		placeView(padLeft, viewWidth/30, viewHeight-padSize-viewHeight/30, padSize, padSize);
		
        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothChatService(this, mHandler);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
    }
    
    @Override
    public synchronized void onPause() {
        super.onPause();
        if(D) Log.e(TAG, "- ON PAUSE -");
        
        mSensorManager.unregisterListener(this);

    }

    @Override
    public void onStop() {
        super.onStop();
        if(D) Log.e(TAG, "-- ON STOP --");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop the Bluetooth chat services
        if (mChatService != null) mChatService.stop();
        if(D) Log.e(TAG, "--- ON DESTROY ---");
    }
    
    /**
     * place pad
     */
    private void placeView(View v, int left, int top, int width, int height) {
		RelativeLayout.LayoutParams params = null;
		params = new RelativeLayout.LayoutParams(width, height);
		params.leftMargin = left;
		params.topMargin = top;
		mRelativeLayout.addView(v, params);
	}
    
    /**
     * Orientation Sensor
     */
    private final int BALENCE = 0;
    private final int NEGATIVE = -1;
    private final int POSITIVE = 1;
    private int xState = BALENCE;
    private int yState = BALENCE;
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    @SuppressWarnings("deprecation")
	@Override
    public void onSensorChanged(SensorEvent event) {
    	this.mAccXText.setText("x:" + event.values[0]);
    	this.mAccYText.setText("y:" + event.values[1]);
    	int myXState = this.xState;
    	int myYState = this.yState;
    	float x = event.values[0];
    	float y = event.values[1];
    	float z = event.values[2];
    	
    	if (x<-4.0)	   myXState = this.NEGATIVE;
    	else if (x>4.0)myXState = this.POSITIVE;
    	else 		   myXState = this.BALENCE;
    	
    	if (y<-4.0) 	myYState = this.NEGATIVE;
    	else if (y>4.0) myYState = this.POSITIVE;
    	else 			myYState = this.BALENCE;
    	
    	if (myXState != this.xState  ||  myYState != this.yState) {
    		ByteArrayOutputStream bout = new ByteArrayOutputStream();
    		DataOutputStream dout = new DataOutputStream(bout);
    		try {
    			dout.writeInt(Constants.ACCELERATION); //Head
    			dout.writeFloat(x);
    			dout.writeFloat(y);
    			dout.writeFloat(z);
    		} catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    		byte[] control = bout.toByteArray();
    		sendMessage(control);
    	}
    	this.xState = myXState;
    	this.yState = myYState;
    }
    
    private void ensureDiscoverable() {
        if(D) Log.d(TAG, "ensure discoverable");
        if (mBluetoothAdapter.getScanMode() !=
            BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }
    
    //Reload sendMessage
    private final void sendMessage(byte[] buffer){
        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }
        mChatService.write(buffer, Constants.CONTROL_DATA);
    }
    
    private final void setStatus(int resId) {
        final ActionBar actionBar = getActionBar();
        actionBar.setSubtitle(resId);
    }

    private final void setStatus(CharSequence subTitle) {
        final ActionBar actionBar = getActionBar();
        actionBar.setSubtitle(subTitle);
    }
    
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_STATE_CHANGE:
                if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                switch (msg.arg1) {
                case BluetoothChatService.STATE_CONNECTED:
                    setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
//                    mConversationArrayAdapter.clear();
                    break;
                case BluetoothChatService.STATE_CONNECTING:
                    setStatus(R.string.title_connecting);
                    break;
                case BluetoothChatService.STATE_LISTEN:
                case BluetoothChatService.STATE_NONE:
                    setStatus(R.string.title_not_connected);
                    break;
                }
                break;
//            case MESSAGE_WRITE:
//                byte[] writeBuf = (byte[]) msg.obj;
//                // construct a string from the buffer
//                String writeMessage = new String(writeBuf);
//                mConversationArrayAdapter.add("Me:  " + writeMessage);
//                break;
//            case MESSAGE_READ:
//                byte[] readBuf = (byte[]) msg.obj;
//                // construct a string from the valid bytes in the buffer
//                String readMessage = new String(readBuf, 0, msg.arg1);
//                switch (readMessage) {
//                case Constants.KEY_UP:
//                	mConversationArrayAdapter.add("Key press:  " + readMessage);
//                	break;
//                case Constants.KEY_DOWN:
//                	mConversationArrayAdapter.add("Key press:  " + readMessage);
//                	break;
//                case Constants.KEY_LEFT:
//                	mConversationArrayAdapter.add("Key press:  " + readMessage);
//                	break;
//                case Constants.KEY_RIGHT:
//                	mConversationArrayAdapter.add("Key press:  " + readMessage);
//                	break;
//                }
//                mConversationArrayAdapter.add(mConnectedDeviceName+":  " + readMessage);
//                break;
            case MESSAGE_DEVICE_NAME:
                // save the connected device's name
                mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                Toast.makeText(getApplicationContext(), "Connected to "
                               + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                break;
            case MESSAGE_TOAST:
                Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                               Toast.LENGTH_SHORT).show();
                break;
            }
        }
    };
    
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(D) Log.d(TAG, "onActivityResult " + resultCode);
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
                this.setUpBluetooth();
            } else {
                // User did not enable Bluetooth or an error occurred
                Log.d(TAG, "BT not enabled");
                Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
    
    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        String address = data.getExtras()
            .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mChatService.connect(device, secure);
    }
    
    @Override
    public boolean onTouch(View v, MotionEvent evt) {
		int count = evt.getPointerCount();
		if(count==1  &&  v == padLeft ) {
			if(((Pad) v).onTouch(evt));
				return true;
		}
		return false;
    }
    
    @Override 
    public void onPartitionEvent(View v, int action, int part) {
		if(v == padLeft) {
    		ByteArrayOutputStream bout = new ByteArrayOutputStream();
    		DataOutputStream dout = new DataOutputStream(bout);
    		try {
    			dout.writeInt(Constants.ARROW_KEY); //Head
				dout.writeInt(action);
				dout.writeInt(part);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		byte[] control = bout.toByteArray();
    		sendMessage(control);
			return;
		}
    }
    
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
        Intent serverIntent = null;
        switch (item.getItemId()) {
        case R.id.secure_connect_scan:
            // Launch the DeviceListActivity to see devices and do scan
            serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
            return true;
        case R.id.insecure_connect_scan:
            // Launch the DeviceListActivity to see devices and do scan
            serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);
            return true;
        case R.id.discoverable:
            // Ensure this device is discoverable by others
            ensureDiscoverable();
            return true;
        }
        return false;
	}
	
}
