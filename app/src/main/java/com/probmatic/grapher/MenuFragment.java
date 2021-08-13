package com.probmatic.grapher;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import java.util.ArrayList;
import java.util.Arrays;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link MenuFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 */
public class MenuFragment extends Fragment {

    private int currentPage = 0;
    private Drawable def;
    private ArrayList<Button> buttons = new ArrayList<>();
    private OnFragmentInteractionListener mListener;

    public MenuFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_menu, container, false);
        Button bConnectSensor = v.findViewById(R.id.bConnectSensor);
        Button bInitializeSensor = v.findViewById(R.id.bInitializeSensor);
        Button bSelectExercise = v.findViewById(R.id.bSelectExercise);
        Button bStartEndPos = v.findViewById(R.id.bStartEndPos);
        Button bBeginExercise = v.findViewById(R.id.bBeginExercise);
        Button bExerciseSummary = v.findViewById(R.id.bExerciseSummary);
        Button bPreviousPage = v.findViewById(R.id.bPreviousPage);
        Button bContinue = v.findViewById(R.id.bContinue);

        buttons = new ArrayList<>(Arrays.asList(bConnectSensor, bInitializeSensor,
                bSelectExercise, bStartEndPos, bBeginExercise,
                bExerciseSummary, bPreviousPage, bContinue)) ;

        def = bConnectSensor.getBackground();

        View.OnClickListener pressListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onButtonPressed(v);
            }
        };

        for(Button b : buttons)
            b.setOnClickListener(pressListener);

        buttonUpdateByPage(currentPage);

        return v;
    }

    // TODO: Rename method, update argument and hook method into UI event
    public void onButtonPressed(View v) {
        if (mListener != null) {
            int startPage = currentPage;
            currentPage = mListener.onFragmentInteraction(v, currentPage);
            if(startPage != currentPage)
                buttonUpdateByPage(currentPage);
            if(currentPage == -1) // Handles home page
                currentPage = 0;
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        int onFragmentInteraction(View v, int currentPage);
    }

    public void setCurrentPage(int currentPage){
        this.currentPage = currentPage;
    }

    public void buttonUpdateByPage(int currentPage) {
        if (currentPage >= 0 && currentPage <= 5) {
            for (int i = 0; i < currentPage; i++)              // Set button color for completed steps
                buttons.get(i).setBackground(getResources().getDrawable(R.drawable.completed));

            for (int i = currentPage; i < buttons.size(); i++) // Set button color for incomplete steps
                buttons.get(i).setBackground(def);
            // Set button color for current step
            buttons.get(currentPage).setBackground(getResources().getDrawable(R.drawable.current));
        }

        // Update the text on the buttons for first/last page
        if (currentPage == 0)
            buttons.get(6).setText("Return to Home");
        else
            buttons.get(6).setText("Previous Page");

        if (currentPage == 5)
            buttons.get(7).setText("Return to Home");
        else
            buttons.get(7).setText("Continue");
    }
}
