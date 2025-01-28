package com.example.tutorial6;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Environment;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.chaquo.python.android.AndroidPlatform;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import org.json.JSONArray;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
/**
 * FINAL TerminalFragment:
 *  - Always parses and plots ANY valid data (no isReceivingData flag).
 *  - "NoDataText" is hidden after the first valid entry.
 *  - Uses an integer pointIndex for chart X-axis, never negative or reversed.
 *  - Expects lines like: "-0.31  Y: -0.31  Z: 8.47" OR "X: -0.31  Y: -0.31  Z: 8.47"
 */
public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {

    /** Connection states */
    private enum Connected {False, Pending, True}
    private Connected connected = Connected.False;

    private String deviceAddress;
    private SerialService service;

    /** Optional UI references */
    private TextView receiveText;
    private TextView sendText; // if your layout has it
    private TextUtil.HexWatcher hexWatcher; // if used

    /** MPAndroidChart objects */
    private LineChart mpLineChart;
    private LineDataSet lineDataSetX, lineDataSetY, lineDataSetZ;

    private LineDataSet lineDataSetN;
    private LineData data;

    /** Strictly-increasing X-axis index */
    private int pointIndex = 0;

    /** Newline and flags (hex, etc.) */
    private boolean hexEnabled = false;
    private String newline = TextUtil.newline_crlf;

    private boolean startedPlot = false;

    private int numberOfSteps = 0;

    private String jumpingType = "None";

    private String save_file_name = "None";

    private long startTime = 0;

    List<Float> timeList = new ArrayList<>(); // For elapsed times in seconds

    private long prev_time = 0;

    private int estimatedJumps = 0;

    private int lengthInterval = 0;


    int flagAlreadySaved = 0;

    /** voice variables */
    private int isFast = 0;
    private TextToSpeech tts;
    private Timer timer;
    private Handler handler = new Handler();
    private Runnable intervalTask = null;
    private List<String> phaseList = new ArrayList<>();


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);

        // Grab the device address from arguments
        deviceAddress = getArguments().getString("device");

        //Text-to-speech
        tts = new TextToSpeech(requireContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US); // Set language to US English
            } else {
                Log.e("TTS", "Initialization failed");
            }
        });
    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False) {
            disconnect();
        }
        requireActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (timer != null) {
            timer.cancel();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (service != null) {
            service.attach(this);
        } else {
            requireActivity().startService(new Intent(getActivity(), SerialService.class));
        }
    }

    @Override
    public void onStop() {
        if (service != null && !requireActivity().isChangingConfigurations()) {
            service.detach();
        }
        super.onStop();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        requireActivity().bindService(new Intent(getActivity(), SerialService.class),
                this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try {
            requireActivity().unbindService(this);
        } catch (Exception ignored) {
        }
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (connected == Connected.False && service != null) {
            requireActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);

        // Attempt to connect once service is bound
        if (connected == Connected.False && isResumed()) {
            requireActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    // ------------------------------------------------------------------------
    // onCreateView - Setup the UI
    // ------------------------------------------------------------------------
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);

        mpLineChart = view.findViewById(R.id.line_chart);
        mpLineChart.setVisibility(View.GONE);

        // Initialize the MPAndroidChart
        initChart();

        Button buttonStartFreeStyle = view.findViewById(R.id.button_start_freestyle);
        if (buttonStartFreeStyle != null) {
            buttonStartFreeStyle.setOnClickListener(v -> {
                jumpingType = "freestyle";
                lengthInterval = 0;
                Toast.makeText(getContext(), "Recording Started!", Toast.LENGTH_SHORT).show();

                Handler handler = new Handler();
                int countdownStart = 5;

                // Countdown task
                for (int i = countdownStart; i > 0; i--) {
                    int finalI = i;
                    handler.postDelayed(() -> {
                        if (tts != null) {
                            tts.speak(String.valueOf(finalI), TextToSpeech.QUEUE_FLUSH, null, null);
                        }
                    }, (countdownStart - finalI) * 1000L); // Delay based on countdown step
                }

                // "Start jumping" task
                handler.postDelayed(() -> {
                    if (tts != null) {
                        tts.speak("Start jumping", TextToSpeech.QUEUE_FLUSH, null, null);
                    }
                    // Start the session after countdown
                    startedPlot = true;
                    startTime = System.currentTimeMillis();
                    flagAlreadySaved = 0;
                }, countdownStart * 1000L); // Delay after the countdown
            });
        }


        // Declare the Handler and Runnable

        Button buttonStartInterval = view.findViewById(R.id.button_start_interval);
        if (buttonStartInterval != null) {
            buttonStartInterval.setOnClickListener(v -> {
                jumpingType = "interval";
                // Create an AlertDialog to input interval length
                AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
                builder.setTitle("Set Interval Length");

                // Create the input field
                final EditText input = new EditText(requireContext());
                input.setHint("Enter interval length (seconds)");
                input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                builder.setView(input);

                // Handle "OK" button click
                builder.setPositiveButton("OK", (dialog, which) -> {
                    // Retrieve input inside the positive button callback
                    String inputText = input.getText().toString().trim();
                    if (inputText.isEmpty()) {
                        Toast.makeText(requireContext(), "Please enter a valid number!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    try {
                        lengthInterval = Integer.parseInt(inputText);
                    } catch (NumberFormatException e) {
                        Toast.makeText(requireContext(), "Invalid input!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Proceed with the countdown and interval logic
                    Toast.makeText(requireContext(), "Recording will start soon!", Toast.LENGTH_SHORT).show();
                    flagAlreadySaved = 0;

                    // Cancel any existing task
                    if (intervalTask != null) {
                        handler.removeCallbacks(intervalTask);
                    }

                    int countdownStart = 5;

                    // Countdown task
                    for (int i = countdownStart; i > 0; i--) {
                        int finalI = i;
                        handler.postDelayed(() -> {
                            if (tts != null) {
                                tts.speak(String.valueOf(finalI), TextToSpeech.QUEUE_FLUSH, null, null);
                            }
                        }, (countdownStart - finalI) * 1000L); // Delay based on countdown step
                    }

                    // Start jump recording and slow/fast cycle after the countdown
                    handler.postDelayed(() -> {
                        // Start jump recording
                        startedPlot = true;
                        startTime = System.currentTimeMillis();

                        // Define the task for slow/fast cycle
                        intervalTask = new Runnable() {

                            @Override
                            public void run() {
                                if (isAdded() && tts != null) { // Ensure the Fragment is attached
                                    String message = (isFast == 1) ? "fast" : "slow";
                                    // Speak the message
                                    tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "TTS_MESSAGE");
                                    isFast = (isFast == 1) ? 0 : 1;
                                }

                                // Schedule the task again after the user-defined interval length
                                handler.postDelayed(this, lengthInterval * 1000L);
                            }
                        };

                        // Start the task immediately
                        handler.post(intervalTask);
                    }, countdownStart * 1000L); // Start after the countdown finishes
                });

                // Handle "Cancel" button click
                builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

                // Show the dialog
                builder.create().show();
            });
        }


        Button buttonPause = view.findViewById(R.id.button_pause);
        if (buttonPause != null) {
            buttonPause.setOnClickListener(v -> {
                startedPlot = false;
                stopSpeechAndTask();
                Toast.makeText(getContext(), "Recording paused!", Toast.LENGTH_SHORT).show();
            });
        }

        Button buttonReset = view.findViewById(R.id.button_reset);
        if (buttonReset != null) {
            buttonReset.setOnClickListener(v -> {
                stopSpeechAndTask();
                LineData currentData = mpLineChart.getData();
                if (currentData != null) {
                    // Remove specific data sets from the chart
                    if (lineDataSetX != null) lineDataSetX.clear();
                    if (lineDataSetY != null) lineDataSetY.clear();
                    if (lineDataSetZ != null) lineDataSetZ.clear();
                    if (lineDataSetN != null) lineDataSetN.clear();
                    if (timeList != null) timeList.clear();
                    TextView stepView = getView().findViewById(R.id.dynamic_jumps);
                    stepView.setText(String.valueOf(0));
                    TextView stepView2 = getView().findViewById(R.id.rate_jumps);
                    stepView2.setText(String.valueOf(0));
                    mpLineChart.notifyDataSetChanged();
                    mpLineChart.invalidate();
                }
                pointIndex = 0;
                startedPlot = false;
                Toast.makeText(getContext(), "Recording deleted!", Toast.LENGTH_SHORT).show();
            });
        }



        Button saveButton = view.findViewById(R.id.button_save);

        if (saveButton != null) {
            saveButton.setOnClickListener(v -> {
                stopSpeechAndTask();
                showSaveDialog();
                if (flagAlreadySaved == 0) {
                    JumpDataManager.updateWeeklyData(requireContext(), estimatedJumps);
                    flagAlreadySaved = 1;
                    Toast.makeText(getContext(), "Jumps saved!", Toast.LENGTH_SHORT).show();
                }
                else if (estimatedJumps == 0)
                {
                    Toast.makeText(getContext(), "No jumps!", Toast.LENGTH_SHORT).show();
                }
                else {
                    Toast.makeText(getContext(), "Jumps already saved!", Toast.LENGTH_SHORT).show();
                }
            });
        }


        return view;
    }

    // ------------------------------------------------------------------------
    // Initialize MPAndroidChart
    // ------------------------------------------------------------------------
    private void initChart() {
        // Create 3 data sets for X, Y, Z lines
        lineDataSetN = new LineDataSet(new ArrayList<>(), "N");
        lineDataSetX = new LineDataSet(new ArrayList<>(), "X");
        lineDataSetY = new LineDataSet(new ArrayList<>(), "Y");
        lineDataSetZ = new LineDataSet(new ArrayList<>(), "Z");


        // Make lines/circles visible
        lineDataSetN.setColor(Color.RED);
        lineDataSetN.setCircleColor(Color.RED);
        lineDataSetN.setCircleRadius(4f);
        lineDataSetN.setLineWidth(2f);
        lineDataSetN.setDrawValues(true);


        // Combine into a single LineData
        data = new LineData(lineDataSetN);

        // Assign data to the chart
        mpLineChart.setData(data);

        // Let user see a message if no data points exist yet
        mpLineChart.setNoDataText("Waiting for data...");

        // Basic chart settings
        mpLineChart.getDescription().setEnabled(false);
        mpLineChart.setTouchEnabled(true);
        mpLineChart.setDragEnabled(true);
        mpLineChart.setScaleEnabled(true);
        mpLineChart.setPinchZoom(true);
        mpLineChart.setBackgroundColor(Color.WHITE);
        mpLineChart.setAutoScaleMinMaxEnabled(true);

        // Provide extra space for axis labels
        mpLineChart.setViewPortOffsets(50f, 20f, 30f, 60f);

        // Force X-axis min = 0
        XAxis xAxis = mpLineChart.getXAxis();
        xAxis.setAxisMinimum(0f);
        xAxis.setTextColor(Color.BLACK);
        xAxis.setTextSize(12f);

        mpLineChart.invalidate();
    }


    // ------------------------------------------------------------------------
    // Menu / Options
    // ------------------------------------------------------------------------
    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
        menu.findItem(R.id.hex).setChecked(hexEnabled);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int menuId = item.getItemId();

        if (menuId == R.id.clear) {
            receiveText.setText("");
            return true;
        } else if (menuId == R.id.newline) {
            String[] newlineNames = getResources().getStringArray(R.array.newline_names);
            String[] newlineValues = getResources().getStringArray(R.array.newline_values);
            int pos = java.util.Arrays.asList(newlineValues).indexOf(newline);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Newline");
            builder.setSingleChoiceItems(newlineNames, pos, (dialog, which) -> {
                newline = newlineValues[which];
                dialog.dismiss();
            });
            builder.create().show();
            return true;
        } else if (menuId == R.id.hex) {
            hexEnabled = !hexEnabled;
            if (sendText != null && hexWatcher != null) {
                sendText.setText("");
                hexWatcher.enable(hexEnabled);
                sendText.setHint(hexEnabled ? "HEX mode" : "");
            }
            item.setChecked(hexEnabled);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // ------------------------------------------------------------------------
    // Connect / Disconnect
    // ------------------------------------------------------------------------
    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            status("connecting...");
            connected = Connected.Pending;
            SerialSocket socket = new SerialSocket(requireActivity().getApplicationContext(), device);
            service.connect(socket);
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        service.disconnect();
    }

    // ------------------------------------------------------------------------
    // SerialListener - receiving data
    // ------------------------------------------------------------------------

    private void receive(byte[] message) {

    if (!startedPlot) {
       return;
    }
        long cur_time = System.currentTimeMillis();
        String rawMessage = new String(message).trim();
        Log.d("interval", "interval: " + (Math.abs(prev_time - cur_time)));
    //    if (prev_time == 0) prev_time = cur_time;
    //    else if (Math.abs(prev_time - cur_time) < 10){
    //       // prev_time = cur_time;
    //        return;
    //    }
    //    else prev_time = cur_time;



        float elapsedTimeInSeconds = (cur_time - startTime) / 1000f;
        Log.d("time", "time: " + elapsedTimeInSeconds);
        Log.d("TerminalFragment", "Raw incoming: " + rawMessage);


        // Try to parse " Y:" and " Z:" => 3 parts
        String[] parts = rawMessage.split(" Y:| Z:");
        if (parts.length == 3) {
            try {
                // If there's "X:" in first part, remove it
                String xString = parts[0].replace("X:", "").trim();
                float xVal = Float.parseFloat(xString);
                float yVal = Float.parseFloat(parts[1].trim());
                float zVal = Float.parseFloat(parts[2].trim());
                float nVal = (float) Math.sqrt(xVal * xVal + yVal * yVal + zVal * zVal);
                requireActivity().runOnUiThread(() -> addDataToChart(xVal, yVal, zVal, nVal, elapsedTimeInSeconds));
            } catch (NumberFormatException e) {
                Log.e("TerminalFragment", "Error parsing floats", e);
            }
        } else {
            // This line doesn't match => skip
            Log.e("TerminalFragment", "Invalid data format: " + rawMessage);
        }
    }

    /**
     * Add square root of x^2 y^2 z^2 to the plot
     */
    private void addDataToChart(float xVal, float yVal, float zVal, float nVal, float elapsedtime) {
        if (mpLineChart == null
                || lineDataSetN == null || lineDataSetX == null || lineDataSetY == null || lineDataSetZ == null
                )
            return;


        String phase = (isFast == 1) ? "Fast" : "Slow";
        phaseList.add(phase); // Add the phase to the phase list

        Log.d("TerminalFragment", "addDataToChart() idx=" + pointIndex
                + " N=" + nVal + " Phase=" + phase);

        // If the chart was showing "Waiting for data...", hide that text
        mpLineChart.setNoDataText("");
        Log.d("CHECK VALUES", "nVal= " + nVal + " xVal= " + xVal + " yVal= " + yVal + " zVal= " + zVal);

        // Add to each dataset at the same X = pointIndex
        timeList.add(elapsedtime);
        lineDataSetN.addEntry(new Entry(pointIndex, nVal));
        lineDataSetX.addEntry(new Entry(pointIndex, xVal));
        lineDataSetY.addEntry(new Entry(pointIndex, yVal));
        lineDataSetZ.addEntry(new Entry(pointIndex, zVal));
        pointIndex++;
        lineDataSetN.notifyDataSetChanged();
        mpLineChart.notifyDataSetChanged();
        mpLineChart.invalidate();

        //=========================================================
        // Using python to calculate number of jumps and jump speed
        //=========================================================

        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(requireContext()));

        }
        Python py = Python.getInstance();

        // Call the Python function
        PyObject pyObject = py.getModule("script"); // Replace with your Python file name (without .py extension)

        List<Float> yValues = new ArrayList<>();

        for (Entry entry : lineDataSetZ.getValues()) {
            yValues.add(entry.getY());
        }
        ArrayList<Entry> entries = (ArrayList<Entry>) lineDataSetZ.getValues();
     //   float[] valuesZ = new float[entries.size()];
     //   float[] timevals = new float[entries.size()];

    //    for (int i = 0; i < entries.size(); i++) {
      //      valuesZ[i] = entries.get(i).getY(); // Get the Y value of each entry
      //      timevals[i] = timeList.get(i); // Get the Y value of each entry
      //  }

        JSONArray jsonArray = new JSONArray(yValues);
        String valuesZ = jsonArray.toString();
        JSONArray jsonArraytime = new JSONArray(timeList);
        String valuesTime = jsonArraytime.toString();

        PyObject result = pyObject.callAttr("smooth_and_find_extrema", valuesZ, valuesTime, false); // Call the Python function "get_jumps"
        Map<PyObject, PyObject> rawMap = result.asMap();
        Map<String, PyObject> resultMap = new HashMap<>();
        for (Map.Entry<PyObject, PyObject> entry : rawMap.entrySet()) {
            resultMap.put(entry.getKey().toString(), entry.getValue());
        }

        // Parse result
        int totalJumps = resultMap.get("jumps").toInt();
        double avgSpeed = resultMap.get("rate_of_jumps").toDouble();
        // Get the result as a number
        // Update the TextView dynamically

        TextView stepView = getView().findViewById(R.id.dynamic_jumps);
        stepView.setText(String.valueOf(totalJumps));
        TextView stepView2 = getView().findViewById(R.id.rate_jumps);
        stepView2.setText(String.valueOf(Math.round(avgSpeed* 100.0) / 100.0));
    }

    private void showSaveDialog() {
        // Inflate the custom dialog layout
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View dialogView = inflater.inflate(R.layout.dialog_save, null);

        EditText sessionNameInput = dialogView.findViewById(R.id.dialog_file_name); // Renamed for clarity
        Button okButton = dialogView.findViewById(R.id.dialog_ok_button);

        // Create an AlertDialog
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();

        okButton.setOnClickListener(v -> {
            String sessionName = sessionNameInput.getText().toString().trim();

            // Check if the session name is empty
            if (sessionName.isEmpty()) {
                Toast.makeText(getContext(), "Please enter a session name!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!sessionName.matches("[a-zA-Z0-9_.-]+")) {
                Toast.makeText(getContext(), "Invalid session name! Only alphanumeric characters, underscores, hyphens, and dots are allowed.", Toast.LENGTH_SHORT).show();
                return;
            }

            save_file_name = sessionName;

            Log.d("ok_button", "ok_button: " + save_file_name);

            // Prepare the CSV data
            StringBuilder csvBuilder = new StringBuilder();
            // Add metadata
            csvBuilder.append("NAME:, ").append(save_file_name).append("\n");
            csvBuilder.append("EXPERIMENT TIME:, ").append(LocalDateTime.now()).append("\n");
            csvBuilder.append("JUMPING TYPE:, ").append(jumpingType).append("\n");
            csvBuilder.append("INTERVAL LENGTH:, ").append(lengthInterval).append("\n");
            csvBuilder.append("\n"); // Blank row

            // Add header row for the tabular data
            csvBuilder.append("Time [sec], ACC X, ACC Y, ACC Z, GYRO X, GYRO Y, GYRO Z, N, Phase\n");

            for (int i = 0; i < lineDataSetN.getEntryCount(); i++) {
                // Get ACC X, Y, Z values (if available)
                float accX = lineDataSetX.getEntryForIndex(i).getY();
                float accY = lineDataSetY.getEntryForIndex(i).getY();
                float accZ = lineDataSetZ.getEntryForIndex(i).getY();
                float nValue = lineDataSetN.getEntryForIndex(i).getY();
                String phase = phaseList.get(i);

                Log.d("CHECK SAVE VALUES", "accX= " + accX + " accY= " + accY + " accZ= " + accZ + " nValue= " + nValue);

                // Append the row to CSV
                csvBuilder.append(timeList.get(i)).append(",")
                        .append(accX).append(",")
                        .append(accY).append(",")
                        .append(accZ).append(",")
                        .append("0,0,0,") // Placeholder for GYRO X, Y, Z (update if you have data)
                        .append(nValue).append(",")
                        .append(phase).append("\n");
            }

            // Write the CSV data to a file
            try {
                File dir = new File(requireContext().getExternalFilesDir(null), "");
                File file = new File(dir, save_file_name + ".csv");

                FileOutputStream fos = new FileOutputStream(file);
                fos.write(csvBuilder.toString().getBytes());
                fos.close();

                Toast.makeText(getContext(), "File saved successfully: " + file.getAbsolutePath(), Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(getContext(), "Failed to save file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }

            // Close the dialog
            dialog.dismiss();
        });

        // Show the dialog
        dialog.show();
    }


    // ------------------------------------------------------------------------
    // Utility
    // ------------------------------------------------------------------------
    private void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(
                new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)),
                0, spn.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        if (receiveText != null) {
            receiveText.append(spn);
        }
    }

    // ------------------------------------------------------------------------
    // SerialListener callbacks
    // ------------------------------------------------------------------------
    @Override
    public void onSerialConnect() {
        status("connected");
        connected = Connected.True;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        receive(data);
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
    }

    // If you have another activity for CSV
    private void OpenLoadCSV() {
        Intent intent = new Intent(getContext(), LoadDataActivity.class);
        startActivity(intent);
    }

    private void stopSpeechAndTask() {
        if (intervalTask != null) {
            handler.removeCallbacks(intervalTask); // Remove scheduled task
        }
        if (tts != null && tts.isSpeaking()) {
            tts.stop(); // Stop any ongoing speech
        }
    }

    public float[] convertLineDataSetToArray(LineDataSet dataSet) {
        ArrayList<Entry> entries = (ArrayList<Entry>) dataSet.getValues();
        float[] values = new float[entries.size()];

        for (int i = 0; i < entries.size(); i++) {
            values[i] = entries.get(i).getY(); // Get the Y value of each entry
        }

        return values;
    }


}
