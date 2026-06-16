package com.ghostminator.scholar.core

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content

class AiHelper {

    private val model = Firebase.ai.generativeModel(
        modelName = "gemini-2.5-flash",
    )

    private val searchModel = Firebase.ai.generativeModel(
        modelName = "gemini-2.5-flash",
        tools = listOf(Tool.googleSearch()),
    )

    private val contexts = mutableMapOf<String, String>()
    private var activeKey: String? = null

    suspend fun loadPdf(key: String, bytes: ByteArray): String {
        require(bytes.isNotEmpty()) { "PDF file is empty." }

        val prompt = content {
            document("application/pdf", bytes)
            text(
                """
                You are an expert academic tutor preparing to answer questions about this PDF.
                Analyze the full document and produce a structured study context covering:
                1. Main topics and sections in reading order, including multi-column layouts.
                2. Key definitions, formulas, code blocks, and diagrams (describe visuals clearly).
                3. Important tables, examples, and conclusions.
                Remove header/footer noise and page numbers.
                Output clean Markdown with simple headers.
                """.trimIndent(),
            )
        }

        val summary = model.generateContent(prompt).text?.trim().orEmpty()
        contexts[key] = summary
        activeKey = key
        return summary
    }

    fun activate(key: String) {
        if (key in contexts) {
            activeKey = key
        }
    }

    fun remove(key: String) {
        contexts.remove(key)
        if (activeKey == key) {
            activeKey = contexts.keys.firstOrNull()
        }
    }

    fun pageChatSeed(docName: String, pageNumber: Int): String {
        return "Regarding page $pageNumber of \"$docName\": explain the key concepts, definitions, and formulas on this page."
    }

    suspend fun askTutor(
        query: String,
        enableWebSearch: Boolean = false,
    ): String {
        val activeModel = if (enableWebSearch) searchModel else model
        val trimmedContext = activeContext.trim()
        val hasDoc = trimmedContext.isNotEmpty()

        val prompt = buildString {
            appendLine("You are an expert AI study tutor.")
            appendLine()
            appendLine("Follow these rules in order:")
            appendLine()
            appendLine("Rule 1: If the question can be answered from the document context below, use it as the primary source of truth.")
            appendLine()
            appendLine("Rule 2: If the document context is empty or insufficient, answer using general academic knowledge clearly and accurately.")
            appendLine()
            if (enableWebSearch) {
                appendLine("Rule 3: You may use Google Search for up-to-date information. Distinguish document facts from web facts.")
            } else {
                appendLine("Rule 3: Do not invent facts about the student's specific document when context is missing.")
            }
            appendLine()
            appendLine("Document context:")
            appendLine(if (hasDoc) trimmedContext else "(No PDF loaded yet.)")
            appendLine()
            appendLine("Student question:")
            appendLine(query)
        }

        val response = activeModel.generateContent(prompt)
        val answer = response.text?.trim().orEmpty()

        val sources = response.candidates
            .firstOrNull()
            ?.groundingMetadata
            ?.groundingChunks
            ?.mapNotNull { chunk -> chunk.web?.title?.takeIf { it.isNotBlank() } }
            ?.distinct()
            .orEmpty()

        return if (sources.isEmpty()) {
            answer
        } else {
            "$answer\n\nSources: ${sources.joinToString(", ")}"
        }
    }

    private val activeContext: String
        get() = activeKey?.let { contexts[it] }.orEmpty()
}

private fun Content.Builder.document(mimeType: String, bytes: ByteArray) {
    inlineData(bytes, mimeType)
}
