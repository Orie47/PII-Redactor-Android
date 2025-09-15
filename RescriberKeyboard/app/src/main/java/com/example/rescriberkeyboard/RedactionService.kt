package com.example.rescriberkeyboard

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Handles communication with the redaction backend API.
 * This service sends text to the server and gets back redacted versions.
 */
class RedactionService {
    
    companion object {
        private const val TAG = "RedactionService"
        private const val BASE_URL = "https://pii-redactor-if5e.onrender.com"
        private const val REDACT_ENDPOINT = "/redact"
        private const val TIMEOUT_SECONDS = 10L
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    
    private val gson = Gson()
    
    /**
     * Request data structure for the API call
     */
    data class RedactionRequest(
        @SerializedName("text")
        val text: String
    )
    
    /**
     * Response data structure from the API
     */
    data class RedactionResponse(
        @SerializedName("redacted")
        val redactedText: String?
    )
    
    /**
     * Sends text to the backend for redaction.
     * This is the main method that handles the async API call.
     * @param text The text that needs to be redacted
     * @param callback Function to call when the request completes
     */
    fun redactText(text: String, callback: (Result<String>) -> Unit) {
        if (text.isBlank()) {
            Log.w(TAG, "Empty text provided for redaction")
            callback(Result.failure(IllegalArgumentException("Text cannot be empty")))
            return
        }
        
        Log.d(TAG, "Sending text for redaction: '$text'")
        
        try {
            val requestBody = gson.toJson(RedactionRequest(text))
                .toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url("$BASE_URL$REDACT_ENDPOINT")
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "RescriberKeyboard/1.0")
                .build()
            
            Log.d(TAG, "Making request to: ${request.url}")
            Log.d(TAG, "Request body: $requestBody")
            
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Network request failed", e)
                    e.printStackTrace()
                    callback(Result.failure(e))
                }
                
                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            Log.d(TAG, "Response received - Code: ${it.code}, Message: ${it.message}")
                            Log.d(TAG, "Response headers: ${it.headers}")
                            
                            if (it.isSuccessful) {
                                val responseBody = it.body?.string()
                                Log.d(TAG, "Raw response body: $responseBody")
                                Log.d(TAG, "Response body length: ${responseBody?.length ?: 0}")
                                
                                if (responseBody != null && responseBody.isNotBlank()) {
                                    try {
                                        val redactionResponse = gson.fromJson(responseBody, RedactionResponse::class.java)
                                        Log.d(TAG, "Parsed redacted text: '${redactionResponse.redactedText}'")
                                        
                                        // Make sure we got valid redacted text back
                                        val redactedText = redactionResponse.redactedText
                                        if (redactedText != null && redactedText.isNotBlank()) {
                                            callback(Result.success(redactedText))
                                        } else {
                                            Log.e(TAG, "Null or empty redacted text in response")
                                            Log.e(TAG, "Response object: $redactionResponse")
                                            callback(Result.failure(IllegalStateException("Null or empty redacted text in response")))
                                        }
                                    } catch (parseError: Exception) {
                                        Log.e(TAG, "JSON parsing error", parseError)
                                        Log.e(TAG, "Response that failed to parse: $responseBody")
                                        callback(Result.failure(parseError))
                                    }
                                } else {
                                    Log.e(TAG, "Empty or null response body")
                                    callback(Result.failure(IllegalStateException("Empty response body")))
                                }
                            } else {
                                val errorBody = it.body?.string()
                                Log.e(TAG, "HTTP error: ${it.code} - ${it.message}")
                                Log.e(TAG, "Error response body: $errorBody")
                                callback(Result.failure(IOException("HTTP ${it.code}: ${it.message}")))
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing response", e)
                        e.printStackTrace()
                        callback(Result.failure(e))
                    }
                }
            })
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating request", e)
            e.printStackTrace()
            callback(Result.failure(e))
        }
    }
    
    /**
     * Synchronous version of the redaction call.
     * Used for testing - blocks until the response comes back.
     */
    fun redactTextSync(text: String): Result<String> {
        if (text.isBlank()) {
            return Result.failure(IllegalArgumentException("Text cannot be empty"))
        }
        
        Log.d(TAG, "Synchronously sending text for redaction: '$text'")
        
        return try {
            val requestBody = gson.toJson(RedactionRequest(text))
                .toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url("$BASE_URL$REDACT_ENDPOINT")
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()
            
            val response = client.newCall(request).execute()
            
            response.use {
                if (it.isSuccessful) {
                    val responseBody = it.body?.string()
                    if (responseBody != null) {
                        val redactionResponse = gson.fromJson(responseBody, RedactionResponse::class.java)
                        Log.d(TAG, "Synchronous redacted text: '${redactionResponse.redactedText}'")
                        
                        val redactedText = redactionResponse.redactedText
                        if (redactedText != null && redactedText.isNotBlank()) {
                            Result.success(redactedText)
                        } else {
                            Result.failure(IllegalStateException("Null or empty redacted text"))
                        }
                    } else {
                        Result.failure(IllegalStateException("Empty response body"))
                    }
                } else {
                    Result.failure(IOException("HTTP ${it.code}: ${it.message}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Synchronous request failed", e)
            Result.failure(e)
        }
    }
}
