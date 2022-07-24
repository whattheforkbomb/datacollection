package io.esense.esenselib;

import com.whattheforkbomb.collection.data.ESenseEvent;

public interface ESenseSensorListener {
    /**
     * Called when there is new sensor data available
     * @param evt object containing the sensor samples received
     */
    void onSensorChanged(ESenseEvent evt);
}
