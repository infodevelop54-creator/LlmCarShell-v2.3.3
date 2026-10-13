package com.example.llmcar.net

import com.example.llmcar.tools.ToolDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
private data class ChatRequest(
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    val tools: List<ToolDefinition>? = null,
    val tool_choice: String = "auto"
)

@Serializable
data class ToolCallFunction(val name: String? = null, val arguments: String? = null)

@Serializable
data class ToolCall(
    val index: Int = 0,
    val id: String? = null,
    val type: String? = null,
    val function: ToolCallFunction? = null
)

@Serializable
data class ChatChoice(
    val message: AssistantMessage? = null,
    val delta: AssistantMessage? = null,
    val finish_reason: String? = null
)

@Serializable
data class AssistantMessage(
    val role: String? = null,
    val content: String? = null,
    val tool_calls: List<ToolCall>? = null
)

@Serializable
private data class ChatResponse(val choices: List<ChatChoice>)

sealed class StreamChunk {
    data class Text(val text: String) : StreamChunk()
    data class ToolCallDelta(
        val index: Int,
        val id: String?,
        val name: String?,
        val argsDelta: String?
    ) : StreamChunk()
    data class Failure(val error: Throwable) : StreamChunk()
    data object Done : StreamChunk()
}

object LlmApiClient {

    private const val BASE_URL = "http://127.0.0.1:8080"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun chat(messages: List<ChatMessage>): Result<String> =
        chatRaw(messages, null).map { it.message?.content.orEmpty() }

    suspend fun chatWithTools(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>
    ): Result<AssistantMessage> =
        chatRaw(messages, tools).map { it.message ?: AssistantMessage() }

    private suspend fun chatRaw(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>?
    ): Result<ChatChoice> = withContext(Dispatchers.IO) {
        try {
            val body = json.encodeToString(ChatRequest(messages, false, tools))
                .toRequestBody("application/json".toMediaType())
            client.newCall(
                Request.Builder().url("$BASE_URL/v1/chat/completions")
                    .post(body).build()
            ).execute().use { r ->
                if (!r.isSuccessful) {
                    // Тело ответа — для диагностики (llama-server возвращает JSON с описанием).
                    val errBody = runCatching { r.body?.string().orEmpty() }.getOrDefault("")
                    return@withContext Result.failure(
                        Exception("HTTP ${r.code}: ${errBody.take(500)}")
                    )
                }
                val b = r.body?.string()
                    ?: return@withContext Result.failure(Exception("Пустой ответ"))
                val p = json.decodeFromString<ChatResponse>(b)
                val c = p.choices.firstOrNull()
                    ?: return@withContext Result.failure(Exception("Нет choices"))
                Result.success(c)
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    fun chatStream(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>? = null
    ): Flow<StreamChunk> = callbackFlow {
        val body = json.encodeToString(ChatRequest(messages, true, tools))
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$BASE_URL/v1/chat/completions")
            .post(body).header("Accept", "text/event-stream").build()

        val listener = object : EventSourceListener() {
            override fun onEvent(es: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") { trySend(StreamChunk.Done); close(); return }
                try {
                    val p = json.decodeFromString<ChatResponse>(data)
                    val d = p.choices.firstOrNull()?.delta ?: return
                    d.content?.takeIf { it.isNotEmpty() }?.let {
                        trySend(StreamChunk.Text(it))
                    }
                    d.tool_calls?.forEach { tc ->
                        trySend(StreamChunk.ToolCallDelta(
                            tc.index, tc.id, tc.function?.name, tc.function?.arguments))
                    }
                } catch (_: Exception) {}
            }
            override fun onFailure(es: EventSource, t: Throwable?, r: Response?) {
                // Извлекаем тело ответа, чтобы серверная ошибка (например,
                // "Unsupported param: tools") дошла до ChatViewModel и была
                // корректно распознана в fallback.
                val errBody = runCatching { r?.body?.string().orEmpty() }.getOrDefault("")
                val msg = when {
                    errBody.isNotBlank() -> "HTTP ${r?.code}: ${errBody.take(500)}"
                    t != null -> t.message ?: t.toString()
                    else -> "SSE fail HTTP ${r?.code}"
                }
                trySend(StreamChunk.Failure(Exception(msg)))
                close()
            }
            override fun onClosed(es: EventSource) { close() }
        }
        val es = EventSources.createFactory(client).newEventSource(req, listener)
        awaitClose { es.cancel() }
    }
}
