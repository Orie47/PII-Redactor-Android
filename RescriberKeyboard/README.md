# Rescriber Keyboard

An Android keyboard that helps protect your privacy by automatically redacting sensitive information from your messages before you send them.

## What it does

This keyboard integrates with a backend service that identifies and removes personally identifiable information (PII) from your text. Things like email addresses, phone numbers, and social security numbers get replaced with placeholder text so you can send messages without accidentally sharing sensitive data.

## How to use it

1. Type your message like normal
2. Tap the "Rescriber 2.0 Redaction" button
3. Wait for it to process your text
4. Hit Enter to send the redacted message

## Installation

1. Build the APK: `./gradlew assembleDebug`
2. Install on your Android device
3. Go to Settings > Language & Input and enable the keyboard

## Backend API

The keyboard connects to a backend service for text processing:

- Endpoint: `https://pii-redactor-if5e.onrender.com/redact`
- Method: POST
- Request format: `{"text": "your message"}`
- Response format: `{"redacted": "redacted message"}`

## Requirements

- Android 7.0 or higher
- Internet connection (needed for the redaction service)