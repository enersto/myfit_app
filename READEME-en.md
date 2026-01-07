# myFit - Personal Fitness Tracker (V5.3)

**myFit** is a personal fitness tracking application built with Android Jetpack Compose. It helps users plan weekly routines, track daily workouts, and visualize training volume and weight trends through **native charts**.

🚀 **V5.3 Major Update**: Introducing the Visual Statistics Center (Canvas charts), a redesigned Exercise Library (3-level categorization + bottom nav), and a Smart CSV Import system (auto-add & deduplication).

---

## ✨ Core Features

* **📈 Visual Statistics Center** `NEW`
* **Native Charts**: High-performance Line and Bar charts rendered via Compose Canvas.
* **Multi-dimensional Analysis**: Track Weight trends, Cardio duration, Strength max weight, and Core reps.
* **View Switching**: Toggle between Daily/Monthly granularity; switch between List and Chart views in the History screen.


* **🔄 Smart CSV Import** `NEW`
* **Auto-Add**: Automatically creates new exercise templates (with body part/equipment info) when importing a routine CSV containing unknown exercises.
* **Smart Deduplication**: Automatically detects and cleans up duplicate exercises in the library during import.
* **Localized Feedback**: Import results are fully localized.


* **🗂️ Exercise Library Redesign** `NEW`
* **3-Level Hierarchy**: Organized by **Category (Bottom Tab) -> Body Part (Expandable Header) -> Equipment (Group)**.
* **Quick Filtering**: Added category tabs (Strength/Cardio/Core) in selection dialogs for faster access.


* **🌍 Internationalization**
* Full support for **Simplified Chinese, English, Deutsch, Español, and 日本語**.


* **💾 Data Security**
* Supports CSV export/import and full **Database (.db)** backup/restore.



---

## 📱 User Guide

### 1. Manage Library

* Go to **Settings -> Manage Exercise Library**.
* Use bottom tabs to switch categories. Click headers to expand/collapse body parts.

### 2. Smart Import

* Go to **Settings -> Data Management -> Import CSV**.
* Paste your CSV content. The app will automatically sync new exercises to your library.

### 3. View Statistics

* Go to the **History** page.
* Tap the **"Charts"** tab at the bottom.
* Select a module (e.g., Weight, Single Exercise) to view trends.

---

## 📂 CSV Import Format (V5.3)

`Day(1-7), Name, Category(STRENGTH/CARDIO/CORE), Target, BodyPartKey, EquipmentKey`

**Example:**

```text
1, Barbell Bench Press, STRENGTH, 4x8, part_chest, equip_barbell
3, Burpees, CORE, 4x15, part_abs, equip_bodyweight

```

---

## 📝 Version Info

* **Version**: 5.3
* **Version Code**: 53
* **Author**: Designed & Built by enersto & Hajimi

---

> **Note**: This project is for personal tracking and learning purposes. Data is stored locally on your device.


