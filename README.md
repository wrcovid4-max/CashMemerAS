# Cash Memer — Android + Wear OS

Smart receipts, live rates and a pocket POS. Rebuilt as a native Android
Studio project with a Wear OS companion.

> **Status:** this is a fresh reconstruction from screenshots of the original
> app, not a recovery of the deleted source. The screens, data model and flows
> match what the old app did; the code underneath is new.

---

## What's in here

| Module  | What it is |
| ------- | ---------- |
| `:core` | Shared Android library — data model, Room database, settings, networking (rates + Gemini OCR), theme, and the phone↔watch payload contract. Used by both apps. |
| `:app`  | The phone app. Compose + Material 3, min SDK 26. |
| `:wear` | The Wear OS companion. Wear Compose, min SDK 30. Not standalone — it needs the phone app. |

### Screens (phone)

- **Receipts** — new receipt form: store, GPS address, member picker, customer
  fields, currency, category, payment-type grid, line items, discount/tax,
  two note pages, signature pad, Generate.
- **History** — search, select-all, pin, bulk delete, JSON backup/restore.
- **Dashboard** — today / month / all-time totals, top stores, spend by category.
- **Inventory** — products with barcode, brand, cost, price, stock, unit;
  All/Active/Archived filters; archive, duplicate, edit, delete.
- **Price List** — the short quick-pick list you drop into receipts.
- **Rates** — USD-base exchange rates with search, refresh, custom currencies.
- **Members** — customer directory.
- **More** — appearance (System/Light/Dark), general, print, app lock,
  4-digit passcode, offline backup & recovery.

### Wear OS companion

Three glanceable cards: today's takings, sync status, live rates (plus a
weather slot wired but not yet fed). The phone pushes a summary over the Data
Layer after every receipt; the watch caches the last payload so it still shows
numbers when the phone is out of range.

---

## Step by step: getting it running in Android Studio

### 1. Install Android Studio

Download the latest stable **Android Studio** from
<https://developer.android.com/studio> and install it. On first launch let the
setup wizard download the SDK.

### 2. Clone this repo

In Android Studio: **File → New → Project from Version Control**, paste the
repo URL, pick a local folder, **Clone**.

Or from a terminal:

```bash
git clone https://github.com/wrcovid4-max/CashMemerAS.git
cd CashMemerAS
```

Then **File → Open** and select the `CashMemerAS` folder (the one with
`settings.gradle.kts` in it — not a subfolder).

### 3. Install the SDK pieces

**Tools → SDK Manager → SDK Platforms**, tick:

- **Android 15 (API 35)** — the compile target
- **Android 14 (API 34)** — the Wear target

**SDK Tools** tab, tick:

- Android SDK Build-Tools
- Android SDK Platform-Tools
- Android Emulator
- (optional) Google Play services — for testing the watch Data Layer

Click **Apply** and let it download.

### 4. Add your API keys

Copy `local.properties.example` to `local.properties` in the project root, then
fill in the three keys. **`local.properties` is gitignored — keys never get
committed.**

```properties
sdk.dir=/path/that/android/studio/already/filled/in

EXCHANGE_RATE_API_KEY=your_exchangerate_key
GEMINI_API_KEY=your_gemini_key
MAPS_API_KEY=your_maps_key
```

Android Studio writes `sdk.dir` itself the first time it opens the project —
just add the three key lines under it.

The app builds and runs fine without keys; the rates screen and AI scan will
report the missing key instead of crashing.

### 5. Sync Gradle

Android Studio shows a **"Gradle files have changed"** banner → click
**Sync Now**. Or **File → Sync Project with Gradle Files**.

First sync downloads the Gradle distribution and all dependencies — it takes a
few minutes. Let it finish.

### 6. Run the phone app

1. **Device Manager** (right sidebar) → **Create Virtual Device**
2. Pick **Pixel 7** → system image **API 35** → Finish
3. In the toolbar, set the run configuration dropdown to **app**
4. Pick your emulator, press **Run** (▶)

To run on a real phone instead: enable **Developer options → USB debugging**,
plug it in, and pick it in the device dropdown.

### 7. Run the Wear OS app

1. **Device Manager → Create Virtual Device → Wear OS** → pick
   **Wear OS Large Round** → system image **API 34** → Finish
2. Change the run configuration dropdown to **wear**
3. Pick the watch emulator, press **Run**

To see real data on the watch, pair it to the phone emulator:

```bash
adb -s emulator-5554 forward tcp:5601 tcp:5601   # phone
adb -s emulator-5556 shell am broadcast \
  -a com.google.android.gms.wearable.DEV_MODE    # watch
```

Then open the phone app, generate a receipt, and the watch's "Today" card
updates. Without pairing the watch shows *"Open Cash Memer on your phone to
sync."*

### 8. Build a shareable APK

**Build → Build Bundle(s) / APK(s) → Build APK(s)**. The file lands in
`app/build/outputs/apk/debug/app-debug.apk`.

For a signed release build you'll need a keystore
(**Build → Generate Signed App Bundle / APK**). Keep the `.jks` file somewhere
safe and **out of this repo** — it's gitignored for a reason. If you lose it
you cannot update the app on Play.

---

## Backing up your work

This repo is the backup. After every change worth keeping:

```bash
git add -A
git commit -m "what changed"
git push
```

If the laptop dies, `git clone` gets everything back. Anything you have not
pushed does not exist.

Two things are deliberately **not** in the repo and will not come back from a
clone — recreate them by hand:

- `local.properties` (your API keys)
- any signing keystore

---

## What still needs building

The scaffolding is complete and the app runs, but these are wired up as UI
without their backing implementation yet:

- **Camera capture / gallery import** — the Scan Receipt, Import Image, Bulk
  Scan and Barcode Scan buttons are in place; CameraX and ML Kit are already on
  the classpath, but the capture flow isn't hooked to `scanReceipt()` /
  `addByBarcode()` yet.
- **GPS auto-fill** — the location field accepts typing; the pin button doesn't
  call the fused location provider yet.
- **PDF generation, printing and share** — the print settings persist, but
  nothing renders a memo to PDF yet.
- **Firebase auth + cloud sync** — the original had Google sign-in and Firestore
  backup. Left out on purpose so the project builds without a
  `google-services.json`. Offline JSON backup/restore works today.
- **Store weather on the watch** — the payload carries a weather slot; nothing
  fills it yet.
- **App lock** — the toggle and passcode persist; biometric prompt on launch
  isn't wired.

---

## Project layout

```
CashMemerAS/
├── settings.gradle.kts        module list
├── gradle/libs.versions.toml  all dependency versions live here
├── local.properties           YOUR KEYS — gitignored, create by hand
├── core/                      shared library
│   └── src/main/java/com/cashmemer/core/
│       ├── model/             Receipt, Product, Member, CurrencyRate
│       ├── data/              Room DB, DAOs, repository, settings
│       ├── network/           ExchangeRateApi, GeminiOcrClient
│       ├── ui/theme/          colours, typography
│       └── wear/              phone↔watch payload contract
├── app/                       phone app
│   └── src/main/java/com/cashmemer/
│       ├── ui/                one package per screen
│       └── wear/              Data Layer push + listener
└── wear/                      Wear OS companion
```

Dependency versions are centralised in `gradle/libs.versions.toml` — change a
version there and every module picks it up.
