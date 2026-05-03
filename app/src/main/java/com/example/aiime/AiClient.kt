package com.example.aiime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class AiClient {
    private val apiKey = BuildConfig.OPENROUTER_API_KEY

    suspend fun generateSuggestions(contextText: String): List<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext defaultSuggestions()
        }

        try {
            val responseText = executeRequest(contextText)
            parseSuggestionsFromResponse(responseText)
        } catch (_: Exception) {
            defaultSuggestions()
        }
    }

    private fun executeRequest(contextText: String): String {
        val requestJson = JSONObject()
            .put("model", "openrouter/free")
            .put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put(
                                "content",
                                """
                                你是中文输入法续写引擎。
                                根据用户已经输入的内容，仅生成后续补全内容。
                                不要重复用户已经输入的前缀。
                                不要解释，不要编号，不要 Markdown。
                                必须只返回 JSON 数组。
                                数组长度必须为 3。
                                每条内容要自然、简短、可直接插入输入框。
                                如果用户输入像“老师您好，我今天”，你应该返回类似：
                                [
                                  "身体有点不舒服，想请假一天。",
                                  "可能会晚一点到实验室。",
                                  "想咨询一下作业提交时间。"
                                ]
                                """.trimIndent()
                            )
                    )
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", "用户已输入：$contextText")
                    )
            )
            .put("temperature", 0.7)

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://localhost")
            .addHeader("X-Title", "AiKeyboardMvp")
            .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        CLIENT.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("OpenRouter request failed: ${response.code}")
            }

            return response.body?.string() ?: error("OpenRouter response body is empty")
        }
    }

    private fun parseSuggestionsFromResponse(responseText: String): List<String> {
        val content = JSONObject(responseText)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")

        return parseSuggestionArray(content)
    }

    private fun parseSuggestionArray(content: String): List<String> {
        val cleanedContent = cleanModelContent(content)

        return parseJsonArrayOrNull(cleanedContent)
            ?: extractJsonArrayText(cleanedContent)?.let { parseJsonArrayOrNull(it) }
            ?: defaultSuggestions()
    }

    private fun cleanModelContent(content: String): String {
        var text = content.trim()

        if (text.startsWith("```json", ignoreCase = true)) {
            text = text.removePrefix("```json").trim()
        } else if (text.startsWith("```")) {
            text = text.removePrefix("```").trim()
        }

        if (text.endsWith("```")) {
            text = text.removeSuffix("```").trim()
        }

        return text
    }

    private fun extractJsonArrayText(content: String): String? {
        val start = content.indexOf('[')
        val end = content.lastIndexOf(']')
        if (start < 0 || end <= start) {
            return null
        }

        return content.substring(start, end + 1)
    }

    private fun parseJsonArrayOrNull(text: String): List<String>? {
        return try {
            val jsonArray = JSONArray(text)
            val suggestions = buildList {
                for (index in 0 until jsonArray.length()) {
                    val suggestion = jsonArray.optString(index).trim()
                    if (suggestion.isNotBlank()) {
                        add(suggestion)
                    }
                }
            }

            if (suggestions.size == 3) {
                suggestions
            } else {
                suggestions.take(3).ifEmpty { null }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun defaultSuggestions(): List<String> {
        return listOf(
            "身体有点不舒服，想请假一天。",
            "可能会晚一点到实验室。",
            "想咨询一下作业提交时间。"
        )
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val CLIENT = OkHttpClient()
    }
}
