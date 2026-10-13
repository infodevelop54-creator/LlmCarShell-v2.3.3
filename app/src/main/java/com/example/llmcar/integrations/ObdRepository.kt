package com.example.llmcar.integrations

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.github.eltonvs.obd.command.control.*
import com.github.eltonvs.obd.connection.ObdDeviceConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

data class DtcResult(val code: String, val description: String)

class ObdRepository(private val context: Context) {

    companion object {
        private val SPP_UUID: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var conn: ObdDeviceConnection? = null

    val isConnected get() = socket?.isConnected == true

    @SuppressLint("MissingPermission")
    suspend fun connect(mac: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val a = BluetoothAdapter.getDefaultAdapter()
                ?: return@withContext Result.failure(IllegalStateException("BT недоступен"))
            val d = a.getRemoteDevice(mac); a.cancelDiscovery()
            val s = d.createRfcommSocketToServiceRecord(SPP_UUID); s.connect()
            socket = s
            input = s.inputStream
            output = s.outputStream
            send("ATZ"); send("ATE0"); send("ATL0"); send("ATSP0")
            conn = ObdDeviceConnection(input!!, output!!)
            Result.success(Unit)
        } catch (e: Exception) { disconnect(); Result.failure(e) }
    }

    fun disconnect() {
        runCatching { input?.close() }
        runCatching { output?.close() }
        runCatching { socket?.close() }
        socket = null; input = null; output = null; conn = null
    }

    suspend fun readPid(pid: String): Result<String> = withContext(Dispatchers.IO) {
        try { Result.success(parse(pid, send("01$pid"))) }
        catch (e: Exception) { Result.failure(e) }
    }

    suspend fun readStoredDtcs(): Result<List<DtcResult>> = withContext(Dispatchers.IO) {
        try {
            val c = TroubleCodesCommand()
            conn!!.run(c)
            Result.success(extractCodes(c).map { DtcResult(it, DtcDatabase.describe(it)) })
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun readPendingDtcs(): Result<List<DtcResult>> = withContext(Dispatchers.IO) {
        try {
            val c = PendingTroubleCodesCommand()
            conn!!.run(c)
            Result.success(extractCodes(c).map { DtcResult(it, DtcDatabase.describe(it)) })
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun readPermanentDtcs(): Result<List<DtcResult>> = withContext(Dispatchers.IO) {
        try {
            val c = PermanentTroubleCodesCommand()
            conn!!.run(c)
            Result.success(extractCodes(c).map { DtcResult(it, DtcDatabase.describe(it)) })
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun clearDtcs(): Result<Unit> = withContext(Dispatchers.IO) {
        try { conn!!.run(ResetTroubleCodesCommand()); Result.success(Unit) }
        catch (e: Exception) { Result.failure(e) }
    }

    /**
     * Безопасное извлечение кодов DTC из команды.
     * В разных версиях kotlin-obd-api свойство может называться по-разному,
     * либо иметь ограниченную видимость. Используем рефлексию как fallback.
     */
    @Suppress("UNCHECKED_CAST")
    private fun extractCodes(cmd: Any): List<String> {
        // Сначала пробуем публичное свойство value
        try {
            val getter = cmd.javaClass.getMethod("getValue")
            val v = getter.invoke(cmd)
            if (v is List<*>) return v.filterIsInstance<String>()
            if (v is String) return listOf(v)
        } catch (_: Exception) {}

        // Fallback: рефлексия по полю value с обходом иерархии
        var clazz: Class<*>? = cmd.javaClass
        while (clazz != null) {
            try {
                val f = clazz.getDeclaredField("value")
                f.isAccessible = true
                val v = f.get(cmd)
                if (v is List<*>) return v.filterIsInstance<String>()
                if (v is String) return listOf(v)
            } catch (_: Exception) {}
            clazz = clazz.superclass
        }
        return emptyList()
    }

    private fun send(cmd: String): String {
        val o = output ?: error("OBD не подключен")
        o.write("$cmd\r".toByteArray()); o.flush()
        Thread.sleep(120)
        return readLine()
    }

    private fun readLine(): String {
        val b = StringBuilder()
        val i = input ?: error("OBD")
        val dl = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < dl) {
            if (i.available() > 0) {
                val c = i.read()
                if (c == '\r'.code || c == '\n'.code) { if (b.isNotEmpty()) break }
                else b.append(c.toChar())
            } else Thread.sleep(50)
        }
        return b.toString().replace(" ", "").replace(">", "").trim()
    }

    private fun parse(pid: String, raw: String): String {
        val c = raw.replace("SEARCHING...", "").trim()
        return when (pid.uppercase()) {
            "0C" -> "${(c.takeLast(4).toIntOrNull(16) ?: return c) / 4} RPM"
            "0D" -> "${c.takeLast(2).toIntOrNull(16) ?: return c} km/h"
            "05" -> "${(c.takeLast(2).toIntOrNull(16) ?: return c) - 40} °C"
            "2F" -> "${(c.takeLast(2).toIntOrNull(16) ?: return c) * 100 / 255} %"
            "11" -> "${(c.takeLast(2).toIntOrNull(16) ?: return c) * 100 / 255} %"
            "42" -> "${(c.takeLast(4).toIntOrNull(16) ?: return c) / 1000.0} V"
            else -> c
        }
    }
}
