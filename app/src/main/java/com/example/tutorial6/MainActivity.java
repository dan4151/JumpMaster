package com.example.tutorial6;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.github.mikephil.charting.charts.BarChart;


public class MainActivity extends AppCompatActivity
        implements FragmentManager.OnBackStackChangedListener {

    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 123;
    private static final long PERMISSION_RETRY_DELAY = 5000; // 5 seconds

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isRequestingPermissions = false;

    // Keep track of whether a device has been selected
    // (If your code passes the device address around, store it here after selection)
    private String selectedDeviceAddress = null;

    private TextView homeNavItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        // Set up the toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportFragmentManager().addOnBackStackChangedListener(this);

        // Check & request permissions
        checkAndRequestPermissions();

        // Bottom navigation (3 items: Home, Jump Session, Statistics)
        setupBottomNavigation();

        // Create a file for weekly jump data if not existing
        JumpDataManager.saveWeeklyData(this);

        // Create a cumulative data if not existing and save previous week if new week
        JumpDataManager.saveCumulativeData(this);
    }

    // --------------------------------------------------
    // 1) PERMISSION LOGIC
    // --------------------------------------------------
    private String[] getRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12 and above
            return new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
            };
        } else {
            return new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN
            };
        }
    }

    private void checkAndRequestPermissions() {
        if (isRequestingPermissions) {
            return;
        }

        String[] permissions = getRequiredPermissions();
        boolean allPermissionsGranted = true;

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                Log.w(TAG, "Missing permission: " + permission);
            }
        }

        if (allPermissionsGranted) {
            Log.i(TAG, "All permissions granted");
            initializeApp();
        } else {
            Log.w(TAG, "Requesting permissions");
            isRequestingPermissions = true;
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }
    }

    private void schedulePermissionRetry() {
        handler.postDelayed(() -> {
            if (!isFinishing()) {
                isRequestingPermissions = false;
                Log.i(TAG, "Retrying permission check after delay");
                Toast.makeText(MainActivity.this,
                        "Please grant required permissions in Settings",
                        Toast.LENGTH_LONG).show();
                checkAndRequestPermissions();
            }
        }, PERMISSION_RETRY_DELAY);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        isRequestingPermissions = false;

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                Log.i(TAG, "All permissions granted in result");
                initializeApp();
            } else {
                Log.w(TAG, "Permissions denied");
                Toast.makeText(this,
                        "Bluetooth permissions are required for this app to function",
                        Toast.LENGTH_LONG).show();
                // Schedule retry if permissions are still missing
                handler.postDelayed(this::checkAndRequestPermissions, PERMISSION_RETRY_DELAY);
            }
        }
    }

    // --------------------------------------------------
    // 2) INITIAL SETUP: SHOW DEVICES FRAGMENT FIRST
    // --------------------------------------------------
    private void initializeApp() {
        Log.i(TAG, "Initializing app");
        FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag("devices") == null) {
            // If there's no DevicesFragment yet, add it
            fm.beginTransaction()
                    .add(R.id.fragment, new DevicesFragment(), "devices")
                    .commit();
        } else {
            // If already present, ensure back/up button state is correct
            onBackStackChanged();
        }
    }

    // --------------------------------------------------
    // 3) BOTTOM NAVIGATION
    // --------------------------------------------------
    private void setupBottomNavigation() {
        LinearLayout bottomNav = findViewById(R.id.custom_navigation_bar);
        if (bottomNav == null) {
            Log.w(TAG, "Custom navigation bar not found in layout.");
            return;
        }

        // Array of navigation items
        TextView[] navItems = new TextView[]{
                findViewById(R.id.nav_home),
                findViewById(R.id.nav_jump_session),
                findViewById(R.id.nav_statistics)
        };

        homeNavItem = navItems[0];

        // Click listener for navigation items
        for (TextView navItem : navItems) {
            navItem.setOnClickListener(v -> {
                // Reset backgrounds for all items
                for (TextView item : navItems) {
                    item.setBackgroundResource(android.R.color.transparent); // Clear previous highlight
                }
                if (selectedDeviceAddress != null)
                    navItem.setBackgroundResource(R.drawable.bottom_nav_indicator);

                // Handle navigation logic
                int id = navItem.getId();
                if (id == R.id.nav_home) {
                    if (selectedDeviceAddress == null) {
                        Toast.makeText(this,
                                "Please select a Bluetooth device first",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Fragment fragment = getSupportFragmentManager().findFragmentByTag("home");
                    if (fragment instanceof HomeFragment) {
                        // Call refreshChart if the fragment is already active
                        HomeFragment homeFragment = (HomeFragment) fragment;
                        BarChart barChart = homeFragment.getView().findViewById(R.id.bar_chart);
                        if (barChart != null) {
                            homeFragment.refreshChart(barChart);
                        }
                    }
                    showFragment("home");
                } else if (id == R.id.nav_jump_session) {
                    if (selectedDeviceAddress == null) {
                        Toast.makeText(this,
                                "Please select a Bluetooth device first",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    showFragment("terminal");
                } else if (id == R.id.nav_statistics) {
                    if (selectedDeviceAddress == null) {
                        Toast.makeText(this,
                                "Please select a Bluetooth device first",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    showFragment("statistics");
                }
            });
        }
    }



    /**
     * Show one of the three main fragments by tag. If that fragment doesn’t exist yet, create it.
     * We also hide the other fragments, including the DevicesFragment, so the chosen one is displayed.
     */
    private void showFragment(String tagToShow) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();

        // Hide all known fragments
        Fragment home = fm.findFragmentByTag("home");
        if (home != null) ft.hide(home);

        Fragment terminal = fm.findFragmentByTag("terminal");
        if (terminal != null) ft.hide(terminal);

        Fragment stats = fm.findFragmentByTag("statistics");
        if (stats != null) ft.hide(stats);

        // Also hide devices fragment if it exists (so the new one is visible)
        Fragment devices = fm.findFragmentByTag("devices");
        if (devices != null) ft.hide(devices);

        // Show or create the requested fragment
        switch (tagToShow) {
            case "home":
                if (home == null) {
                    home = new HomeFragment();
                    ft.add(R.id.fragment, home, "home");
                } else {
                    ft.show(home);
                }
                break;

            case "terminal":
                if (terminal == null) {
                    TerminalFragment newTerm = new TerminalFragment();
                    // Pass the selectedDeviceAddress as an argument
                    Bundle args = new Bundle();
                    args.putString("device", selectedDeviceAddress);
                    newTerm.setArguments(args);

                    ft.add(R.id.fragment, newTerm, "terminal");
                } else {
                    ft.show(terminal);
                }
                break;

            case "statistics":
                if (stats == null) {
                    stats = new StatisticsFragment();
                    ft.add(R.id.fragment, stats, "statistics");
                } else {
                    ft.show(stats);
                }
                break;
        }

        ft.commit();
    }

    /**
     * You may call this from DevicesFragment once a user picks a device,
     * so we remember the chosen device address. Then you can navigate to TerminalFragment.
     */
    public void onDeviceSelected(String deviceAddress) {
        this.selectedDeviceAddress = deviceAddress;

        // Now that a device is selected, go to the TerminalFragment
        // Or simply let user pick it from bottom nav. For example, we force it:
        showFragment("home");
        homeNavItem.setBackgroundResource(R.drawable.bottom_nav_indicator);
    }

    /**
     * If you wanted to re-show DevicesFragment from code, here's an example:
     */
    public void showDevicesFragmentAgain() {
        // Hide other fragments, show the devices
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();

        // Hide the "home", "terminal", "statistics" if they exist
        Fragment home = fm.findFragmentByTag("home");
        if (home != null) ft.hide(home);

        Fragment terminal = fm.findFragmentByTag("terminal");
        if (terminal != null) ft.hide(terminal);

        Fragment stats = fm.findFragmentByTag("statistics");
        if (stats != null) ft.hide(stats);

        Fragment devices = fm.findFragmentByTag("devices");
        if (devices != null) {
            ft.show(devices);
        } else {
            ft.add(R.id.fragment, new DevicesFragment(), "devices");
        }
        ft.commit();
    }

    // --------------------------------------------------
    // 4) BACK STACK & ACTIVITY LIFECYCLE
    // --------------------------------------------------
    @Override
    public void onBackStackChanged() {
        // Show Up button if there's something on the back stack
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(
                    getSupportFragmentManager().getBackStackEntryCount() > 0
            );
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
