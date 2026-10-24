package com.example.data.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Handles executing tool calls requested by Gemini Live or REST API.
 */
class ToolExecutor(private val context: Context) {

    companion object {
        private const val TAG = "ToolExecutor"
    }

    /**
     * Executes `openWebsite` tool call.
     */
    fun openWebsite(rawUrl: String): ToolExecutionResult {
        return try {
            var url = rawUrl.trim()
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolExecutionResult(
                toolName = "openWebsite",
                success = true,
                message = "Opened $url",
                clientBadge = "Opening $url 🌐"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open URL: $rawUrl", e)
            ToolExecutionResult(
                toolName = "openWebsite",
                success = false,
                message = "Failed to open $rawUrl: ${e.message}",
                clientBadge = "Failed to open link"
            )
        }
    }

    /**
     * Executes `googleSearch` tool call or fallback browser search.
     */
    fun openSearch(query: String): ToolExecutionResult {
        return try {
            val encodedQuery = Uri.encode(query)
            val searchUrl = "https://www.google.com/search?q=$encodedQuery"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolExecutionResult(
                toolName = "googleSearch",
                success = true,
                message = "Searched Google for '$query'",
                clientBadge = "Searching: $query 🔍"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search query: $query", e)
            ToolExecutionResult(
                toolName = "googleSearch",
                success = false,
                message = "Failed to search: ${e.message}",
                clientBadge = "Search failed"
            )
        }
    }
}

data class ToolExecutionResult(
    val toolName: String,
    val success: Boolean,
    val message: String,
    val clientBadge: String
)
