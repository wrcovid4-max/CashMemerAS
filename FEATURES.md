# Cash Memer — complete feature list

Everything currently built, screen by screen. Items marked **⛔ not built** are
listed so the gaps are visible rather than discovered later.

---

## Receipts tab — building a memo

**Capture**
- Scan Receipt — camera capture, parsed by Gemini AI into every form field
- Import Image — pick one photo from the gallery and parse it
- Bulk Scan — pick up to 10 photos; each is parsed and merged in turn
- Barcode Scan — live camera barcode read, matched against inventory and added
  as a line item
- Images are capped at 1600px on the long edge before upload, so a 12 MP phone
  photo doesn't get billed at full resolution
- Camera permission requested at the point of use, not at launch

**Receipt details**
- Place / store name
- Location address, two ways:
  - GPS pin — fixes your position and reverse-geocodes it (on-device geocoder
    first, Maps Geocoding API as fallback)
  - Map picker — full Google map, search by address or place name, drag to
    position, Confirm Location. Coordinates are stored with the receipt and
    printed on the memo.
- Live barcode feed from a connected Bluetooth scanner drops straight into the
  open sale
- Member picker — selecting a saved customer fills name, phone and email at once
- Customer name, phone, email (phone and email optional)

**Money**
- Currency selector, remembered as your default for next time
- Category: Shopping, Groceries, Food & Drink, Fuel, Utilities, Services,
  Medical, Other
- Payment type, 9 options: Cash, Card, Bank Transfer, Mobile Wallet, Apple Pay,
  Google Wallet, Google Pay, Klarna, PayPak
- Add purchased items — product name with autocomplete from your inventory,
  auto-filling the price; quantity and price per line
- Line items list with per-line totals and remove
- Discount (flat) and tax (percentage)
- Live totals: subtotal → discount → tax → grand total
- Cash Given, with change calculated automatically (never negative)

**Finishing**
- Notes, page 1 and page 2
- Digital signature — freehand capture, "Captured" badge, Clear & Redraw
- Save as Default Signature, reused on the next receipt
- Generate — writes the receipt, clears the form, pushes to the watch, and
  uploads to the cloud when signed in
- Clear — discards the in-progress receipt

---

## History tab

**Weekly AI Summary card** — always the last 7 days, regardless of filters:
- Total Spend, Txns, Avg Value, Top Customer, Total Tax, Total Discount
- The six figures are computed locally and always shown — they never depend on
  the network or an API key
- **Generate insight** adds one sentence of interpretation from Gemini on top;
  if the key is missing or the call fails, the numbers still stand
- Collapsible

**Filtering**
- Search across title, location and customer
- Start Date / End Date range pickers, with a Clear button. The end date covers
  the whole day, not just its midnight instant

**Bulk actions**
- Select individual receipts, or Select All
- Bulk print (one job), bulk share as PDF, bulk delete

**Per-row**
- Tap to expand: every line item, Notes (Page 1), Saved Location,
  Payment method and Change given
- Six actions: **Share · PDF · Print · Dupe · Edit · Delete**
- Dupe copies the sale as a fresh receipt — the "same order again" case
- Edit loads it back into the Receipts form and updates the original rather
  than creating a second copy
- Pin / unpin, pinned rows held at the top

**Backup**
- Backup JSON — write the whole database to a file you name
- Restore JSON — read one back

---

## Dashboard tab

- Today's takings and receipt count
- This month's total
- All-time sales
- Top 5 stores by value, as bars
- Spend by category, as bars

---

## Inventory

- Products with name, barcode, brand, category, cost price, selling price,
  stock level and unit
- Search across name, barcode and brand
- Filter: All / Active / Archived
- Summary line: active count, low-stock count, total sell value
- Low-stock warning at 5 units or fewer
- Add, edit, duplicate, archive and delete
- Barcode lookup — scanning a code on the receipt form finds the product here

---

## Price List

- The short quick-pick list of products you ring up most often
- Add, edit and remove, kept separate from full inventory
- Feeds the product autocomplete on the receipt form

---

## Rates

- Live exchange rates, USD base, from ExchangeRate-API
- Search by code or currency name
- Manual refresh with last-updated timestamp
- Flag emoji per currency, derived from the country code
- Register a custom currency with your own rate — hand-entered rates are never
  overwritten by a refresh

---

## Members

- Customer directory: name, phone, email, address
- Add, edit, delete
- Selectable from the receipt form

---

## More (Settings)

**Cloud Sync & Backup**
- Sign in with Google (Credential Manager — no password handling)
- Sync — upload the whole shop to Firestore
- Restore — pull the cloud copy down onto this device
- Automatic per-receipt upload on Generate
- Reports clearly when Firebase isn't configured, rather than failing silently

**Automatic Backup**
- Pick a folder once; a dated JSON snapshot is written there daily
- Keeps the last 30 snapshots, pruning older ones
- Back up now, for a snapshot on demand
- Shows last successful backup, or the reason the last one failed

**Appearance**
- Theme: System / Light / Dark

**General**
- Auto-Send toggle ⛔ *persists but nothing acts on it yet*
- Save Signature toggle

**Print**
- Auto-Print toggle ⛔ *persists but nothing acts on it yet*
- Show Page 1 / Show Page 2 in the viewer
- Mass Print Option: Page 1 / Page 2 / Both — governs what actually prints

**Security**
- App Lock toggle ⛔ *persists; the biometric prompt on launch isn't wired*
- Custom 4-digit passcode with confirmation

**Backup & Recovery**
- Export JSON / Restore JSON as raw text

---

## Connected Devices & Integrations

Real Bluetooth, not placeholder switches. Works with barcode scanners, payment
terminals and thermal receipt printers that speak the Serial Port Profile —
which is nearly all counter hardware.

- Live connection banner: connected / connecting / failed, with the reason
- **Payment Terminal Integration** and **Android OCR Companion** toggles
- **Connection Preferences**, all seven persisted: auto-reconnect to paired
  devices, auto-connect to default, ask before connecting new, show status in
  status bar, connection notifications, scan/payment confirmation sounds,
  vibration feedback
- **Manage Paired Devices** — reads the real Bluetooth bonded-device list,
  guesses each device's type from its name, connect / disconnect, set a default
- Scanned barcodes stream from the connected device straight into the open sale
- Raw byte send, for ESC/POS printers and terminal protocol frames
- **Run Connection Diagnostics** — five real checks: radio present, Bluetooth
  on, permission granted, something paired, something connected
- **View Integration Logs** — rolling 200-entry event log, clearable
- **Forget All Paired Devices** — disconnects and clears the default, and says
  plainly that Android keeps the pairings themselves

Runtime permissions handled for both the pre-Android-12 and Android-12+ models.

## Android Auto

Read-only and driving-safe — no text entry, short lists.

- Today's takings and receipt count
- Recent receipts list, capped at 6 rows (Android Auto blocks longer lists in
  motion)
- Built on the Car App Library template host

## Printing

- Receipts render to A4 PDF using the platform PDF engine — no external library
- Page 1: store, address, receipt number, timestamp, customer block, itemised
  table, subtotal / discount / tax / grand total, cash given and change,
  payment method, GPS coordinates
- Page 2: both note fields plus the signature image
- QR block on both pages encoding receipt number, store, total, timestamp and
  coordinates — short payload on purpose, so it still scans off thermal paper
- Print through the Android print dialog — any printer the phone can see
- Share as PDF through the standard share sheet
- Single or bulk, honouring the Mass Print Option setting

---

## Wear OS companion

- Today's takings and receipt count
- Sync status — backed up, or how many are pending
- Live rates for USD, PKR, AED, SAR, GBP, EUR
- Store weather card ⛔ *the payload carries a weather slot; nothing fills it*
- Caches the last payload, so the numbers still show when the phone is out of
  range
- Asks the phone for a refresh whenever the watch app is opened

---

## Throughout

- English and Urdu, switchable in-app from the header without restarting
- Right-to-left layout support for Urdu
- Light and dark themes
- Works fully offline — only rates, AI scanning, geocoding and cloud sync need
  the network
- Every API key is optional; each feature reports its own missing key instead of
  crashing the app

---

## Known gaps

| Gap | Detail |
| --- | --- |
| Auto-Send | Setting persists, nothing sends on generate |
| Auto-Print | Setting persists, nothing prints on generate |
| App Lock | Toggle and passcode persist, no prompt on launch |
| Watch weather | Payload slot exists, no weather source wired |
| Sync conflicts | Push overwrites cloud, pull overwrites phone — no merge |
| Tests | No unit or instrumentation tests yet |
