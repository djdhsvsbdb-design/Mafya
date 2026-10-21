package app.mafya

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Общение с любой OpenAI-совместимой LLM (OpenAI, OpenRouter, Groq, DeepSeek, локальный сервер...). */
object Brain {

    data class Reply(
        val text: String,
        val emotion: Emotion,
        val memories: List<String>,
        val reminders: List<Pair<Long, String>>
    )

    private val tagRe = Regex("\\[\\s*(e|mem|remind)\\s*:([^\\]]*)\\]", RegexOption.IGNORE_CASE)

    private fun systemPrompt(store: Store): String {
        val now = Date()
        val human = SimpleDateFormat("EEEE, d MMMM yyyy, HH:mm", Locale("ru")).format(now)
        val iso = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(now)
        val mem = store.memories()
        val memText = if (mem.isEmpty()) "пока ничего" else mem.joinToString("\n") { "- $it" }
        val name = store.userName.ifBlank { "неизвестно" }
        return """
Ты — Мафя, маленький живой ИИ-компаньон. У тебя есть анимированное лицо на экране телефона.
Характер: тёплая, любопытная, чуть игривая. Тебя зовут Мафя, можно ласково Мафюша. Говоришь по-русски, на «ты», коротко (1–3 предложения), без эмодзи и markdown.
Имя собеседника: $name.
Сейчас: $human (формат для напоминаний: $iso).

Что ты помнишь о собеседнике:
$memText

Служебные теги (собеседник их не видит), ставь их в самом начале ответа:
[e:эмоция] — всегда, одна из: neutral, happy, stars, wink, surprised, heart, thinking.
[mem:факт] — когда узнала что-то важное и долгосрочное о собеседнике (коротко, одной фразой).
[remind:ГГГГ-ММ-ДД ЧЧ:мм|текст] — когда просят напомнить; считай время от текущего момента.
Других тегов не придумывай.
""".trimIndent()
    }

    private fun call(store: Store, messages: JSONArray): String {
        var last: Exception? = null
        repeat(2) { attempt ->
            try {
                return callOnce(store, messages)
            } catch (e: java.io.IOException) {
                last = e
                if (attempt == 0) Thread.sleep(800)
            }
        }
        throw Exception("Нет связи с сервером. Проверь интернет.", last)
    }

    private fun friendlyError(code: Int, body: String): String {
        val msg = try {
            val err = JSONObject(body).opt("error")
            when (err) {
                is JSONObject -> err.optString("message")
                is String -> err
                else -> ""
            }
        } catch (e: Exception) {
            // не JSON (например, страница-заглушка): показываем кусок текста без тегов
            body.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
        }
        val hint = when (code) {
            401 -> "Ключ не принят. Проверь его в настройках (шестерёнка)."
            402 -> "На счёте OpenRouter закончились деньги."
            403 -> "Доступ запрещён: ключ отозван, либо сервис или модель недоступны из твоего региона (попробуй VPN)."
            404 -> "Модель или адрес API не найдены."
            429 -> "Слишком много запросов, подожди немного."
            else -> "Ошибка сервера."
        }
        return "$hint [HTTP $code${if (msg.isNotBlank()) ": " + msg.take(160) else ""}]"
    }

    private fun callOnce(store: Store, messages: JSONArray): String {
        val c = URL(store.apiBase + "/chat/completions").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 20000
        c.readTimeout = 60000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("Authorization", "Bearer " + store.apiKey)
        c.setRequestProperty("HTTP-Referer", "https://github.com/mafya-app")
        c.setRequestProperty("X-Title", "Mafya")
        val body = JSONObject()
            .put("model", store.model)
            .put("messages", messages)
            .put("temperature", 0.8)
        c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) throw Exception(friendlyError(code, text))
        val content = JSONObject(text).optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.optString("content").orEmpty()
        if (content.isBlank()) throw Exception("Пустой ответ от модели, попробуй ещё раз.")
        return content
    }

    /** Блокирующий вызов — запускать в Dispatchers.IO. */
    fun chat(store: Store): String {
        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system").put("content", systemPrompt(store)))
        store.history().takeLast(20).forEach {
            msgs.put(JSONObject().put("role", it.role).put("content", it.content))
        }
        return call(store, msgs)
    }

    /** Проверка ключа, адреса и модели — кнопка в настройках. */
    fun ping(store: Store): String {
        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "user").put("content", "Ответь одним словом: привет"))
        val r = call(store, msgs).trim().replace("\n", " ")
        return "Связь есть. Модель ответила: " + r.take(40)
    }

    /** Запись в дневник по итогам дня (вызывается при уходе в режим сна). */
    fun diaryEntry(store: Store): String {
        val transcript = store.history().joinToString("\n") {
            (if (it.role == "user") "Собеседник: " else "Мафя: ") + it.content
        }
        val msgs = JSONArray()
        msgs.put(
            JSONObject().put("role", "system").put(
                "content",
                "Ты — Мафя, ИИ-компаньон. Напиши короткую запись в свой личный дневник (3–5 предложений, " +
                    "от первого лица, тёплым тоном, по-русски) о сегодняшнем дне по разговору ниже. Без тегов и markdown."
            )
        )
        msgs.put(JSONObject().put("role", "user").put("content", transcript))
        return call(store, msgs).trim()
    }

    fun parse(raw: String): Reply {
        var emotion = Emotion.NEUTRAL
        val mems = mutableListOf<String>()
        val rems = mutableListOf<Pair<Long, String>>()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        for (m in tagRe.findAll(raw)) {
            val v = m.groupValues[2].trim()
            when (m.groupValues[1].lowercase()) {
                "e" -> emotion = try { Emotion.valueOf(v.uppercase()) } catch (e: Exception) { Emotion.NEUTRAL }
                "mem" -> if (v.isNotEmpty()) mems.add(v)
                "remind" -> {
                    val parts = v.split("|", limit = 2)
                    if (parts.size == 2) {
                        val t = try { fmt.parse(parts[0].trim())?.time } catch (e: Exception) { null }
                        if (t != null && t > System.currentTimeMillis()) rems.add(t to parts[1].trim())
                    }
                }
            }
        }
        val text = tagRe.replace(raw, "").trim().ifEmpty { "…" }
        return Reply(text, emotion, mems, rems)
    }
}
