# myFit - Personal Workout Tracker & Memo (V4.1)
**myFit** is a personal fitness tracking and memo application built with Android Jetpack Compose. It is designed to help users plan weekly training routines, log daily check-ins, and track workout and weight changes through visualized history records.

🚀 **New in V4.1**: Full support for 5 languages, visual weekly routine scheduling, and deep integration of weight data.

---

## ✨ Features

* **🌍 Internationalization (i18n)**
* Full support for **English, Simplified Chinese, Deutsch, Español, and 日本語**.
* One-tap language switching within the app (changes apply instantly without a manual restart).


* **📅 Smart Weekly Routine**
* **Type Planning**: Assign training types (e.g., Core Day, Active Rest, Rest Day) to each day of the week.
* **Routine Planning**: A visual interface to manually schedule specific exercises for fixed days (e.g., automatically schedule "Squats" every "Monday").


* **✅ Daily Check-in**
* Automatically generates today's to-do list based on your weekly routine.
* **Pill-shaped Button**: Interactive check-in buttons that trigger a 🎉 confetti explosion effect upon completion.
* Swipe left to delete tasks you don't need for the day.
* Quickly log your current weight via the top-right button.


* **📊 History & Weight Tracking**
* View all training records in reverse chronological order.
* **Weight Integration**: If weight is recorded on a specific day, a prominent orange tag (e.g., `⚖️ 75.0 KG`) appears next to the date.


* **💪 Exercise Library Management**
* Create, edit, and delete custom exercises.
* Categorize by "Strength" or "Cardio" (this determines if a weight input field is shown during check-in).


* **💾 Data Import/Export**
* Export workout history to a CSV file.
* Batch import weekly routines via CSV text.



---

## 📱 User Guide

### 1. Initial Setup

* Go to the **Settings** tab.
* **Language**: Switch to your preferred language (defaults to system settings).
* **Theme**: Choose your preferred color scheme (Dark, Green, Blue, etc.).

### 2. Build Your Library

* Click **Manage Library** in Settings.
* Tap the `+` button to add exercises you frequently perform.
* *Name*: e.g., "Bench Press", "Jogging".
* *Default Target*: e.g., "4 sets x 10 reps" or "30 mins".
* *Category*: Choose "Strength" or "Cardio".



### 3. Plan Your Week (Core Workflow)

This is the foundation for generating your daily plans:

1. Click **Weekly Routine Plan** in Settings.
2. Select a day of the week at the top (e.g., "Mon").
3. Click the **Add** button at the bottom and select exercises from your library.
4. Repeat this for the rest of the week.
5. Return to the main Settings list and assign **Day Types** (e.g., Monday is "Core Day", Sunday is "Rest Day") for visual indicators on the home screen.

### 4. Start Training

* Go back to the **Check-in** (Home) tab.
* You will see today's plan automatically listed.
* **To Train**:
* Tap a card to expand it and input the actual weight/sets completed.
* Tap the **"Check"** button on the right to finish. Enjoy the confetti!


* **Log Weight**: Tap the orange button in the top right corner to log today's weight.

---

## 📂 CSV Import Format

If you wish to batch import a routine, please prepare your CSV text in the following format:

`Day(1-7), Exercise Name, Category(must contain "Cardio" for cardio), Target`

**Example:**

```text
1, Bench Press, Strength, 3 sets x 12 reps
1, Lat Pulldown, Strength, 4 sets x 10 reps
3, Running, Cardio, 30 mins

```

*(Note: Both English and Chinese commas are supported)*

---

## 🛠️ Tech Stack

* **Language**: Kotlin
* **UI Framework**: Jetpack Compose (Material3)
* **Architecture**: MVVM (Model-View-ViewModel)
* **Database**: Room (SQLite)
* **Asynchronous**: Coroutines & Flow
* **Navigation**: Jetpack Navigation Compose

---

## 📝 Version Info

* **Version**: 4.1
* **Version Code**: 41
* **Author**: Designed & Built by enersto

---


> **Note**: This project is for personal tracking and learning purposes. Data is stored locally on your device.
