# Gymnota - Personal Workout Tracker & Memo (V5.7)

**Gymnota** is a personal fitness tracking and memo application developed using Android Jetpack Compose. It aims to help users plan weekly training routines, log daily check-ins, and visualize training volume and body status changes in depth through **native charts**.

🚀 **Major Update V5.7**: Adds body metrics management (BMI/BMR automatic analysis), completely refactors the timer service (supports persistent lock screen display and prevents background killing), and optimizes the notification permission guidance experience.

---

## ✨ Core Features

* **💪 Body Stats & Metrics** `NEW`
* **Smart Logging**: Added a "Log Body Stats" entry on the check-in page, supporting records for weight, height, age, and gender.
* **Auto-Calculation**: The system automatically calculates daily **BMI (Body Mass Index)** and **BMR (Basal Metabolic Rate)** based on the recorded basic info.
* **Privacy Protection**: Basic info (height/age/gender) is stored only in the local database and can be modified in Settings at any time.


* **⏱️ Pro Workout Timer** `NEW`
* **Foreground Service**: Refactored timing logic; the timer will no longer be killed by the system during lock screen, screen off, or background execution.
* **Persistent Lock Screen Display**: The notification bar displays the countdown seconds in real-time, supporting viewing remaining time directly on the lock screen.
* **Permission Guidance**: Added a smart guidance dialog for "Lock Screen Notification" permissions (optimized for Xiaomi HyperOS/MIUI).


* **📈 Visual Statistics Center**
* **Native Charts**: High-performance line and bar charts drawn based on Compose Canvas.
* **Multi-dimensional Analysis**:
* **Body Status** `NEW`: Added BMI trend and BMR trend charts, forming the "Body Status" section combined with weight trends.
* **Cardio Endurance**: Statistics on daily/monthly total cardio duration.
* **Strength/Core**: Tracks the trend of max weight or rep counts for individual exercises.


* **View Switching**: Supports "Day/Month" granularity switching.


* **🔄 Smart CSV Import**
* **Automatic Ingestion**: When importing a weekly plan CSV, if it contains new exercises, the system automatically creates templates and supplements body part/equipment info.
* **Smart Deduplication**: Automatically detects and cleans duplicate data in the exercise library during import.


* **🌍 Multi-language Support (Internationalization)**
* Fully supports **Simplified Chinese, English, Deutsch, Español, and Japanese**.
* Interface text, chart labels, and import feedback are fully localized.


* **📅 Smart Weekly Routine**
* Visual interface to arrange weekly fixed training exercises, automatically generating daily to-do tasks.
* Supports setting type tags for each day (e.g., Core Day, Rest Day), with the home page theme color changing automatically based on the type.



---

## 📱 User Guide

### 1. Recording Body Data `NEW`

* Click the **"Log Body Stats"** button below the date on the home page.
* **First Use**: A popup will ask for height, age, gender, and weight to establish a basic profile.
* **Daily Logging**: Subsequent logs only require weight input; the system automatically reuses basic info to calculate BMI/BMR.
* **Modify Profile**: Go to **Settings -> Basic Info** to modify fixed metrics like height/age.

### 2. Training Timer & Lock Screen Display `NEW`

* Input time (minutes) on the right side of an exercise set and click the play button to start timing.
* **Lock Screen Viewing**:
* Once timing starts, you can lock the screen directly.
* Light up the screen (no need to unlock) to see the countdown in the notification area.
* *Note: If not displayed, please follow the App popup guide to enable "Lock Screen Notification" permission in system settings.*



### 3. Viewing Health Trends

* Go to the **History** page.
* Click the **"Charts"** tab at the bottom.
* The top section features the new **"Body Status"** panel; swipe left/right to view long-term trends for Weight, BMI, and BMR.

### 4. Managing Library & Weekly Plan

* Go to the **Settings** page.
* **Exercise Library**: Efficiently manage exercises via three-level classification (Category -> Body Part -> Equipment).
* **Weekly Plan**: Manually check or batch import weekly fixed training via CSV.

---

## 📂 CSV Import Format Description

Supports automatically expanding the exercise library via CSV import. The recommended full format is:

`Day(1-7), Exercise Name, Category(STRENGTH/CARDIO/CORE), Target, BodyPartKey, EquipmentKey`

**Key Reference**:

* **Body Part**: `part_chest`, `part_back`, `part_legs`, `part_abs`, `part_cardio` ...
* **Equipment**: `equip_barbell`, `equip_dumbbell`, `equip_bodyweight`, `equip_machine` ...

**Example:**

```text
1, Barbell Bench Press, STRENGTH, 4x8, part_chest, equip_barbell
3, Burpees, CORE, 4x15, part_abs, equip_bodyweight

```

---

## 🛠️ Tech Stack

* **Language**: Kotlin
* **UI Framework**: Jetpack Compose (Material3)
* **Architecture**: MVVM (Model-View-ViewModel)
* **Database**: Room (SQLite) with Migrations (v9)
* **Service**: Android Foreground Service (Keep-alive Timer)
* **Graphics**: Compose Canvas (Custom Charts)
* **Concurrency**: Coroutines & Flow

---

## 📝 Version Info

* **Version**: 5.7
* **Version Code**: 57
* **Author**: Designed & Built by enersto & Hajimi

---

