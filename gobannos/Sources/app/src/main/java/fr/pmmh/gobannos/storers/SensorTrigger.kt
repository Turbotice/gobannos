package fr.pmmh.gobannos.storers

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import androidx.compose.runtime.MutableState
import kotlin.math.abs
import kotlin.math.max


class SensorTrigger (
    private val originTimeMicroSSinceBoot: MutableState<Long>,
    private var feedback: MutableState<String>
) : SensorEventListener {
    private var calibrating = false
    private var testing = false
    private var threshold : Float = 0f

    override fun onSensorChanged(event: SensorEvent) {
        if (!(calibrating || testing)) {
            return
        }

        val timestamp = event.timestamp / 1000 // micros  of uptime
        val max = max(abs(event.values[0]), abs(event.values[1]))
        if (max > threshold ) {
            if (calibrating) {
                originTimeMicroSSinceBoot.value =  timestamp
                feedback.value = "calibrated with threshold: $threshold"
                calibrating = false
            }
            if (testing) {
                val delay = timestamp - originTimeMicroSSinceBoot.value
                feedback.value = "calibrated with threshold: $threshold\n" +
                        "Test : \n" +
                        "time since calibration: $delay micros\n" +
                        "trigger x/y acceleration: $max"
                testing = false
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Do something here if sensor accuracy changes.
    }

    public fun start(threshold: Float) {
        feedback.value = "calibrating..."
        this.threshold = threshold
        calibrating = true
    }

    public fun startTest() {
        feedback.value = "calibrated with threshold: $threshold\n" +
                "testing..."
        testing = true
    }

    public fun stop() {
        calibrating = false
    }
}
