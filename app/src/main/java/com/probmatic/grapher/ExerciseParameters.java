package com.probmatic.grapher;

import javax.vecmath.Quat4d;

public class ExerciseParameters {
    String name = "";
    Quat4d qStart = new Quat4d();   // start pos
    Quat4d qEnd = new Quat4d();     // end pos
    Quat4d qIdeal = new Quat4d();   // angle between start and end
    float angIdeal;
    int holdTop = 4;
    int holdBottom = 1;
    long toc = 0; //matches toc from matlab, loaded from CountDownTimer with current hold time
    int totalReps = 10;
    int totalSets = 1;
    int currentRep = 0;
    int currentSet = 0;

    ExerciseParameters(){}

    ExerciseParameters(ExerciseParameters ep){
        this.name = ep.name;
        this.qStart.set(ep.qStart);
        this.qEnd.set(ep.qEnd);
        this.qIdeal.set(ep.qIdeal);
        this.angIdeal = ep.angIdeal;
        this.holdTop = ep.holdTop;
        this.holdBottom = ep.holdBottom;
        this.totalReps = ep.totalReps;
        this.totalSets = ep.totalSets;
    }

    public void set(ExerciseParameters ep) {
        this.name = ep.name;
        this.qStart.set(ep.qStart);
        this.qEnd.set(ep.qEnd);
        this.qIdeal.set(ep.qIdeal);
        this.angIdeal = ep.angIdeal;
        this.holdTop = ep.holdTop;
        this.holdBottom = ep.holdBottom;
        this.totalReps = ep.totalReps;
        this.totalSets = ep.totalSets;
    }
}
