package tv.castly

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import org.json.JSONObject
import java.util.UUID

/** Finds Castly TVs over Bluetooth Low Energy and reads their Wi-Fi address. Only the TV's name and address are exchanged. */
@SuppressLint("MissingPermission")
class BleFinder(private val ctx: Context, private val onFound: (TvFinder.Tv) -> Unit) {
    private val scanner get() = ctx.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner
    private val seen = mutableSetOf<String>()
    private val lan = Regex("^(10\\.|192\\.168\\.|172\\.(1[6-9]|2\\d|3[01])\\.)[0-9.]+$")   // home-network addresses only

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(t: Int, r: ScanResult) { if (seen.add(r.device.address)) r.device.connectGatt(ctx, false, gattCb) }
    }
    private val gattCb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, st: Int, ns: Int) { if (ns == BluetoothProfile.STATE_CONNECTED) g.discoverServices() else g.close() }
        override fun onServicesDiscovered(g: BluetoothGatt, st: Int) { g.getService(SERVICE)?.getCharacteristic(CHAR)?.let { g.readCharacteristic(it) } ?: g.close() }
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) { done(g, value) }
        @Deprecated("Android 12 and older")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) { done(g, c.value ?: ByteArray(0)) }
    }

    private fun done(g: BluetoothGatt, v: ByteArray) {
        runCatching { JSONObject(String(v)).let { if (lan.matches(it.getString("h"))) onFound(TvFinder.Tv(it.getString("n"), it.getString("h"), it.getInt("p"))) } }
        g.close()
    }

    fun start(onEnd: () -> Unit) {
        seen.clear()
        scanner?.startScan(listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE)).build()),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCb)
        Handler(Looper.getMainLooper()).postDelayed({ runCatching { scanner?.stopScan(scanCb) }; onEnd() }, 12_000)   // looks for 12 seconds
    }

    companion object {
        val SERVICE: UUID = UUID.fromString("7c1a5e00-0000-4c5e-9c1a-ca57100c0001")
        val CHAR: UUID = UUID.fromString("7c1a5e00-0000-4c5e-9c1a-ca57100c0002")
    }
}
