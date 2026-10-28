package tv.castly.tv

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import java.util.UUID

/** Makes the TV visible to phones over Bluetooth Low Energy. It only shares the TV's name and Wi-Fi address; video and sound still use Wi-Fi. */
@SuppressLint("MissingPermission")
class BleBeacon(private val ctx: Context, private val info: () -> String) {
    private val mgr = ctx.getSystemService(BluetoothManager::class.java)
    private var gatt: BluetoothGattServer? = null
    private var adv: BluetoothLeAdvertiser? = null
    private val cb = object : AdvertiseCallback() {}

    fun start() {
        val ad = mgr?.adapter?.takeIf { it.isEnabled } ?: return
        gatt = mgr.openGattServer(ctx, object : BluetoothGattServerCallback() {
            override fun onCharacteristicReadRequest(d: BluetoothDevice, id: Int, off: Int, c: BluetoothGattCharacteristic) {
                val v = info().toByteArray()
                gatt?.sendResponse(d, id, BluetoothGatt.GATT_SUCCESS, off, v.copyOfRange(minOf(off, v.size), v.size))
            }
        })
        gatt?.addService(BluetoothGattService(SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
            addCharacteristic(BluetoothGattCharacteristic(CHAR, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ)) })
        adv = ad.bluetoothLeAdvertiser          // some TV boxes cannot advertise; then this simply does nothing
        adv?.startAdvertising(AdvertiseSettings.Builder().setConnectable(true).setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY).build(),
            AdvertiseData.Builder().addServiceUuid(ParcelUuid(SERVICE)).build(), cb)
    }
    fun stop() { runCatching { adv?.stopAdvertising(cb); gatt?.close() } }

    companion object {
        val SERVICE: UUID = UUID.fromString("7c1a5e00-0000-4c5e-9c1a-ca57100c0001")
        val CHAR: UUID = UUID.fromString("7c1a5e00-0000-4c5e-9c1a-ca57100c0002")
    }
}
