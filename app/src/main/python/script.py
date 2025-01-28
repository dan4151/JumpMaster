import pandas as pd
import numpy as np
from scipy.signal import find_peaks
from datetime import datetime
import json



def open_csv(csv_path):
    with open(csv_path, 'r') as file:
        lines = file.readlines()
        file.close()
    lines = [l.strip().split(",") for l in lines]
    header = [c.strip() for c in lines[5]]
    name = lines[0][1].strip()
    time = datetime.strptime(lines[1][1].strip(), "%Y-%m-%dT%H:%M:%S.%f")
    activity = lines[2][1].strip()
    num_steps = int(lines[3][1].strip())
    lines = lines[6:]
    df = pd.DataFrame(lines, columns=header)
    csv_dict = {'df': df, 'name': name, "time": time, "activity": activity, "num_steps": num_steps}
    return csv_dict



def smooth_and_find_extrema(z_arr, time_arr, if_statistics=False ,window_size=5, peak_prominence=0.05, peak_thresh=0.55,
                            vally_thresh=0.45, normalize=True):
    """
    Smooth a series using rolling mean and find peaks and valleys.

    Parameters:
        df (pd.DataFrame): Input DataFrame containing the data.
        time_col (str): Name of the time column.
        y_col (str): Name of the y-axis column to analyze.
        window_size (int): Size of the rolling window for smoothing.
        peak_prominence (float): Minimum prominence for detecting peaks and valleys.

    Returns:
        smoothed_series (pd.DataFrame): DataFrame with smoothed values.
        peaks (list): Indices of peaks in the original series.
        valleys (list): Indices of valleys in the original series.
    """

    # Smooth the series using rolling mean
    time_col = "Time [sec]"
    y_col="z"
    if not if_statistics:
        z_arr = json.loads(z_arr)
        time_arr = json.loads(time_arr)
        print("z_arr", z_arr)
        print("time_arr", time_arr)
    smoothed_series = pd.DataFrame({time_col: time_arr, y_col: z_arr}).astype(float)

    if normalize:
        smoothed_series[y_col] = (smoothed_series[y_col] - min(smoothed_series[y_col])) / (
                max(smoothed_series[y_col]) - min(smoothed_series[y_col]))
    smoothed_series[y_col] = smoothed_series[y_col].rolling(window=window_size, center=True).mean()

    # Find peaks in the smoothed series
    peaks, _ = find_peaks(smoothed_series[y_col], prominence=peak_prominence)
    # Find valleys by inverting the y_col values
    valleys, _ = find_peaks(-smoothed_series[y_col], prominence=peak_prominence)
    peaks = [p for p in peaks if smoothed_series[y_col].iloc[p] >= peak_thresh]
    valleys = [v for v in valleys if smoothed_series[y_col].iloc[v] <= vally_thresh]
    total_time = smoothed_series["Time [sec]"].iloc[-1] - smoothed_series["Time [sec]"].iloc[0]
    rate_of_jumps = len(peaks) / total_time
    print("peaks: ", len(peaks))
    return {"jumps": len(peaks),
            "rate_of_jumps": rate_of_jumps,
                   }



def create_statistics(csv_path, interval="interval"):
    data_dict = open_csv(csv_path)
    data = data_dict['df'][['Time [sec]',"ACC Z", 'Phase']]
    activity = data_dict['activity']

    print("interval=", interval)
    if activity == "interval":
        print("data2=", data)
        slow_data=data[data['Phase'] =='Slow']
        fast_data =data[data['Phase'] =='Fast']
        print("slow_data=", slow_data)
        print("fast_data=", fast_data)


        slow_time, slow_z = list(slow_data['Time [sec]']),list(slow_data['ACC Z'])
        fast_time, fast_z = list(fast_data['Time [sec]']),list(fast_data['ACC Z'])
        print("slow_time=", slow_z)
        slow_result_dict = smooth_and_find_extrema(slow_z, slow_time, True)
        slow_jumps, slow_avg_speed = slow_result_dict["jumps"], slow_result_dict["rate_of_jumps"]
        fast_result_dict = smooth_and_find_extrema(fast_z, fast_time, True)
        fast_jumps, fast_avg_speed = fast_result_dict["jumps"], fast_result_dict["rate_of_jumps"]

        return {"jumping_type": "interval",
                "total_jumps": slow_jumps + fast_jumps,
                "avg_speed": 0,
                "slow_jumps": slow_jumps,
                "fast_jumps": fast_jumps,
                "slow_avg_speed": slow_avg_speed,
                "fast_avg_speed": fast_avg_speed}
    else:
        time, z = list(data['Time [sec]']),list(data['ACC Z'])
        result_dict = smooth_and_find_extrema(z, time, True)
        jumps, avg_speed = result_dict["jumps"], result_dict["rate_of_jumps"]


    return {"jumping_type": "freestyle",
                "total_jumps": jumps,
                "avg_speed": avg_speed,
                "slow_jumps": None,
                "fast_jumps": None,
                "slow_avg_speed": None,
                "fast_avg_speed": None}

