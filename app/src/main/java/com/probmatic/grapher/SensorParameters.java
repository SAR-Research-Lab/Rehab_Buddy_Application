package com.probmatic.grapher;

class SensorParameters {
    boolean connected;             // Shimmer is connected to device
    boolean connecting;            // Shimmer is connecting to device
    boolean initializing;          // Shimmer is calibrating
    boolean calibrate;             // During exercise, Start Pos was changed and flagged
    boolean recordingStart;        // Recording the start pos for new exercise
    boolean recordingStop;         // Recording the stop pos for new exercise
    boolean running;               // Exercise in progress with feedback
    boolean finished;              // Exercise has been finished
    boolean no_feedback;           // Exercise in progress without feedback
    boolean pt_feedback;           // Exercise in progress with pt feedback
    boolean log_all;               // All streamed data is being logged when true,
    //    When false, data is only logged during events
    boolean stream_all = true;     // Shimmer is always streaming when true,
    //    When false, Shimmer is streaming only during events
    State state;                   // State of the exercise in runtime
}
