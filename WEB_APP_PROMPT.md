# Prompt — build the Cash Memer web app

Copy everything below the line into a fresh Claude Code session. It is written
to be handed over cold, with no memory of this project.

---

## Build me the Cash Memer web app

I run a small shop in Lahore, Pakistan. I already have an Android app called
**Cash Memer** that issues receipts ("cash memos"). I want the same thing as a
**web app I can run on my computer**, so I can type at a keyboard instead of a
phone, and use my phone as a barcode scanner for it.

### Before anything else — read this about me

**I do not back anything up.** I lost a Mac once and lost an entire iOS app, a
web app and an Android project with it. GitHub is now my only copy of anything.

So:

- Put this project in a **git repo from the very first commit**, not at the end
- Commit and push after every working chunk, not once at the finish
- Never put API keys, tokens or secrets in a tracked file. Use a gitignored
  `.env`, ship a `.env.example` with placeholders, and **make the app refuse to
  start if it finds something that looks like a real key in `.env.example`**
- Tell me plainly, in the README, which files will NOT come back from a
  `git clone` and that I must recreate them by hand
- If you write something you have not run, say so. Do not tell me it works
  when you have not seen it work

I am not a developer. Explain each step like I have never used a terminal.
When something needs doing on my side, give me the exact command or the exact
menu path, not a description of the idea.

### What it has to do

**Runs on my computer.** I open a browser and use it. No app store, no install
step beyond starting it.

**Two addresses, shown on startup:**

- `http://localhost:PORT` — for the computer it runs on
- `http://<my-LAN-IP>:PORT` — for my phone on the same Wi-Fi

Detect the LAN IP automatically and print **both** URLs in the terminal when
the server starts. Do not make me find my own IP address.

**Phone as barcode scanner — this is the important part.**

1. The web app shows a **QR code** on screen
2. I scan it with my phone's camera; it opens a scanner page in the phone's
   browser at the LAN address
3. The phone page opens the camera and reads barcodes
4. **Every barcode the phone reads appears instantly in the receipt open on my
   computer screen** — no refresh, no copy-paste

Use a WebSocket for that link. If a code matches a product in my inventory,
add it as a line item with its price. If it does not match, prompt me on the
computer to save it as a new product against that barcode, then add it — so
the next scan of the same item just works.

Show me clearly when the phone is connected and when it drops.

**The pairing must survive real conditions:** my phone will lock, walk out of
range and come back. Reconnect automatically, and queue scans that happen
while the socket is down rather than silently dropping them.

### Screens

Sidebar navigation: **Dashboard · Receipts · History · Inventory · Price List ·
Members · Rates · Settings**

**Receipts** — build a memo with a live preview beside the form as I type:

- Store name, location address
- Customer name, phone, email
- Currency (default PKR), category, payment method
- Line items: product name with autocomplete from inventory, quantity, price
- Discount (flat) and tax (percent)
- Cash given, with change calculated automatically, never negative
- Notes page 1 and page 2
- Signature capture with the mouse or a touchscreen
- Generate

**Drafts must auto-save.** If the browser closes mid-sale I want it back when
I reopen. Save a beat after typing stops, not on every keystroke.

**Every receipt produces a two-page PDF**, and the pages differ:

- **Page 1 — the customer's copy.** Store, receipt number, date, time,
  category, payment method, **customer name only**, the items table, subtotal,
  discount, tax, grand total, cash given, change, the page-1 note, the
  signature, and a QR code. It must **not** show the customer's phone, their
  address, or the issuing account.
- **Page 2 — my record.** Everything: full customer details including phone,
  email and address, GPS coordinates if present, the items, all totals, **both**
  note fields, the **issuer account name and email** taken from my Google
  sign-in, the signature, and the QR code.

Note 1 defaults to **`Thank You for shopping !!!`** on every receipt and I can
edit it. Note 2 starts empty — it is mine, and it never appears on page 1.

The QR payload should be short and readable (receipt number, store, total,
timestamp), not a JSON blob — a dense code will not scan off thermal paper.

**History** — search by title, location or customer; filter by a start and end
date where the end date covers the whole day; select receipts individually or
all at once; bulk print, share and delete. Expand a row to see its line items,
notes, saved location, payment method and change. Per-row actions: **Share,
PDF, Print, Duplicate, Edit, Delete**. Edit must update the original receipt,
not leave a duplicate behind. Duplicate deliberately does create a new one.

Plus a **Weekly Summary** card over the last 7 days: total spend, transaction
count, average value, top customer, total tax, total discount. Compute those
figures locally so they always show. If an AI key is configured, add one
sentence of interpretation on top — but the numbers must never depend on it.

**Dashboard** — total receipts, total products, total spending, average
receipt, highest and lowest receipt, average product cost, most-visited store,
unique store count, top product, storage used, sync status, and charts for
sales over time and spend by category.

**Inventory** — products with name, barcode, brand, category, cost price,
selling price, stock and unit. Search, All/Active/Archived filters, low-stock
warning, and add / edit / duplicate / archive / delete. Show live totals for
item count, low-stock count and total sell value.

**Price List** — a short quick-pick list of the things I sell most, separate
from full inventory, that I can drop into a receipt in one click.

**Members** — customer directory: name, phone, email, address. Picking a member
on the receipt form fills their details in one go.

**Rates** — live exchange rates, USD base, from exchangerate-api.com. Search,
manual refresh with a last-updated timestamp, and let me register a custom
currency with my own rate that a refresh will never overwrite.

**Settings** — Google sign-in, theme (system / light / dark), auto-print,
auto-send, printing options, and backup & restore.

### Language

English and **Urdu**, switchable in the app without a reload, with proper
right-to-left layout for Urdu. The app name in Urdu is **کیش میمر**.

### Data and backup

Local database on the computer. On top of that:

- **Export / import the whole database as one JSON file**, so I can move
  between machines
- **Automatic daily backup** to a folder I choose. If I point it at a synced
  folder, it leaves the machine on its own
- Keep the last 30 snapshots and prune older ones
- Show me when the last backup ran, or why it failed

If Google sign-in is set up, also sync to the cloud — but **the app must work
completely without it**. Never make cloud setup a precondition for the app
running.

### Keys

Every key optional. If one is missing, that one feature says exactly what is
missing and where to put it — the app still starts and everything else works.

- `EXCHANGE_RATE_API_KEY` — exchangerate-api.com, for the Rates screen
- `GEMINI_API_KEY` — optional, for the weekly insight sentence
- `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` — optional, for sign-in

### Design

Match the Android app so they look like one product:

- Deep shop-sign green `#2E6B1F`, warm off-white paper background `#F8F9EF`,
  white cards with a hairline border
- Bold headings — readable at arm's length across a counter, not desk-sized
- One spacing scale used everywhere, not per-screen guesses
- Light and dark themes
- Must work on a laptop screen and on the phone browser for the scanner page

### How to work

1. **Set up the git repo and push an empty first commit before writing code**
2. Ask me what I want if the answer changes what you build. Don't guess on
   something big
3. Get it running end to end early, even if screens are unfinished — I would
   rather see a working skeleton than a perfect half
4. Build the phone-scanner pairing early too. It is the feature I care most
   about and the one most likely to be fiddly
5. After each chunk: commit, push, and tell me in one line what to click to
   check it
6. At the end, give me a `README.md` covering how to start it, where the two
   URLs come from, how to pair the phone, which keys go where, and exactly what
   is not finished

Start by telling me what you are going to build and in what order, then begin.
