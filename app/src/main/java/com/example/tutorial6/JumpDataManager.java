package com.example.tutorial6;


import android.content.Context;
import android.os.Environment;
import android.util.Log;
import java.util.HashSet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class JumpDataManager {

    private static final String TAG = "JumpDataManager";

    private static Date getStartOfWeek() {
        Calendar calendar = Calendar.getInstance();
        calendar.setFirstDayOfWeek(Calendar.SUNDAY); // Force Sunday as the start
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        return calendar.getTime();
    }

    private static Date getStartOfWeek(Calendar calendar) {
        calendar.set(Calendar.DAY_OF_WEEK, calendar.getFirstDayOfWeek());
        return calendar.getTime();
    }

    public static void saveWeeklyData(Context context) {
        // Get the current week's start date
        String weekStartDate = new SimpleDateFormat("yyyy_MM_dd", Locale.getDefault())
                .format(getStartOfWeek());

        String fileName = "weekly_jumps_" + weekStartDate + ".csv";
        File directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (directory == null) {
            Log.e(TAG, "Could not access external storage directory");
            return;
        }

        File file = new File(directory, fileName);

        // Check if the file already exists
        if (!file.exists()) {
            Log.i(TAG, "Weekly file does not exist, creating with zeros: " + fileName);

            // Create the file with zeros for each day of the week
            try (FileWriter writer = new FileWriter(file)) {
                // Add headers for the weekly file
                writer.append("Date,Day of Week,Jumps\n");

                // Initialize the file with zeros for all days of the week
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                String todayDate = dateFormat.format(Calendar.getInstance().getTime());

                String[] daysOfWeek = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
                for (String day : daysOfWeek) {
                    writer.append(todayDate).append(",").append(day).append(",").append("0").append("\n");
                }

                Log.i(TAG, "Weekly file successfully created with zeros: " + fileName);
            } catch (IOException e) {
                Log.e(TAG, "Failed to create weekly file with zeros", e);
            }
        } else {
            Log.i(TAG, "Weekly file already exists: " + fileName);
        }
    }


    public static void saveCumulativeData(Context context) {
        // Define the cumulative file name
        String fileName = "all_jumps_history.csv";
        File directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (directory == null) {
            Log.e(TAG, "External storage directory not accessible");
            return;
        }

        File cumulativeFile = new File(directory, fileName);

        // Create the cumulative file if it doesn't exist
        if (!cumulativeFile.exists()) {
            try (FileWriter writer = new FileWriter(cumulativeFile)) {
                // Add headers for the cumulative file
                writer.append("Date,Day of Week,Jumps\n");
                Log.i(TAG, "Cumulative file created: " + fileName);
            } catch (IOException e) {
                Log.e(TAG, "Failed to create cumulative file", e);
                return;
            }
        }

        // Load existing rows to avoid duplicates
        HashSet<String> existingRows = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(cumulativeFile))) {
            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false; // Skip the header line
                    continue;
                }
                existingRows.add(line.trim());
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read existing cumulative file", e);
        }

        // Get the previous week's start date
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.WEEK_OF_YEAR, -1); // Move to the previous week
        String previousWeekStartDate = new SimpleDateFormat("yyyy_MM_dd", Locale.getDefault())
                .format(getStartOfWeek(calendar));

        String previousWeekFileName = "weekly_jumps_" + previousWeekStartDate + ".csv";
        File previousWeekFile = new File(directory, previousWeekFileName);

        // Check if the previous week's file exists
        if (!previousWeekFile.exists()) {
            Log.i(TAG, "Previous week's file does not exist: " + previousWeekFileName);
            return; // Nothing to append
        }

        // Read data from the previous week's file and append it to the cumulative file
        try (BufferedReader reader = new BufferedReader(new FileReader(previousWeekFile));
             FileWriter writer = new FileWriter(cumulativeFile, true)) {

            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false; // Skip the header line
                    continue;
                }
                if (!existingRows.contains(line.trim())) {
                    writer.append(line).append("\n");
                    existingRows.add(line.trim());
                }
            }
            Log.i(TAG, "Previous week's data appended to cumulative file: " + previousWeekFileName);
        } catch (IOException e) {
            Log.e(TAG, "Failed to append data to cumulative file", e);
        }
    }



    public static Map<String, Integer> readWeeklyData(Context context) {
        Map<String, Integer> data = new LinkedHashMap<>();

        // Get the current week's start date
        String weekStartDate = new SimpleDateFormat("yyyy_MM_dd", Locale.getDefault())
                .format(getStartOfWeek());

        // Define the correct filename
        String fileName = "weekly_jumps_" + weekStartDate + ".csv";
        File directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (directory == null) {
            Log.w(TAG, "External storage directory not accessible");
            return data;
        }

        File file = new File(directory, fileName);

        if (!file.exists()) {
            Log.w(TAG, "Weekly data file not found: " + fileName);
            return data;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue; // Skip the header line
                }

                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    String dayOfWeek = parts[1].trim();
                    int jumps = Integer.parseInt(parts[2].trim());
                    data.put(dayOfWeek, jumps);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading weekly data file", e);
        }

        return data;
    }


    // debuging porpuse only
    public static void replaceOrCreateWeeklyFile(Context context) {
        String fileName = "weekly_jumps_2025_01_19.csv";
        File directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (directory == null) {
            Log.e(TAG, "Could not access external storage directory");
            return;
        }

        File file = new File(directory, fileName);

        try (FileWriter writer = new FileWriter(file, false)) { // 'false' overwrites the file
            // Add headers for the weekly file
            writer.append("Date,Day of Week,Jumps\n");

            // Add new content with specific numbers
            writer.append("2025-01-19,Sunday,70\n");
            writer.append("2025-01-19,Monday,0\n");
            writer.append("2025-01-19,Tuesday,30\n");
            writer.append("2025-01-19,Wednesday,120\n");
            writer.append("2025-01-19,Thursday,40\n");
            writer.append("2025-01-19,Friday,10\n");
            writer.append("2025-01-19,Saturday,49\n");

            if (!file.exists()) {
                Log.i(TAG, "File created successfully: " + fileName);
            } else {
                Log.i(TAG, "File replaced successfully: " + fileName);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to create or replace the file", e);
        }
    }

    public static void createAllJumpHistoryFile(Context context) {
        String fileName = "all_jumps_history.csv";
        File directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (directory == null) {
            Log.e(TAG, "External storage directory not accessible");
            return;
        }

        File file = new File(directory, fileName);

        // Check if the file already exists
        if (file.exists()) {
            Log.i(TAG, "Cumulative file already exists: " + fileName);
            return;
        }

        try (FileWriter writer = new FileWriter(file)) {
            // Add headers for the cumulative file
            writer.append("Date,Day of Week,Jumps\n");

            // Data for the week starting on 2025-01-12
            writer.append("2025-01-12,Sunday,10\n");
            writer.append("2025-01-12,Monday,0\n");
            writer.append("2025-01-12,Tuesday,30\n");
            writer.append("2025-01-12,Wednesday,120\n");
            writer.append("2025-01-12,Thursday,30\n");
            writer.append("2025-01-12,Friday,55\n");
            writer.append("2025-01-12,Saturday,49\n");

            // Data for the week starting on 2025-01-05
            writer.append("2025-01-05,Sunday,15\n");
            writer.append("2025-01-05,Monday,25\n");
            writer.append("2025-01-05,Tuesday,35\n");
            writer.append("2025-01-05,Wednesday,45\n");
            writer.append("2025-01-05,Thursday,55\n");
            writer.append("2025-01-05,Friday,65\n");
            writer.append("2025-01-05,Saturday,75\n");

            // Data for the week starting on 2024-12-29
            writer.append("2024-12-29,Sunday,5\n");
            writer.append("2024-12-29,Monday,15\n");
            writer.append("2024-12-29,Tuesday,25\n");
            writer.append("2024-12-29,Wednesday,35\n");
            writer.append("2024-12-29,Thursday,45\n");
            writer.append("2024-12-29,Friday,55\n");
            writer.append("2024-12-29,Saturday,65\n");

            Log.i(TAG, "Cumulative file created successfully: " + fileName);
        } catch (IOException e) {
            Log.e(TAG, "Failed to create cumulative file", e);
        }
    }

    public static Map<String, Float> calculateDayAverages(Context context) {
        Map<String, Integer> dayTotals = new LinkedHashMap<>();
        Map<String, Integer> dayCounts = new LinkedHashMap<>();
        String fileName = "all_jumps_history.csv";
        File directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (directory == null) {
            Log.e(TAG, "External storage directory not accessible");
            return initializeDayAveragesWithDefault();
        }

        File file = new File(directory, fileName);
        if (!file.exists()) {
            Log.e(TAG, "All jumps history file not found: " + fileName);
            return initializeDayAveragesWithDefault();
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false; // Skip header
                    continue;
                }

                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    String dayOfWeek = parts[1].trim();
                    int jumps = Integer.parseInt(parts[2].trim());
                    dayTotals.put(dayOfWeek, dayTotals.getOrDefault(dayOfWeek, 0) + jumps);
                    dayCounts.put(dayOfWeek, dayCounts.getOrDefault(dayOfWeek, 0) + 1);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading all jumps history file", e);
        }

        // Calculate averages
        Map<String, Float> dayAverages = new LinkedHashMap<>();
        for (String day : dayTotals.keySet()) {
            int total = dayTotals.getOrDefault(day, 0);
            int count = dayCounts.getOrDefault(day, 0);
            dayAverages.put(day, count > 0 ? (float) total / count : -1f); // -1f means no data
        }

        return dayAverages;
    }

    /**
     * Initialize averages with -1f (no data) for all days of the week.
     */
    public static Map<String, Float> initializeDayAveragesWithDefault() {
        Map<String, Float> defaultAverages = new LinkedHashMap<>();
        String[] daysOfWeek = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
        for (String day : daysOfWeek) {
            defaultAverages.put(day, -1f);
        }
        return defaultAverages;
    }

}




