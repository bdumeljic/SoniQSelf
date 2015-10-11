package com.bdumeljic.soniqself;

import android.content.Intent;
import android.content.IntentSender;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessActivities;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DataReadResult;

import org.billthefarmer.mididriver.GeneralMidiConstants;
import org.billthefarmer.mididriver.MidiConstants;
import org.billthefarmer.mididriver.MidiDriver;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PlayDayActivity extends AppCompatActivity implements MidiDriver.OnMidiStartListener {

    private static String TAG = "PlayActivity";
    private static final String DATE_FORMAT = "yyyy.MM.dd HH:mm:ss";
    public static final String SAMPLE_SESSION_NAME = "Afternoon run";

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

    private PauseHandler mHandler;
    private static int MINS = 1;
    private static int DAY_DURATION_MS = 24 * 60 * 60 * 1000; // 86400000
    private static int PLAY_DURATION_MS = MINS * 60 * 1000; // 120000

    private Button mPlayButton;
    private TextView mActivityText;

    long endTime;
    long startTime;

    private ProgressBar mProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_play_day);

        if (savedInstanceState != null) {
            authInProgress = savedInstanceState.getBoolean(AUTH_PENDING);
        }

        buildFitnessClient();

        midi = new MidiDriver();

        mHandler = new PauseHandler();
        mHandler.pause();

        mProgress = (ProgressBar) findViewById(R.id.progressBar);

        mPlayButton = (Button) findViewById(R.id.play_button);
        mPlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHandler.pause();
                mPlayButton.setEnabled(false);
                mActivityText.setText("");
                new RetrieveDayDataTask().execute();
                //setupIdlePlayer();
                //startProgressBar();
            }
        });

        mActivityText = (TextView) findViewById(R.id.play_activity);

        if (midi != null) {
            midi.setOnMidiStartListener(this);
        }
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
                                mPlayButton.setClickable(true);
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
                                            PlayDayActivity.this, 0).show();
                                    return;
                                }
                                // The failure has a resolution. Resolve it.
                                // Called typically when the app is not yet authorized, and an
                                // authorization dialog is displayed to the user.
                                if (!authInProgress) {
                                    try {
                                        Log.i(TAG, "Attempting to resolve failed connection");
                                        authInProgress = true;
                                        result.startResolutionForResult(PlayDayActivity.this,
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
    private class RetrieveDayDataTask extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... params) {
            // Set a start and end time for our query, using a start time of 1 week before this moment.
            Calendar cal = Calendar.getInstance();
            Date now = new Date();
            cal.setTime(now);
            endTime = cal.getTimeInMillis();
            cal.add(Calendar.DAY_OF_MONTH, -1);
            startTime = cal.getTimeInMillis();

            SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
            Log.i(TAG, "Range Start: " + dateFormat.format(startTime) + " ms: " + startTime);
            Log.i(TAG, "Range End: " + dateFormat.format(endTime) + " ms: " + endTime);

            DataReadRequest dayDataReadRequest = queryDataForDay();
            DataReadResult dayDataReadResult = Fitness.HistoryApi.readData(mClient, dayDataReadRequest).await(1, TimeUnit.MINUTES);
            handleActivitySegment(dayDataReadResult);

            //mHandler.resume();
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            setupIdlePlayer();
            mHandler.resume();
            startProgressBar();
        }
    }

    private DataReadRequest queryDataForDay() {
        DataReadRequest readRequest = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
                .aggregate(DataType.TYPE_ACTIVITY_SEGMENT, DataType.AGGREGATE_ACTIVITY_SUMMARY)
                .aggregate(DataType.TYPE_CALORIES_EXPENDED, DataType.AGGREGATE_CALORIES_EXPENDED)
                .aggregate(DataType.TYPE_DISTANCE_DELTA, DataType.AGGREGATE_DISTANCE_DELTA)
                .aggregate(DataType.TYPE_SPEED, DataType.AGGREGATE_SPEED_SUMMARY)
                .bucketByActivitySegment(3, TimeUnit.MINUTES)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build();

        return readRequest;
    }

    private void handleActivitySegment(DataReadResult dataReadResult) {
        if (dataReadResult.getBuckets().size() > 0) {
            Log.i(TAG, "Number of returned buckets of DataSets is: "
                    + dataReadResult.getBuckets().size());
            for (Bucket bucket : dataReadResult.getBuckets()) {
                Log.i(TAG, "Bucket: " + bucket.getActivity());

                switch (bucket.getActivity()) {
                    case FitnessActivities.STILL:
                    case FitnessActivities.UNKNOWN:
                    case FitnessActivities.TILTING:
                    case FitnessActivities.ON_FOOT:
                    case FitnessActivities.IN_VEHICLE:
                        Log.i(TAG, "Ignore");
                        break;
                    case FitnessActivities.WALKING:
                        Log.i(TAG, "Walking");

                        readStepsBucket(bucket);

                        break;
                    case FitnessActivities.SLEEP:
                    case FitnessActivities.SLEEP_LIGHT:
                    case FitnessActivities.SLEEP_DEEP:
                    case FitnessActivities.SLEEP_REM:
                        Log.i(TAG, "Sleeping");

                        readSleepBucket(bucket);

                        break;
                    default:
                        Log.i(TAG, "Activity");

                        List<DataSet> dataSets = bucket.getDataSets();
                        for (DataSet dataSet : dataSets) {
                            dumpDataSet(dataSet);
                        }

                        readActBucket(bucket);

                        break;
                }
            }
        }
    }

    private void readStepsBucket(Bucket bucket) {
        Log.i(TAG, "Reading steps bucket ");

        long startActSeg = bucket.getStartTime(TimeUnit.MILLISECONDS);
        long endActSeg = bucket.getEndTime(TimeUnit.MILLISECONDS);

        int steps = 0;
        float distance = 0;

        List<DataSet> dataSets = bucket.getDataSets();
        for (DataSet dataSet : dataSets) {
            for (DataPoint dp : dataSet.getDataPoints()) {
                for (final Field field : dp.getDataType().getFields()) {
                    if (field.equals(Field.FIELD_STEPS)) {
                        steps = dp.getValue(field).asInt();
                    } else if (field.equals(Field.FIELD_DISTANCE)) {
                        distance = dp.getValue(field).asFloat();
                    }
                }
            }
        }

        long duration = calculateDurationPlay(startActSeg, endActSeg);

        if (steps > 20 && (steps / duration > 0.0001)) {
            Log.i(TAG, "Bucket step " + steps + " dist " + distance + " duration " + (endActSeg - startActSeg) + " d play " + calculateDurationPlay(startActSeg, endActSeg));
            playSteps(startActSeg, duration, steps, distance);
        }
    }

    private void readSleepBucket(Bucket bucket) {
        Log.i(TAG, "Reading sleep bucket " + bucket.getActivity());

        long startActSeg = bucket.getStartTime(TimeUnit.MILLISECONDS);
        long endActSeg = bucket.getEndTime(TimeUnit.MILLISECONDS);

        long duration = calculateDurationPlay(startActSeg, endActSeg);

        // 0: Default, 1: Light, 2: Deep, 3: REM
        int type = -1;

        switch (bucket.getActivity()) {
            case FitnessActivities.SLEEP:
                type = 0;
                break;
            case FitnessActivities.SLEEP_LIGHT:
                type = 1;
                break;
            case FitnessActivities.SLEEP_DEEP:
                type = 2;
                break;
            case FitnessActivities.SLEEP_REM:
                type = 3;
                break;
        }

        playSleep(startActSeg, duration, type);
    }

    private void readActBucket(Bucket bucket) {
        Log.i(TAG, "Reading act bucket: " + bucket.getActivity());

        long startActSeg = bucket.getStartTime(TimeUnit.MILLISECONDS);
        long endActSeg = bucket.getEndTime(TimeUnit.MILLISECONDS);

        long duration = calculateDurationPlay(startActSeg, endActSeg);

        int steps = 0;
        float distance = 0;
        float speed = 0;
        float calories = 0;

        List<DataSet> dataSets = bucket.getDataSets();
        for (DataSet dataSet : dataSets) {
            for (DataPoint dp : dataSet.getDataPoints()) {
                for (final Field field : dp.getDataType().getFields()) {
                    if (field.equals(Field.FIELD_STEPS)) {
                        steps = dp.getValue(field).asInt();
                    } else if (field.equals(Field.FIELD_DISTANCE)) {
                        distance = dp.getValue(field).asFloat();
                    } else if (field.equals(Field.FIELD_SPEED)) {
                        speed = dp.getValue(field).asFloat();
                    } else if (field.equals(Field.FIELD_CALORIES)) {
                        calories = dp.getValue(field).asFloat();
                    }
                }
            }
        }

        playAct(startActSeg, duration, bucket.getActivity(), steps, distance, speed, calories);
    }

    private void playSleep(long start, final long duration, int type) {
        mHandler.postPausedDelayed(new Runnable() {
            @Override
            public void run() {
                switchToSleep();
                mActivityText.append("\n" + "Sleeping");

                for (int i = 0; i < duration; i = i + 80) {
                    sendMidi(MidiConstants.NOTE_ON, 48, 63);
                    sendMidi(MidiConstants.NOTE_ON, 52, 63);
                    sendMidi(MidiConstants.NOTE_ON, 55, 63);

                    try {
                        Thread.sleep(30);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    sendMidi(MidiConstants.NOTE_OFF, 48, 0);
                    sendMidi(MidiConstants.NOTE_OFF, 52, 0);
                    sendMidi(MidiConstants.NOTE_OFF, 55, 0);

                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                switchToFootSteps();

            }
        }, calculateDelay(start));
    }

    private void playAct(long start, final long duration, final String activity, int steps, float distance, float speed, float calories) {
        mHandler.postPausedDelayed(new Runnable() {
            @Override
            public void run() {
                switchToActive();
                String act = activity.substring(0,1).toUpperCase() + activity.substring(1).toLowerCase();
                mActivityText.append("\n" + act);

                for (int i = 0; i < duration; i = i + 100) {
                    sendMidi(MidiConstants.NOTE_ON, 48, 63);
                    sendMidi(MidiConstants.NOTE_ON, 52, 63);
                    sendMidi(MidiConstants.NOTE_ON, 55, 63);
                    sendMidi(MidiConstants.NOTE_OFF, 48, 0);
                    sendMidi(MidiConstants.NOTE_OFF, 52, 0);
                    sendMidi(MidiConstants.NOTE_OFF, 55, 0);

                    try {
                        Thread.sleep(98);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                switchToFootSteps();

            }
        }, calculateDelay(start));
    }

    private void playSteps(long start, final long duration, final int steps, final float distance) {
        mHandler.postPausedDelayed(new Runnable() {
            @Override
            public void run() {
                mActivityText.append("\n Walking (" + steps + " steps)");

                for (int i = 0; i < duration; i = i + 60) {
                    sendMidi(MidiConstants.NOTE_ON, 48, 63);
                    sendMidi(MidiConstants.NOTE_ON, 52, 63);
                    sendMidi(MidiConstants.NOTE_ON, 55, 63);
                    sendMidi(MidiConstants.NOTE_OFF, 48, 0);
                    sendMidi(MidiConstants.NOTE_OFF, 52, 0);
                    sendMidi(MidiConstants.NOTE_OFF, 55, 0);

                    try {
                        Thread.sleep(60);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

            }
        }, calculateDelay(start));
    }

    private long calculateDurationPlay(long start, long end) {
        return (end - start) / (DAY_DURATION_MS / PLAY_DURATION_MS);
    }

    private long calculateDelay(long dpStartTime) {
        float floating = (dpStartTime - startTime) / (float) DAY_DURATION_MS;
        float result = floating * PLAY_DURATION_MS;
        return (long) result;
    }

    private void setupIdlePlayer() {
        player = MediaPlayer.create(this, R.raw.heartbeat2);
        player.setVolume(0.4f, 0.4f);
        player.setLooping(true);
        player.start();

        mHandler.postPausedDelayed(new Runnable() {
            @Override
            public void run() {
                if (player != null && player.isPlaying()) {
                    player.pause();
                    player.seekTo(0);
                }

                mPlayButton.setEnabled(true);
            }
        }, PLAY_DURATION_MS);
    }

    private void playTestSound() {
        mHandler.postPausedDelayed(new Runnable() {
            @Override
            public void run() {
                mActivityText.append("\n test");

                switchToFootSteps();

                sendMidi(MidiConstants.NOTE_ON, 48, 35);
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                sendMidi(MidiConstants.NOTE_ON, 52, 35);
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                sendMidi(MidiConstants.NOTE_ON, 55, 35);
                /*try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                sendMidi(MidiConstants.NOTE_OFF, 48, 0);
                sendMidi(MidiConstants.NOTE_OFF, 52, 0);
                sendMidi(MidiConstants.NOTE_OFF, 55, 0);

               /* try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                switchToSleep();

                sendMidi(MidiConstants.NOTE_ON, 48, 35);
                sendMidi(MidiConstants.NOTE_ON, 52, 35);
                sendMidi(MidiConstants.NOTE_ON, 55, 35);
                sendMidi(MidiConstants.NOTE_OFF, 48, 0);
                sendMidi(MidiConstants.NOTE_OFF, 52, 0);
                sendMidi(MidiConstants.NOTE_OFF, 55, 0);

                switchToFootSteps();*/
            }
        }, 0);
    }

    private void startProgressBar() {
        mProgress.setVisibility(View.VISIBLE);
        mProgress.setMax(100);

        new CountDownTimer(PLAY_DURATION_MS, 1000) {
            public void onTick(long millisUntilFinished) {
                float progress = (PLAY_DURATION_MS - millisUntilFinished) / (float) PLAY_DURATION_MS * 100f;
                mProgress.setProgress((int) progress);
            }

            public void onFinish() {
                mProgress.setVisibility(View.INVISIBLE);
            }
        }.start();
    }

    private void dumpDataSet(DataSet dataSet) {

        Log.i(TAG, "Data returned for Data type: " + dataSet.getDataType().getName());
        for (DataPoint dp : dataSet.getDataPoints()) {
            SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
            Log.i(TAG, "Data point:");
            Log.i(TAG, "\tType: " + dp.getDataType().getName());
            Log.i(TAG, "\tStart: " + dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)) + " ms: " + dp.getStartTime(TimeUnit.MILLISECONDS));
            Log.i(TAG, "\tEnd: " + dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)) + " ms: " + dp.getEndTime(TimeUnit.MILLISECONDS));
            for (final Field field : dp.getDataType().getFields()) {
                Log.i(TAG, "\tField: " + field.getName() + " Value: " + dp.getValue(field));
            }


        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Start midi

        if (midi != null)
            midi.start();
    }

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

    // Listener for sending initial midi messages when the Sonivox
    // synthesizer has been started, such as program change.

    @Override
    public void onMidiStart() {
        // Program change
        switchToFootSteps();
    }

    public void switchToFootSteps() {
        sendMidi(MidiConstants.PROGRAM_CHANGE, GeneralMidiConstants.SYNTH_DRUM);
    }

    public void switchToActive() {
        sendMidi(MidiConstants.PROGRAM_CHANGE, GeneralMidiConstants.SLAP_BASS_0);
    }

    public void switchToSleep() {
        sendMidi(MidiConstants.PROGRAM_CHANGE, GeneralMidiConstants.ACOUSTIC_GRAND_PIANO);
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
