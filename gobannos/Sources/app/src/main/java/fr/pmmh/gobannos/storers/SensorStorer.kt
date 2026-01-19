package fr.pmmh.gobannos.storers

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import androidx.compose.runtime.MutableState
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class SensorStorer(
    private val size: Int,
    private val type: String,
    private var feedback: MutableState<String>,
    private val dumpFolder: File
) : SensorEventListener {
    class SensorValues(val size: Int) {
        var timestamps : LongArray = LongArray(size) {_ -> -1}
        var x : FloatArray = FloatArray(size) {_ -> Float.NaN}
        var y : FloatArray = FloatArray(size) {_ -> Float.NaN}
        var z : FloatArray = FloatArray(size) {_ -> Float.NaN}
    }

    private var values1: SensorValues = SensorValues(size)
    private var values2: SensorValues = SensorValues(size)
    private var values: SensorValues = values1
    private var idx: Int = 0
    private var numWritten = 0
    private var respond = false
    private var experimentName: String = SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss", Locale.US).format(
        Date()
    )

    override fun onSensorChanged(event: SensorEvent) {
        if (!respond) {
            return
        }

        if (idx == size) {
            switchAndDump()
            idx = 0
        }

        val timestamp = event.timestamp // ns of uptime
        val storedTimestamp = timestamp/1000 // micros
        values.timestamps[idx] = storedTimestamp
        values.x[idx] = event.values[0]
        values.y[idx] = event.values[1]
        values.z[idx] = event.values[2]
        idx++

        if (idx % 1000 == 0) {
            feedback.value = "\nt: $storedTimestamp micros\n" +
                    "      ${event.values[0]}\n" +
                    "      ${event.values[1]}\n" +
                    "      ${event.values[2]} "
        }
    }

    private fun switchAndDump() {
        if (numWritten % 2 == 0) {
            values = values2
            numWritten++
            dump(values1, numWritten) {
                values1 = SensorValues(size)
            }
        } else {
            values = values1
            numWritten++
            dump(values2, numWritten) {
                values2 = SensorValues(size)
            }
        }
    }

    private fun dump(valuesToDump: SensorValues, numWritten: Int, reset: () -> Unit) {
        thread {
            val start = valuesToDump.timestamps[0]
            val end = valuesToDump.timestamps[values1.size - 1]
            FileOutputStream(dumpFolder.absolutePath + "/$experimentName-$type-$numWritten-$start-$end.csv").apply {
                writeCsv(
                    valuesToDump
                )
            }
            reset()
        }
    }

    private fun OutputStream.writeCsv(values: SensorValues) {
        val nbLines = values.size - 1
        bufferedWriter().use{ writer ->
            writer.write("${values.timestamps[0]}, ${values.x[0]}, ${values.y[0]}, ${values.z[0]}, ")
            for (i in 1..nbLines) {
                writer.newLine()
                writer.write("${values.timestamps[i]}, ${values.x[i]}, ${values.y[i]}, ${values.z[i]}, ")
            }
            writer.flush() // force flush to disk
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Do something here if sensor accuracy changes.
    }

    public fun start(experimentName: String) {
        this.experimentName = experimentName
        values1 = SensorValues(size)
        values2 = SensorValues(size)
        values = values1
        idx = 0;
        numWritten = 0
        respond = true
    }

    public fun stop(){
        respond = false
        switchAndDump()
    }
}