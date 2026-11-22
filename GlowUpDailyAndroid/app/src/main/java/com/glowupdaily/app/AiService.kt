package com.glowupdaily.app

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Sends images only to the user's configured backend. Provider credentials never live in Android.
 * The app intentionally returns a clear error when no backend is configured instead of inventing
 * an AI result.
 */
object AiService {
    suspend fun analyze(imagePath: String, kind: String): String = withContext(Dispatchers.IO) {
        val base = BuildConfig.AI_BACKEND_URL.trim().trimEnd('/')
        require(base.isNotBlank()) {
            "El análisis con IA todavía no está configurado. Las demás funciones siguen disponibles."
        }
        val file = File(imagePath)
        require(file.exists() && file.length() > 0) { "No se encontró la fotografía seleccionada." }
        val boundary = "GlowUp-${UUID.randomUUID()}"
        val conn = (URL("$base/api/analyze/$kind").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
        }
        try {
            DataOutputStream(conn.outputStream).use { out ->
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"image\"; filename=\"${file.name}\"\r\n")
                out.writeBytes("Content-Type: image/jpeg\r\n\r\n")
                file.inputStream().use { it.copyTo(out) }
                out.writeBytes("\r\n--$boundary--\r\n")
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (status !in 200..299) {
                val message = runCatching { JsonParser.parseString(body).asJsonObject.get("error")?.asString }.getOrNull()
                error(message ?: "El servicio de IA respondió con el código $status.")
            }
            val json = JsonParser.parseString(body).asJsonObject
            json.get("analysis")?.asString ?: error("El servidor no devolvió un análisis válido.")
        } finally {
            conn.disconnect()
        }
    }
}
