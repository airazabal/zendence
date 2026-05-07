package com.alex.zendence

import com.google.ai.client.generativeai.GenerativeModel

class AiIntelligence(private val apiKey: String?) {

    private val model = if (!apiKey.isNullOrBlank()) {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKey
        )
    } else null

    suspend fun analyzeInsights(history: List<Meditation>): String {
        if (model == null) return "AI analysis requires a Gemini API key. Add it in settings to see your trends."
        
        val insightsText = history.filter { !it.insight.isNullOrBlank() }
            .take(20) // Limit to last 20 insights
            .joinToString("\n") { "- ${it.durationMinutes}min: ${it.insight}" }

        if (insightsText.isBlank()) return "No insights recorded yet. Keep meditating to see your patterns!"

        val prompt = """
            Below is a history of my meditation sessions with their duration and my post-session insights.
            Please analyze these and provide:
            1. A short summary of my overall mood and progress.
            2. Any patterns you see between session duration and my insights.
            3. A personalized recommendation for my next session.
            
            Keep the response encouraging, concise, and insightful.
            
            History:
            $insightsText
        """.trimIndent()

        return try {
            val response = model.generateContent(prompt)
            response.text ?: "Could not generate analysis."
        } catch (e: Exception) {
            "Error analyzing insights: ${e.localizedMessage}"
        }
    }
}
