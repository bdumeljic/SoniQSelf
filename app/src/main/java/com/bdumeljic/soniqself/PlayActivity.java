package com.bdumeljic.soniqself;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.IntentSender;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.Session;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.request.SessionReadRequest;
import com.google.android.gms.fitness.result.DataReadResult;

import org.billthefarmer.mididriver.GeneralMidiConstants;
import org.billthefarmer.mididriver.MidiConstants;
import org.billthefarmer.mididriver.MidiDriver;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class PlayActivity extends AppCompatActivity implements View.OnTouchListener, View.OnClickListener,
        MidiDriver.OnMidiStartListener {
    private static String TAG = "PlayActivity";
    private static final String DATE_FORMAT = "yyyy.MM.dd HH:mm:ss";
    public static final String SAMPLE_SESSION_NAME = "Afternoon run";


    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;

    private View mContentView;
    private View mControlsView;
    private boolean mVisible;

    private static final int REQUEST_OAUTH = 1;

    /**
     * Track whether an authorization activity is stacking over the current activity, i.e. when
     * a known auth error is being resolved, such as showing the account chooser or presenting a
     * consent dialog. This avoids common duplications as might happen on screen rotations, etc.
     */
    private static final String AUTH_PENDING = "auth_state_pending";
    private boolean authInProgress = false;

    private GoogleApiClient mClient = null;

    protected MidiDriver midi;
    protected MediaPlayer player;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            authInProgress = savedInstanceState.getBoolean(AUTH_PENDING);
        }

        buildFitnessClient();

        midi = new MidiDriver();


        setContentView(R.layout.activity_play);

        mVisible = true;
        mControlsView = findViewById(R.id.fullscreen_content_controls);
        mContentView = findViewById(R.id.fullscreen_content);


        // Set up the user interaction to manually show or hide the system UI.
        mContentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggle();
            }
        });

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        findViewById(R.id.dummy_button).setOnTouchListener(mDelayHideTouchListener);


        // Set on touch listener

        View v = findViewById(R.id.button1);
        if (v != null)
            v.setOnTouchListener(this);

        v = findViewById(R.id.button2);
        if (v != null)
            v.setOnTouchListener(this);

        v = findViewById(R.id.button3);
        if (v != null)
            v.setOnClickListener(this);

        v = findViewById(R.id.button4);
        if (v != null)
            v.setOnClickListener(this);

        if (midi != null) {
            midi.setOnMidiStartListener(this);
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mControlsView.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };

    @SuppressLint("InlinedApi")
    private void show() {
        // Show the system bar
        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            mControlsView.setVisibility(View.VISIBLE);
        }
    };

    private final Handler mHideHandler = new Handler();
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    /**
     * Build a {@link GoogleApiClient} that will authenticate the user and allow the application
     * to connect to Fitness APIs. The scopes included should match the scopes your app needs
     * (see documentation for details). Authentication will occasionally fail intentionally,
     * and in those cases, there will be a known resolution, which the OnConnectionFailedListener()
     * can address. Examples of this include the user never having signed in before, or having
     * multiple accounts on the device and needing to specify which account to use, etc.
     */
    private void buildFitnessClient() {
        // Create the Google API Client
        mClient = new GoogleApiClient.Builder(this)
                .addApi(Fitness.HISTORY_API)
                .addApi(Fitness.SESSIONS_API)
                .addScope(new Scope(Scopes.FITNESS_LOCATION_READ_WRITE))
                .addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ_WRITE))
                .addConnectionCallbacks(
                        new GoogleApiClient.ConnectionCallbacks() {

                            @Override
                            public void onConnected(Bundle bundle) {
                                Log.i(TAG, "Connected!!!");
                                // Now you can make calls to the Fitness APIs.
                                // Put application specific code here.
                                new InsertAndVerifyDataTask().execute();

                            }

                            @Override
                            public void onConnectionSuspended(int i) {
                                // If your connection to the sensor gets lost at some point,
                                // you'll be able to determine the reason and react to it here.
                                if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_NETWORK_LOST) {
                                    Log.i(TAG, "Connection lost.  Cause: Network Lost.");
                                } else if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED) {
                                    Log.i(TAG, "Connection lost.  Reason: Service Disconnected");
                                }
                            }
                        }
                )
                .addOnConnectionFailedListener(
                        new GoogleApiClient.OnConnectionFailedListener() {
                            // Called whenever the API client fails to connect.
                            @Override
                            public void onConnectionFailed(ConnectionResult result) {
                                Log.i(TAG, "Connection failed. Cause: " + result.toString());
                                if (!result.hasResolution()) {
                                    // Show the localized error dialog
                                    GooglePlayServicesUtil.getErrorDialog(result.getErrorCode(),
                                            PlayActivity.this, 0).show();
                                    return;
                                }
                                // The failure has a resolution. Resolve it.
                                // Called typically when the app is not yet authorized, and an
                                // authorization dialog is displayed to the user.
                                if (!authInProgress) {
                                    try {
                                        Log.i(TAG, "Attempting to resolve failed connection");
                                        authInProgress = true;
                                        result.startResolutionForResult(PlayActivity.this,
                                                REQUEST_OAUTH);
                                    } catch (IntentSender.SendIntentException e) {
                                        Log.e(TAG,
                                                "Exception while starting resolution activity", e);
                                    }
                                }
                            }
                        }
                )
                .build();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Connect to the Fitness API
        Log.i(TAG, "Connecting...");
        mClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mClient.isConnected()) {
            mClient.disconnect();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_OAUTH) {
            authInProgress = false;
            if (resultCode == RESULT_OK) {
                // Make sure the app is not already connected or attempting to connect
                if (!mClient.isConnecting() && !mClient.isConnected()) {
                    mClient.connect();
                }
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(AUTH_PENDING, authInProgress);
    }

    /**
     * Create a {@link DataSet} to insert data into the History API, and
     * then create and execute a {@link DataReadRequest} to verify the insertion succeeded.
     * By using an {@link AsyncTask}, we can schedule synchronous calls, so that we can query for
     * data after confirming that our insert was successful. Using asynchronous calls and callbacks
     * would not guarantee that the insertion had concluded before the read request was made.
     * An example of an asynchronous call using a callback can be found in the example
     * on deleting data below.
     */
    private class InsertAndVerifyDataTask extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... params) {
            // Begin by creating the query.
            DataReadRequest readRequest = queryFitnessData();

            // [START read_dataset]
            // Invoke the History API to fetch the data with the query and await the result of
            // the read request.
            DataReadResult dataReadResult =
                    Fitness.HistoryApi.readData(mClient, readRequest).await(1, TimeUnit.MINUTES);
            // [END read_dataset]

            // For the sake of the sample, we'll print the data so we can see what we just added.
            // In general, logging fitness information should be avoided for privacy reasons.
            printData(dataReadResult);

            /*
            /// Begin by creating the query.
            SessionReadRequest readRequest = readFitnessSession();

            // [START read_session]
            // Invoke the Sessions API to fetch the session with the query and wait for the result
            // of the read request.
            SessionReadResult sessionReadResult =
                    Fitness.SessionsApi.readSession(mClient, readRequest)
                            .await(1, TimeUnit.MINUTES);

            // Get a list of the sessions that match the criteria to check the result.
            Log.i(TAG, "Session read was successful. Number of returned sessions is: "
                    + sessionReadResult.getSessions().size());
            for (Session session : sessionReadResult.getSessions()) {
                // Process the session
                dumpSession(session);

                // Process the data sets for this session
                List<DataSet> dataSets = sessionReadResult.getDataSet(session);
                for (DataSet dataSet : dataSets) {
                    dumpDataSet(dataSet);
                }
            }
            // [END read_session] */

            return null;
        }
    }

    /**
     * Return a {@link DataReadRequest} for all step count changes in the past week.
     */
    private DataReadRequest queryFitnessData() {
        // [START build_read_data_request]
        // Setting a start and end date using a range of 1 week before this moment.
        Calendar cal = Calendar.getInstance();
        Date now = new Date();
        cal.setTime(now);
        long endTime = cal.getTimeInMillis();
        cal.add(Calendar.WEEK_OF_YEAR, -1);
        long startTime = cal.getTimeInMillis();

        SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
        Log.i(TAG, "Range Start: " + dateFormat.format(startTime));
        Log.i(TAG, "Range End: " + dateFormat.format(endTime));

        DataReadRequest readRequest = new DataReadRequest.Builder()
                // The data request can specify multiple data types to return, effectively
                // combining multiple data queries into one call.
                // In this example, it's very unlikely that the request is for several hundred
                // datapoints each consisting of a few steps and a timestamp.  The more likely
                // scenario is wanting to see how many steps were walked per day, for 7 days.
                .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
                        // Analogous to a "Group By" in SQL, defines how data should be aggregated.
                        // bucketByTime allows for a time span, whereas bucketBySession would allow
                        // bucketing by "sessions", which would need to be defined in code.
                .bucketByTime(1, TimeUnit.DAYS)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build();
        // [END build_read_data_request]

        return readRequest;
    }

    /**
     * Log a record of the query result. It's possible to get more constrained data sets by
     * specifying a data source or data type, but for demonstrative purposes here's how one would
     * dump all the data. In this sample, logging also prints to the device screen, so we can see
     * what the query returns, but your app should not log fitness information as a privacy
     * consideration. A better option would be to dump the data you receive to a local data
     * directory to avoid exposing it to other applications.
     */
    private void printData(DataReadResult dataReadResult) {
        // [START parse_read_data_result]
        // If the DataReadRequest object specified aggregated data, dataReadResult will be returned
        // as buckets containing DataSets, instead of just DataSets.
        if (dataReadResult.getBuckets().size() > 0) {
            Log.i(TAG, "Number of returned buckets of DataSets is: "
                    + dataReadResult.getBuckets().size());
            for (Bucket bucket : dataReadResult.getBuckets()) {
                List<DataSet> dataSets = bucket.getDataSets();
                for (DataSet dataSet : dataSets) {
                    dumpDataSet(dataSet);
                }
            }
        } else if (dataReadResult.getDataSets().size() > 0) {
            Log.i(TAG, "Number of returned DataSets is: "
                    + dataReadResult.getDataSets().size());
            for (DataSet dataSet : dataReadResult.getDataSets()) {
                dumpDataSet(dataSet);
            }
        }
        // [END parse_read_data_result]
    }

    /**
     * Return a {@link SessionReadRequest} for all speed data in the past week.
     */
    private SessionReadRequest readFitnessSession() {
        Log.i(TAG, "Reading History API results for session: " + SAMPLE_SESSION_NAME);
        // [START build_read_session_request]
        // Set a start and end time for our query, using a start time of 1 week before this moment.
        Calendar cal = Calendar.getInstance();
        Date now = new Date();
        cal.setTime(now);
        long endTime = cal.getTimeInMillis();
        cal.add(Calendar.WEEK_OF_YEAR, -1);
        long startTime = cal.getTimeInMillis();

        // Build a session read request
        SessionReadRequest readRequest = new SessionReadRequest.Builder()
                .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
                .read(DataType.TYPE_SPEED)
                .setSessionName(SAMPLE_SESSION_NAME)
                .build();
        // [END build_read_session_request]

        return readRequest;
    }

    private void dumpDataSet(DataSet dataSet) {
        Log.i(TAG, "Data returned for Data type: " + dataSet.getDataType().getName());
        for (DataPoint dp : dataSet.getDataPoints()) {
            SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
            Log.i(TAG, "Data point:");
            Log.i(TAG, "\tType: " + dp.getDataType().getName());
            Log.i(TAG, "\tStart: " + dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
            Log.i(TAG, "\tEnd: " + dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)));
            for (Field field : dp.getDataType().getFields()) {
                Log.i(TAG, "\tField: " + field.getName() +
                        " Value: " + dp.getValue(field));
            }
        }
    }

    private void dumpSession(Session session) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
        Log.i(TAG, "Data returned for Session: " + session.getName()
                + "\n\tDescription: " + session.getDescription()
                + "\n\tStart: " + dateFormat.format(session.getStartTime(TimeUnit.MILLISECONDS))
                + "\n\tEnd: " + dateFormat.format(session.getEndTime(TimeUnit.MILLISECONDS)));
    }

    // On resume

    @Override
    protected void onResume() {
        super.onResume();

        // Start midi

        if (midi != null)
            midi.start();
    }

    // On pause

    @Override
    protected void onPause() {
        super.onPause();

        // Stop midi

        if (midi != null)
            midi.stop();

        // Stop player

        if (player != null)
            player.stop();
    }

    // On touch

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        int action = event.getAction();
        int id = v.getId();

        switch (action) {
            // Down

            case MotionEvent.ACTION_DOWN:
                switch (id) {
                    case R.id.button1:
                        sendMidi(MidiConstants.NOTE_ON, 48, 63);
                        sendMidi(MidiConstants.NOTE_ON, 52, 63);
                        sendMidi(MidiConstants.NOTE_ON, 55, 63);
                        break;

                    case R.id.button2:
                        sendMidi(MidiConstants.NOTE_ON, 55, 63);
                        sendMidi(MidiConstants.NOTE_ON, 59, 63);
                        sendMidi(MidiConstants.NOTE_ON, 62, 63);
                        break;

                    default:
                        return false;
                }
                break;

            // Up

            case MotionEvent.ACTION_UP:
                switch (id) {
                    case R.id.button1:
                        sendMidi(MidiConstants.NOTE_OFF, 48, 0);
                        sendMidi(MidiConstants.NOTE_OFF, 52, 0);
                        sendMidi(MidiConstants.NOTE_OFF, 55, 0);
                        break;

                    case R.id.button2:
                        sendMidi(MidiConstants.NOTE_OFF, 55, 0);
                        sendMidi(MidiConstants.NOTE_OFF, 59, 0);
                        sendMidi(MidiConstants.NOTE_OFF, 62, 0);
                        break;

                    default:
                        return false;
                }
                break;

            default:
                return false;
        }

        return false;
    }

    // On click

    @Override
    public void onClick(View v) {
        int id = v.getId();

        switch (id) {
            case R.id.button3:
                if (player != null) {
                    player.stop();
                    player.release();
                }

                player = MediaPlayer.create(this, R.raw.ants);
                player.start();
                break;

            case R.id.button4:
                if (player != null)
                    player.stop();
                break;
        }
    }

    // Listener for sending initial midi messages when the Sonivox
    // synthesizer has been started, such as program change.

    @Override
    public void onMidiStart() {
        // Program change - harpsicord

        sendMidi(MidiConstants.PROGRAM_CHANGE, GeneralMidiConstants.HARPSICHORD);
    }

    // Send a midi message

    protected void sendMidi(int m, int p) {
        byte msg[] = new byte[2];

        msg[0] = (byte) m;
        msg[1] = (byte) p;

        midi.queueEvent(msg);
    }

    // Send a midi message

    protected void sendMidi(int m, int n, int v) {
        byte msg[] = new byte[3];

        msg[0] = (byte) m;
        msg[1] = (byte) n;
        msg[2] = (byte) v;

        midi.queueEvent(msg);
    }
}
