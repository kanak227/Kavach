package com.kavach.app.telegram

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class TelegramUpdate(
    val updateId: Long,
    val chatId: String,
    val command: String
)

object TelegramService {

    private const val BASE_URL = "https://api.telegram.org/bot"

    /**
     * Send Markdown-formatted text message to Telegram chat.
     */
    suspend fun sendMessage(
        botToken: String,
        chatId: String,
        text: String,
        includeQuickKeyboard: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        if (botToken.isBlank() || chatId.isBlank()) return@withContext false
        try {
            val url = URL("$BASE_URL$botToken/sendMessage")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 7000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            }

            val postData = StringBuilder()
            postData.append("chat_id=").append(URLEncoder.encode(chatId, "UTF-8"))
            postData.append("&text=").append(URLEncoder.encode(text, "UTF-8"))
            postData.append("&parse_mode=Markdown")

            if (includeQuickKeyboard) {
                // Interactive buttons for Telegram contact
                val keyboardJson = """
                    {"inline_keyboard":[
                        [{"text":"📸 Snap Front Photo","callback_data":"/photo"},
                         {"text":"🔄 Rear Photo","callback_data":"/backphoto"}],
                        [{"text":"📍 Live Location Pin","callback_data":"/loc"},
                         {"text":"🔋 Status & Battery","callback_data":"/status"}]
                    ]}
                """.trimIndent().replace("\n", "").replace(" ", "")
                postData.append("&reply_markup=").append(URLEncoder.encode(keyboardJson, "UTF-8"))
            }

            conn.outputStream.use { os ->
                os.write(postData.toString().toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = conn.responseCode
            conn.disconnect()
            responseCode in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Sends native interactive GPS pin to Telegram chat.
     */
    /**
     * Sends live streaming GPS location to Telegram.
     * Returns the Telegram message_id on success, or -1L on failure.
     */
    suspend fun sendLiveLocation(
        botToken: String,
        chatId: String,
        latitude: Double,
        longitude: Double,
        livePeriodSeconds: Int = 3600
    ): Long = withContext(Dispatchers.IO) {
        if (botToken.isBlank() || chatId.isBlank()) return@withContext -1L
        try {
            val url = URL("$BASE_URL$botToken/sendLocation")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 7000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            }

            val postData = "chat_id=${URLEncoder.encode(chatId, "UTF-8")}&latitude=$latitude&longitude=$longitude&live_period=$livePeriodSeconds"

            conn.outputStream.use { os ->
                os.write(postData.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val root = JSONObject(json)
                if (root.optBoolean("ok")) {
                    return@withContext root.optJSONObject("result")?.optLong("message_id", -1L) ?: -1L
                }
            } else {
                conn.disconnect()
            }
            -1L
        } catch (e: Exception) {
            e.printStackTrace()
            -1L
        }
    }

    /**
     * Edits/refreshes an existing live location message with fresh coordinates.
     */
    suspend fun editMessageLiveLocation(
        botToken: String,
        chatId: String,
        messageId: Long,
        latitude: Double,
        longitude: Double
    ): Boolean = withContext(Dispatchers.IO) {
        if (botToken.isBlank() || chatId.isBlank() || messageId <= 0L) return@withContext false
        try {
            val url = URL("$BASE_URL$botToken/editMessageLiveLocation")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 7000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            }

            val postData = "chat_id=${URLEncoder.encode(chatId, "UTF-8")}&message_id=$messageId&latitude=$latitude&longitude=$longitude"

            conn.outputStream.use { os ->
                os.write(postData.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = conn.responseCode
            conn.disconnect()
            responseCode in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Sends a single static location pin.
     */
    suspend fun sendLocation(
        botToken: String,
        chatId: String,
        latitude: Double,
        longitude: Double
    ): Boolean = withContext(Dispatchers.IO) {
        if (botToken.isBlank() || chatId.isBlank()) return@withContext false
        try {
            val url = URL("$BASE_URL$botToken/sendLocation")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 7000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            }

            val postData = "chat_id=${URLEncoder.encode(chatId, "UTF-8")}&latitude=$latitude&longitude=$longitude"

            conn.outputStream.use { os ->
                os.write(postData.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = conn.responseCode
            conn.disconnect()
            responseCode in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Sends photo JPEG bytes as multipart/form-data to Telegram chat.
     */
    suspend fun sendPhoto(
        botToken: String,
        chatId: String,
        photoBytes: ByteArray,
        caption: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        if (botToken.isBlank() || chatId.isBlank() || photoBytes.isEmpty()) return@withContext false
        try {
            val boundary = "---KavachBoundary" + System.currentTimeMillis()
            val LINE_FEED = "\r\n"
            val url = URL("$BASE_URL$botToken/sendPhoto")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 20000
                doOutput = true
                doInput = true
                useCaches = false
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }

            conn.outputStream.use { os ->
                val writer = PrintWriter(OutputStreamWriter(os, "UTF-8"), true)

                // chat_id field
                writer.append("--$boundary").append(LINE_FEED)
                writer.append("Content-Disposition: form-data; name=\"chat_id\"").append(LINE_FEED)
                writer.append(LINE_FEED).append(chatId).append(LINE_FEED)
                writer.flush()

                // caption field
                if (caption.isNotBlank()) {
                    writer.append("--$boundary").append(LINE_FEED)
                    writer.append("Content-Disposition: form-data; name=\"caption\"").append(LINE_FEED)
                    writer.append(LINE_FEED).append(caption).append(LINE_FEED)
                    writer.flush()
                }

                // photo file part
                writer.append("--$boundary").append(LINE_FEED)
                writer.append("Content-Disposition: form-data; name=\"photo\"; filename=\"snapshot.jpg\"").append(LINE_FEED)
                writer.append("Content-Type: image/jpeg").append(LINE_FEED)
                writer.append(LINE_FEED)
                writer.flush()

                os.write(photoBytes)
                os.flush()

                writer.append(LINE_FEED)
                writer.append("--$boundary--").append(LINE_FEED)
                writer.flush()
            }

            val responseCode = conn.responseCode
            conn.disconnect()
            responseCode in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Long-polls Telegram for incoming updates/commands.
     */
    suspend fun getUpdates(
        botToken: String,
        offset: Long,
        timeoutSeconds: Int = 2
    ): List<TelegramUpdate> = withContext(Dispatchers.IO) {
        if (botToken.isBlank()) return@withContext emptyList()
        val updates = mutableListOf<TelegramUpdate>()
        try {
            val urlString = "$BASE_URL$botToken/getUpdates?offset=$offset&timeout=$timeoutSeconds"
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = (timeoutSeconds + 5) * 1000
                readTimeout = (timeoutSeconds + 5) * 1000
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val jsonString = reader.use { it.readText() }
                val root = JSONObject(jsonString)
                if (root.optBoolean("ok")) {
                    val result = root.optJSONArray("result") ?: JSONArray()
                    for (i in 0 until result.length()) {
                        val item = result.getJSONObject(i)
                        val updateId = item.getLong("update_id")

                        // 1. Check direct message text
                        if (item.has("message")) {
                            val msg = item.getJSONObject("message")
                            val text = msg.optString("text", "").trim()
                            val from = msg.optJSONObject("chat")
                            val chatId = from?.opt("id")?.toString() ?: ""
                            if (text.isNotBlank() && chatId.isNotBlank()) {
                                updates.add(TelegramUpdate(updateId, chatId, text.lowercase()))
                            }
                        }
                        // 2. Check inline button callback queries
                        else if (item.has("callback_query")) {
                            val cb = item.getJSONObject("callback_query")
                            val data = cb.optString("data", "").trim()
                            val msg = cb.optJSONObject("message")
                            val from = msg?.optJSONObject("chat")
                            val chatId = from?.opt("id")?.toString() ?: ""
                            if (data.isNotBlank() && chatId.isNotBlank()) {
                                updates.add(TelegramUpdate(updateId, chatId, data.lowercase()))
                            }
                        }
                    }
                }
            }
            conn.disconnect()
        } catch (_: Exception) {}
        updates
    }

    /**
     * Test credentials by fetching bot profile info (`getMe`).
     */
    suspend fun testBotConnection(botToken: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (botToken.isBlank()) return@withContext Pair(false, "Bot token cannot be empty.")
        try {
            val url = URL("$BASE_URL$botToken/getMe")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
            }
            val code = conn.responseCode
            if (code in 200..299) {
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(json)
                if (root.optBoolean("ok")) {
                    val result = root.getJSONObject("result")
                    val username = result.optString("username", "Bot")
                    conn.disconnect()
                    return@withContext Pair(true, "@$username")
                }
            }
            conn.disconnect()
            Pair(false, "HTTP $code: Invalid bot token.")
        } catch (e: Exception) {
            Pair(false, e.localizedMessage ?: "Connection error.")
        }
    }
}
