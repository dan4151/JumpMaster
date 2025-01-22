package com.example.tutorial6;

import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * An activity that lists all CSV files under the public Documents folder in a Spinner,
 * and upon selecting one, plots its data in a LineChart.
 */
public class LoadDataActivity extends AppCompatActivity {

    private Spinner csvSpinner;
    private Button loadCsvButton, goBackButton;
    private LineChart lineChart;

    // Holds CSV files for quick reference
    private List<File> csvFilesList;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_load_csv);

        // Initialize UI elements
        csvSpinner = findViewById(R.id.spinner_csv);
        loadCsvButton = findViewById(R.id.button_load_csv);
        goBackButton = findViewById(R.id.button_go_back);
        lineChart = findViewById(R.id.line_chart);
        lineChart.setBackgroundColor(android.graphics.Color.WHITE);
        lineChart.setDrawGridBackground(true);
        lineChart.setGridBackgroundColor(android.graphics.Color.WHITE);

        // 1. Find all CSV files in the public Documents folder
        csvFilesList = findCsvFiles();

        // 2. Populate the spinner
        if (csvFilesList.isEmpty()) {
            // No CSV files found
            List<String> emptyList = new ArrayList<>();
            emptyList.add("No CSV files found");
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    this, android.R.layout.simple_spinner_item, emptyList
            );
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            csvSpinner.setAdapter(adapter);
        } else {
            // Build file name list for the spinner
            List<String> fileNames = new ArrayList<>();
            for (File f : csvFilesList) {
                fileNames.add(f.getName());
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    this, android.R.layout.simple_spinner_item, fileNames
            );
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            csvSpinner.setAdapter(adapter);
        }

        // 3. Set up button to load the selected CSV
        loadCsvButton.setOnClickListener(v -> loadSelectedCsv());

        // 4. Go back button
        goBackButton.setOnClickListener(v -> finish());
    }

    /**
     * Finds all .csv files under public Documents directory.
     */
    private List<File> findCsvFiles() {
        List<File> csvList = new ArrayList<>();

        // Path: /storage/emulated/0/Documents
        File docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        if (docsDir != null && docsDir.exists() && docsDir.isDirectory()) {
            File[] files = docsDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    // Check if the file ends with .csv (case-insensitive)
                    if (file.isFile() && file.getName().toLowerCase().endsWith(".csv")) {
                        csvList.add(file);
                    }
                }
            }
        } else {
            Toast.makeText(this, "Documents folder not found or empty", Toast.LENGTH_SHORT).show();
        }
        return csvList;
    }

    /**
     * Loads the CSV file chosen in the Spinner and plots it on the line chart.
     */
    private void loadSelectedCsv() {
        // If no CSV files exist, do nothing
        if (csvFilesList.isEmpty()) {
            Toast.makeText(this, "No CSV files available to load.", Toast.LENGTH_SHORT).show();
            return;
        }

        int selectedIndex = csvSpinner.getSelectedItemPosition();
        // If an empty list was used ("No CSV files found"), skip
        if (selectedIndex < 0 || selectedIndex >= csvFilesList.size()) {
            Toast.makeText(this, "Invalid selection", Toast.LENGTH_SHORT).show();
            return;
        }

        File selectedFile = csvFilesList.get(selectedIndex);
        Toast.makeText(this, "Loading: " + selectedFile.getName(), Toast.LENGTH_SHORT).show();

        // Parse the CSV & plot
        parseAndPlotCsv(selectedFile);
    }

    /**
     * Parses the CSV and plots only the N values on the LineChart.
     */
    private void parseAndPlotCsv(File csvFile) {
        // Prepare a list of entries for N values
        List<Entry> entriesN = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            int index = 0;

            while ((line = br.readLine()) != null) {
                line = line.trim();
                // Skip empty or header lines
                if (line.isEmpty() || line.toLowerCase().startsWith("time")) {
                    continue;
                }

                // Split by comma
                String[] parts = line.split(",");
                if (parts.length < 8) {
                    // We need at least 8 columns: Time, ACC X, Y, Z, GYRO X, Y, Z, N
                    continue;
                }

                try {
                    // Parse N value from the last column
                    float nValue = Float.parseFloat(parts[7].trim());

                    // Use 'index' as X-axis for plotting
                    entriesN.add(new Entry(index, nValue));
                    index++;
                } catch (NumberFormatException e) {
                    // Skip lines that don't parse
                }
            }

            // Create a dataset for N values
            LineDataSet dataSetN = new LineDataSet(entriesN, "N Values");

            // Style the dataset
            dataSetN.setColor(android.graphics.Color.RED);
            dataSetN.setCircleColor(android.graphics.Color.RED);
            dataSetN.setLineWidth(2f);
            dataSetN.setCircleRadius(3f);

            // Assign data to the chart
            LineData lineData = new LineData(dataSetN);
            lineChart.setData(lineData);
            lineChart.invalidate(); // Refresh chart

            Toast.makeText(this, "CSV loaded successfully!", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "Error reading CSV: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

}
