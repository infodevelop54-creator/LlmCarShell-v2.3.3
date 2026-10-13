package com.example.llmcar.data

import android.accounts.Account
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.llmcar.data.db.LlmCarDatabase
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class CloudSyncRepository(private val context: Context) {

    private val mk = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val prefs = EncryptedSharedPreferences.create(context, "cloud_sync_prefs", mk,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)

    private fun key(): SecretKey {
        val s = prefs.getString("k", null)
        if (s != null) {
            val decoded: ByteArray = Base64.getDecoder().decode(s)
            return SecretKeySpec(decoded, "AES")
        }
        val k = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        prefs.edit().putString("k", Base64.getEncoder().encodeToString(k.encoded)).apply()
        return k
    }

    private fun enc(bytes: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        return c.iv + c.doFinal(bytes)
    }

    private fun dec(bytes: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return c.doFinal(bytes.copyOfRange(12, bytes.size))
    }

    fun performSyncBlocking() {
        val account = GoogleSignIn.getLastSignedInAccount(context)?.account
            ?: throw IllegalStateException("Не выполнен вход в Google")
        val drive = build(account)

        val dbFile = context.getDatabasePath("llmcar.db")
        LlmCarDatabase.get(context).openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint(FULL)").close()

        val tmp = java.io.File(context.cacheDir, "b.db")
        FileInputStream(dbFile).use { i ->
            FileOutputStream(tmp).use { o -> i.copyTo(o) }
        }

        val encFile = java.io.File(context.cacheDir, "b.enc")
        encFile.writeBytes(enc(tmp.readBytes()))

        val existing = drive.files().list()
            .setSpaces("appDataFolder")
            .setQ("name = 'llmcar_backup.enc'")
            .execute().files.firstOrNull()

        val meta = DriveFile().apply {
            name = "llmcar_backup.enc"
            if (existing == null) parents = listOf("appDataFolder")
        }
        val media = FileContent("application/octet-stream", encFile)
        if (existing == null) drive.files().create(meta, media).execute()
        else drive.files().update(existing.id, meta, media).execute()

        tmp.delete(); encFile.delete()
    }

    suspend fun restore() = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context)?.account
            ?: throw IllegalStateException("Не выполнен вход в Google")
        val drive = build(account)
        val existing = drive.files().list()
            .setSpaces("appDataFolder")
            .setQ("name = 'llmcar_backup.enc'")
            .execute().files.firstOrNull()
            ?: throw IllegalStateException("Бэкап не найден")
        val tmp = java.io.File(context.cacheDir, "r.enc")
        drive.files().get(existing.id).executeMediaAndDownloadTo(FileOutputStream(tmp))
        context.getDatabasePath("llmcar.db").writeBytes(dec(tmp.readBytes()))
        tmp.delete()
    }

    private fun build(a: Account): Drive {
        val cred = GoogleAccountCredential.usingOAuth2(context,
            listOf(DriveScopes.DRIVE_APPDATA)).apply { selectedAccount = a }
        return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), cred)
            .setApplicationName("LlmCarShell").build()
    }
}
