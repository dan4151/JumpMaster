# 🏆 JumpMaster – The Ultimate Rope Jumping Tracker
## 📱 JumpMaster: Elevate Your Jump Rope Training
JumpMaster is an Android application designed to track, analyze, and visualize jump rope sessions in real time. It integrates with an Arduino ESP32 via Bluetooth to receive raw IMU sensor data, providing real-time jump tracking, historical performance analysis, and data-driven insights to help users improve their training.

### 🔥 Key Features
Real-time Jump Tracking: Live jump count and jump rate display.
Multiple Training Modes: Freestyle and Interval-based jumping.
Voice Guidance: Audio cues for interval transitions and countdowns.
Performance Analytics: Weekly statistics, historical trends, and session comparisons.
Seamless Bluetooth Connectivity: Wireless data transfer from ESP32 to the app.
Data Storage & Export: Session logs stored in CSV files for long-term tracking.
Python-powered Analytics: Advanced data analysis via Chaquopy.
### 📂 Project Structure
The application is divided into several core components, each responsible for different functionalities:

### 🏠 Main Activity (App Entry Point)
Manages navigation between key sections: Home, Jump Session, and Statistics.  
Handles Bluetooth permissions & device selection.  
Provides a custom navigation bar for seamless navigation.  
Manages CSV data storage for weekly and all-time performance tracking.  
📊 Home Fragment  
Displays weekly jump statistics in a bar chart.  
Bars are color-coded based on performance compared to the all-time average:  
🟢 Green: Above average  
⚫ Black: At average or no data  
🔴 Red: Below average  
The chart dynamically updates every time the user opens this section.  
🎯 Jump Session Fragment  
Real-time Jump Tracking: Receives data from ESP32 via Bluetooth.  
Two Training Modes:  
Freestyle Mode: Tracks jumps continuously.  
Interval Mode: Alternates between fast and slow phases.  
Voice Guidance: Uses Text-to-Speech for countdowns and transitions.  
Dynamic Display: Shows jump count and jump rate in real time.  
Session Saving: Allows users to save their session data.  
📈 Statistics Fragment  
Session History: Users can view previous jump sessions.  
File Management: Load and delete session files.  
Adaptive UI: Detects session type and adjusts the display accordingly.  
📊 Jump Data Manager  
Handles data storage, retrieval, and updates for weekly and long-term tracking.  
Automatically creates and updates CSV files.  
Supports historical performance tracking by appending weekly data to a cumulative file.  
🔗 Serial Service (Bluetooth Communication)  
Manages the Bluetooth connection between the ESP32 and the app.  
Ensures continuous data transmission, even when the app runs in the background.  
