package fr.pmmh.gobannos

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import fr.pmmh.gobannos.storers.LocationStorer
import fr.pmmh.gobannos.storers.SensorStorer
import fr.pmmh.gobannos.storers.SensorTrigger
import fr.pmmh.gobannos.storers.UsbStorer
import fr.pmmh.gobannos.ui.theme.GobannosTheme
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread


class MainActivity : ComponentActivity() {
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private var kickTimeMicroSSinceBoot: MutableState<Long> = mutableLongStateOf(SystemClock.elapsedRealtimeNanos()/1000)
    private val dumpFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath + "/Gobannos")
    private var fileSpanTimeMinutes = 15
    private var cleanFilesReady = false
    private val globalStatus = mutableStateOf("STOPPED")
    private val calibrationText = mutableStateOf("not calibrated")
    private lateinit var calibrationListener: SensorTrigger

    private val accelerationText = mutableStateOf("")
    private lateinit var accelerationSensor: Sensor
    private lateinit var accelerationListener: SensorStorer

    private val gyroscopeText = mutableStateOf("")
    private lateinit var gyroscopeSensor: Sensor
    private lateinit var gyroscopeListener: SensorStorer

    private val magneticText = mutableStateOf("")
    private lateinit var magneticSensor: Sensor
    private lateinit var magneticListener: SensorStorer

    private lateinit var locationManager: LocationManager
    private lateinit var locationListener: LocationStorer

    private var usbManager: UsbManager? = null
    private var usbDevice: UsbDevice? = null
    private var usbSerialPort: UsbSerialPort? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var serialIoManager: SerialInputOutputManager? = null
    //private var isReceiverRegistered = false
    private var baudRate: Int = 115200
    private val usbText = mutableStateOf("")
    private val connectionUSB = mutableStateOf("no usb available")
    private var usbListener: UsbStorer? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        val ipAddress = getLocalIpAddress(context = this) ?: "Non disponible"
        enableEdgeToEdge()
        setContent {
            GobannosTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column {
                        Text(
                            text = "V1 Status : ${globalStatus.value}",
                            modifier = Modifier.padding(innerPadding)
                        )
                        Text(
                            text = "IP Locale: $ipAddress",
                        )
                        Text(
                            text = "USB status: ${connectionUSB.value}",
                        )
                        Text(
                            text = "Calibration : ${calibrationText.value}",
                        )

                        Text(
                            text = "Accelerometer : ${accelerationText.value}",
                        )
                        Text(
                            text = "Gyroscope : ${gyroscopeText.value}",
                        )
                        Text(
                            text = "Magnetometer : ${magneticText.value}",
                        )
                        Text(
                            text = "USB : ${usbText.value}",
                        )
                    }
                }
            }
        }

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Keep awake with wakelock
        val wakeLock: PowerManager.WakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Gobannos::WakeLock").apply {
                    acquire()
                }
            }

        accelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: throw Exception("boom")
        val accelerationSize = Math.floorDiv(fileSpanTimeMinutes*60*1000*1000, accelerationSensor.minDelay) // minDelay is in micros
        accelerationListener = SensorStorer(accelerationSize, accelerationSensor.stringType, accelerationText, dumpFolder)
        
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            ?: throw Exception("boom")
        val gyroscopeSize = Math.floorDiv(fileSpanTimeMinutes*60*1000*1000, gyroscopeSensor.minDelay) // minDelay is in micros
        gyroscopeListener = SensorStorer(gyroscopeSize, gyroscopeSensor.stringType, gyroscopeText, dumpFolder)

        magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            ?: throw Exception("boom")
        val magneticSize = Math.floorDiv(fileSpanTimeMinutes*60*1000*1000, magneticSensor.minDelay) // minDelay is in micros
        magneticListener = SensorStorer(magneticSize, magneticSensor.stringType, magneticText, dumpFolder)

        calibrationListener = SensorTrigger(kickTimeMicroSSinceBoot, calibrationText)
        sensorManager.registerListener(calibrationListener, accelerationSensor, SensorManager.SENSOR_DELAY_FASTEST)

        sensorManager.registerListener(accelerationListener, accelerationSensor, SensorManager.SENSOR_DELAY_FASTEST)
        sensorManager.registerListener(gyroscopeListener, gyroscopeSensor, SensorManager.SENSOR_DELAY_FASTEST)
        sensorManager.registerListener(magneticListener, magneticSensor, SensorManager.SENSOR_DELAY_FASTEST)

        registerLocation(fileSpanTimeMinutes)
        registerUsb(fileSpanTimeMinutes)

        if (!dumpFolder.exists()) {
            dumpFolder.mkdirs()
        }
        startKtorServer()
    }

    private fun getLocalIpAddress(context: Context): String? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return null
        val linkProperties: LinkProperties = connectivityManager.getLinkProperties(network) ?: return null

        for (linkAddress in linkProperties.linkAddresses) {
            val address = linkAddress.address
            if (address is Inet4Address) { // Filtrer seulement IPv4
                return address.hostAddress
            }
        }
        return null
    }

    // usb stuff
    companion object {
        private const val ACTION_USB_PERMISSION = "fr.pmmh.gobannos.USB_PERMISSION"
        private const val TAG = "UsbSerialActivity"
    }

    private fun registerUsb (fileSpanTimeMinutes: Int) {

        try {
            usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

            // Enregistrer le récepteur pour différentes actions USB
            val filter = IntentFilter().apply {
                addAction(ACTION_USB_PERMISSION)
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(usbPermissionReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(usbPermissionReceiver, filter)
            }
            //isReceiverRegistered = true

            // Vérifier si l'activité a été lancée par une connexion USB
            val intent = intent
            if (intent != null && UsbManager.ACTION_USB_DEVICE_ATTACHED == intent.action) {
                Log.d(TAG, "Activité lancée par connexion USB")
                connectionUSB.value = "Activité lancée par connexion USB"
            }

            // Chercher les appareils USB disponibles
            findUsbDevice()

        } catch (e: Exception) {
            Log.e(TAG, "Erreur dans onCreate: ${e.message}", e)
            connectionUSB.value = "Erreur dans onCreate" + e.message
        }

    }

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (ACTION_USB_PERMISSION == action) {
                synchronized(this) {
                    // Utiliser getParcelableExtra avec type explicite pour compatibilité
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let {
                            Log.d(TAG, "Permission accordée pour l'appareil USB")
                            connectionUSB.value = "Permission accordée pour l'appareil USB"
                            // Permission accordée, on peut ouvrir la connexion
                            connectToDevice(it)
                        }
                    } else {
                        // Permission refusée par l'utilisateur
                        Log.d(TAG, "Permission refusée pour l'appareil USB")
                        connectionUSB.value = "Permission refusée pour l'appareil USB"
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED == action) {
                Log.d(TAG, "Appareil USB connecté")
                connectionUSB.value = "Appareil USB connecté"
                findUsbDevice()
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
                // Utiliser getParcelableExtra avec type explicite pour compatibilité
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
                device?.let {
                    if (it == usbDevice) {
                        Log.d(TAG, "Appareil USB déconnecté")
                        connectionUSB.value = "Appareil USB déconnecté"
                        closeConnection()
                    }
                }
            }
        }
    }

    private fun findUsbDevice() {
        try {
            // Obtenir la liste des drivers disponibles
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

            if (availableDrivers.isEmpty()) {
                Log.d(TAG, "Aucun périphérique USB série trouvé")
                connectionUSB.value = "Aucun périphérique USB série trouvé"
                return
            }

            // Utiliser le premier driver trouvé
            val driver: UsbSerialDriver = availableDrivers[0]
            usbDevice = driver.device

            Log.d(TAG, "Périphérique USB trouvé: ${usbDevice?.deviceName}")

            // Vérifier si on a déjà la permission
            if (usbManager!!.hasPermission(usbDevice)) {
                Log.d(TAG, "Permission déjà accordée, connexion...")
                connectionUSB.value = "Permission déjà accordée, connexion..."
                connectToDevice(usbDevice!!)
            } else {
                // Demander la permission
                requestUsbPermission(usbDevice!!)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur dans findUsbDevice: ${e.message}", e)
            connectionUSB.value = "Erreur dans findUsbDevice"
        }
    }

    private fun requestUsbPermission(device: UsbDevice) {
        try {
            val permissionIntent = PendingIntent.getBroadcast(
                this,
                0,
                Intent(ACTION_USB_PERMISSION),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                else
                    PendingIntent.FLAG_UPDATE_CURRENT
            )

            usbManager!!.requestPermission(device, permissionIntent)
            Log.d(TAG, "Demande de permission USB envoyée")
            connectionUSB.value = "Demande de permission USB envoyée"
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la demande de permission: ${e.message}", e)
            connectionUSB.value = "Erreur lors de la demande de permission"
        }
    }

    private fun connectToDevice(device: UsbDevice) {
        try {
            // Obtenir la connexion
            val connection = usbManager!!.openDevice(device)
            if (connection == null) {
                Log.e(TAG, "Impossible d'ouvrir la connexion à l'appareil")
                connectionUSB.value = "Impossible d'ouvrir la connexion à l'appareil"
                return
            }

            // Trouver le driver pour l'appareil
            val driver = UsbSerialProber.getDefaultProber().probeDevice(device)

            if (driver == null) {
                Log.e(TAG, "Driver introuvable pour cet appareil")
                connectionUSB.value = "Driver introuvable pour cet appareil"
                return
            }

            // Ouvrir le premier port
            try {
                usbSerialPort = driver.ports[0]
                usbConnection = connection

                usbSerialPort?.open(connection)
                usbSerialPort?.setParameters(baudRate, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

                Log.d(TAG, "Connexion USB établie avec succès")
                connectionUSB.value = "Connexion USB établie avec succès"

                if (usbSerialPort != null) {
                    connectionUSB.value = "Connexion USB établie avec succès + usbSerialPort"
                }

                // espace dispo pour l'USB 200 bytes par secondes
                usbListener = UsbStorer(usbSerialPort!!,fileSpanTimeMinutes*60*200, "Usb",  usbText, dumpFolder)

                serialIoManager = SerialInputOutputManager(usbSerialPort, usbListener)
                serialIoManager?.start()


            } catch (e: IOException) {
                Log.e(TAG, "Erreur lors de l'ouverture du port USB: ${e.message}", e)
                connectionUSB.value = "Erreur lors de l'ouverture du port USB" + e.message
                closeConnection()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur dans connectToDevice: ${e.message}", e)
            connectionUSB.value = "Erreur dans connectToDevice" + e.message
        }
    }

    private fun sendCommand(command: String): Boolean {
        return try {
            val data = command.toByteArray(Charsets.UTF_8)
            usbSerialPort?.write(data, 1000) // timeout en ms
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }


    private fun closeConnection() {
        try {
            serialIoManager?.stop()
            usbSerialPort?.close()
        } catch (e: IOException) {
            // Ignorer
        } finally {
            usbSerialPort = null
            serialIoManager = null
            usbConnection = null
        }

    }

// GPS
    private fun registerLocation(fileSpanTimeMinutes: Int) {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val locationSize = fileSpanTimeMinutes*60 // 1 point per second.
        locationListener = LocationStorer(locationSize, "gps", dumpFolder)
        while (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            val locationPermissionRequest = registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                when {
                    permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                        // Precise location access granted.
                    }
                    permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                        // Only approximate location access granted.
                    } else -> {
                    // No location access granted.
                }
                }
            }
            locationPermissionRequest.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION))
        }

        // Tried to ask for background loc permission. Does not work very well, app has to reboot to ask for permissions..
//        while (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
//        ) {
//            val locationPermissionRequest = registerForActivityResult(
//                ActivityResultContracts.RequestMultiplePermissions()
//            ) { permissions ->
//                when {
//                    permissions.getOrDefault(Manifest.permission.ACCESS_BACKGROUND_LOCATION, false) -> {
//                        // background location access granted.
//                    } else -> {
//                    // No location access granted.
//                }
//                }
//            }
//            locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
//        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0F, locationListener)
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        val sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorManager.unregisterListener(accelerationListener)
        sensorManager.unregisterListener(gyroscopeListener)
        sensorManager.unregisterListener(magneticListener)
//        locationManager.loc(locationListener)
        server?.stop(1000, 2000)
        if (usbSerialPort != null) {
            closeConnection()
        }
    }

    private fun syncUdp(port: Int) {
        val timeout: Int = 99999
        val listenPort: Int = port
        val sendPort: Int = port + 1
        thread {
            globalStatus.value = "UDP CALIB"
            try {
                UdpServer(listenPort, timeout).use { udpServer ->
                    UdpClient(sendPort, timeout) .use { udpClient -> // Todo : might be initialized (inc. address) on the first "0" packet
                        var answerAndSource = UdpServer.AnswerAndSource(0, InetAddress.getLocalHost()) // 0:continue ; 1:respond ; 2:stop
                        while (answerAndSource.answer != 2) {
                            answerAndSource = udpServer.receiveAsInt(); // this is blocking
                            if (answerAndSource.answer == 1) {
                                udpClient.send(SystemClock.elapsedRealtimeNanos(), answerAndSource.address)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
            globalStatus.value = "UDP CALIB FINISHED"
        }
    }

    // returns true if the cleaning was done
    private fun cleanFilesWithConfirmation() : Boolean {
        if (cleanFilesReady) {
            cleanFilesReady = false
            dumpFolder.listFiles()?.forEach { file ->
                if (file.isFile) {
                    file.delete()
                }
            }
            return true
        } else {
            cleanFilesReady = true
            // Auto-reverting to a safe state
            thread {
                Thread.sleep(10000)
                cleanFilesReady = false
            }
        }
        return false
    }

    private fun allowOff() {
        runOnUiThread {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private fun startKtorServer() {
        CoroutineScope(Dispatchers.IO).launch {
            server = embeddedServer(Netty, port = 8080) {
                routing {
                    get("/start") {
                        val name: String? = call.request.queryParameters["name"]
                        val date: String =
                            SimpleDateFormat("yyyy-MM-dd'T'HH_mm_ss", Locale.US).format(
                                Date()
                            )
                        val experimentName: String = if (name != null) "$name-$date" else date
                        globalStatus.value = "RECORDING"
                        accelerationListener.start(experimentName)
                        gyroscopeListener.start(experimentName)
                        magneticListener.start(experimentName)
                        locationListener.start(experimentName)
                        usbListener?.start(experimentName)
                        call.respondText("Recording experiment $experimentName, please stop me")
                    }
                    get("/stop") {
                        globalStatus.value = "STOPPED"
                        accelerationListener.stop()
                        gyroscopeListener.stop()
                        magneticListener.stop()
                        locationListener.stop()
                        usbListener?.stop()
                        call.respondText("Finished")
                    }
                    get("/status") {
                        call.respondText(globalStatus.value)
                    }
                    get("/kick-sync") {
                        val threshold: Float? = call.request.queryParameters["threshold"]?.toFloat()
                        if (threshold == null) {
                            kickTimeMicroSSinceBoot.value =
                                SystemClock.elapsedRealtimeNanos() // in theory
                        } else {
                            calibrationListener.start(threshold)
                        }
                        call.respondText("Calibrating, please kick me")
                    }
                    get("/kick-time") {
                        call.respondText(kickTimeMicroSSinceBoot.value.toString())
                    }
                    get("/udp-sync") {
                        val port: Int? = call.request.queryParameters["port"]?.toIntOrNull()
                        syncUdp(port ?: 5000)
                        call.respondText("Syncing via udp")
                    }
                    get("/test") {
                        calibrationListener.startTest()
                        call.respondText("Testing, please kick me")
                    }
                    get("/time") {
                        var resp = "System.nanoTime: ${System.nanoTime()}\n" +
                                    "Date (millis): ${Date().time}\n" +
                                    "System.currentTimeMillis: ${System.currentTimeMillis()}\n" +
                                    "SystemClock.elapsedRealtimeNanos: ${SystemClock.elapsedRealtimeNanos()}\n"
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) try {
                            resp += "SystemClock.currentGnssTimeClock: ${SystemClock.currentGnssTimeClock().instant().nano}\n"
                        } catch (_: Exception) {}
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) try {
                            resp += "SystemClock.currentNetworkTimeClock: ${SystemClock.currentNetworkTimeClock().instant().nano}\n"
                        } catch (_: Exception) {}
                        call.respondText(resp)
                    }
                    get("/screen-off") {
                        allowOff()
                        call.respondText("OK, will let the screen shut off automatically")
                    }
                    get("/list-files") {
                        val files = dumpFolder.listFiles()
                            ?.filter { it.isFile }
                            ?.map { it.name }
                            ?.toTypedArray()
                            ?: emptyArray()
                        call.respondText("[" + files.joinToString(", ") + "]")
                    }
                    get("/get-file/{fileName}") {
                        val fileName: String? = call.parameters["fileName"]
                        if (fileName == null) {
                            call.respondText("Hey, I need a file name !")
                        } else {
                            call.respondFile(dumpFolder, fileName)
                        }
                    }
                    get("/clean-files") {
                        if (cleanFilesWithConfirmation()) {
                            call.respondText("All files in ${dumpFolder.path} have been deleted.")
                        } else {
                            call.respondText("Cleaning not done, please confirm you want to delete ALL files in ${dumpFolder.path}  retry within 10s.")
                        }
                    }
                    get("/usb-cmd/{usbCmd}") {
                        val usbCmd: String? = call.parameters["usbCmd"]
                        if (usbCmd == null) {
                            Log.e(TAG, "Null USB command!")
                            usbText.value = "Null USB command!"
                            call.respondText("Null USB command!")
                        } else {
                            if (sendCommand(usbCmd)) {
                                Log.e(TAG, "command : '$usbCmd' sent")
                                usbText.value = "command : '$usbCmd' sent"
                                call.respondText("command : '$usbCmd' sent")
                            } else  {
                                Log.e(TAG, "Error command : '$usbCmd' not send")
                                usbText.value = "Error command : '$usbCmd' not send"
                                call.respondText("Error command : '$usbCmd' not send")
                            }
                        }
                    }
                }
            }.start(wait = false)
        }
    }
}