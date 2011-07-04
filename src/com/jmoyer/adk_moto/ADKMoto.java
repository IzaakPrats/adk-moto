/*
 *    Copyright (C) 2011 Jeff Moyer
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.jmoyer.adk_moto;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;

import com.android.future.usb.UsbAccessory;
import com.android.future.usb.UsbManager;
import com.jmoyer.adk_moto.R;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

public class ADKMoto extends Activity {
	private static final String TAG = "ADKMoto";

	private static final String ACTION_USB_PERMISSION = "com.google.android.DemoKit.action.USB_PERMISSION";

	private ListView mList;
	private TextView mInputStatus;
	private UsbManager mUsbManager;
	private PendingIntent mPermissionIntent;
	private boolean mPermissionRequestPending;
	private SpeechRecognizer mSpeechRecognizer;
	private Intent mRecognizerIntent;
	private PowerManager.WakeLock mWakeLock;
	private short mSpeed = 0;
	// TODO: create a timer handler to timeout when processing speech takes too long

	UsbAccessory mAccessory;
	ParcelFileDescriptor mFileDescriptor;
	FileInputStream mInputStream;
	FileOutputStream mOutputStream;

	Hashtable<String, String> voiceMatches = new Hashtable<String, String>();
	private final Class<?>[] mVoidSignature = new Class[] {};

	private void addCommandMatches(String cmd, ArrayList<String> matches) {
		if (!voiceMatches.containsKey(cmd)) {
			voiceMatches.put(cmd, cmd);
		}
		for (String s : matches) {
			voiceMatches.put(s, cmd);
		}
	}

	private String getCommand(ArrayList<String> matches) {
		for (String s : matches) {
			String cmd = voiceMatches.get(s);
			if (cmd != null)
				return cmd;
		}
		return null;
	}

	private void runCommand(String cmd) {
		if (cmd == null)
			return;

		Method mCmd;
		try {
			Log.d(TAG, getClass() + "." + cmd);
			mCmd = getClass().getMethod(cmd, mVoidSignature);
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
			return;
		}
		try {
			mCmd.invoke(this);
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
		mUsbManager = UsbManager.getInstance(this);
		mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(
				ACTION_USB_PERMISSION), 0);
		IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
		filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
		registerReceiver(mUsbReceiver, filter);

		if (getLastNonConfigurationInstance() != null) {
			mAccessory = (UsbAccessory) getLastNonConfigurationInstance();
			openAccessory(mAccessory);
		}

		setContentView(R.layout.main);

		final ToggleButton togglebutton = (ToggleButton) findViewById(R.id.togglebutton);
		togglebutton.setOnClickListener(new OnClickListener() {
		    public void onClick(View v) {
		        // Perform action on clicks
		        if (togglebutton.isChecked()) {
		        	forward();
		        } else {
		        	stop();
		        }
		    }
		});

		mList = (ListView)findViewById(R.id.voiceresults);
		mInputStatus = (TextView)findViewById(R.id.inputstate);
		
		Log.d(TAG, "speech recognition available: " + SpeechRecognizer.isRecognitionAvailable(getBaseContext()));
		mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(getBaseContext());
		mSpeechRecognizer.setRecognitionListener(mRecognitionListener);
		mRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
		mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
		        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
		mRecognizerIntent.putExtra("calling_package", "com.jmoyer.adk_moto");
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "DoNotDimScreen");
    }

    private void cancelSpeechRecognition() {
    	mSpeechRecognizer.stopListening();
    	mSpeechRecognizer.cancel();
    	mInputStatus.setText(R.string.not_listening);
    }
    private RecognitionListener mRecognitionListener = new RecognitionListener() {
		@Override
		public void onBufferReceived(byte[] buffer) {
			// TODO Auto-generated method stub
			//Log.d(TAG, "onBufferReceived");
		}

		@Override
		public void onError(int error) {
			// TODO Auto-generated method stub
			Log.d(TAG, "onError: " + error);
			mInputStatus.setText(R.string.error);
			mSpeechRecognizer.startListening(mRecognizerIntent);
		}

		@Override
		public void onEvent(int eventType, Bundle params) {
			// TODO Auto-generated method stub
			//Log.d(TAG, "onEvent");
		}

		@Override
		public void onPartialResults(Bundle partialResults) {
			// TODO Auto-generated method stub
			//Log.d(TAG, "onPartialResults");
		}

		@Override
		public void onReadyForSpeech(Bundle params) {
			// TODO Auto-generated method stub
			Log.d(TAG, "onReadyForSpeech");
			mInputStatus.setText(R.string.speak);
		}

		@Override
		public void onResults(Bundle results) {
			String cmd;

			Log.d(TAG, "onResults");
			Toast.makeText(getBaseContext(), "got voice results!", Toast.LENGTH_SHORT);

			ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
			cmd = getCommand(matches);
			if (cmd == null) {
				for (String s : matches) {
					Log.d(TAG, s);
					if ("forward".equals(s)) {
						Log.d(TAG, "matched forward");
						cmd = "forward";
						addCommandMatches(cmd, matches);
						break;
					} else if ("stop".equals(s)) {
						Log.d(TAG, "matched stop");
						cmd = "stop";
						addCommandMatches(cmd, matches);
						break;
					} else if ("right".equals(s)) {
						// add code to remember whether we're already going forward, and just chnage
						// direction in that case.
						Log.d(TAG, "matched right");
						cmd = "right";
						addCommandMatches(cmd, matches);
						break;
					} else if ("left".equals(s)) {
						Log.d(TAG, "matched left");
						cmd = "left";
						addCommandMatches(cmd, matches);
						break;
					} else if ("reverse".equals(s)) {
						Log.d(TAG, "matched reverse");
						cmd = "reverse";
						addCommandMatches(cmd, matches);
						break;
					} else if ("dock".equals(s)) {
						Log.d(TAG, "matched dock");
						cmd = "dock";
						addCommandMatches(cmd, matches);
						break;
					} else if ("whistle while you work".equals(s)) {
						Log.d(TAG, "matched song");
						cmd = "whistlewhileyouwork";
						addCommandMatches(cmd, matches);
						break;
					}
				}
			}
			if (cmd == null)
				mList.setAdapter(new ArrayAdapter<String>(getBaseContext(), android.R.layout.simple_list_item_1, matches));
			else
				runCommand(cmd);
			mInputStatus.setText(R.string.initializing);
			mSpeechRecognizer.startListening(mRecognizerIntent);
		}

		@Override
		public void onRmsChanged(float rmsdB) {
			// TODO Auto-generated method stub
			//Log.d(TAG, "onRmsChanged");
		}

		@Override
		public void onBeginningOfSpeech() {
			// TODO Auto-generated method stub
			//Log.d(TAG, "onBeginningOfSpeech");
		}

		@Override
		public void onEndOfSpeech() {
			// TODO Auto-generated method stub
			Log.d(TAG, "onEndOfSpeech");
			mInputStatus.setText(R.string.processing);
		}
    	
    };

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (ACTION_USB_PERMISSION.equals(action)) {
				synchronized (this) {
					UsbAccessory accessory = UsbManager.getAccessory(intent);
					if (intent.getBooleanExtra(
							UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
						openAccessory(accessory);
					} else {
						Log.d(TAG, "permission denied for accessory "
								+ accessory);
					}
					mPermissionRequestPending = false;
				}
			} else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
				UsbAccessory accessory = UsbManager.getAccessory(intent);
				if (accessory != null && accessory.equals(mAccessory)) {
					closeAccessory();
				}
			}
		}
	};

	private void sendCommand(byte[] command) {
		if (mOutputStream != null) {
			try {
				mOutputStream.write(command);
			} catch (IOException e) {
				Log.e(TAG, "write failed", e);
				closeAccessory();
			}
		}
	}

	public void right() {
		byte[] buffer = new byte[5];
		buffer[0] = 'd';
		if (mSpeed == 0) { // turn in place
			buffer[1] = (byte)(250>>8 & 0xff);
			buffer[2] = (byte)(250 & 0xff);
			buffer[3] = (byte)0xff;
			buffer[4] = (byte)0xff;
		} else {
			buffer[1] = (byte)(mSpeed>>8 & 0xff);
			buffer[2] = (byte)(mSpeed & 0xff);
			buffer[3] = (byte)(-2000>>8 & 0xff);
			buffer[4] = (byte)(-2000 & 0xff);
		}
		sendCommand(buffer);
	}

	public void left() {
		byte[] buffer = new byte[5];
		buffer[0] = 'd';
		if (mSpeed == 0) {
			buffer[1] = (byte)(250>>8 & 0xff);
			buffer[2] = (byte)(250 & 0xff);
			buffer[3] = (byte)0x0;
			buffer[4] = (byte)0x1;
		} else {
			buffer[1] = (byte)(mSpeed>>8 & 0xff);
			buffer[2] = (byte)(mSpeed & 0xff);
			buffer[3] = (byte)(2000>>8 & 0xff);
			buffer[4] = (byte)(2000 & 0xff);
		}
		sendCommand(buffer);
	}

	public void reverse() {
		byte[] buffer = new byte[5];
		buffer[0] = 'd';
		mSpeed = -250;
		
		buffer[1] = (byte)(mSpeed>>8 & 0xff);
		buffer[2] = (byte)(mSpeed & 0xff);
		buffer[3] = (byte)((short)32768>>8 & 0xff);
		buffer[4] = (byte)((short)32768 & 0xff);
		sendCommand(buffer);
	}

	private void motorControl(boolean on) {
		byte[] buffer = new byte[5];

		if (on)
			mSpeed = 250;
		else
			mSpeed = 0;

		buffer[0] = 'd';
		buffer[1] = (byte)(mSpeed>>8 & 0xff);
		buffer[2] = (byte)(mSpeed & 0xff);
		buffer[3] = (byte)((short)32768>>8 & 0xff);
		buffer[4] = (byte)((short)32768 & 0xff);

		sendCommand(buffer);
	}

	public void stop() {
		motorControl(false);
	}

	public void forward() {
		motorControl(true);
	}

	public void dock() {
		byte[] buffer = new byte[2];
		buffer[0] = 'D';
		buffer[1] = (byte)1;
		sendCommand(buffer);
	}

	public void whistlewhileyouwork() {
		byte[] buffer = new byte[1];
		buffer[0] = 's';
		sendCommand(buffer);
	}

	private void openAccessory(UsbAccessory accessory) {
		mFileDescriptor = mUsbManager.openAccessory(accessory);
		if (mFileDescriptor != null) {
			mAccessory = accessory;
			FileDescriptor fd = mFileDescriptor.getFileDescriptor();
			mInputStream = new FileInputStream(fd);
			mOutputStream = new FileOutputStream(fd);
			Log.d(TAG, "accessory opened");
			Toast.makeText(getBaseContext(), "openAccessory: starting speech recognizer", Toast.LENGTH_SHORT);
			Log.d(TAG, "openAccessory: starting speech recognition");
			mWakeLock.acquire();
			mSpeechRecognizer.startListening(mRecognizerIntent);
		} else {
			Log.d(TAG, "accessory open fail");
		}
	}

	private void closeAccessory() {

		try {
			if (mInputStream != null)
				mInputStream.close();
			if (mOutputStream != null)
				mOutputStream.close();
			if (mFileDescriptor != null)
				mFileDescriptor.close();
		} catch (IOException e) {
		} finally {
			mFileDescriptor = null;
			mAccessory = null;
			mOutputStream = null;
			mInputStream = null;
		}
		Log.d(TAG, "stopping speech recognition");
		cancelSpeechRecognition();
		mInputStatus.setText(R.string.disconnected);
		// This can be called even if the activity is not in the foreground.
		if (mWakeLock.isHeld())
			mWakeLock.release();
		Enumeration<String> e = voiceMatches.keys();
		while (e.hasMoreElements()) {
			String key = (String)e.nextElement();
			Log.d(TAG, key + "->" + voiceMatches.get(key));
		}
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		if (mAccessory != null) {
			return mAccessory;
		} else {
			return super.onRetainNonConfigurationInstance();
		}
	}

	@Override
	public void onResume() {
		super.onResume();

		if (mInputStream != null && mOutputStream != null) {
			Log.d(TAG, "restarting speech recognition");
			mSpeechRecognizer.startListening(mRecognizerIntent);
			mWakeLock.acquire();
			return;
		}

		UsbAccessory[] accessories = mUsbManager.getAccessoryList();
		UsbAccessory accessory = (accessories == null ? null : accessories[0]);
		if (accessory != null) {
			if (mUsbManager.hasPermission(accessory)) {
				openAccessory(accessory);
			} else {
				synchronized (mUsbReceiver) {
					if (!mPermissionRequestPending) {
						mUsbManager.requestPermission(accessory,
								mPermissionIntent);
						mPermissionRequestPending = true;
					}
				}
			}
		} else {
			Log.d(TAG, "mAccessory is null");
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		Log.d(TAG, "onPause: stopping speech recognition");
		cancelSpeechRecognition();
		if (mWakeLock.isHeld())
			mWakeLock.release();
	}

	@Override
	public void onDestroy() {
		unregisterReceiver(mUsbReceiver);
		if (mSpeechRecognizer != null) {
			Log.d(TAG, "onDestroy: stopping listening");
			cancelSpeechRecognition();
			mSpeechRecognizer.destroy();
		}
		super.onDestroy();
	}
}