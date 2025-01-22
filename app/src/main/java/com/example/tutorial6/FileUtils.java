package com.example.tutorial6;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import java.io.File;
import java.util.List;

public class FileUtils {
    private static File storageDir = null;
    private static final String APP_FOLDER_NAME = "Tutorial6_CSV";

    public static synchronized File getStorageDirectory(Context context) {
        if (storageDir != null) {
            return storageDir;
        }

        // Try to find SD card using StorageManager (most reliable method)
        StorageManager storageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                List<StorageVolume> volumes = storageManager.getStorageVolumes();
                for (StorageVolume volume : volumes) {
                    if (!volume.isPrimary() && volume.isRemovable()) {
                        // Found SD card
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            File sdCard = volume.getDirectory();
                            if (sdCard != null && sdCard.canWrite()) {
                                storageDir = new File(sdCard, APP_FOLDER_NAME);
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // If no SD card found or not accessible, use public Documents directory
        if (storageDir == null) {
            storageDir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS), APP_FOLDER_NAME);
        }

        // Create directory if it doesn't exist
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }

        return storageDir;
    }

    public static File getCSVFile(Context context) {
        return new File(getStorageDirectory(context), "data.csv");
    }

    public static void resetStorageDir() {
        storageDir = null;
    }

    // Optional: Method to get storage info
    public static String getStorageInfo(Context context) {
        File dir = getStorageDirectory(context);
        return "Storage location: " + dir.getAbsolutePath() +
                "\nWritable: " + dir.canWrite() +
                "\nFree space: " + dir.getFreeSpace() / (1024 * 1024) + " MB";
    }
}