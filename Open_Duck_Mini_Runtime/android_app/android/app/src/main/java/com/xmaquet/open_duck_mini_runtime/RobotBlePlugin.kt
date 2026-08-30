package com.xmaquet.open_duck_mini_runtime

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.getcapacitor.JSObject
import com.getcapacitor.Logger
import com.getcapacitor.PermissionState
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

@CapacitorPlugin(
    name = "RobotBle",
    permissions = [
        Permission(
            strings = [
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ],
            alias = "bluetooth"
        ),
        Permission(
            strings = [Manifest.permission.ACCESS_FINE_LOCATION],
            alias = "location"
        )
    ]
)
class RobotBlePlugin : Plugin() {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val defaultServiceUuid = UUID.fromString("12345678-1234-5678-1234-56789abcdef0")
    private val defaultTxUuid = UUID.fromString("12345678-1234-5678-1234-56789abcdef1")
    private val defaultRxUuid = UUID.fromString("12345678-1234-5678-1234-56789abcdef2")

    private var serviceUuid: UUID = defaultServiceUuid
    private var txUuid: UUID = defaultTxUuid
    private var rxUuid: UUID = defaultRxUuid

    private var autoReconnect: Boolean = true
    private var targetAddress: String? = null

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null

    private var gatt: BluetoothGatt? = null
    private var txChar: BluetoothGattCharacteristic? = null
    private var rxChar: BluetoothGattCharacteristic? = null

    private var mtu: Int = 23

    private val writeQueue: ConcurrentLinkedQueue<ByteArray> = ConcurrentLinkedQueue()
    private val pendingPayloads: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()
    private var writing: Boolean = false
    private var writeEpoch: Int = 0
    private var rxReady: Boolean = false
    private var cccdPending: Boolean = false
    private var cccdEpoch: Int = 0
    private var servicesDiscovered: Boolean = false
    private var notificationsStarted: Boolean = false
    private var gattCacheInvalidated: Boolean = false
    private var rxNotifyLogs: Int = 0
    private val cccdUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var lastSendMs: Long = 0
    private var estopEnabled: Boolean = false

    private val watchdogIntervalMs = 200L
    private val watchdogTimeoutMs = 500L
    private var watchdogRunning = false

    fun interface NativeStringCallback {
        fun onResult(value: String)
    }

    fun interface NativeStatusCallback {
        fun onStatus(connected: Boolean)
    }

    fun interface NativeRxCallback {
        fun onText(text: String)
    }

    private var nativeOnDevice: NativeStringCallback? = null
    private var nativeOnError: NativeStringCallback? = null
    var nativeOnStatus: NativeStatusCallback? = null
    var nativeOnRx: NativeRxCallback? = null
    var nativeOnLog: NativeStringCallback? = null

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun ensureBle(): Boolean {
        if (bluetoothAdapter != null) return true
        return try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothAdapter = manager.adapter
            if (hasScanPermission()) {
                scanner = bluetoothAdapter?.bluetoothLeScanner
            }
            startWatchdog()
            bluetoothAdapter != null
        } catch (e: SecurityException) {
            Logger.error("RobotBle ensureBle permission", e)
            false
        } catch (e: Exception) {
            Logger.error("RobotBle ensureBle", e)
            false
        }
    }

    private fun ensurePermissions(call: PluginCall): Boolean {
        val needsBluetooth = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val needsLocation = Build.VERSION.SDK_INT < Build.VERSION_CODES.S

        val bluetoothGranted = !needsBluetooth || getPermissionState("bluetooth") == PermissionState.GRANTED
        val locationGranted = !needsLocation || getPermissionState("location") == PermissionState.GRANTED

        if (bluetoothGranted && locationGranted) return true

        requestAllPermissions(call, "permissionsCallback")
        return false
    }

    @Suppress("unused")
    @PermissionCallback
    private fun permissionsCallback(call: PluginCall) {
        if (!ensurePermissions(call)) return
        connect(call)
    }

    @Suppress("unused")
    @PluginMethod
    fun connect(call: PluginCall) {
        if (!ensureBle()) {
            call.reject("Bluetooth indisponible")
            return
        }
        if (!ensurePermissions(call)) return

        serviceUuid = UUID.fromString(call.getString("serviceUuid") ?: defaultServiceUuid.toString())
        txUuid = UUID.fromString(call.getString("txUuid") ?: defaultTxUuid.toString())
        rxUuid = UUID.fromString(call.getString("rxUuid") ?: defaultRxUuid.toString())
        autoReconnect = call.getBoolean("autoReconnect", true) == true
        targetAddress = call.getString("deviceAddress")

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            call.reject("Bluetooth indisponible ou désactivé")
            return
        }

        disconnectInternal()

        nativeOnDevice = NativeStringCallback { name -> call.resolve(JSObject().put("deviceName", name)) }
        nativeOnError = NativeStringCallback { msg -> call.reject(msg) }

        val address = targetAddress
        if (address != null) {
            if (!hasConnectPermission()) {
                call.reject("Permission Bluetooth manquante")
                return
            }
            try {
                val device = adapter.getRemoteDevice(address)
                connectGatt(device)
                val name = if (hasConnectPermission()) device.name ?: "Robot" else "Robot"
                resolveDevice(name)
            } catch (e: SecurityException) {
                call.reject("Permission Bluetooth refusée")
            }
            return
        }

        startScan()
    }

    fun connectNative(onDevice: NativeStringCallback, onError: NativeStringCallback) {
        if (!ensureBle()) {
            onError.onResult("Bluetooth indisponible")
            return
        }
        if (!hasScanPermission() || !hasConnectPermission()) {
            onError.onResult("Permission Bluetooth manquante")
            return
        }
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            onError.onResult("Bluetooth indisponible ou désactivé")
            return
        }
        nativeOnDevice = onDevice
        nativeOnError = onError
        autoReconnect = true
        targetAddress = null
        disconnectInternal()
        startScan()
    }

    fun disconnectNative() {
        autoReconnect = false
        disconnectInternal()
        nativeOnStatus?.onStatus(false)
    }

    fun sendNative(payload: String) {
        lastSendMs = System.currentTimeMillis()
        enqueueOutgoing(if (estopEnabled) buildNeutralFrame(estop = true) else payload)
    }

    fun isLinkReady(): Boolean = gatt != null && txChar != null && rxReady

    private fun resolveDevice(name: String) {
        val cb = nativeOnDevice
        nativeOnDevice = null
        nativeOnError = null
        cb?.onResult(name)
    }

    private fun rejectPending(message: String) {
        val cb = nativeOnError
        nativeOnDevice = null
        nativeOnError = null
        cb?.onResult(message)
    }

    private fun startScan() {
        if (!hasScanPermission()) {
            rejectPending("Permission Bluetooth manquante")
            return
        }
        val leScanner = scanner ?: run {
            rejectPending("Scanner BLE indisponible")
            return
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(serviceUuid))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        stopScan()
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                if (device.address == null) return
                targetAddress = device.address
                stopScan()
                connectGatt(device)
                val name = try {
                    if (hasConnectPermission()) device.name ?: "Robot" else "Robot"
                } catch (_: SecurityException) {
                    "Robot"
                }
                resolveDevice(name)
            }

            override fun onScanFailed(errorCode: Int) {
                rejectPending("Scan BLE échoué: $errorCode")
            }
        }

        try {
            leScanner.startScan(filters, settings, scanCallback)
        } catch (e: SecurityException) {
            rejectPending("Permission Bluetooth refusée")
            return
        }

        mainHandler.postDelayed({
            if (gatt == null) {
                stopScan()
                rejectPending("Scan BLE timeout (aucun robot trouvé)")
            }
        }, 10_000)
    }

    private fun stopScan() {
        val cb = scanCallback ?: return
        scanCallback = null
        if (!hasScanPermission()) return
        try {
            scanner?.stopScan(cb)
        } catch (_: SecurityException) {
        }
    }

    private fun connectGatt(device: BluetoothDevice) {
        if (!hasConnectPermission()) {
            rejectPending("Permission Bluetooth manquante")
            return
        }
        try {
            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            rejectPending("Permission Bluetooth refusée")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                servicesDiscovered = false
                notificationsStarted = false
                rxReady = false
                cccdPending = false
                mtu = 23
                rxNotifyLogs = 0
                // Flux GATT Android standard : discovery → MTU → CCCD (une opération à la fois, thread UI).
                mainHandler.post {
                    if (gatt !== g || !hasConnectPermission()) return@post
                    try {
                        g.discoverServices()
                    } catch (_: SecurityException) {
                    }
                }
                notifyListeners("status", JSObject().put("connected", true))
                mainHandler.post { nativeOnStatus?.onStatus(true) }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                txChar = null
                rxChar = null
                writing = false
                rxReady = false
                cccdPending = false
                notificationsStarted = false
                mtu = 23
                servicesDiscovered = false
                writeQueue.clear()
                Logger.warn("RobotBle GATT disconnected status=$status")
                try {
                    g.close()
                } catch (_: Exception) {
                }
                if (gatt === g) {
                    gatt = null
                }
                notifyListeners("status", JSObject().put("connected", false))
                mainHandler.post { nativeOnStatus?.onStatus(false) }
                if (autoReconnect) {
                    scheduleReconnect()
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtuValue: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mtu = mtuValue
            }
            emitLog("MTU=$mtuValue status=$status")
            if (servicesDiscovered && !notificationsStarted) {
                mainHandler.post { enableNotifications(g) }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || servicesDiscovered) return
            servicesDiscovered = true
            val service = g.getService(serviceUuid) ?: run {
                emitLog("Service GATT introuvable")
                if (!invalidateGattCacheAndRediscover(g, "service absent")) {
                    finishSetup()
                }
                return
            }
            txChar = service.getCharacteristic(txUuid)
            rxChar = service.getCharacteristic(rxUuid)
            mainHandler.post { requestMtuThenSubscribe(g) }
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            val bytes = characteristic.value
            if (bytes == null || bytes.isEmpty()) {
                emitLog("notify vide uuid=${characteristic.uuid}")
                return
            }
            onGattRx(characteristic, bytes)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            onGattRx(characteristic, value)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                completeWrite()
                return
            }
            if (status == 143) {
                mainHandler.postDelayed({ completeWrite() }, 40)
                return
            }
            Logger.warn("RobotBle write status=$status")
            completeWrite()
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (!cccdPending) return
            cccdPending = false
            cccdEpoch++
            if (status == BluetoothGatt.GATT_SUCCESS) {
                emitLog("Notifications RX activées (CCCD)")
                finishSetup()
                return
            }
            // 0x01 = GATT_INVALID_HANDLE : cache d’attributs périmé (Android + GATT qui a changé).
            if (status == 1 && invalidateGattCacheAndRediscover(g, "CCCD status=1")) {
                return
            }
            emitLog("CCCD non écrit (status=$status) — le robot n’enverra pas de notify tant que 0x2902 n’est pas accepté")
            finishSetup()
        }
    }

    private fun onGattRx(characteristic: BluetoothGattCharacteristic, bytes: ByteArray) {
        if (rxNotifyLogs < 5) {
            rxNotifyLogs++
            emitLog("notify uuid=${characteristic.uuid} len=${bytes.size}")
        }
        if (characteristic.uuid != rxUuid) return
        val text = String(bytes, StandardCharsets.UTF_8)
        notifyListeners("rx", JSObject().put("text", text))
        mainHandler.post { nativeOnRx?.onText(text) }
    }

    private fun emitLog(message: String) {
        Logger.info("RobotBle $message")
        mainHandler.post { nativeOnLog?.onResult(message) }
    }

    /**
     * Une seule invalidation de cache par session (pas de boucle refresh → disconnect → refresh).
     * `BluetoothGatt.refresh()` est l’API cachée utilisée par les libs BLE Android
     * pour forcer une rediscovery après GATT_INVALID_HANDLE.
     */
    private fun invalidateGattCacheAndRediscover(g: BluetoothGatt, reason: String): Boolean {
        if (gattCacheInvalidated) return false
        gattCacheInvalidated = true
        servicesDiscovered = false
        notificationsStarted = false
        cccdPending = false
        rxReady = false
        txChar = null
        rxChar = null
        var refreshed = false
        try {
            val method = g.javaClass.getMethod("refresh")
            refreshed = method.invoke(g) as? Boolean == true
        } catch (_: Exception) {
        }
        emitLog("Rediscovery GATT ($reason) refresh=$refreshed")
        mainHandler.postDelayed({
            if (gatt !== g || !hasConnectPermission()) return@postDelayed
            try {
                g.discoverServices()
            } catch (_: SecurityException) {
            }
        }, 400)
        return true
    }

    private fun finishSetup() {
        rxReady = txChar != null
        if (txChar == null) {
            emitLog("Caractéristique TX absente")
        } else {
            emitLog("Lien prêt (TX)")
        }
        mainHandler.post { writePendingIfIdle() }
    }

    private fun requestMtuThenSubscribe(g: BluetoothGatt) {
        if (!hasConnectPermission()) {
            enableNotifications(g)
            return
        }
        try {
            if (!g.requestMtu(517)) {
                enableNotifications(g)
                return
            }
        } catch (_: SecurityException) {
            enableNotifications(g)
            return
        }
        mainHandler.postDelayed({
            if (gatt === g && !notificationsStarted) {
                emitLog("MTU timeout — poursuite CCCD avec MTU=$mtu")
                enableNotifications(g)
            }
        }, 1000)
    }

    private fun enableNotifications(g: BluetoothGatt) {
        if (notificationsStarted) return
        notificationsStarted = true
        if (!hasConnectPermission()) {
            emitLog("Permission Bluetooth manquante pour CCCD")
            finishSetup()
            return
        }
        val c = rxChar
        if (c == null) {
            emitLog("Caractéristique RX absente")
            finishSetup()
            return
        }
        emitLog("RX properties=${c.properties}")
        try {
            g.setCharacteristicNotification(c, true)
        } catch (_: SecurityException) {
            emitLog("setCharacteristicNotification refusé")
            finishSetup()
            return
        }
        val desc = c.getDescriptor(cccdUuid)
        if (desc == null) {
            emitLog("Descripteur CCCD 0x2902 absent")
            finishSetup()
            return
        }
        cccdPending = true
        val epoch = ++cccdEpoch
        val parent = desc.characteristic
        @Suppress("DEPRECATION")
        val previousWriteType = parent.writeType
        try {
            @Suppress("DEPRECATION")
            parent.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            val queued = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(desc)
            }
            if (!queued) {
                cccdPending = false
                emitLog("writeDescriptor CCCD refusé par la file GATT")
                finishSetup()
                return
            }
            mainHandler.postDelayed({
                if (cccdPending && epoch == cccdEpoch) {
                    cccdPending = false
                    emitLog("CCCD timeout — pas de callback writeDescriptor")
                    finishSetup()
                }
            }, 2000)
        } catch (_: SecurityException) {
            cccdPending = false
            emitLog("writeDescriptor CCCD : SecurityException")
            finishSetup()
        } finally {
            try {
                @Suppress("DEPRECATION")
                parent.writeType = previousWriteType
            } catch (_: Exception) {
            }
        }
    }

    private fun scheduleReconnect() {
        mainHandler.postDelayed({
            val adapter = bluetoothAdapter ?: return@postDelayed
            val address = targetAddress ?: return@postDelayed
            if (!hasConnectPermission()) return@postDelayed
            try {
                val device = adapter.getRemoteDevice(address)
                connectGatt(device)
            } catch (_: SecurityException) {
            }
        }, 1500)
    }

    @Suppress("unused")
    @PluginMethod
    fun disconnect(call: PluginCall) {
        autoReconnect = false
        disconnectInternal()
        call.resolve()
    }

    private fun disconnectInternal() {
        stopScan()
        try {
            gatt?.disconnect()
        } catch (_: Exception) {
        }
        try {
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
        txChar = null
        rxChar = null
        writing = false
        writeEpoch++
        rxReady = false
        cccdPending = false
        servicesDiscovered = false
        notificationsStarted = false
        gattCacheInvalidated = false
        mtu = 23
        cccdEpoch++
        pendingPayloads.clear()
        writeQueue.clear()
    }

    @Suppress("unused")
    @PluginMethod
    fun emergencyStop(call: PluginCall) {
        estopEnabled = call.getBoolean("enabled", false) == true
        enqueueOutgoing(buildNeutralFrame(estop = estopEnabled))
        call.resolve()
    }

    @Suppress("unused")
    @PluginMethod
    fun send(call: PluginCall) {
        val payload = call.getString("payload")
        if (payload == null) {
            call.reject("payload manquant")
            return
        }
        lastSendMs = System.currentTimeMillis()
        if (estopEnabled) {
            enqueueOutgoing(buildNeutralFrame(estop = true))
            call.resolve()
            return
        }
        enqueueOutgoing(payload)
        call.resolve()
    }

    private fun enqueueOutgoing(payload: String) {
        pendingPayloads.add(payload)
        writePendingIfIdle()
    }

    private fun writePendingIfIdle() {
        if (writing || cccdPending) return
        if (writeQueue.isNotEmpty()) return
        val payload = pendingPayloads.poll() ?: return
        enqueuePayload(payload)
    }

    private fun enqueuePayload(payload: String) {
        val bytes = payload.toByteArray(StandardCharsets.UTF_8)
        val maxChunk = (mtu - 3).coerceAtLeast(20)
        var offset = 0
        while (offset < bytes.size) {
            val len = (bytes.size - offset).coerceAtMost(maxChunk)
            val chunk = bytes.copyOfRange(offset, offset + len)
            writeQueue.add(chunk)
            offset += len
        }
        writeNextChunk()
    }

    private fun completeWrite() {
        writeEpoch++
        writing = false
        writeNextChunk()
    }

    private fun writeNextChunk() {
        if (writing || cccdPending) return
        if (!hasConnectPermission()) return
        val g = gatt ?: return
        val c = txChar ?: return
        val chunk = writeQueue.poll()
        if (chunk == null) {
            writePendingIfIdle()
            return
        }
        writing = true
        val epoch = writeEpoch
        try {
            val queued = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(c, chunk, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) ==
                    BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                @Suppress("DEPRECATION")
                c.value = chunk
                @Suppress("DEPRECATION")
                g.writeCharacteristic(c)
            }
            if (!queued) {
                writing = false
                return
            }
            mainHandler.postDelayed({
                if (writing && epoch == writeEpoch) {
                    completeWrite()
                }
            }, 20)
        } catch (_: SecurityException) {
            writing = false
        }
    }

    private fun startWatchdog() {
        if (watchdogRunning) return
        watchdogRunning = true
        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                try {
                    val now = System.currentTimeMillis()
                    val linkReady = gatt != null && txChar != null
                    if (linkReady) {
                        val delta = now - lastSendMs
                        if (delta > watchdogTimeoutMs) {
                            pendingPayloads.add(buildNeutralFrame(estop = estopEnabled))
                            writePendingIfIdle()
                        }
                    }
                } finally {
                    mainHandler.postDelayed(this, watchdogIntervalMs)
                }
            }
        }, watchdogIntervalMs)
    }

    private fun buildNeutralFrame(estop: Boolean): String {
        val o = JSONObject()
        o.put("v", 1)
        o.put("ts_ms", System.currentTimeMillis())
        o.put("seq", 0)
        o.put("axes", JSONObject().put("lx", 0).put("ly", 0).put("rx", 0).put("ry", 0))
        o.put("triggers", JSONObject().put("lt", 0).put("rt", 0))
        o.put(
            "buttons",
            JSONObject().put("A", false).put("B", false).put("X", false).put("Y", false).put("LB", false).put("RB", false)
        )
        o.put("dpad", JSONObject().put("up", false).put("down", false))
        o.put("safety", JSONObject().put("estop", estop))
        return o.toString()
    }
}
