package fr.pmmh.gobannos.storers

import android.os.SystemClock
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import androidx.compose.runtime.MutableState
import java.io.File
import java.io.IOException

class UsbStorer(
    private val usbSerialPort: UsbSerialPort,
    private val size: Int,
    private val type: String,
    private var feedback: MutableState<String>,
    private val dumpFolder: File
)  : SerialInputOutputManager.Listener {

    companion object {
        private const val TAG = "UsbStorer"
        private const val WRITE_TIMEOUT_MS = 1000
    }

    class SensorValues(val size: Int) {
        var timestamps : LongArray = LongArray(size) {_ -> -1}
        var x : ByteArray = ByteArray(size) { _ -> -1}
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

    /**
     * Implémentation des méthodes de SerialInputOutputManager.Listener
     */
    override fun onNewData(data: ByteArray) {
        if (!respond) {
            return
        }

        if (idx == size) {
            switchAndDump()
            idx = 0
        }

        var storedTimestamp: Long = 0
        for (value in data) {
            val timestamp = SystemClock.elapsedRealtimeNanos() // ns of uptime
            storedTimestamp = timestamp/1000 // micros
            values.timestamps[idx] = storedTimestamp
            values.x[idx] = value
            idx++
        }

        if (idx % 10 == 0) {
            feedback.value = "\nt: $storedTimestamp micros\n" +
                    "      ${data}\n"
        }
    }

    override fun onRunError(e: Exception) {
        Log.e(TAG, "Erreur de communication: ${e.message}")
        feedback.value = "Erreur de communication: " + e.message
    }


    fun writeData(data: String) {
        if (usbSerialPort == null || !usbSerialPort.isOpen) {
            Log.e(TAG, "Port série USB non disponible ou fermé")
            return
        }

        try {
            val bytes = data.toByteArray()
            val bytesWritten = usbSerialPort.write(bytes, WRITE_TIMEOUT_MS)
            Log.d(TAG, "Données écrites ($bytesWritten octets): $data")
        } catch (e: IOException) {
            Log.e(TAG, "Erreur lors de l'écriture des données: ${e.message}")
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
            writer.write("${values.timestamps[0]}, ${values.x[0]}, ")
            for (i in 1..nbLines) {
                writer.newLine()
                writer.write("${values.timestamps[i]}, ${values.x[i]}, ")
            }
            writer.flush() // force flush to disk
        }
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

    fun stop() {
        respond = false
        switchAndDump()
    }

}

