package com.cashmemer.ui.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One saved conversation. Kept only on this phone. */
data class SavedChat(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messages: List<ChatMessage>,
    /** The conversation as Gemini sees it, so a reopened chat carries on in context. */
    val gemini: String,
)

object AiChatHistory {
    private const val PREFS = "ai_chat_history"
    private const val KEY = "chats"
    const val LIMIT = 20

    fun load(context: Context): List<SavedChat> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        return runCatching {
            val chats = JSONArray(raw)
            (0 until chats.length()).map { i ->
                val o = chats.getJSONObject(i)
                val messages = o.getJSONArray("messages")
                SavedChat(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    updatedAt = o.getLong("updatedAt"),
                    messages = (0 until messages.length()).map { j ->
                        val m = messages.getJSONObject(j)
                        ChatMessage(fromUser = m.getBoolean("fromUser"), text = m.getString("text"))
                    },
                    gemini = o.getString("gemini"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, chats: List<SavedChat>) {
        val array = JSONArray()
        chats.take(LIMIT).forEach { chat ->
            val messages = JSONArray()
            chat.messages.forEach { m ->
                messages.put(JSONObject().put("fromUser", m.fromUser).put("text", m.text))
            }
            array.put(
                JSONObject()
                    .put("id", chat.id)
                    .put("title", chat.title)
                    .put("updatedAt", chat.updatedAt)
                    .put("messages", messages)
                    .put("gemini", chat.gemini)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, array.toString()).apply()
    }
}
