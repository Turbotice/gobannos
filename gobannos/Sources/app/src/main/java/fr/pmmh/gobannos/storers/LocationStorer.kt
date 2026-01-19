package fr.pmmh.gobannos.storers

import android.location.Location
import android.location.LocationListener
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread


class LocationStorer(
    private val size: Int,
    private val type: String,
    private val dumpFolder: File
) : LocationListener {
    class LocationValues(val size: Int) {
        var timestamps : LongArray = LongArray(size) {_ -> -1}
        var x : DoubleArray = DoubleArray(size) {_ -> Double.NaN}
        var y : DoubleArray = DoubleArray(size) {_ -> Double.NaN}
        var z : DoubleArray = DoubleArray(size) {_ -> Double.NaN}
    }

    private var values1: LocationValues = LocationValues(size)
    private var values2: LocationValues = LocationValues(size)
    private var values: LocationValues = values1
    private var idx: Int = 0
    private var numWritten = 0
    private var respond = false
    private var experimentName: String = SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss", Locale.US).format(
        Date()
    )


    override fun onLocationChanged(location: Location) {
        if (!respond) {
            return
        }

        if (idx == size) {
            switchAndDump()
            idx = 0
        }

        val timestamp = location.elapsedRealtimeNanos // ns of uptime
        val storedTimestamp = timestamp/1000 // micros
        values.timestamps[idx] = storedTimestamp
        values.x[idx] = location.latitude
        values.y[idx] = location.longitude
        values.z[idx] = location.altitude
        idx++
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}


    private fun switchAndDump() {
        if (numWritten % 2 == 0) {
            values = values2
            numWritten++
            dump(values1, numWritten) {
                values1 = LocationValues(size)
            }
        } else {
            values = values1
            numWritten++
            dump(values2, numWritten) {
                values2 = LocationValues(size)
            }
        }
    }

    private fun dump(valuesToDump: LocationValues, numWritten: Int, reset: () -> Unit) {
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

    private fun OutputStream.writeCsv(values: LocationValues) {
        val nbLines = values.size - 1
        bufferedWriter().use { writer ->
            writer.write("${values.timestamps[0]}, ${values.x[0]}, ${values.y[0]}, ${values.z[0]}, ")
            for (i in 1..nbLines) {
                writer.newLine()
                writer.write("${values.timestamps[i]}, ${values.x[i]}, ${values.y[i]}, ${values.z[i]}, ")
            }
            writer.flush() // force flush to disk
        }
    }

    public fun start(experimentName: String) {
        this.experimentName = experimentName
        this.experimentName = experimentName
        values1 = LocationValues(size)
        values2 = LocationValues(size)
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