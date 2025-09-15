package com.example.rescriberkeyboard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Main activity for testing the RescriberKeyboard.
 * This is basically a debug screen to help users set up and test the keyboard.
 */
class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "RescriberMain"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity created")
        
        // Set up the debug UI - this is what users see when they open the app
        createDebugUI()
    }
    
    private fun createDebugUI() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        // Main title for the debug screen
        val title = TextView(this).apply {
            text = "RescriberKeyboard Debug"
            textSize = 24f
            setPadding(0, 0, 0, 32)
        }
        layout.addView(title)
        
        // Instructions for users on how to set up and use the keyboard
        val instructions = TextView(this).apply {
            text = """
                To use this keyboard:
                1. Go to Settings > System > Languages & Input
                2. Tap "Virtual Keyboard" > "Manage Keyboards"
                3. Enable "RescriberKeyboard"
                4. Set it as your default keyboard
                
                How it works:
                - Type your message normally
                - Press "Rescriber 2.0 Redaction" button to redact sensitive information
                - Review the redacted text
                - Press Enter to send the message normally
                
                Test PII redaction by typing:
                - Email: test@example.com
                - Phone: (555) 123-4567
                - SSN: 123-45-6789
            """.trimIndent()
            textSize = 14f
            setPadding(0, 0, 0, 32)
        }
        layout.addView(instructions)
        
        // Text input field for testing the redaction functionality
        val testInput = EditText(this).apply {
            hint = "Type here to test PII redaction..."
            textSize = 16f
            setPadding(0, 0, 0, 16)
        }
        layout.addView(testInput)
        
        // Button to open Android keyboard settings
        val settingsButton = Button(this).apply {
            text = "Open Keyboard Settings"
            setOnClickListener {
                try {
                    val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open settings", e)
                }
            }
        }
        layout.addView(settingsButton)
        
        // Button to test the redaction service with a sample text
        val testButton = Button(this).apply {
            text = "Test Redaction Service"
            setOnClickListener {
                testRedactionService()
            }
        }
        layout.addView(testButton)
        
        // Button to test the backend API directly (synchronous call)
        val testBackendButton = Button(this).apply {
            text = "Test Backend Directly"
            setOnClickListener {
                testBackendDirectly()
            }
        }
        layout.addView(testBackendButton)
        
        // Status text to show test results
        val statusText = TextView(this).apply {
            id = R.id.statusText
            text = "Ready to test..."
            textSize = 14f
            setPadding(0, 16, 0, 0)
        }
        layout.addView(statusText)
        
        setContentView(layout)
    }
    
    private fun testRedactionService() {
        try {
            // Create a new redaction service instance
            val redactionService = RedactionService()
            // Test with some sample text that contains PII
            val testText = "Hello world test@example.com"
            
            Log.d(TAG, "Testing Rescriber 2.0 with: '$testText'")
            
            // Call the redaction service asynchronously
            redactionService.redactText(testText) { result ->
                result.fold(
                    onSuccess = { redactedText ->
                        Log.d(TAG, "Rescriber 2.0 test successful!")
                        Log.d(TAG, "Original: '$testText'")
                        Log.d(TAG, "Redacted: '$redactedText'")
                        
                        // Update the UI on the main thread with success message
                        runOnUiThread {
                            val statusText = findViewById<TextView>(R.id.statusText)
                            statusText?.text = "Test successful: $redactedText"
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Rescriber 2.0 test failed: ${error.message}")
                        error.printStackTrace()
                        
                        // Update the UI on the main thread with error message
                        runOnUiThread {
                            val statusText = findViewById<TextView>(R.id.statusText)
                            statusText?.text = "Test failed: ${error.message}"
                        }
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Rescriber 2.0 test failed", e)
            e.printStackTrace()
        }
    }
    
    private fun testBackendDirectly() {
        try {
            // Test the backend API with a synchronous call
            val redactionService = RedactionService()
            val testText = "Hello world"
            
            Log.d(TAG, "Testing backend directly with: '$testText'")
            
            // Use the synchronous method for direct testing (blocks until response)
            val result = redactionService.redactTextSync(testText)
            
            result.fold(
                onSuccess = { redactedText ->
                    Log.d(TAG, "Backend test successful!")
                    Log.d(TAG, "Original: '$testText'")
                    Log.d(TAG, "Redacted: '$redactedText'")
                    
                    // Update UI with success message
                    runOnUiThread {
                        val statusText = findViewById<TextView>(R.id.statusText)
                        statusText?.text = "Backend works: $redactedText"
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Backend test failed: ${error.message}")
                    error.printStackTrace()
                    
                    // Update UI with error message
                    runOnUiThread {
                        val statusText = findViewById<TextView>(R.id.statusText)
                        statusText?.text = "Backend failed: ${error.message}"
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Backend test failed", e)
            e.printStackTrace()
        }
    }
}
