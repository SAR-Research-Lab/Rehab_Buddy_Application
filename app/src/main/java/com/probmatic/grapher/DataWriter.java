package com.probmatic.grapher;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.vecmath.Quat4d;

public class DataWriter { //Formerly known as csvHelper

    private final static String APP_FOLDER_NAME = "RehabBuddyLogs";
    private final static String APP_DIR_PATH = android.os.Environment.getExternalStorageDirectory().getAbsolutePath()
            + File.separator + APP_FOLDER_NAME + File.separator;
    /** This can be found in the Manifest */

    // Write to CSV variables
    private FileWriter fw;
    private BufferedWriter bw;
    private File file;
    boolean firstTimeWrite = true;

    private boolean log_empty = false;

    // Runs in onCreate to make the new file.
    // Location is /storage/self/primary/ShimmerArraysExample/
    //    with the name Data_Date.csv
    public DataWriter(String fileName, String type){ // Base constructor

        File dir = new File(APP_DIR_PATH);
        if (!dir.exists()) {
            //Create the directory if it doesn't already exist
            dir.mkdir();
        }

        if(fileName.length() == 0)
            fileName += "_";
        fileName =  fileName + new SimpleDateFormat("MM-dd-yy_HHmm").format(new Date()) + type + ".csv";

        String filePath = APP_DIR_PATH + File.separator + fileName;
        file = new File(filePath);
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
            fw = new FileWriter(file.getAbsoluteFile());
            bw = new BufferedWriter(fw);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public DataWriter(String fileName, String type, boolean log_empty) { // Log option constructor
        this(fileName, type);
        this.log_empty = log_empty;
    }

    // Runs on shimmer msg callback to append the line of values to the csv
    public void writeDataToCSV(String exerciseName, Quat4d pos, Long timestamp, String event,
                               int currentRep, int currentSet, Unit3d gyro, Unit3d mag,
                               Unit3d accel) throws IOException {
        if (firstTimeWrite) {
            //Write headers on first-time
            try {
                bw.write("exercise,qtw,qtx,qty,qtz,time,event,rep,set,gx,gy,gz,mx,my,mz,ax,ay,az\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
            firstTimeWrite = false;
        }

        try {
            if(event.length() > 0 || log_empty)
                bw.write(exerciseName + "," + pos.w + "," + pos.x + "," + pos.y + "," + pos.z + "," +
                    timestamp + "," + event + "," + currentRep + "," + currentSet + "," +
                    gyro.x + "," + gyro.y + "," + gyro.z + "," + mag.x + "," + mag.y + "," +
                    mag.z + "," + accel.x + "," + accel.y + "," + accel.z + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NullPointerException e){
            e.printStackTrace();
        }
    }

    // Runs when exercise created to append the line of values to the csv
    public void writeExerciseToCSV(ExerciseParameters ep) throws IOException {

        if (firstTimeWrite) {
            //Write headers on first-time
            try {
                bw.write("name,qStartw,qStartx,qStarty,qStartz,qEndw,qEndx,qEndy,qEndz,qIdealw," +
                        "qIdealx,qIdealy,qIdealz,angIdeal,holdTop,holdBottom,reps,sets\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
            firstTimeWrite = false;
        }

        try {
            bw.write(ep.name + "," +
                    ep.qStart.w + "," + ep.qStart.x + "," + ep.qStart.y + "," + ep.qStart.z + "," +
                    ep.qEnd.w + "," + ep.qEnd.x + "," + ep.qEnd.y + "," + ep.qEnd.z + "," +
                    ep.qIdeal.w + "," + ep.qIdeal.x + "," + ep.qIdeal.y + "," + ep.qIdeal.z + "," +
                    ep.angIdeal + "," + ep.holdTop + "," + ep.holdBottom + "," +
                    ep.totalReps + "," + ep.totalSets + "\n");

            bw.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public File getFile(){
        return file;
    } // Getter for file
    
    public void flush() {  // Makes sure that the BW pushes everything to the file when app closed
        try{
            //if(bw!=null)
        bw.flush();
        } catch (IOException e){
            e.printStackTrace();
        }
    }
}
