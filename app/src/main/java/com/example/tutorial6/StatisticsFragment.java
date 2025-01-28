package com.example.tutorial6;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatisticsFragment extends Fragment {

    private static final String TAG = "StatisticsFragment";

    private Spinner fileSpinner;
    private TextView averageSpeedValue, totalJumpsValue;
    private TextView slowStatsLabel, slowStatsValue;
    private TextView fastStatsLabel, fastStatsValue;

    private PyObject script;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_statistics, container, false);

        // Initialize Python and load the script
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(requireContext()));
        }
        Python python = Python.getInstance();
        script = python.getModule("script"); // Replace with your Python file name (without .py)

        // Initialize views
        fileSpinner = view.findViewById(R.id.file_spinner);
        Button loadButton = view.findViewById(R.id.load_button);
        Button deleteButton = view.findViewById(R.id.delete_button); // New Delete Button
        averageSpeedValue = view.findViewById(R.id.average_speed_value);
        totalJumpsValue = view.findViewById(R.id.total_jumps_value);
        slowStatsLabel = view.findViewById(R.id.slow_stats_label);
        slowStatsValue = view.findViewById(R.id.slow_stats_value);
        fastStatsLabel = view.findViewById(R.id.fast_stats_label);
        fastStatsValue = view.findViewById(R.id.fast_stats_value);

        // Populate file spinner with available files
        populateFileSpinner();

        // Set up Load button click listener
        loadButton.setOnClickListener(v -> {
            String selectedFile = (String) fileSpinner.getSelectedItem();
            if (selectedFile != null && !selectedFile.isEmpty()) {
                File file = new File(requireContext().getExternalFilesDir(null), selectedFile);
                if (file.exists()) {
                    analyzeData(file);
                } else {
                    Toast.makeText(getContext(), "File not found!", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(getContext(), "No file selected!", Toast.LENGTH_SHORT).show();
            }
        });

        // Set up Delete button click listener
        deleteButton.setOnClickListener(v -> {
            String selectedFile = (String) fileSpinner.getSelectedItem();
            if (selectedFile != null && !selectedFile.isEmpty()) {
                File file = new File(requireContext().getExternalFilesDir(null), selectedFile);
                if (file.exists()) {
                    if (file.delete()) {
                        Toast.makeText(getContext(), "File deleted successfully.", Toast.LENGTH_SHORT).show();
                        populateFileSpinner(); // Refresh spinner
                    } else {
                        Toast.makeText(getContext(), "Failed to delete file.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(getContext(), "File not found.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(getContext(), "No file selected.", Toast.LENGTH_SHORT).show();
            }
        });

        return view;
    }

    private void populateFileSpinner() {
        File directory = requireContext().getExternalFilesDir(null);
        if (directory != null && directory.exists()) {
            File[] files = directory.listFiles();
            List<String> fileNames = new ArrayList<>();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        fileNames.add(file.getName());
                    }
                }
            }

            if (!fileNames.isEmpty()) {
                ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, fileNames);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                fileSpinner.setAdapter(adapter);
            } else {
                Toast.makeText(requireContext(), "No files available.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(requireContext(), "Directory not found.", Toast.LENGTH_SHORT).show();
        }
    }

    private void analyzeData(File file) {
        try {
            PyObject result = script.callAttr("create_statistics", file.getAbsolutePath());
            Log.d(TAG, "Python result: " + result.toString());

            if (result != null) {
                Map<PyObject, PyObject> rawMap = result.asMap();
                Map<String, PyObject> resultMap = new HashMap<>();
                for (Map.Entry<PyObject, PyObject> entry : rawMap.entrySet()) {
                    resultMap.put(entry.getKey().toString(), entry.getValue());
                }

                // Parse result
                String jumpingType = resultMap.get("jumping_type").toString();
                int totalJumps = resultMap.get("total_jumps").toInt();
                double avgSpeed = resultMap.get("avg_speed").toDouble();

                // Update common stats
                averageSpeedValue.setText(String.format("%.2f", avgSpeed));
                totalJumpsValue.setText(String.valueOf(totalJumps));

                // Update interval-specific stats
                if ("interval".equalsIgnoreCase(jumpingType)) {
                    int slowJumps = resultMap.get("slow_jumps").toInt();
                    int fastJumps = resultMap.get("fast_jumps").toInt();
                    double slowAvgSpeed = resultMap.get("slow_avg_speed").toDouble();
                    double fastAvgSpeed = resultMap.get("fast_avg_speed").toDouble();

                    slowStatsLabel.setVisibility(View.VISIBLE);
                    slowStatsValue.setVisibility(View.VISIBLE);
                    slowStatsValue.setText(String.format("Jumps: %d, Avg Speed: %.2f", slowJumps, slowAvgSpeed));

                    fastStatsLabel.setVisibility(View.VISIBLE);
                    fastStatsValue.setVisibility(View.VISIBLE);
                    fastStatsValue.setText(String.format("Jumps: %d, Avg Speed: %.2f", fastJumps, fastAvgSpeed));
                } else {
                    slowStatsLabel.setVisibility(View.GONE);
                    slowStatsValue.setVisibility(View.GONE);
                    fastStatsLabel.setVisibility(View.GONE);
                    fastStatsValue.setVisibility(View.GONE);
                }
            } else {
                Toast.makeText(getContext(), "No data returned from analysis.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error analyzing data: " + e.getMessage(), e);
            Toast.makeText(getContext(), "Error analyzing data. Please check the file.", Toast.LENGTH_SHORT).show();
        }
    }
}
