# Zendence - Meditation Timer & Insight Tracker

**Zendence** is a modern, minimalist meditation application designed to help you find peace, maintain consistency, and track your spiritual growth. It combines a highly customizable timer with ambient soundscapes, insightful session logging, and daily wisdom.

![Screenshot](images/zendence_info.png)
## ✨ Key Features

-   **Customizable Meditation Timer**: Set your preferred duration with an intuitive circular interface.
-   **Import Custom Sounds**: Load your own background music and bell sounds from local files or web URLs.
-   **Session Presets (Templates)**: Save your favorite configurations (duration, volume, sound sources, and bells) as presets to start your practice instantly.
-   **Dynamic Interval Bells**: Add multiple bells throughout your session to mark milestones. Customize timing, sound source, volume, and repeats for each bell.
-   **Configurable Initial Silence**: Choose exactly how much silence you want at the start of your session before the ambient music begins.
-   **Ambient Soundscapes**: Built-in nature streams to help ground your focus.
-   **AI Intelligence (Gemini 2.5/3.0)**: Integrates with the latest Google Gemini models to analyze your meditation insights over time, providing summaries of your progress and personalized recommendations.
-   **Daily Wisdom**: Receive a fresh inspirational quote each day to set the tone for your practice.
-   **Intelligent Reminders**: Receive daily notifications with up-to-date stats including your current streak and weekly meditation minutes.
-   **Editable Readings**: Store and update your own meditation scripts or readings directly in the app.
-   **Session Insights**: Record your thoughts and feelings immediately after every meditation. Edit your insights at any time from your history.
-   **Consistency Tracking**: Monitor your daily meditation streak with a visual fire indicator in the history panel.
-   **Backup & Restore**: Export all your settings, custom sound URLs, presets, and meditation history to a local JSON file.
-   **Obsidian Integration**: Export your meditation history to Markdown format, optimized for Obsidian or other personal knowledge bases.

---
![Screenshot](images/zendence_main.png)
## 📖 User Guide

### Starting a Session
1.  **Adjust Duration**: Tap the timer text to manually enter minutes, or use the **<** and **>** icons to adjust.
2.  **Press Play**: Tap the large play button at the bottom to begin.
3.  **The Starting Bell**: If enabled, a bell will sound immediately, followed by your configured period of "Initial Silence" before the music fades in.

### Managing Presets
-   **Save Current**: Tap the **Save** icon in the **SESSIONS** section to save your current settings as a new template.
-   **Load**: Tap any session in the list to apply its settings. Loading a preset makes it the **Active Preset**.
-   **Dynamic Evolution**: While a preset is active, any changes you make to settings (volume, silence, duration, or bells) are **automatically saved** back to that preset.
-   **Edit/Delete**: Use the pencil or trash icons to manage your presets.

### Configuration & Settings
Tap the **Gear** icon to expand the Settings:
-   **AI Intelligence**: Enter your Gemini API key (get one for free at aistudio.google.com).
-   **Music Volume**: Control the loudness of the background nature stream in real-time.
-   **Custom Sounds**: Tap the **Pencil** icon next to "Background Sound" or "Starting Bell Sound" to use local files or web URLs.
-   **Initial Silence**: Adjust the slider (0-120 seconds) for the delay before music starts.
-   **Interval Bells**: Expand this section to add or modify bells. Each bell can have its own sound source, volume, and repeats.
-   **Backup & Restore**: Use **Export** and **Import** at the bottom of the Settings panel to manage your entire configuration as a JSON file.

### AI Trends & Insights
-   **Unlock Patterns**: Tap the **AI Insights & Trends** card above the history section to generate deep analysis.
-   **Copy to Clipboard**: Use the **Copy icon** at the top-right of the AI results to share or save your analysis elsewhere.
-   **Model Auto-Switching**: The engine automatically cycles through available Gemini models (2.5 Flash, 2.5 Pro, 1.5 Flash) to ensure consistent service regardless of regional restrictions.
-   **Diagnostic Tools**: Includes a built-in technical log and debug key viewer to troubleshoot API issues (like regional billing restrictions) directly in-app.

### Custom Readings
Tap the quote text in the center of the timer to open the full reading. Use the **Edit** (pencil) icon to paste your own scripts, mantras, or reflections.

### History and Streaks
Tap the **Clock** icon to view your history:
-   **Daily Streak**: The 🔥 indicator shows your consecutive meditation days.
-   **Edit Insights**: Tap the pencil next to a past session to update your reflections.
-   **Export**: Use the **Share** icon to send your history to Obsidian in Markdown format.

---

## 🛠 Technical Overview

Zendence is built with modern Android development best practices:

-   **UI**: 100% Jetpack Compose for a reactive, declarative interface.
-   **Architecture**: MVVM with Repository Pattern, integrating Room and SharedPreferences.
-   **Database**: Room for local persistence of history and presets.
-   **Media**: Jetpack Media3 (ExoPlayer) for audio playback and foreground service management.
-   **AI**: Google Generative AI SDK with support for Gemini 2.5/3.0 and intelligent regional error handling.
-   **Background Tasks**: WorkManager for reliable scheduling of daily intelligent notifications.
-   **Serialization**: Kotlinx Serialization (JSON) for robust storage of complex data structures.
-   **File Access**: Storage Access Framework (SAF) with persistable URI permissions for custom local sounds.

---

## 🚀 Getting Started

1.  Clone the repository.
2.  Open in **Android Studio (Ladybug or newer)**.
3.  Sync Gradle and run the `:app` module on your device or emulator.
