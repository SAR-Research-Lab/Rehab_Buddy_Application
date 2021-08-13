package com.probmatic.grapher;

import com.github.mikephil.charting.data.Entry;

import java.util.ArrayList;

public class RecordData {
    ArrayList<Record> records = new ArrayList<>();

    Record current;

    Double tempStartTime;

    class Record{
        String exerciseName;


        double noPomError;
        double ptPomError;
        double rbPomError;

        float noROMSum;
        float ptROMSum;
        float rbROMSum;

        //ArrayList<Entry> rbHoldPoints;
        //ArrayList<Entry> noHoldPoints;
        //ArrayList<Entry> ptHoldPoints;

        ArrayList<Entry> noFeedback;
        ArrayList<Entry> ptFeedback;
        ArrayList<Entry> rbFeedback;

        Record(String exerciseName){
            this.exerciseName = exerciseName;
        }
    }

    public Record get(String name){
        for (Record r : records){
            if(r.exerciseName.equals(name))
                return r;
        }
        return null;
    }

    public Record get(int id){
        return records.get(id);
    }

    public void load(String name){
        for (Record r : records){
            if(r.exerciseName.equals(name))
                current = r;
        }
    }


    public void createNew(String name){
        if(get(name) == null){
            current = new Record(name);
            records.add(current);
        }
    }
}
