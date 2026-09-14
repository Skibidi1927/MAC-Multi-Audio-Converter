[readme.md](https://github.com/user-attachments/files/32206123/readme.md)
# MAC-Multi-Audio-Converter
A high-performance, multi-threaded batch audio converter built in Java with a macOS-style dark mode UI.
# 🍏 MAC (Multi Audio Converter) v1.0.0

**By Karin**

MAC is a high-performance, multi-threaded batch audio processing tool built with Java. It wraps the raw power of `ffmpeg` in a sleek, macOS-inspired Dark Mode UI, while seamlessly integrating with native Windows Explorer behaviors. 

Designed for heavy-duty audio workflows, it is perfect for prepping stem kits, normalizing sample packs, or quickly generating osu! hitsounds without needing to manually open a DAW like Audacity.

## ✨ Features
* **Multi-Core Batching:** Utilizes Java `ExecutorService` to process massive folders of audio concurrently across all CPU threads.
* **Smart Silence Trimming:** Auto-detects and strips dead air (-50dB) from the start and end of tracks.
* **Auto-Balance Volume (Loudnorm):** Automatically normalizes tracks to a consistent -1.0dB peak.
* **Batch Metadata Editor:** Custom Audacity-style tag editor. Supports dynamic `{count}` variables for sequential auto-renaming (e.g., `Track {count}`).
* **Native OS Integration:** Features a completely custom `JTable` file selector that natively mimics Windows Explorer (supports Shift+Click ranges, Ctrl+Click toggling, column sorting, and double-click audio preview).
* **Spectrogram Analysis:** Generates 2.5D visual spectrogram comparisons between your original track and the estimated export.

## 🛠️ Tech Stack & Dependencies
* **Language:** Java (Swing)
* **Engine:** FFmpeg (CLI)
* **UI Theme:** [FlatLaf](https://www.formdev.com/flatlaf/) (FlatMacDarkLaf)

## 🚀 Installation & Usage
1. Ensure Java 8+ is installed.
2. The application requires `ffmpeg` to run. If it is not detected on your system, MAC will prompt an automatic installation via Windows Package Manager (`winget`).
3. Add `flatlaf-3.7.2.jar` to your build path.
4. Run `MainUI.java` to launch.
