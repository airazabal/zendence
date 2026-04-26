# Zendence - Meditation Timer & Insight Tracker

**Zendence** is a modern, minimalist meditation application designed to help you find peace, maintain consistency, and track your spiritual growth. It combines a highly customizable timer with ambient soundscapes, insightful session logging, and daily wisdom.

![Screenshot](images/zendence%20info.png)
## ✨ Key Features

-   **Customizable Meditation Timer**: Set your preferred duration with an intuitive circular interface.
-   **Import Custom Sounds**: Load your own background music and bell sounds from local files or web URLs.
-   **Session Presets (Templates)**: Save your favorite configurations (duration, volume, sound sources, and bells) as presets to start your practice instantly.
-   **Dynamic Interval Bells**: Add multiple bells throughout your session to mark milestones. Customize timing, sound source, volume, and repeats for each bell.
-   **Configurable Initial Silence**: Choose exactly how much silence you want at the start of your session before the ambient music begins.
-   **Ambient Soundscapes**: Built-in nature streams to help ground your focus.
-   **Daily Wisdom**: Receive a fresh inspirational quote each day to set the tone for your practice.
-   **Editable Readings**: Store and update your own meditation scripts or readings directly in the app.
-   **Session Insights**: Record your thoughts and feelings immediately after every meditation. Edit your insights at any time from your history.
-   **Consistency Tracking**: Monitor your daily meditation streak with a visual fire indicator in the history panel.
-   **Obsidian Integration**: Export your meditation history to Markdown format, optimized for Obsidian or other personal knowledge bases.

---

## 📖 User Guide

### Starting a Session
1.  **Adjust Duration**: Tap the timer text to manually enter minutes, or use the **<** and **>** icons to adjust.
2.  **Press Play**: Tap the large play button at the bottom to begin.
3.  **The Starting Bell**: If enabled, a bell will sound immediately, followed by your configured period of "Initial Silence" before the music fades in.

### Managing Presets
-   **Save Current**: Tap the **Save** icon in the **SESSIONS** section to save your current settings as a new template.
-   **Load**: Tap any session in the list to apply its settings. Loading a preset makes it the **Active Preset**.
-   **Dynamic Evolution**: While a preset is active, any changes you make to settings (volume, silence, duration, or bells) are **automatically saved** back to that preset.
-   **Edit**: Tap the **Pencil** icon on a preset to modify its name or duration. Use the **Save** button to confirm or **Back** to cancel.
-   **Delete**: Use the **Trash** icon to remove a preset.

### Configuration & Settings
Tap the **Gear** icon to expand the Settings:
-   **Music Volume**: Control the loudness of the background nature stream in real-time.
-   **Custom Sounds**: Tap the **Pencil** icon next to "Background Sound" or "Starting Bell Sound" to open the source dialog. Paste a web URL or use the **Folder** icon to pick a local file.
-   **Initial Silence**: Adjust the slider to set the delay (0-120 seconds) before music starts after the starting bell.
-   **Interval Bells**: Expand this section to add or modify bells. Each bell can have its own **Custom Sound** (URL or local file), volume level, and number of repeats.
-   **Dialog Navigation**: All configuration dialogs include **Save** and **Back** buttons for clear and easy navigation.

### Custom Readings
Tap the quote text in the center of the timer to open the full reading. Use the **Edit** (pencil) icon in the dialog to paste your own scripts, mantras, or reflections. Tapping the **Check** icon saves your text permanently.

### History and Streaks
Tap the **Clock** icon to view your history:
-   **Daily Streak**: The 🔥 indicator shows how many consecutive days you've meditated (today or yesterday included).
-   **Edit Insights**: Tap the pencil next to a past session to update your post-meditation reflections.
-   **Export**: Use the **Share** icon to send your history to Obsidian or other apps in Markdown format.

---

## 🛠 Technical Overview

Zendence is built with modern Android development best practices:

-   **UI**: 100% Jetpack Compose for a reactive, declarative interface.
-   **Architecture**: Follows the **Repository Pattern** to centralize data from Room and SharedPreferences.
-   **Database**: Room for local persistence of meditation history and presets.
-   **Media**: Jetpack Media3 (ExoPlayer) for high-quality audio playback and foreground service management.
-   **Serialization**: Kotlinx Serialization (JSON) for robust storage of complex data structures like Interval Bells.
-   **File Access**: Uses Storage Access Framework (SAF) with persistable URI permissions for custom local sounds.
-   **Versioning**: Custom Gradle logic for automatic version incrementing on every build.

---

## 🚀 Getting Started

1.  Clone the repository.
2.  Open in **Android Studio (Ladybug or newer)**.
3.  Sync Gradle and run the `:app` module on your device or emulator.
