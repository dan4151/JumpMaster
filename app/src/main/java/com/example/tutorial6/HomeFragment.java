package com.example.tutorial6;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import android.widget.TextView;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // Set up the bar chart
        BarChart barChart = view.findViewById(R.id.bar_chart);
        setupBarChart(barChart);

        // Set the explanation text with colors
        TextView explanationTextView = view.findViewById(R.id.chart_color_explanation);
        String explanation = "Chart Colors: \n• Black: Matches the average or no data available.\n• Red: Below the average for that day.\n• Green: Above the average for that day.";
        SpannableString spannableExplanation = new SpannableString(explanation);

        // Apply colors to the text
        int blackStart = explanation.indexOf("Black:");
        int blackEnd = blackStart + "Black".length();
        spannableExplanation.setSpan(
                new ForegroundColorSpan(getResources().getColor(android.R.color.black)),
                blackStart, blackEnd, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        int redStart = explanation.indexOf("Red:");
        int redEnd = redStart + "Red".length();
        spannableExplanation.setSpan(
                new ForegroundColorSpan(getResources().getColor(android.R.color.holo_red_dark)),
                redStart, redEnd, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        int greenStart = explanation.indexOf("Green:");
        int greenEnd = greenStart + "Green".length();
        spannableExplanation.setSpan(
                new ForegroundColorSpan(getResources().getColor(android.R.color.holo_green_dark)),
                greenStart, greenEnd, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        // Set the formatted text to the TextView
        explanationTextView.setText(spannableExplanation);

        return view;
    }


    private void setupBarChart(BarChart barChart) {
        // Read weekly data from the CSV file
        Map<String, Integer> weeklyData = JumpDataManager.readWeeklyData(requireContext());

        // Calculate averages for each day from the all_jump_history.csv file
        Map<String, Float> dayAverages = JumpDataManager.calculateDayAverages(requireContext());


        // Convert data to BarEntries and assign colors
        ArrayList<BarEntry> entries = new ArrayList<>();
        ArrayList<Integer> colors = new ArrayList<>();
        String[] daysOfWeek = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};

        for (int i = 0; i < daysOfWeek.length; i++) {
            String day = daysOfWeek[i];
            int jumps = weeklyData.getOrDefault(day, 0);
            entries.add(new BarEntry(i, jumps));

            // Determine the color based on the average
            float average = dayAverages.getOrDefault(day, -1f); // Default to -1f if no data
            if (average == -1f || jumps == average) {
                colors.add(getResources().getColor(android.R.color.black)); // No data or equal to average
            } else if (jumps < average) {
                colors.add(getResources().getColor(android.R.color.holo_red_dark)); // Below average
            } else {
                colors.add(getResources().getColor(android.R.color.holo_green_dark)); // Above average
            }
        }

        // Create the dataset and bar chart
        BarDataSet dataSet = new BarDataSet(entries, "Jumps Per Day");
        dataSet.setColors(colors); // Set bar colors dynamically

        BarData barData = new BarData(dataSet);

        // Customize the X-axis labels
        XAxis xAxis = barChart.getXAxis();
        barChart.getAxisLeft().setAxisMinimum(0f);
        barChart.getAxisRight().setAxisMinimum(0f);
        barChart.getLegend().setEnabled(false);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(daysOfWeek));
        xAxis.setGranularity(1f);
        xAxis.setGranularityEnabled(true);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

        barChart.setData(barData);
        barChart.invalidate(); // Refresh chart
    }



}
