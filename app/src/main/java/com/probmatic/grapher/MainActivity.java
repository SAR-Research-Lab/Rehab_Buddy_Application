package com.probmatic.grapher;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.vecmath.AxisAngle4d;
import javax.vecmath.Quat4d;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.NumberPicker;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.DataSet;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.shimmerresearch.android.Shimmer;
import com.shimmerresearch.android.guiUtilities.ShimmerBluetoothDialog;
import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.FormatCluster;
import com.shimmerresearch.driver.ObjectCluster;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentManager;
//EER 9/11
import android.widget.ToggleButton;

import android.widget.Button;
import android.widget.EditText;

import org.w3c.dom.Text;


public class MainActivity extends AppCompatActivity implements
        MenuFragment.OnFragmentInteractionListener {

    // Constants
    final float PI = 3.141592653f;
    private final static String LOG_TAG = "RehabBuddy";
    static final int REQUEST_CONNECT_SHIMMER = 2;
    static final int REQUEST_STORAGE_PERMISSION = 1;

    // TextViews
    TextView mDirectionFeedback;
    TextView mCountFeedback;
    TextView tHoldTime;
    TextView tRepsCounter;
    TextView tSetsCounter;

    // Shimmer Object
    private Shimmer shimmer = null;

    // Arrays for data collection
    ArrayList<Quat4d> rStartData;
    ArrayList<Quat4d> rStopData;
    ArrayList<Quat4d> calibData = new ArrayList<>();

    // CSV Writers
    DataWriter csvData, csvExercise;

    /**** sensor parameters
     * These are all the active states that the system could be utilizing
     * They are also the flags used for logging current events ****/
    SensorParameters sensor = new SensorParameters();

    /*** exercise state
     * Possible states for exercise select Spinner menu in exerciseInitialize() ***/
    ExerciseState exerciseState;

    /**** exercise parameters
     * This is the information stored by each recorded exercise
     * name, qStart, qEnd, qIdeal, angIdeal, holdTop, holdBottom, totalReps, and totalSets
     *   are recorded to CSV ****/
    ArrayList<ExerciseParameters> exercisesAvailable = new ArrayList<>();
    ExerciseParameters exercise = new ExerciseParameters();

    /**** handles parameters
     * Holds graph information****/
    HandlesParameters handles = new HandlesParameters();

    /**** record data
     * Stores recorded exercises for later viewing***/
    RecordData records = new RecordData();

    MenuFragment fragment = new MenuFragment();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        homeLogic();

        // Initializes shimmer object. This deprecated function is the one that works properly
        shimmer = new Shimmer(this, mHandler,"RightArm", 51.2, 0,
                0,Shimmer.SENSOR_ACCEL|Shimmer.SENSOR_GYRO|Shimmer.SENSOR_MAG,
                false);

        // Below attempt to use the newer Shimmer3 constructor but it crashes
        //Integer[] sensorArray = {Shimmer.SENSOR_ACCEL,Shimmer.SENSOR_GYRO,Shimmer.SENSOR_MAG};
        //shimmer = new Shimmer(mHandler, "Shimmer", 51.2, 0,
        //        0, sensorArray,0, 0, 1, 1, true);

        shimmer.enable3DOrientation(true);

        // Checks if permissions have been granted before
        if (ContextCompat.checkSelfPermission(getBaseContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            // No explanation needed; request the permission
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if(csvData != null)
            csvData.flush(); // Makes sure CSV buffer is written
    }

    @Override
    public int onFragmentInteraction(View v, int currentPage) {
        int id = v.getId();
        int startPage = currentPage;

        switch(id){ // Finalize current page and find what page to go to
            case R.id.bContinue:
                if(startPage == 2)
                    switch(exerciseState) {
                        case SELECT:
                            Toast.makeText(getApplicationContext(), "Please select an exercise",
                                    Toast.LENGTH_SHORT).show();
                            break;
                        case CREATE:
                            currentPage = 3;
                            exercise.qStart = null;
                            exercise.qEnd = null;
                            break;
                        case SELECTED:
                            currentPage = 4;
                            break;
                    }
                else if(startPage == 3){
                    EditText etExerciseName = (EditText) findViewById(R.id.etExerciseName);

                    if(etExerciseName.getText().toString().equals("") || exercise.qStart == null ||
                            exercise.qEnd == null){
                        Toast.makeText(getApplicationContext(),
                                "Record and name exercise", Toast.LENGTH_SHORT).show();
                        currentPage = 3;
                    }
                    else {
                        exercise.name = etExerciseName.getText().toString();
                        exercisesAvailable.add(new ExerciseParameters(exercise));

                        try {
                            csvExercise.writeExerciseToCSV(exercise);
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (NullPointerException e) {
                            e.printStackTrace();
                        }
                        currentPage = 4;
                    }
                }
                else if(startPage == 5)
                    currentPage = -1;
                else
                    currentPage += 1;
                break;
            case R.id.bPreviousPage:
                if(startPage == 4)
                    currentPage = 2;
                else if(startPage != -1)
                    currentPage -= 1;
                break;
            case R.id.bConnectSensor:
                if(startPage > 0)
                    currentPage = 0;
                break;
            case R.id.bInitializeSensor:
                if(startPage > 1)
                    currentPage = 1;
                break;
            case R.id.bSelectExercise:
                if(startPage > 2)
                    currentPage = 2;
                break;
            case R.id.bStartEndPos:
                if(startPage > 3)
                    currentPage = 3;
                break;
            case R.id.bBeginExercise:
                if(startPage > 4)
                    currentPage = 4;
                break;
            case R.id.bExerciseSummary:
                break;
        }
        if(currentPage != startPage){ // Go to proper page
            FragmentManager fm = getSupportFragmentManager();
            fm.beginTransaction().remove(fragment).commit();
            fm.executePendingTransactions();

            // Stop running graph if back button is pressed
            if (sensor.running)
                sensor.running = false;
            if(sensor.no_feedback)
                sensor.no_feedback = false;
            if(sensor.pt_feedback)
                sensor.pt_feedback = false;

            switch(currentPage){
                case -1: // Home Page
                    homeLogic();
                    break;
                case 0: // Connect Page
                    connectSensorPage();
                    break;
                case 1: // Initialize Page
                    setContentView(R.layout.initialize_sensor);
                    fm.beginTransaction().replace(R.id.initializeSensorTablet, fragment).commitNow();
                    break;
                case 2: // Exercise Select Page
                    setContentView(R.layout.exercise_select);
                    exerciseInitialize();
                    fm.beginTransaction().replace(R.id.exerciseSelectTablet, fragment).commitNow();
                    break;
                case 3: // Start End Pos Page
                    setContentView(R.layout.startendpos);
                    fm.beginTransaction().replace(R.id.startEndPosTablet, fragment).commitNow();
                    break;
                case 4: // Begin Exercise Page
                    setContentView(R.layout.beginexercise);
                    beginExerciseLogic();
                    fm.beginTransaction().replace(R.id.beginExerciseTablet, fragment).commitNow();
                    break;
                case 5: // Summary Page
                    setContentView(R.layout.summary);
                    fm.beginTransaction().replace(R.id.summaryTablet, fragment).commitNow();
                    summaryPage();
                    break;
            }
        }
        return currentPage;
    }

    /*******************************PAGE LAYOUT LOGIC START************************************/

    /***********************Home Page (Real Home Page)***********************************/
    public void homeLogic(){  // Handles checkboxes and warnings on home page
        setContentView(R.layout.home);

        CheckBox cbStreamAll = (CheckBox) findViewById(R.id.cbStreamAll);
        cbStreamAll.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked) sensor.stream_all = true;
                else sensor.stream_all = false;
            }
        });

        CheckBox cbLogAll = (CheckBox) findViewById(R.id.cbLogAll);
        cbLogAll.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked) sensor.log_all = true;
                else sensor.log_all = false;
            }
        });


        if(!firstTime){
            TextView mWarning = (TextView) findViewById(R.id.mWarning);
            mWarning.setText(getApplicationContext().getResources().getString(R.string.csv_warning));
        }
    }
    boolean firstTime = true;

    public void finishHomePage(View v){
        // Get CSV Name from Home before switching Views
        if(firstTime){
            EditText etCSVName = (EditText) findViewById(R.id.etCSVName);
            String csvName = etCSVName.getText().toString();
            csvData = new DataWriter(csvName, "Data", sensor.log_all);
            csvExercise = new DataWriter(csvName,"Exercises");
            firstTime = false;
        }
        fragment.setCurrentPage(0);
        connectSensorPage();
    }

    /***********************Experimental database stuff***********************************/
    /***********************Experimental database stuff***********************************/

    // Go to ChooserActivity
    public void ChooseActivityPage(View v) {
        startActivity(new Intent(MainActivity.this, ChooserActivity.class));
        finish();
    }

    /***********************Experimental database stuff***********************************/
    /***********************Experimental database stuff***********************************/



    /***********************CONNECT SENSOR***********************************/
    public void connectSensorPage() { // Handles buttons landing on connectSensorPage
        setContentView(R.layout.connect_sensor);
        mDirectionFeedback = (TextView) findViewById(R.id.directionFeedback);
        mDirectionFeedback.setText(getApplicationContext().getResources().getString(R.string.descriptionConnect));

        FragmentManager fm = getSupportFragmentManager();
        fm.beginTransaction().remove(fragment).commit();
        fm.executePendingTransactions();
        fm.beginTransaction().replace(R.id.connectSensorTablet, fragment).commitNow();
    }

    public void connectButtonPressed(View v){
        if(!sensor.connecting) {
            sensor.connecting = true;
            connectDevice();

            // Waits 15s for connection to update messages accordingly
            //new CountDownTimer(15000, 15000) {
            new CountDownTimer(12000, 3000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    if (sensor.connected)
                        onFinish();
                }

                @Override
                public void onFinish() {
                    if (!sensor.connected)
                        try{ // Avoids crashes if page is switched before connection
                            mDirectionFeedback.setText(getApplicationContext().getResources().getString(R.string.connectFailure));
                        }catch (Exception e){
                            e.printStackTrace();
                        }
                    sensor.connecting = false;
                    cancel();
                }
            }.start();
        }
    }

    public void connectDevice() { // Use Shimmer API for connection handling
        mDirectionFeedback.setText(getApplicationContext().getResources().getString(R.string.descriptionConnecting));
        // If there is a sensor already connected, it is disconnected
        if (sensor.connected) {
            if(!sensor.stream_all)
                shimmer.stopStreaming();
            shimmer.stop();
            sensor.connected = false;
        }

        Log.i(LOG_TAG, "Loading sensor");
        Intent intent = new Intent(getApplicationContext(), ShimmerBluetoothDialog.class);
        startActivityForResult(intent, ShimmerBluetoothDialog.REQUEST_CONNECT_SHIMMER);
    }

    /**************************CALIBRATE SENSOR********************************/
    public void initializeByRunning(View v){ // Let the sensor stream for a while
        calibData.clear();
        sensor.initializing = true;
        if(!sensor.stream_all)
            shimmer.startStreaming();
        TextView tCal = (TextView) findViewById(R.id.tInitializeDirections);
        tCal.setText(getApplicationContext().getResources().getString(R.string.initializingInProgress));
    }

    /*****************************SELECT EXERCISE*****************************/
    public void exerciseInitialize() {
        numberPickerInit();

        String[] names = new String[exercisesAvailable.size() + 2]; // Makes and loads all exercise names
        names[0] = "Select exercise";
        names[1] = "Create new";
        for(int i = 0; i < exercisesAvailable.size(); i++){
            names[i+2] = exercisesAvailable.get(i).name;
        }

        // Set up Spinner (drop down menu)
        Spinner sClickToSelectExercise = (Spinner) findViewById(R.id.sClickToSelectExercise);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item, names);
        sClickToSelectExercise.setAdapter(adapter);
        sClickToSelectExercise.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Log.i(LOG_TAG, "ID: " + id);
                switch((int)id){
                    case 0: // Select exercise option
                        Toast.makeText(getApplicationContext(), "Please select an exercise",
                                Toast.LENGTH_SHORT).show();
                        exerciseState = ExerciseState.SELECT;
                        // Cannot continue until one is selected
                        break;
                    case 1: // Create new option
                        Toast.makeText(getApplicationContext(), "Select parameters and continue",
                                Toast.LENGTH_SHORT).show();
                        exerciseState = ExerciseState.CREATE;
                        // Continues to set start/end pos page
                        break;
                    default: // Previously made exercise option
                        exerciseState = ExerciseState.SELECTED;
                        Log.i(LOG_TAG, "Id " + id + " selected");
                        exercise.set(exercisesAvailable.get((int)id - 2));
                        Log.i(LOG_TAG, "Loaded exercise: " + exercise.name + " " + exercise.qStart.w + " " + exercise.qIdeal.w);
                        // Continues to begin exercise
                        numberPickerUpdate();
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    public void numberPickerInit() { // Sets boundaries and listeners to update NP values
        NumberPicker npSets = (NumberPicker) findViewById(R.id.npSets);
        NumberPicker npReps = (NumberPicker) findViewById(R.id.npReps);
        NumberPicker npHoldTop = (NumberPicker) findViewById(R.id.npHoldTop);
        NumberPicker npHoldBottom = (NumberPicker) findViewById(R.id.npHoldBottom);

        npSets.setMinValue(1);
        npSets.setMaxValue(5);
        npReps.setMinValue(1);
        npReps.setMaxValue(20);
        npHoldTop.setMinValue(1);
        npHoldTop.setMaxValue(100);
        npHoldBottom.setMinValue(1);
        npHoldBottom.setMaxValue(100);

        numberPickerUpdate();

        npSets.setWrapSelectorWheel(false);
        npReps.setWrapSelectorWheel(false);
        npHoldTop.setWrapSelectorWheel(false);
        npHoldBottom.setWrapSelectorWheel(false);

        npSets.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldVal, int newVal){
                exercise.totalSets = newVal;
            }
        });
        npReps.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldVal, int newVal){
                exercise.totalReps = newVal;
            }
        });
        npHoldTop.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldVal, int newVal){
                exercise.holdTop = newVal;
            }
        });
        npHoldBottom.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldVal, int newVal){
                exercise.holdBottom = newVal;
            }
        });
    }

    public void numberPickerUpdate(){ // Loads NumberPickers with current exercise values
        NumberPicker npSets = (NumberPicker) findViewById(R.id.npSets);
        NumberPicker npReps = (NumberPicker) findViewById(R.id.npReps);
        NumberPicker npHoldTop = (NumberPicker) findViewById(R.id.npHoldTop);
        NumberPicker npHoldBottom = (NumberPicker) findViewById(R.id.npHoldBottom);

        npSets.setValue(exercise.totalSets);
        npReps.setValue(exercise.totalReps);
        npHoldTop.setValue(exercise.holdTop);
        npHoldBottom.setValue(exercise.holdBottom);
    }

    /*****************************START/END POSITION*****************************/
    public void recordStartPos(View v){
        if(!sensor.recordingStart) {
            sensor.recordingStart = true;

            TextView tStartPosFeedback;
            tStartPosFeedback = (TextView) findViewById(R.id.tStartPosFeedback);
            tStartPosFeedback.setText(getApplicationContext().getResources().getString(R.string.descriptionRecordStart));

            rStartData = new ArrayList<Quat4d>();
            if(!sensor.running && !sensor.stream_all)
                shimmer.startStreaming();
        }
    }
    public void finishRecordStartPos(){
        if(!sensor.running && !sensor.stream_all)
            shimmer.stopStreaming();

        exercise.qStart = new Quat4d();
        float[] temp = {0f, 0f, 0f, 0f};
        for(Quat4d qC : rStartData){
            temp[0] += qC.w;
            temp[1] += qC.x;
            temp[2] += qC.y;
            temp[3] += qC.z;
        }

        exercise.qStart.w = temp[0]/rStartData.size();
        exercise.qStart.x = temp[1]/rStartData.size();
        exercise.qStart.y = temp[2]/rStartData.size();
        exercise.qStart.z = temp[3]/rStartData.size();

        Log.i(LOG_TAG, "Calibrated qStart: " + exercise.qStart.w + ' ' + exercise.qStart.x
                + ' ' + exercise.qStart.y + ' ' + exercise.qStart.z);

        TextView tPosFeedback = (TextView) findViewById(R.id.tPosFeedback);
        TextView tStartPosFeedback = (TextView) findViewById(R.id.tStartPosFeedback);
        try {
            tStartPosFeedback.setText(getApplicationContext().getResources().getString(R.string.descriptionComplete));
        } catch (Exception e){
            e.printStackTrace();
        }

        if(exercise.qEnd == null)
            try {
                tPosFeedback.setText("Start position set, please set end position");
            } catch (Exception e){
                e.printStackTrace();
            }
        else{
            exercise.qIdeal.set(exercise.qStart);
            exercise.qIdeal.conjugate();
            exercise.qIdeal.mul(exercise.qEnd);

            AxisAngle4d ideal = new AxisAngle4d();
            ideal.set(exercise.qIdeal);
            exercise.angIdeal = (float) ideal.angle;

            int a = (int) (exercise.angIdeal*180/PI);
            try {
                tPosFeedback.setText("Positions move about " + a + "°. If this is correct, please name " +
                        "and click CONTINUE to save. Otherwise, rerecord.");
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    public void recordStopPos(View v){
        if(!sensor.recordingStop) {
            sensor.recordingStop = true;
            TextView tStopPosFeedback;
            tStopPosFeedback = (TextView) findViewById(R.id.tStopPosFeedback);
            tStopPosFeedback.setText(getApplicationContext().getResources().getString(R.string.descriptionRecordStop));
            Log.i(LOG_TAG, "Starting qEnd calibration");
            rStopData = new ArrayList<Quat4d>();
            if(!sensor.stream_all)
                shimmer.startStreaming();
        }
    }
    public void finishRecordStopPos(){
        if(!sensor.stream_all)
            shimmer.stopStreaming();


        exercise.qEnd = new Quat4d();
        float[] temp = {0f, 0f, 0f, 0f};
        for(Quat4d qC : rStopData){
            temp[0] += qC.w;
            temp[1] += qC.x;
            temp[2] += qC.y;
            temp[3] += qC.z;
        }

        exercise.qEnd.w = temp[0]/rStopData.size();
        exercise.qEnd.x = temp[1]/rStopData.size();
        exercise.qEnd.y = temp[2]/rStopData.size();
        exercise.qEnd.z = temp[3]/rStopData.size();

        Log.i(LOG_TAG, "Calibrated qEnd: " + exercise.qEnd.w + ' ' + exercise.qEnd.x + ' ' +
                exercise.qEnd.y + ' ' + exercise.qEnd.z);

        TextView tPosFeedback = (TextView) findViewById(R.id.tPosFeedback);
        TextView tStopPosFeedback = (TextView) findViewById(R.id.tStopPosFeedback);
        try {
            tStopPosFeedback.setText(getApplicationContext().getResources().getString(R.string.descriptionComplete));
        } catch (Exception e){
            e.printStackTrace();
        }

        if(exercise.qStart == null){
            try {
                tPosFeedback.setText("End position set, please set start position");
            } catch (Exception e){
                e.printStackTrace();
            }
        }
        else{
            exercise.qIdeal.set(exercise.qStart);
            exercise.qIdeal.conjugate();
            exercise.qIdeal.mul(exercise.qEnd);

            AxisAngle4d ideal = new AxisAngle4d();
            ideal.set(exercise.qIdeal);
            exercise.angIdeal = (float) ideal.angle;

            int a = (int) (exercise.angIdeal*180/PI);
            try {
                tPosFeedback.setText("Positions move about " + a + "°. If this is correct, please name " +
                        "and click CONTINUE to save. Otherwise, rerecord.");
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    /*****************************BEGIN EXERCISE*****************************/
    public void beginExerciseLogic(){ // Generates an_guide and initializes TextViews for exercise

        TextView tGraphDescription = (TextView) findViewById(R.id.tGraphDescription);
        tGraphDescription.setText("Exercise Name: " + exercise.name);

        // In terms of ptIndex (from runtimeFcn)
        //  0-4 flat
        //  5-14 rising
        //  15-34 flat
        //  35-44 falling
        //  45-49 flat

        float slope = 2;
        //float top_val = exercise.angIdeal * 180/PI;// + 15;
        float top_val = 100;
        float yStartRange = 10*exercise.holdBottom;
        float yIncRange = top_val/slope;
        float yPlatRange = 10*exercise.holdTop;
        float yDecRange = yIncRange;
        float yEndRange = 10*exercise.holdBottom;
        float val = 0f;

        handles.an_guide.clear();
        handles.end_zone.clear();

        for(int i = 0; i < yStartRange + yIncRange + yPlatRange + yDecRange + yEndRange; i++){
            if(i < yStartRange)
                val = 0f;
            else if(i < yStartRange + yIncRange)
                val = slope * (i-yStartRange);
            else if(i < yStartRange + yIncRange + yPlatRange)
                val = top_val;
            else if(i < yStartRange + yIncRange + yPlatRange + yDecRange) {
                val = top_val - slope * (i - (yStartRange + yIncRange + yPlatRange));
                handles.decline.add(new Entry(i, val));
            }else
                val = 0f;
            // Added 4/30/21: Quick hack to jump to hold bottom if user moves down too early.
            handles.an_guide.add(new Entry(i++, val));
            handles.end_zone.add(new Entry(i, handles.end_zone_threshold));
        }
        drawBar(0.);
        drawGraphFromHandles();

        tHoldTime = (TextView) findViewById(R.id.tHoldTimer);
        tRepsCounter = (TextView) findViewById(R.id.RepsCounter);
        tSetsCounter = (TextView) findViewById(R.id.SetsCounter);
        mCountFeedback = (TextView) findViewById(R.id.mCountFeedback);
        mDirectionFeedback = (TextView) findViewById(R.id.mDirectionFeedback);

        tRepsCounter.setText(exercise.currentRep + "/" + exercise.totalReps);
        tSetsCounter.setText(exercise.currentSet + "/" + exercise.totalSets);
    }
    //EER 9/11 Simple delay to indicate button press
    public void calibrateStartOnFly(View v){ // Sets start position in one click during exercise

        final Button bCalibrateOnFly = findViewById(R.id.bCalibrateOnFly);
        bCalibrateOnFly.setBackgroundColor(Color.LTGRAY);
        //To create a simple delay
        Handler handler = new Handler();
        sensor.calibrate = true;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                bCalibrateOnFly.setBackgroundColor(Color.rgb(0,128,0));
            }
        }, 100);

    }

    public void startRecordNoFeedbackHandler(View v){ // Records data without graphing
        Button bRecordNoFeedback =
                findViewById(R.id.bRecordNoFeedback);

        if(!sensor.no_feedback){
            if(records.get(exercise.name) == null)
                records.createNew(exercise.name);
            else
                records.load(exercise.name);
            records.current.noFeedback = new ArrayList<>();
            records.current.noPomError = 0;

            records.current.noROMSum = 0;
            //records.current.noHoldPoints = new ArrayList<>();
            records.tempStartTime = null;

            sensor.calibrate = true; // Sets start position when recording begins
            sensor.no_feedback = true;
            if(!sensor.stream_all)
                shimmer.startStreaming();
            bRecordNoFeedback.setText("Currently Recording, Stop?");  //hi Max!
            //EER 9/11 Changing button color
            bRecordNoFeedback.setBackgroundColor(Color.RED);
        }
        else {
            sensor.no_feedback = false;
            if(!sensor.stream_all)
                shimmer.stopStreaming();
            bRecordNoFeedback.setText("Record Without Feedback");
            bRecordNoFeedback.setBackgroundColor(Color.GRAY);
        }
    }

    public void startRecordPTFeedbackHandler(View v){ // Records PT data without graphing
        Button bRecordPTFeedback =
                findViewById(R.id.bRecordPTFeedback);

        if(!sensor.pt_feedback){
            if(records.get(exercise.name) == null)
                records.createNew(exercise.name);
            else
                records.load(exercise.name);
            records.current.ptFeedback = new ArrayList<>();
            records.current.ptPomError = 0;

            records.current.ptROMSum = 0;
            //records.current.ptHoldPoints = new ArrayList<>();
            records.tempStartTime = null;

            sensor.calibrate = true; // Sets start position when recording begins
            sensor.pt_feedback = true;
            if(!sensor.stream_all)
                shimmer.startStreaming();
            bRecordPTFeedback.setText("Currently Recording, Stop?");  //hi Max!
            bRecordPTFeedback.setBackgroundColor(Color.RED);
        }
        else {
            sensor.pt_feedback = false;
            if(!sensor.stream_all)
                shimmer.stopStreaming();
            bRecordPTFeedback.setText("Record With PT Feedback");
            bRecordPTFeedback.setBackgroundColor(Color.GRAY);
        }
    }

    /*****************************EXERCISE SUMMARY*****************************/
    public void summaryPage(){
        final TextView tPtPOM = (TextView) findViewById(R.id.tPtPOM);
        final TextView tNoPOM = (TextView) findViewById(R.id.tNoPOM);
        final TextView tRbPOM = (TextView) findViewById(R.id.tRbPOM);


        final TextView tNoReps = (TextView) findViewById(R.id.tNoReps);
        final TextView tPtReps = (TextView) findViewById(R.id.tPtReps);
        final TextView tRbReps = (TextView) findViewById(R.id.tRbReps);


        final TextView tNoHoldTime = (TextView) findViewById(R.id.tNoHoldTime);
        final TextView tPtHoldTime = (TextView) findViewById(R.id.tPtHoldTime);
        final TextView tRbHoldTime = (TextView) findViewById(R.id.tRbHoldTime);

        final TextView tNoTotalTime = (TextView) findViewById(R.id.tNoTotalTime);
        final TextView tPtTotalTime = (TextView) findViewById(R.id.tPtTotalTime);
        final TextView tRbTotalTime = (TextView) findViewById(R.id.tRbTotalTime);

        final TextView tNoAvgROM = (TextView) findViewById(R.id.tNoAvgROM);
        final TextView tPtAvgROM = (TextView) findViewById(R.id.tPtAvgROM);
        final TextView tRbAvgROM = (TextView) findViewById(R.id.tRbAvgROM);


        String[] names = new String[records.records.size()]; // Makes and loads all exercise names
        for(int i = 0; i < records.records.size(); i++){
            names[i] = records.records.get(i).exerciseName;
        }

        // Set up Spinner (drop down menu)
        Spinner sExerciseGraphSelect = (Spinner) findViewById(R.id.sExerciseGraphSelect);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item, names);
        sExerciseGraphSelect.setAdapter(adapter);
        sExerciseGraphSelect.setSelection(records.records.indexOf(records.current));
        sExerciseGraphSelect.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                try {
                    Log.i(LOG_TAG, records.get(position).exerciseName);
                    RecordData.Record r = records.get(position);
                    int noReps = 0;
                    float noAvgHold = 0;
                    float noTotalTime = 0;
                    int rbReps = 0;
                    float rbAvgHold = 0;
                    float rbTotalTime = 0;
                    int ptReps = 0;
                    float ptAvgHold = 0;
                    float ptTotalTime = 0;
                    float noMaxROM = 0;
                    int noMaxROMSize = 0;
                    float rbMaxROM = 0;
                    int rbMaxROMSize = 0;
                    float ptMaxROM = 0;
                    int ptMaxROMSize = 0;

                    ArrayList<Entry> noHoldPoints = new ArrayList<>();
                    ArrayList<Entry> rbHoldPoints = new ArrayList<>();
                    ArrayList<Entry> ptHoldPoints = new ArrayList<>();

                    if(r.noFeedback != null) {
                        float avg = r.noROMSum / r.noFeedback.size();
                        float s;
                        float xprev = -1010;
                        float xcurr = 0;
                        for (Entry e : r.noFeedback) {
                            xcurr = e.getX();
                            s = e.getY() - avg;
                            if (((xcurr - xprev) > 1000) && (s > -3 && s < 3)) {
                                noHoldPoints.add((Entry) e.copy());
                                xprev = xcurr;
                            }
                            s = s - avg/2 ;
                            if (s > 0) {
                                noMaxROM += e.getY();
                                noMaxROMSize++;
                                //r.noMaxPoints.add((Entry) e.copy());
                            }
                        }
                        noReps = Math.round(noHoldPoints.size() / 2);

                        for (int i = 1; i < noHoldPoints.size(); i += 2) {
                            noAvgHold += noHoldPoints.get(i).getX() - noHoldPoints.get(i - 1).getX();
                        }
                        noAvgHold = noAvgHold / noReps / 1000;
                        noTotalTime = (noHoldPoints.get(noHoldPoints.size() - 1).getX() - noHoldPoints.get(0).getX()) / 1000;
                    }

                    if(r.rbFeedback != null) {
                        float avg = r.rbROMSum / r.rbFeedback.size();
                        float s;
                        float xprev = -1010;
                        float xcurr = 0;
                        for (Entry e : r.rbFeedback) {
                            xcurr = e.getX();
                            s = e.getY() - avg;
                            if (((xcurr - xprev) > 1000) && (s > -3 && s < 3)) {
                                rbHoldPoints.add((Entry) e.copy());
                                xprev = xcurr;
                            }
                            s = s - avg/2;
                            if (s > 0) {
                                rbMaxROM += e.getY();
                                rbMaxROMSize++;
                            }
                        }
                        rbReps = Math.round(rbHoldPoints.size() / 2);

                        for (int i = 1; i < rbHoldPoints.size(); i += 2) {
                            rbAvgHold += rbHoldPoints.get(i).getX() - rbHoldPoints.get(i - 1).getX();
                        }
                        rbAvgHold = rbAvgHold / rbReps / 1000;
                        rbTotalTime = (rbHoldPoints.get(rbHoldPoints.size() - 1).getX() - rbHoldPoints.get(0).getX()) / 1000;
                    }

                    if(r.ptFeedback != null) {
                        float avg = r.ptROMSum / r.ptFeedback.size();
                        float s;
                        float xprev = -1010;
                        float xcurr = 0;
                        for (Entry e : r.ptFeedback) {
                            xcurr = e.getX();
                            s = e.getY() - avg;
                            if (((xcurr - xprev) > 1000) && (s > -3 && s < 3)) {
                                ptHoldPoints.add((Entry) e.copy());
                                xprev = xcurr;
                            }
                            s = s - avg/2;
                            if (s > 0) {
                                ptMaxROM += e.getY();
                                ptMaxROMSize++;
                            }
                        }
                        ptReps = Math.round(ptHoldPoints.size() / 2);

                        for (int i = 1; i < ptHoldPoints.size(); i += 2) {
                            ptAvgHold += ptHoldPoints.get(i).getX() - ptHoldPoints.get(i - 1).getX();
                        }
                        ptAvgHold = ptAvgHold / ptReps / 1000;
                        ptTotalTime = (ptHoldPoints.get(ptHoldPoints.size() - 1).getX() - ptHoldPoints.get(0).getX()) / 1000;
                    }

                    float seconds = 0;
                    float minutes = 0;
                    if(r.ptPomError == 0)
                        tPtPOM.setText("N/A");
                    else
                        tPtPOM.setText(String.format("%.2f°", (r.ptPomError/r.ptFeedback.size())));

                    if(r.noPomError == 0)
                        tNoPOM.setText("N/A");
                    else
                        tNoPOM.setText(String.format("%.2f°",  (r.noPomError/r.noFeedback.size())));

                    if(r.rbPomError == 0)
                        tRbPOM.setText("N/A");
                    else
                        tRbPOM.setText(String.format("%.2f°", (r.rbPomError/r.rbFeedback.size())));

                    if(noMaxROM  == 0)
                        tNoAvgROM.setText("N/A");
                    else
                        tNoAvgROM.setText(String.format("%.2f°", noMaxROM/noMaxROMSize));

                    if(ptMaxROM  == 0)
                        tPtAvgROM.setText("N/A");
                    else
                        tPtAvgROM.setText(String.format("%.2f°", ptMaxROM/ptMaxROMSize));

                    if(rbMaxROM  == 0)
                        tRbAvgROM.setText("N/A");
                    else
                        tRbAvgROM.setText(String.format("%.2f°", rbMaxROM/rbMaxROMSize));

                    if(noReps == 0)
                        tNoReps.setText("N/A");
                    else
                        tNoReps.setText(String.format("%d", noReps));

                    if(noAvgHold == 0)
                        tNoHoldTime.setText("N/A");
                    else
                        tNoHoldTime.setText(String.format("%.2f s", noAvgHold));

                    if(noTotalTime == 0)
                        tNoTotalTime.setText("N/A");
                    else{
                        seconds = noTotalTime%60;
                        minutes = (noTotalTime-seconds)/60;
                        tNoTotalTime.setText(String.format("%02.0f:%05.2f", minutes,seconds));
                    }
                    //tNoTotalTime.setText(String.format("%.2f ", noTotalTime));

                    if(rbReps == 0)
                        tRbReps.setText("N/A");
                    else
                        tRbReps.setText(String.format("%d", rbReps));

                    if(rbAvgHold == 0)
                        tRbHoldTime.setText("N/A");
                    else
                        tRbHoldTime.setText(String.format("%.2f s", rbAvgHold));

                    if(rbTotalTime == 0)
                        tRbTotalTime.setText("N/A");
                    else{
                        seconds = rbTotalTime%60;
                        minutes = (rbTotalTime-seconds)/60;
                        tRbTotalTime.setText(String.format("%02.0f:%05.2f", minutes,seconds));
                    }
                    //tRbTotalTime.setText(String.format("%.2f", rbTotalTime));

                    if(ptReps == 0)
                        tPtReps.setText("N/A");
                    else
                        tPtReps.setText(String.format("%d", ptReps));

                    if(ptAvgHold == 0)
                        tPtHoldTime.setText("N/A");
                    else
                        tPtHoldTime.setText(String.format("%.2f s", ptAvgHold));

                    if(ptTotalTime == 0)
                        tPtTotalTime.setText("N/A");
                    else{
                        seconds = ptTotalTime%60;
                        minutes = (ptTotalTime-seconds)/60;
                        tPtTotalTime.setText(String.format("%02.0f:%05.2f", minutes,seconds));
                    }
                    drawSummaryGraph(r);

                } catch (Exception e){
                    e.printStackTrace();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        try {
            RecordData.Record r = records.get(exercise.name);
            //drawSummaryGraph(r);

            if(r.ptPomError == 0)
                tPtPOM.setText("N/A");
            else
                tPtPOM.setText((int) (r.ptPomError/
                        r.ptFeedback.size()));

            if(r.noPomError == 0)
                tNoPOM.setText("N/A");
            else
                tNoPOM.setText((int) (r.noPomError/
                        r.noFeedback.size()));

            if(r.rbPomError == 0)
                tRbPOM.setText("N/A");
            else
                tRbPOM.setText((int) (r.rbPomError/
                        r.rbFeedback.size()));
            drawSummaryGraph(r);

        } catch (Exception e){
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), "Error loading data", Toast.LENGTH_SHORT).show();
        }
    }

    public void drawSummaryGraph(RecordData.Record record){
        LineChart summary = (LineChart) findViewById(R.id.chartSummary);

        float max = 0;
        List<ILineDataSet> dataSets = new ArrayList<>();

        if(record.rbFeedback != null) {
            LineDataSet rbDataSet = new LineDataSet(record.rbFeedback, "");
            //rbDataSet.setColor(Color.BLUE);
            rbDataSet.setColor(Color.rgb(0,150,250));
            rbDataSet.setDrawValues(false);
            rbDataSet.setDrawCircles(false);
            rbDataSet.setLabel("With RehabBuddy Feedback");
            for (Entry e : record.rbFeedback) {
                if (e.getY() > max)
                    max = e.getY();
            }
            dataSets.add(rbDataSet);
            //LineDataSet rbDataHoldTime = new LineDataSet(record.rbHoldPoints, "");
            //dataSets.add(rbDataHoldTime);
        }
        if(record.ptFeedback != null) {
            LineDataSet ptDataSet = new LineDataSet(record.ptFeedback, "");
            ptDataSet.setColor(Color.GREEN);
            ptDataSet.setDrawValues(false);
            ptDataSet.setDrawCircles(false);
            ptDataSet.setLabel("With PT Feedback");
            for (Entry e : record.ptFeedback) {
                if (e.getY() > max)
                    max = e.getY();
            }
            dataSets.add(ptDataSet);
            //LineDataSet ptDataHoldTime = new LineDataSet(record.ptHoldPoints, "");
            //dataSets.add(ptDataHoldTime);
        }
        if(record.noFeedback != null) {
            LineDataSet noDataSet = new LineDataSet(record.noFeedback, "");
            noDataSet.setColor(Color.RED);
            noDataSet.setDrawValues(false);
            noDataSet.setDrawCircles(false);
            noDataSet.setLabel("Without Feedback");
            for (Entry e : record.noFeedback) {
                if (e.getY() > max)
                    max = e.getY();
            }
            dataSets.add(noDataSet);
            //LineDataSet noDataHoldTime = new LineDataSet(record.noHoldPoints, "");
            //dataSets.add(noDataHoldTime);
        }

        LineData lineData = new LineData(dataSets);

        XAxis cx = summary.getXAxis();
        cx.setDrawLabels(true);
        ValueFormatter formatterX = new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                float time = value / 1000;
                return Integer.toString((int)time) + '.' + (int)((time - (int)time)*100) + 's';
            }
        };
        cx.setValueFormatter(formatterX);

        YAxis cr = summary.getAxisRight();
        cr.setEnabled(false);

        YAxis cl = summary.getAxisLeft();
        cl.setAxisMinimum(0f);
        cl.setAxisMaximum(max);
        cl.setDrawLabels(true);
        ValueFormatter formatterY = new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                return (int)value + "%";
                //return (int)value + "°";
            }
        };
        cl.setValueFormatter(formatterY);

        summary.getLegend().setEnabled(true);
        summary.setDescription(null);
        summary.setData(lineData);
        summary.invalidate();
    }

    // Unused
    /*public void openFile(View v){ // Opens file export dialog on summary page

        File f = csvData.getFile();
        Uri uri = FileProvider.getUriForFile(getApplicationContext(), "com.probmatic.grapher.fileprovider", f);
        Intent fileIntent = new Intent(Intent.ACTION_SEND);
        fileIntent.setType("text/csv");
        fileIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        fileIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        fileIntent.putExtra(Intent.EXTRA_STREAM, uri);
        startActivity(Intent.createChooser(fileIntent, "Open Recorded Data"));
    }*/

    /*******************************PAGE LAYOUT LOGIC END************************************/
    /*******************************PAGE LAYOUT LOGIC END************************************/

    /*******************************SHIMMER MESSAGE CALLBACK************************************/
    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler() { // This calls whenever the shimmer sends a new message
        public void handleMessage(Message msg) {
            boolean callbackLogging = false;
            switch (msg.what) { // handlers have a what identifier which is used to identify the type of msg
                case ShimmerBluetooth.MSG_IDENTIFIER_DATA_PACKET:
                    if ((msg.obj instanceof ObjectCluster)){
                        // within each msg an object can be include, objectclusters are used to
                        // represent the data structure of the shimmer device
                        ObjectCluster objectCluster =  (ObjectCluster) msg.obj;

                        Collection<FormatCluster> allFormats = objectCluster.getCollectionOfFormatClusters(
                                Configuration.Shimmer3.ObjectClusterSensorName.TIMESTAMP);
                        FormatCluster timeStampCluster =
                                (ObjectCluster.returnFormatCluster(allFormats,"CAL"));
                        double timeStampData = timeStampCluster.mData;

                        Collection<FormatCluster> bFormats = objectCluster.getCollectionOfFormatClusters(
                                Configuration.Shimmer3.ObjectClusterSensorName.BATT_PERCENTAGE);
                        FormatCluster batteryCluster =
                                (ObjectCluster.returnFormatCluster(bFormats,"CAL"));
                        double batteryData = batteryCluster.mData;


                        // Load gyroscope data for logging
                        Unit3d gyro = new Unit3d();
                        Collection<FormatCluster> gXFormats = objectCluster.getCollectionOfFormatClusters(
                                Configuration.Shimmer3.ObjectClusterSensorName.GYRO_X);
                        if (gXFormats != null){
                            FormatCluster formatCluster = ((FormatCluster)ObjectCluster.returnFormatCluster(
                                    gXFormats,"CAL")); // retrieve the calibrated data
                            if(formatCluster!= null)
                                gyro.x = formatCluster.mData;
                        }

                        Collection<FormatCluster> gYFormats = objectCluster.getCollectionOfFormatClusters(
                                Configuration.Shimmer3.ObjectClusterSensorName.GYRO_Y);
                        if (gYFormats != null){
                            FormatCluster formatCluster = ((FormatCluster)ObjectCluster.returnFormatCluster(
                                    gYFormats,"CAL")); // retrieve the calibrated data
                            if(formatCluster!= null)
                                gyro.y = formatCluster.mData;
                        }

                        Collection<FormatCluster> gZFormats = objectCluster.getCollectionOfFormatClusters(
                                Configuration.Shimmer3.ObjectClusterSensorName.GYRO_Z);
                        if (gZFormats != null){
                            FormatCluster formatCluster = ((FormatCluster)ObjectCluster.returnFormatCluster(
                                    gZFormats,"CAL")); // retrieve the calibrated data
                            if(formatCluster!= null)
                                gyro.z = formatCluster.mData;
                        }

                        // Load magnetometer data for logging
                        Unit3d mag = new Unit3d();
                        Collection<FormatCluster> mXFormats = objectCluster.getCollectionOfFormatClusters(
                                Configuration.Shimmer3.ObjectClusterSensorName.MAG_X);
                        if (mXFormats != null){
                            FormatCluster formatCluster = ((FormatCluster)ObjectCluster.returnFormatCluster(
                                    mXFormats,"CAL")); // retrieve the calibrated data
                            if(formatCluster!= null)
                                mag.x = formatCluster.mData;
                        }

                        Collection<FormatCluster> mYFormats = objectCluster.getCollectionOfFormatClusters(
                                Configuration.Shimmer3.ObjectClusterSensorName.MAG_Y);
                        if (mYFormats != null){
                            FormatCluster formatCluster = ((FormatCluster)ObjectCluster.returnFormatCluster(
                                    mYFormats,"CAL")); // retrieve the calibrated data
                            if(formatCluster!= null)
                                mag.y = formatCluster.mData;
                        }

                        Collection<FormatCluster> mZFormats = objectCluster.getCollectionOfFormatClusters(
                                Configuration.Shimmer3.ObjectClusterSensorName.MAG_Z);
                        if (mZFormats != null){
                            FormatCluster formatCluster = ((FormatCluster)ObjectCluster.returnFormatCluster(
                                    mZFormats,"CAL")); // retrieve the calibrated data
                            if(formatCluster!= null)
                                mag.z = formatCluster.mData;
                        }

                        // Load accelerometer data for logging
                        Unit3d accel = new Unit3d();
                        Collection<FormatCluster> aXFormats = objectCluster.getCollectionOfFormatClusters(
                                Configuration.Shimmer3.ObjectClusterSensorName.ACCEL_LN_X);
                        if (aXFormats != null){
                            FormatCluster formatCluster = ((FormatCluster)ObjectCluster.returnFormatCluster(
                                    aXFormats,"CAL")); // retrieve the calibrated data
                            if(formatCluster!= null)
                                accel.x = formatCluster.mData;
                        }

                        Collection<FormatCluster> aYFormats = objectCluster.getCollectionOfFormatClusters(
                                Configuration.Shimmer3.ObjectClusterSensorName.ACCEL_LN_Y);
                        if (aYFormats != null){
                            FormatCluster formatCluster = ((FormatCluster)ObjectCluster.returnFormatCluster(
                                    aYFormats,"CAL")); // retrieve the calibrated data
                            if(formatCluster!= null)
                                accel.y = formatCluster.mData;
                        }

                        Collection<FormatCluster> aZFormats = objectCluster.getCollectionOfFormatClusters(
                                Configuration.Shimmer3.ObjectClusterSensorName.ACCEL_LN_Z);
                        if (aZFormats != null){
                            FormatCluster formatCluster = ((FormatCluster)ObjectCluster.returnFormatCluster(
                                    aZFormats,"CAL")); // retrieve the calibrated data
                            if(formatCluster!= null)
                                accel.z = formatCluster.mData;
                        }

                        // Load Quaternion data for logging
                        Quat4d qt = new Quat4d();
                        Collection<FormatCluster> qWFormats = objectCluster.getCollectionOfFormatClusters(
                                Configuration.Shimmer3.ObjectClusterSensorName.QUAT_MADGE_9DOF_W);
                        if (qWFormats != null){
                            FormatCluster formatCluster = (ObjectCluster.returnFormatCluster(
                                    qWFormats,"CAL")); // retrieve the calibrated data
                            if(formatCluster!= null) {
                                qt.w = formatCluster.mData;
                            }
                        }

                        Collection<FormatCluster> qXFormats = objectCluster.getCollectionOfFormatClusters(
                                Configuration.Shimmer3.ObjectClusterSensorName.QUAT_MADGE_9DOF_X);
                        if (qXFormats != null){
                            FormatCluster formatCluster = (ObjectCluster.returnFormatCluster(
                                    qXFormats,"CAL")); // retrieve the calibrated data
                            if(formatCluster!= null) {
                                qt.x = formatCluster.mData;
                            }
                        }

                        Collection<FormatCluster> qYFormats = objectCluster.getCollectionOfFormatClusters(
                                Configuration.Shimmer3.ObjectClusterSensorName.QUAT_MADGE_9DOF_Y);
                        if (qYFormats != null){
                            FormatCluster formatCluster = ((FormatCluster)ObjectCluster.returnFormatCluster(
                                    qYFormats,"CAL")); // retrieve the calibrated data
                            if(formatCluster!= null) {
                                qt.y = formatCluster.mData;
                            }
                        }

                        Collection<FormatCluster> qZFormats = objectCluster.getCollectionOfFormatClusters(
                                Configuration.Shimmer3.ObjectClusterSensorName.QUAT_MADGE_9DOF_Z);
                        if (qZFormats != null){
                            FormatCluster formatCluster = ((FormatCluster)ObjectCluster.returnFormatCluster(
                                    qZFormats,"CAL")); // retrieve the calibrated data
                            if(formatCluster!= null) {
                                qt.z = formatCluster.mData;


                                if(sensor.initializing) { //for calibration function use
                                    if (qt != null)
                                        calibData.add(qt);
                                    if(callbackLogging)
                                        Log.i(LOG_TAG, "Added calibration quaternion");
                                    if (calibData.size() == 750) {
                                        Log.i(LOG_TAG, "Calibration data finished");
                                        sensor.initializing = false;
                                        try {
                                            TextView tCalibrate = (TextView) findViewById(R.id.tInitializeDirections);
                                            tCalibrate.setText(getApplicationContext().getResources().getString(R.string.initializingComplete));
                                        } catch (Exception e){
                                            e.printStackTrace();
                                        }
                                        if(!sensor.stream_all)
                                            shimmer.stopStreaming();
                                    }
                                }

                                boolean addCalibEvent = false;
                                if(sensor.calibrate){
                                    exercise.qStart = qt;
                                    sensor.calibrate = false;
                                    addCalibEvent = true;
                                }

                                if(sensor.recordingStart){ //for calibration function use
                                    if(qt != null)
                                        rStartData.add(qt);
                                    if(callbackLogging)
                                        Log.i(LOG_TAG, "Added recorded start quaternion");
                                    if(rStartData.size() == 200){
                                        Log.i(LOG_TAG, "Recorded start data finished");
                                        sensor.recordingStart = false;
                                        finishRecordStartPos();
                                    }
                                }

                                if(sensor.recordingStop){ //for calibration function use
                                    if(qt != null)
                                        rStopData.add(qt);
                                    if(callbackLogging)
                                        Log.i(LOG_TAG, "Added recorded stop quaternion");
                                    if(rStopData.size() == 200){
                                        Log.i(LOG_TAG, "Recorded stop data finished");
                                        sensor.recordingStop = false;
                                        finishRecordStopPos();
                                    }
                                }

                                if(sensor.running) {     // Runs the graphing callback when new
                                    runtimeCallback(qt, timeStampData); //    data comes in and is running
                                }

                                if(sensor.no_feedback){
                                    noFeedbackCallback(qt, timeStampData);
                                }

                                if(sensor.pt_feedback){
                                    ptFeedbackCallback(qt, timeStampData);
                                }

                                if(callbackLogging){
                                    Log.i(LOG_TAG, "Received packet from shimmer");
                                    Log.i(LOG_TAG, "Battery: " + batteryData);
                                    Log.i(LOG_TAG, "Time Stamp: " + timeStampData);
                                }

                                if (ContextCompat.checkSelfPermission(getBaseContext(),
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                        == PackageManager.PERMISSION_GRANTED) {
                                    try {
                                        if(!addCalibEvent)
                                            csvData.writeDataToCSV(exercise.name, qt,
                                                    System.currentTimeMillis(),
                                                    getEvents(sensor), exercise.currentRep,
                                                    exercise.currentSet, gyro, mag, accel);
                                        else
                                            /*csvData.writeDataToCSV(exercise.name, qt,
                                                System.currentTimeMillis(),
                                                getEvents(sensor)+"+calibrated",
                                                exercise.currentRep, exercise.currentSet,
                                                gyro, mag, accel);*/
                                            csvData.writeDataToCSV(exercise.name, qt,
                                                    System.currentTimeMillis(),"setStart",
                                                    exercise.currentRep, exercise.currentSet,
                                                    gyro, mag, accel);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                    }
                    break;
                case Shimmer.MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString(Shimmer.TOAST),
                            Toast.LENGTH_SHORT).show();
                    break;

                case ShimmerBluetooth.MSG_IDENTIFIER_STATE_CHANGE:
                    switch (((ObjectCluster)msg.obj).mState) {
                        case CONNECTED:
                            Log.d("ConnectionStatus","Successful");

                            //Because the default mag range for Shimmer2 and 3 are 0 and 1
                            // respectively, please be aware of what range you use when calibrating
                            // using Shimmer 9DOF Cal App, and use the same range here
                            shimmer.enableOnTheFlyGyroCal(true, 102, 1.2);
                            if(sensor.stream_all)
                                shimmer.startStreaming();
                            if(!sensor.connected){
                                mDirectionFeedback.setText(getApplicationContext().getResources().getString(R.string.descriptionConnected));
                                sensor.connected = true;
                            }
                            break;

                        case CONNECTING:
                            Log.d("ConnectionStatus","Connecting");
                            break;
                        case STREAMING:
                            break;
                        case STREAMING_AND_SDLOGGING:
                            break;
                        case SDLOGGING:
                            break;
                        case DISCONNECTED:
                            Log.d("ConnectionStatus","No State");
                            sensor.connected = false;
                            break;
                    }

                    break;

            }
        }
    };

    /*******************************SHIMMER MESSAGE CALLBACK END************************************/

    public void onActivityResult(int requestCode, int resultCode, Intent data) { //

        switch (requestCode) {

            case REQUEST_CONNECT_SHIMMER:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    String bluetoothAddress= data.getExtras()
                            .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    shimmer.connect(bluetoothAddress,"default");
                }
                break;
        }
    }

    public String getEvents(SensorParameters sp){ //different event options to be printed inside CSV when they occur
        String events = "";

        if(sp.initializing)         events += "initializing";
        if(sp.recordingStart)      events += "recordingStart";
        if(sp.recordingStop)       events += "recordingStop";
        if(sp.no_feedback)         events += "recordingNoFeedback";
        if(sp.pt_feedback)         events += "recordingPTFeedback";
        if(sp.running) {
            switch(sp.state) {
                case MOVING_UP   : events += "moving_up";   break;
                case HOLD_TOP    : events += "hold_top";    break;
                case MOVING_DOWN : events += "moving_down"; break;
                case HOLD_BOTTOM : events += "hold_bottom"; break;
            }
        }
        if(sp.finished)            events += "finished";

        return events;
    }

    /*******************************PAGE BACKEND LOGIC BEGIN************************************/

    /*******************************GRAPH HELPERS BEGIN************************************/

    public void drawGraphFromHandles(){ // Draws graph from handles global data structure

        LineChart chart = (LineChart) findViewById(R.id.chart);
        chartSettings(chart);

        LineDataSet targetDataSet = new LineDataSet(handles.an_guide, "");
        //EER 9/11 Widening lines
        targetDataSet.setLineWidth(4f);
        targetDataSet.setColor(Color.BLACK);
        targetDataSet.setDrawValues(false);
        targetDataSet.setDrawCircles(false);

        LineData targetData = new LineData(targetDataSet);

        chart.setData(targetData);

        chart.invalidate(); // refresh
    }

    public void updateGraphFromHandles(){ // Draws graph from handles global data structure

        LineChart chart = (LineChart) findViewById(R.id.chart);


        LineDataSet dataSet = new LineDataSet(handles.an, ""); // add entries to dataset
        dataSet.setLineWidth(6f);
        //dataSet.setColor(Color.BLUE);
        dataSet.setColor(Color.rgb(0,150,250));
        //dataSet.setColor(Color.rgb(0,191,255));
        dataSet.setDrawValues(false);
        dataSet.setDrawCircles(false);

        //EER 9/16
        LineDataSet endZoneSet = new LineDataSet(handles.end_zone, "");
        endZoneSet.setLineWidth(0f);
        endZoneSet.setDrawFilled(true);
        endZoneSet.setFillAlpha(30);
        endZoneSet.setFillColor(Color.GREEN);
        endZoneSet.setColor(Color.LTGRAY);
        endZoneSet.setDrawValues(false);
        endZoneSet.setDrawCircles(false);

        LineDataSet targetDataSet = new LineDataSet(handles.an_guide, "");
        targetDataSet.setLineWidth(6f);
        targetDataSet.setColor(Color.BLACK);
        targetDataSet.setDrawValues(false);
        targetDataSet.setDrawCircles(false);

        LineDataSet errorDataSet = new LineDataSet(handles.an_error, "");
        errorDataSet.setColor(handles.an_color);
        errorDataSet.setDrawValues(false);
        errorDataSet.setDrawCircles(true);
        errorDataSet.setCircleRadius(8f);

        List<ILineDataSet> dataSets = new ArrayList<>();

        dataSets.add(targetDataSet);
        dataSets.add(dataSet);
        dataSets.add(errorDataSet);
        dataSets.add(endZoneSet);

        LineData lineData = new LineData(dataSets);

        chart.setData(lineData);
        chart.invalidate(); // refresh
    }

    public void chartSettings(LineChart chart){ // Sets common settings between the line graphs
        XAxis cx = chart.getXAxis();
        cx.setAxisMaximum((float)handles.an_guide.size()*2-1);
        cx.setAxisMinimum(0f);
        cx.setDrawLabels(false);

        YAxis cr = chart.getAxisRight();
        cr.setEnabled(false);

        YAxis cl = chart.getAxisLeft();
        cl.setAxisMinimum(-10f);

        // Check to make sure the desired exercise fits in the window
        /*float angDegrees = exercise.angIdeal * 180/PI;
        if(angDegrees < 90)
            cl.setAxisMaximum(100f);
        else if(angDegrees < 140)
            cl.setAxisMaximum(150f);
        else if(angDegrees < 185)
            cl.setAxisMaximum(200f);
        else
            cl.setAxisMaximum(angDegrees+10f);*/
        cl.setAxisMaximum(120f);
        cl.setDrawLabels(true);

        ValueFormatter formatter = new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                return (int)value + "%";
                //return (int)value + "°";
            }
        };
        cl.setValueFormatter(formatter);

        chart.getLegend().setEnabled(false);
        chart.setTouchEnabled(false);
        chart.setDescription(null);
    }

    public void drawBar(double data){ // Draws error bar on right hand side

        List<BarEntry> entries = new ArrayList<BarEntry>();
        BarChart error = (BarChart) findViewById(R.id.error);
        XAxis ex = error.getXAxis();
        ex.setEnabled(false);

        YAxis ey = error.getAxisLeft(); //Axis left side of error bar graph
        ey.setAxisMaximum(50f);
        ey.setAxisMinimum(0f);

        YAxis er = error.getAxisRight(); //Axis right side of error bar graph making this invisible
        er.setAxisMaximum(50f); //axis scale to match left side scaling
        er.setAxisMinimum(0f);
        er.setDrawLabels(false);

        entries.add(new BarEntry(0.5f, (float) data));

        BarDataSet dataSet = new BarDataSet(entries, "Error"); // add entries to dataset
        if(data > 25)
            dataSet.setColor(Color.RED);
        else if(data <= 25 && data > 15)
            dataSet.setColor(Color.YELLOW);
        else if(data <=15)
            dataSet.setColor(Color.GREEN);
        dataSet.setValueTextColor(1); // styling, ...

        BarData barData = new BarData(dataSet);
        error.setData(barData);
        error.setTouchEnabled(false);
        error.invalidate(); // refresh
        error.setDescription(null);
    }

    /*******************************GRAPH HELPERS END************************************/

    /*******************************RUNTIME GRAPH FUNCTIONS************************************/

    String UP = "\u2191";
    String DOWN = "\u2193";
    final int MOVING_UP = 0;
    final int HOLD_TOP = 1;
    final int MOVING_DOWN = 2;
    final int HOLD_BOTTOM = 3;
    final int SET_FINISHED = 4;
    final int HOLDING = 5;
    final int REP_FINISHED = 6;
    int state = MOVING_UP;
    int holdTime = 0;
    int ptIndex = 1;
    int nearestYIndex = 0;
    Quat4d qLive = new Quat4d();
    Quat4d qErr = new Quat4d();
    int modder = 0;

    //For pt timer 9/25/20
    boolean holding = false;

    //int ptTime = 0;
    boolean ptEnable = true;

    public void runtimeFcn(View V){ //WIP, comes from the main matlab function
        int id = V.getId();
        //bStartRecordingExercise
        // R.id.bStartRecordingExercise.setBackgroundColor(Color.RED);
        if(id == R.id.bStartRecordingExercise){
            Button bStartRecordingExercise = findViewById(R.id.bStartRecordingExercise);
            bStartRecordingExercise.setBackgroundColor(Color.RED);
            exercise.currentRep = 0;
            exercise.currentSet = 0;
        }

        tSetsCounter.setText("0/" + exercise.totalSets);
        tRepsCounter.setText("0/" + exercise.totalReps);

        if(records.get(exercise.name) == null)
            records.createNew(exercise.name);
        else
            records.load(exercise.name);
        records.current.rbFeedback = new ArrayList<>();
        records.current.rbPomError = 0;

        records.current.rbROMSum = 0;
        //records.current.rbHoldPoints = new ArrayList<>();
        records.tempStartTime = null;

        if(!sensor.stream_all)
            shimmer.startStreaming();
        sensor.calibrate = true; // Sets start position when recording begins
        sensor.running = true;
    }

    public void runtimeCallback(Quat4d qMov, double time){

        mCountFeedback = (TextView) findViewById(R.id.mCountFeedback);

        if(exercise.currentSet < exercise.totalSets) {

            Log.i(LOG_TAG, "Executing set " + exercise.currentSet);
            //Gets quaternion data from Shimmers which is stored as
            //'qMov' locally and 'qCurrent' globally

            Log.i(LOG_TAG, "Received new pos data");
            // calculate if the user is on track or not, the information that
            // will be graphed below
            qLive.set(exercise.qStart);
            qLive.conjugate();
            qLive.mul(qMov);     //gets quaternion between start and current

            qErr.set(qLive);     //gets quaternion between previous quaternion and target quaternion
            qErr.conjugate();
            qErr.mul(exercise.qIdeal);

            AxisAngle4d angLive = new AxisAngle4d();
            AxisAngle4d angError = new AxisAngle4d();
            angLive.set(qLive);
            angError.set(qErr);

            Log.i(LOG_TAG, "Angle: "+angLive.angle+" x: "+angLive.x+" y: "+angLive.y+" z: "+angLive.z);

            // Plane of Motion Error (come up with easier name to understand than POM)
            double POM_error = (angError.angle + angLive.angle - exercise.angIdeal) * 180 / PI;
            Log.i(LOG_TAG, "angError: " + angError.angle + "\tangLive: " + angLive.angle + "\tangIdeal: " + exercise.angIdeal);

            //Plot error gauge
            drawBar(POM_error);

            double ROM = angLive.angle * 180 / PI;
            final double perROM = Math.min(ROM,Math.pow(ROM/20.0,4)*20.0)/(exercise.angIdeal*180/PI)*100;

            if(records.tempStartTime == null)
                records.tempStartTime = time;
            records.current.rbFeedback.add(new Entry((float)(time-records.tempStartTime), (float)ROM));
            records.current.rbPomError += POM_error;
            records.current.rbROMSum += ROM;
            handles.an_error.clear();
            //           Keeps it from throwing index out of bounds error at end of array
            if (handles.an_guide.size() == ptIndex)
                ptIndex = ptIndex - 1;
            //           If on path, keep drawing the green line, else don't draw and don't advance
            Log.i(LOG_TAG, "Checking tag at index: " + ptIndex);
            // EER 9/11 change threshold from 10 deg to 20
            if ((perROM < (handles.an_guide.get(ptIndex).getY() + handles.top_zone_threshold_upper)) &&
                    (perROM > (handles.an_guide.get(ptIndex).getY() - handles.top_zone_threshold_lower))) {
                if(!ptEnable)
                    modder = (modder + 1) % 12;

                if (ptEnable || modder == 0) {
                    handles.an.add(new Entry(ptIndex * 2, (float) perROM));
                    ptIndex = ptIndex + 1;
                    handles.an_color = Color.GREEN;
                }
            } else //hold your horses!
                handles.an_error.add(new Entry(ptIndex * 2, (float) perROM));

            //           State machine that determines if user is making progress based
            //           on where they currently are and where there current state
            //           should be, and what should be updated as a result depending on
            //           where in the exercise the user currently is

            // Used to verify the time for user to hold at top/bottom before moving
            final CountDownTimer holdTimer = new CountDownTimer(100000, 100) {
                public void onTick(long millisUntilFinished) {
                    long milliCount = 100000 - millisUntilFinished;
                    exercise.toc = milliCount / 1000;
                    tHoldTime.setText(Long.toString(holdTime - exercise.toc));
                    ptEnable = false;

                    if(exercise.toc >= holdTime)
                        onFinish();

                    Log.i(LOG_TAG, "tic at " + milliCount);
                }

                public void onFinish() {
                    tHoldTime.setText("Done!");
                    ptEnable = true;
                    //4/30/21
                    //state = MOVING_DOWN;
                    cancel();
                }
            };

            Log.i(LOG_TAG, "Current state is " + state);

            switch (state) {
                case MOVING_UP:// moving arm up
                    sensor.state = State.MOVING_UP;
                    mCountFeedback.setText(UP);
                    mDirectionFeedback.setText("Move arm up");
                    //double top_val = exercise.angIdeal * 180/PI;
                    double top_val = 100;
                    if (perROM > (top_val - handles.top_zone_threshold_lower) && perROM <  (top_val + handles.top_zone_threshold_upper))
                        //&& (int)handles.an_guide.get(ptIndex).getY()== (int)top_val)
                        state = HOLD_TOP; // = state.final_pos
                    break;

                case HOLD_TOP:// Condition if arm is in final position
                    sensor.state = State.HOLD_TOP;
                    mDirectionFeedback.setText("Hold");
                    holdTime = exercise.holdTop;
                    holdTimer.start(); //tic; //starts timer in Matlab
                    state = HOLDING;
                    break;

                case MOVING_DOWN: // moving arm down
                    sensor.state= State.MOVING_DOWN;
                    if (handles.an_guide.get(ptIndex-1).getY() < handles.end_zone_threshold)
                        state = HOLD_BOTTOM;
                    //8/19/21
                    /*
                    if(!handles.an_error.isEmpty()) {
                        nearestYIndex = 4 * Math.round(handles.an_error.get(0).getY() / 4);
                        for (Entry e : handles.decline) {
                            if(e.getY() == nearestYIndex) {
                                ptIndex = ((int) e.getX() / 2) - 1;
                                break;
                            }
                        }
                    }

                     */
                    break;

                case HOLD_BOTTOM: // Condition if arm is back in start position
                    sensor.state = State.HOLD_BOTTOM;
                    mDirectionFeedback.setText("Hold");
                    holdTime = exercise.holdBottom;
                    holdTimer.start(); //tic
                    state = HOLDING;
                    break;

                case REP_FINISHED:
                    if (exercise.currentRep >= exercise.totalReps-1) {
                        exercise.currentSet = exercise.currentSet + 1;
                        exercise.currentRep = 0;
                        tSetsCounter.setText(exercise.currentSet + "/" + exercise.totalSets);
                    } else
                        exercise.currentRep = exercise.currentRep + 1;
                    tRepsCounter.setText(exercise.currentRep + "/" + exercise.totalReps);
                    mCountFeedback.setText(UP);
                    ptIndex = 1;
                    handles.an.clear();
                    state = MOVING_UP;
                    break;

                case SET_FINISHED: // If set is finished
                    sensor.finished = true;
                    exercise.currentSet = exercise.currentSet + 1;
                    exercise.currentRep = 0;

                    tSetsCounter.setText(exercise.currentSet + "/" + exercise.totalSets);
                    tRepsCounter.setText(exercise.currentRep + "/" + exercise.totalReps);
                    state = MOVING_UP;
                    break;
                //Edit 4/29/21
                case HOLDING:
                    //if (exercise.toc < holdTime) {
                    //   mCountFeedback.setText("");
                    if (sensor.state == State.HOLD_TOP && (exercise.toc >= holdTime)){
                        /*
                        if(!handles.an_error.isEmpty()) {
                            nearestYIndex = 4 * Math.round(handles.an_error.get(0).getY() / 4);
                            for (Entry e : handles.decline) {
                                if(e.getY() == nearestYIndex) {
                                    ptIndex = ((int) e.getX() / 2) - 1;
                                    break;
                                }
                            }
                        }

                         */
                        handles.an_color = Color.GREEN;
                        mCountFeedback.setText(DOWN);
                        mDirectionFeedback.setText("Move arm down");
                        state = MOVING_DOWN;
                    } else if (sensor.state == State.HOLD_BOTTOM && exercise.toc >= holdTime){
                        state = REP_FINISHED;
                    }
                    break;
            }
            updateGraphFromHandles();
        }
        else
        {
            mDirectionFeedback.setText("Exercise complete.");
            Button bStartRecordingExercise = findViewById(R.id.bStartRecordingExercise);
            bStartRecordingExercise.setBackgroundColor(Color.GRAY);
            mCountFeedback.setText("");
            handles.an.clear();
            handles.an_error.clear();

            sensor.running = false;
            if(!sensor.stream_all)
                shimmer.stopStreaming();
        }
    }

    public void noFeedbackCallback(Quat4d qMov, double time){
        Quat4d q = new Quat4d();
        Quat4d qEr = new Quat4d();
        q.set(exercise.qStart);
        q.conjugate();
        q.mul(qMov);     //gets quaternion between start and current
        qEr.set(q);     //gets quaternion between previous quaternion and target quaternion
        qEr.conjugate();
        qEr.mul(exercise.qIdeal);

        AxisAngle4d ang = new AxisAngle4d();
        ang.set(q);
        AxisAngle4d angError = new AxisAngle4d();
        angError.set(qEr);

        double ROM = ang.angle * 180 / PI;
        if(records.tempStartTime == null)
            records.tempStartTime = time;
        records.current.noFeedback.add(new Entry((float)(time-records.tempStartTime), (float)ROM));

        records.current.noROMSum += ROM;

        // Plane of Motion Error (come up with easier name to understand than POM)
        double POM_error = (angError.angle + ang.angle - exercise.angIdeal) * 180 / PI;
        records.current.noPomError += POM_error;


    }

    public void ptFeedbackCallback(Quat4d qMov, double time){
        Quat4d q = new Quat4d();
        Quat4d qEr = new Quat4d();
        q.set(exercise.qStart);
        q.conjugate();
        q.mul(qMov);     //gets quaternion between start and current
        qEr.set(q);     //gets quaternion between previous quaternion and target quaternion
        qEr.conjugate();
        qEr.mul(exercise.qIdeal);

        AxisAngle4d ang = new AxisAngle4d();
        ang.set(q);
        AxisAngle4d angError = new AxisAngle4d();
        angError.set(qEr);

        double ROM = ang.angle * 180 / PI;
        if(records.tempStartTime == null)
            records.tempStartTime = time;
        records.current.ptFeedback.add(new Entry((float)(time-records.tempStartTime), (float)ROM));
        records.current.ptROMSum += ROM;

        if((ROM > (exercise.angIdeal*180/PI - 15))&& holding == false) {
            holding =true;
            new CountDownTimer(((exercise.holdTop+1)*1000), 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    tHoldTime.setText(Long.toString(Math.round(millisUntilFinished/1000)));
                }

                @Override
                public void onFinish() {
                    new CountDownTimer(3000, 1000) {
                        @Override
                        public void onTick(long millisUntilFinished) {
                            //tHoldTime.setText(Long.toString(millisUntilFinished));
                        }

                        @Override
                        public void onFinish() {
                            holding = false;
                        }
                    }.start();
                }
            }.start();
        }

        // Plane of Motion Error (come up with easier name to understand than POM)
        double POM_error = (angError.angle + ang.angle - exercise.angIdeal) * 180 / PI;
        records.current.ptPomError += POM_error;

    }



    /*******************************RUNTIME GRAPH FUNCTIONS END************************************/
    /*******************************PAGE BACKEND LOGIC END************************************/
}