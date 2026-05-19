package com.alex.zendence

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.RequestOptions

class AiIntelligence(private val apiKey: String?) {

    private fun createModel(name: String, version: String?) = if (!apiKey.isNullOrBlank()) {
        GenerativeModel(
            modelName = name.trim(),
            apiKey = apiKey.trim(),
            requestOptions = version?.let { RequestOptions(apiVersion = it) } ?: RequestOptions()
        )
    } else null

    suspend fun analyzeInsights(history: List<Meditation>): String {
        if (apiKey.isNullOrBlank()) return "❌ AI analysis requires a Gemini API key. Please add it in Settings > Gemini AI Intelligence."
        
        val insightsWithText = history.filter { !it.insight.isNullOrBlank() }
        val insightsText = insightsWithText
            .take(20) 
            .joinToString("\n") { "- ${it.durationMinutes}min: ${it.insight}" }

        if (insightsText.isBlank()) {
            return "📭 No insights recorded. Tap the pencil in History to add some reflections!"
        }

        val prompt = """
            Analyze my meditation history and provide:
            1. Progress summary.
            2. Duration/mood patterns.
            3. Recommendation for next session.
            
            History:
            $insightsText
        """.trimIndent()

        // Comprehensive list of models. Updated for 2026 availability.
        // Updated for May 2026: Google is transitioning to Gemini 2.5 and 3.0 series.
        // 2.0 models are currently restricted for new users.
        val attempts = listOf(
            "gemini-2.5-flash" to "v1beta",
            "gemini-2.5-flash" to "v1",
            "gemini-2.5-pro" to "v1beta",
            "gemini-1.5-flash" to "v1",
            "gemini-1.5-pro" to "v1",
            "gemini-2.0-flash-exp" to "v1beta"
        )
        
        val keySnippet = if ((apiKey?.length ?: 0) > 6) "${apiKey?.take(4)}...${apiKey?.takeLast(2)}" else "Invalid/Short"
        val errorLog = mutableListOf<String>()
        // We'll add the debug info to the final output instead of the loop check list
        val debugInfo = "Debug Key: $keySnippet"

        for ((modelName, version) in attempts) {
            try {
                val model = createModel(modelName, version) ?: continue
                val response = model.generateContent(prompt)
                val result = response.text
                if (!result.isNullOrBlank()) return result
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Unknown error"
                errorLog.add("[$modelName/${version ?: "v1"}]: $msg")
                
                // Continue if it's a 404, 400 or specific "not found" error
                if (msg.contains("404") || msg.contains("400") || msg.contains("not found", ignoreCase = true)) {
                    continue
                }
                
                // For critical errors (Auth, Quota), stop immediately
                if (msg.contains("401") || msg.contains("403") || msg.contains("API_KEY") || msg.contains("QUOTA")) {
                    break
                }
            }
        }

        val lastError = errorLog.lastOrNull() ?: "No attempts made"
        val fullLog = "$debugInfo\n${errorLog.joinToString("\n")}"
        
        return when {
            lastError.contains("API_KEY_INVALID") || lastError.contains("401") -> "❌ Invalid API Key. Please re-generate your key in Google AI Studio and ensure no extra spaces are added.\n\n$debugInfo"
            lastError.contains("prepayment credits") || lastError.contains("depleted") -> 
                "💳 Your Google AI Studio prepayment credits are depleted.\n\n" +
                "Even on the free tier, users in the EU/UK/CH often need an active 'Pay-as-you-go' billing plan with a positive balance to access the API.\n\n" +
                "✅ Fix:\n" +
                "1. Go to aistudio.google.com > Settings > Billing.\n" +
                "2. Ensure your payment method is active and check if a small prepayment (\$5-\$10) is required to 'wake up' the project for your region.\n" +
                "3. If you just enabled billing, it may take up to an hour for Google to sync your status.\n\n" +
                "Technical Log:\n$fullLog"
            lastError.contains("QUOTA_EXCEEDED") || lastError.contains("429") -> "⏳ API Quota exceeded. Please wait a minute (Free tier limit reached).\n\n$debugInfo"
            errorLog.isNotEmpty() && errorLog.all { it.contains("404") || it.contains("400") || it.contains("not found") } -> 
                "❗ All models returned 404 Not Found or 400 Bad Request.\n\n" +
                "This almost always means the Gemini API is restricted in your region (very common in the EU, UK, and Switzerland).\n\n" +
                "✅ Solution for EU/UK users:\n" +
                "1. Go to aistudio.google.com\n" +
                "2. In Settings, link a billing account (the 'Pay-as-you-go' plan).\n" +
                "3. This 'unlocks' the models for the API in restricted regions. You will not be charged as long as you stay within the free tier limits (usually \$0.00).\n\n" +
                "Technical Log:\n$fullLog"
            else -> "❗ AI Error: $lastError\n\nFull Log:\n$fullLog"
        }
    }
}
