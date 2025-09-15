package com.example.rescriberkeyboard

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray

class RescriberInputMethodService :
    InputMethodService(),
    KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: KeyboardView
    private lateinit var qwertyKeyboard: Keyboard
    private lateinit var symbolsKeyboard: Keyboard
    private var showingSymbols = false
    private var isInitialized = false
    
    // UI components
    private lateinit var redactPIIButton: Button
    private lateinit var statusText: TextView
    
    // Network service for redaction
    private lateinit var redactionService: RedactionService
    
    // Prevent multiple simultaneous requests
    private var isRedactionInProgress = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "RescriberInputMethodService created")
    }

    override fun onCreateInputView(): View {
        Log.d(TAG, "Creating input view")
        
        try {
            // Set up the redaction service for API calls
            redactionService = RedactionService()

            val root = layoutInflater.inflate(R.layout.keyboard_view, null)
            keyboardView = root.findViewById(R.id.keyboardView)
            redactPIIButton = root.findViewById(R.id.redactPIIButton)
            statusText = root.findViewById(R.id.statusText)

            qwertyKeyboard = Keyboard(this, R.xml.qwerty)
            symbolsKeyboard = Keyboard(this, R.xml.symbols)

            keyboardView.keyboard = qwertyKeyboard
            keyboardView.setOnKeyboardActionListener(this)
            keyboardView.isPreviewEnabled = true
            keyboardView.isProximityCorrectionEnabled = true
            
            // Configure the redaction button click handler
            setupRedactionButton()
            
            isInitialized = true
            Log.d(TAG, "Keyboard view created successfully")
            
            return root
            
        } catch (e: Exception) {
            Log.e(TAG, "Critical error creating input view", e)
            e.printStackTrace()
            
            // Return a fallback view if something goes wrong
            return createFallbackView(e)
        }
    }
    
    override fun onStartInput(attribute: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        Log.d(TAG, "onStartInput called - restarting: $restarting")
        try {
            if (attribute != null) {
                Log.d(TAG, "Input type: ${attribute.inputType}, Package: ${attribute.packageName}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartInput", e)
        }
    }
    
    override fun onFinishInput() {
        super.onFinishInput()
        Log.d(TAG, "onFinishInput called")
    }


    private fun createFallbackView(error: Exception): View {
        Log.e(TAG, "Creating fallback view due to error: ${error.message}")
        return try {
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 32, 32, 32)
                
                addView(android.widget.Button(this@RescriberInputMethodService).apply {
                    text = "Rescriber Keyboard Error"
                    setBackgroundColor(0xFFFFCDD2.toInt())
                    setTextColor(0xFFD32F2F.toInt())
                    setPadding(16, 16, 16, 16)
                    setOnClickListener { 
                        Log.e(TAG, "Fallback keyboard clicked - Error: ${error.message}")
                    }
                })
                
                addView(android.widget.Button(this@RescriberInputMethodService).apply {
                    text = "Check Logs for Details"
                    setBackgroundColor(0xFFE3F2FD.toInt())
                    setTextColor(0xFF1976D2.toInt())
                    setPadding(16, 16, 16, 16)
                    setOnClickListener { 
                        Log.i(TAG, "Error details: ${error.message}")
                        Log.i(TAG, "Stack trace: ${error.stackTrace.joinToString("\n")}")
                    }
                })
            }
        } catch (fallbackError: Exception) {
            Log.e(TAG, "Even fallback view creation failed", fallbackError)
            // Return a minimal view
            android.widget.TextView(this).apply {
                text = "Keyboard Error"
                setPadding(32, 32, 32, 32)
            }
        }
    }

    /**
     * Configures the redaction button to handle clicks.
     * This is where the magic happens when users tap the redaction button.
     */
    private fun setupRedactionButton() {
        try {
            redactPIIButton.setOnClickListener {
                Log.d(TAG, "Redact PII button clicked")
                try {
                    handleRedactionRequest()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in button click handler", e)
                    e.printStackTrace()
                    updateStatusText("Error occurred. Try again.")
                }
            }
            
            // Set initial status message
            updateStatusText("Type your message...")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up redaction button", e)
            e.printStackTrace()
        }
    }
    
    /**
     * Handles when the user presses the redaction button.
     * Gets the current text, sends it to the backend, and replaces it with redacted version.
     */
    private fun handleRedactionRequest() {
        // Prevent multiple simultaneous requests
        if (isRedactionInProgress) {
            Log.w(TAG, "Redaction already in progress, ignoring request")
            updateStatusText("Already processing...")
            return
        }
        
        val ic = currentInputConnection ?: return
        
        // Get all text before cursor (the full message)
        val fullText = ic.getTextBeforeCursor(1000, 0)?.toString() ?: ""
        
        if (fullText.isBlank()) {
            Log.d(TAG, "No text to redact")
            updateStatusText("No text to redact")
            return
        }
        
        Log.d(TAG, "Redacting text: '$fullText'")
        updateStatusText("Rescriber 2.0 processing...")
        
        // Prevent multiple simultaneous redaction requests
        isRedactionInProgress = true
        
        // Disable the button and show processing state
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            redactPIIButton.isEnabled = false
            redactPIIButton.text = "Processing..."
        }
        
        // Call the redaction service to process the text
        redactionService.redactText(fullText) { result ->
            result.fold(
                onSuccess = { redactedText ->
                    Log.d(TAG, "Received redacted text: '$redactedText'")
                    
                    // Replace the original text with the redacted version
                    ic.deleteSurroundingText(fullText.length, 0)
                    ic.commitText(redactedText, 1)
                    
                    // Update the UI to show success
                    updateStatusText("Rescriber 2.0 complete! Press Enter to send.")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        redactPIIButton.isEnabled = true
                        redactPIIButton.text = "Rescriber 2.0 Redaction"
                        isRedactionInProgress = false
                    }
                    
                    Log.d(TAG, "Text replaced with redacted version")
                },
                onFailure = { error ->
                    Log.e(TAG, "Redaction failed: ${error.message}")
                    
                    // Update the UI to show the error
                    updateStatusText("Rescriber 2.0 failed. Try again.")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        redactPIIButton.isEnabled = true
                        redactPIIButton.text = "Rescriber 2.0 Redaction"
                        isRedactionInProgress = false
                    }
                    
                    Log.d(TAG, "Redaction failed: ${error.message}")
                }
            )
        }
    }
    
    /**
     * Updates the status text in a thread-safe way.
     * Makes sure UI updates happen on the main thread.
     */
    private fun updateStatusText(message: String) {
        try {
            // Make sure we update the UI on the main thread
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    statusText.text = message
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update status text on main thread", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post status text update", e)
        }
    }
    
    /**
     * Handles when the user presses Enter.
     * Just sends the message normally - the redaction should already be done.
     */
    private fun handleEnterKey() {
        val ic = currentInputConnection ?: return
        
        Log.d(TAG, "Enter pressed - sending message")
        updateStatusText("Sending message...")
        
        // Send the Enter key to submit the message
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        
        // Reset the status message after a short delay
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            updateStatusText("Type your message...")
        }, 1000)
    }





    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        Log.d(TAG, "onKey called with primaryCode: $primaryCode")
        
        val ic = currentInputConnection
        if (ic == null) {
            Log.w(TAG, "No input connection available")
            return
        }
        
        if (!isInitialized) {
            Log.w(TAG, "Keyboard not initialized, using basic functionality")
        }
        
        try {
            when (primaryCode) {
                Keyboard.KEYCODE_DELETE, -5 -> {
                    Log.d(TAG, "Delete key pressed")
                    ic.deleteSurroundingText(1, 0)
                }
                Keyboard.KEYCODE_SHIFT, -1 -> {
                    Log.d(TAG, "Shift key pressed")
                    toggleShift()
                }
                Keyboard.KEYCODE_MODE_CHANGE, -2, -10 -> {
                    Log.d(TAG, "Mode change key pressed")
                    toggleSymbols()
                }
                Keyboard.KEYCODE_DONE, -4 -> {
                    Log.d(TAG, "Enter key pressed")
                    handleEnterKey()
                }
                else -> {
                    try {
                        val c = primaryCode.toChar()
                        val out = if (keyboardView.isShifted) c.uppercaseChar() else c
                        Log.d(TAG, "Character key pressed: '$out'")
                        ic.commitText(out.toString(), 1)
                    } catch (charError: Exception) {
                        Log.e(TAG, "Error processing character", charError)
                        // Try a basic fallback approach
                        try {
                            val c = primaryCode.toChar()
                            ic.commitText(c.toString(), 1)
                        } catch (fallbackError: Exception) {
                            Log.e(TAG, "Character fallback failed", fallbackError)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onKey", e)
            e.printStackTrace()
            // Fall back to basic character input
            try {
                val c = primaryCode.toChar()
                ic.commitText(c.toString(), 1)
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Even fallback failed", fallbackError)
            }
        }
    }



    private fun toggleShift() {
        qwertyKeyboard.isShifted = !qwertyKeyboard.isShifted
        keyboardView.isShifted = qwertyKeyboard.isShifted
        keyboardView.invalidateAllKeys()
    }

    private fun toggleSymbols() {
        showingSymbols = !showingSymbols
        keyboardView.keyboard = if (showingSymbols) symbolsKeyboard else qwertyKeyboard
    }

    override fun onEvaluateFullscreenMode() = false
    override fun onPress(primaryCode: Int) {
        // Give the user a little vibration feedback when they press a key
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(10, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(10)
            }
        } catch (e: Exception) {
            // Don't worry if vibration fails
        }
    }
    
    override fun onRelease(primaryCode: Int) {}
    override fun onText(text: CharSequence?) {
        currentInputConnection?.commitText(text ?: "", 1)
    }
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeUp() {}
    override fun swipeDown() {}

    companion object { private const val TAG = "RescriberIME" }
}

fun loadLabels(context: Context): List<String> {
    return runCatching {
        context.assets.open("labels.json").bufferedReader().use { it.readText() }
    }.mapCatching { json ->
        val arr = JSONArray(json)
        List(arr.length()) { i -> arr.getString(i) }
    }.getOrElse {
        Log.w("RescriberIME", "labels.json missing or invalid; defaulting to all-O")
        emptyList()
    }
}