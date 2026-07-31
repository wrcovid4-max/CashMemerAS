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
GOOGLE_WEB_CLIENT_ID=your_web_client_id.apps.googleusercontent.com
```

Android Studio writes `sdk.dir` itself the first time it opens the project —
just add the key lines under it.

**The app builds and runs with all of these blank.** Each feature reports its
missing key rather than crashing, so you can add them one at a time.

#### Which key does what

| Key | Powers | Where to get it |
| --- | --- | --- |
| `EXCHANGE_RATE_API_KEY` | Rates screen | <https://www.exchangerate-api.com> |
| `GEMINI_API_KEY` | AI receipt scanning | <https://aistudio.google.com/apikey> |
| `MAPS_API_KEY` | GPS address fallback | Google Cloud Console |
| `GOOGLE_WEB_CLIENT_ID` | Sign in with Google | Google Cloud Console |

#### Setting up the Maps key

The GPS pin button tries Android's built-in geocoder first, which is free and
needs no key. The Maps key is only the fallback for when that returns nothing —
common on emulators and some devices.

1. Google Cloud Console → **APIs & Services → Library** → enable **Geocoding API**
2. **Credentials → Create credentials → API key**
3. Paste it as `MAPS_API_KEY`

Geocoding API requests are billed per call and *cannot* be restricted by
Android package name — only by IP or left unrestricted. Keep this key separate
from any Android-restricted key, and set a quota cap on it.

#### Setting up Google sign-in

Sign-in uses Credential Manager, so there is **no Firebase and no
`google-services.json`**. You need two OAuth client IDs in the same Cloud
project:

1. Google Cloud Console → **APIs & Services → OAuth consent screen** → configure
   it (External, add your own email as a test user)
2. **Credentials → Create credentials → OAuth client ID → Android**
   - Package name: `com.cashmemer`
   - SHA-1: run `./gradlew signingReport` and copy the **debug** SHA-1
3. **Create credentials → OAuth client ID → Web application**
4. Paste the **Web** client ID into `GOOGLE_WEB_CLIENT_ID`

The Android client makes the account chooser appear; the Web client ID is what
the app sends. Only the Web one goes in `local.properties`.

**What sign-in does and does not do:** it identifies who is at the counter. It
does **not** back anything up — that is what the folder snapshots below are for.
Real cloud sync would need Firebase/Firestore, which is not wired up.

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

## Keeping your data safe

Everything lives in one SQLite database on the phone. Lose the phone, lose the
records — so the app has three ways out:

1. **Automatic daily backup** (More → Automatic Backup). Choose a folder once;
   a dated JSON snapshot is written there every day and the last 30 are kept.
   Point it at a Drive- or Dropbox-synced folder and the data leaves the phone
   on its own. There's a **Back up now** button for a snapshot on demand.
2. **Backup JSON / Restore JSON** on the History tab — writes or reads a single
   file wherever you choose.
3. **Export / Restore JSON** in More — the same payload as raw text.

All three produce the same format, so a snapshot from any of them restores
through any of the others.

## Printing a memo

Receipts render to A4 PDF with the platform PDF engine — no external library.
From History: the **printer icon** on any row prints that receipt; select
several and use the printer or share icon in the bar to do them in one job.
Which pages get printed follows the **Mass Print Option** in More
(Page 1 / Page 2 / Both). Share opens the normal sheet, so WhatsApp and email
work with no extra setup.

## What still needs building

- **Cloud sync** — Google sign-in works, but it only establishes identity.
  Syncing receipts to Firestore would mean adding Firebase and a
  `google-services.json`; the folder snapshots cover the same need without it.
- **Store weather on the watch** — the payload carries a weather slot; nothing
  fills it yet.
- **App lock** — the toggle and passcode persist; the biometric prompt on launch
  isn't wired.
- **Auto-send / auto-print on generate** — both settings persist but nothing
  acts on them at generation time yet.

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
