package edu.unt.nslab.butshuti.bluetoothvpn.utils;

/**
 * Created by butshuti on 1/18/18.
 */

public class MovingAverage {

    private static final int DEFAULT_WINDOW_SIZE = 10;
	public final int windowSize;

	private float[] values;
    private double sum;
	private int currentIndex;

	public MovingAverage(int historyLength, float staticResetVal){
        if(historyLength == 0){
            historyLength = DEFAULT_WINDOW_SIZE;
        }
        windowSize = historyLength;
        values = new float[historyLength];
        resetTo(staticResetVal);
    }

	public MovingAverage(int historyLength) {
        this(historyLength, 0);
	}

    private void resetTo(float val){
        for (int i = 0; i < windowSize; i++) {
            values[i] = val;
        }
        sum = val * windowSize;
        currentIndex = 0;
    }

	public MovingAverage pushValue(float value) {
            sum -= values[currentIndex];
            values[currentIndex] = value;
            sum += values[currentIndex];
            currentIndex = (currentIndex + 1) % windowSize;
            if (currentIndex == 0) {
                sum = 0;
                for (int i = 0; i < windowSize; i++) {
                    sum += values[i];
                }
            }
        return this;
	}

	public float getAverage() {
		return (float)(sum / windowSize);
	}
}
