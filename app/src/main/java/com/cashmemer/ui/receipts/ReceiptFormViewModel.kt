package com.cashmemer.ui.receipts

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import com.cashmemer.R
import com.cashmemer.core.data.CashMemerRepository
import com.cashmemer.core.data.ReceiptItemCodec
import com.cashmemer.core.data.SettingsStore
import com.cashmemer.core.model.Member
import com.cashmemer.core.model.PaymentType
import com.cashmemer.core.model.Product
import com.cashmemer.core.model.Receipt
import com.cashmemer.core.model.ReceiptCategory
import com.cashmemer.core.model.ReceiptItem
import com.cashmemer.core.network.GeminiOcrClient
import com.cashmemer.devices.TerminalManager
import com.cashmemer.location.LocationResolver
import com.cashmemer.sync.FirebaseSync
import com.cashmemer.wear.PhoneWearSyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Printed on page 1 of every receipt unless the shopkeeper edits it. */
const val DEFAULT_NOTE_1 = "Thank You for shopping !!!"

/** Everything the new-receipt form holds while it is being filled in. */
data class ReceiptFormState(
    /** Non-zero when editing an existing receipt rather than creating one. */
    val editingId: Long = 0,
    val placeName: String = "",
    val locationAddress: String = "",
    val selectedMember: Member? = null,
    val customerName: String = "",
    val customerPhone: String = "",
    val customerEmail: String = "",
    val currencyCode: String = "PKR",
    val category: ReceiptCategory = ReceiptCategory.SHOPPING,
    val paymentType: PaymentType = PaymentType.CASH,
    val items: List<ReceiptItem> = emptyList(),
    val discount: Double = 0.0,
    val taxPercent: Double = 0.0,
    val cashGiven: Double = 0.0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Pre-filled with the standard thank-you; editable per receipt. */
    val notesPage1: String = DEFAULT_NOTE_1,
    /** Stays empty on purpose — page 2 is the shopkeeper's own note. */
    val notesPage2: String = "",
    val signatureBase64: String? = null,
    val sourceImageUri: String? = null,
    val saveSignatureAsDefault: Boolean = true,
    val scanning: Boolean = false,
    val locatingAddress: Boolean = false,
    /** Set when a scanned code matched nothing, so the UI can offer to add it. */
    val unknownBarcode: String? = null,
    val message: String? = null,
    val draftSavedAt: Long? = null,
) {
    val subtotal: Double get() = items.sumOf { it.lineTotal }
    val taxAmount: Double get() = (subtotal - discount).coerceAtLeast(0.0) * taxPercent / 100.0
    val total: Double get() = (subtotal - discount).coerceAtLeast(0.0) + taxAmount
    val changeAmount: Double get() = (cashGiven - total).coerceAtLeast(0.0)
    val canGenerate: Boolean get() = placeName.isNotBlank() && items.isNotEmpty()
}

class ReceiptFormViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CashMemerRepository.get(application)
    private val settingsStore = SettingsStore(application)

    /**
     * ViewModels have no composable scope, so `stringResource` is unavailable —
     * every message the user reads goes through here instead, which is what
     * makes toasts follow the app language rather than staying English.
     */
    private fun str(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    private val _state = MutableStateFlow(ReceiptFormState())
    val state: StateFlow<ReceiptFormState> = _state.asStateFlow()

    val members: StateFlow<List<Member>> = repository.observeMembers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val products: StateFlow<List<Product>> = repository.observeProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Emitted after Generate so the UI can auto-print / auto-send. */
    private val _generated = MutableSharedFlow<Receipt>(extraBufferCapacity = 4)
    val generated: SharedFlow<Receipt> = _generated.asSharedFlow()

    /** Transient confirmations. Shown as toasts so they cannot scroll away. */
    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    init {
        // Bring back whatever was being typed when the app last went away.
        viewModelScope.launch {
            DraftStore.load(application)?.let { draft ->
                _state.value = draft.copy(draftSavedAt = System.currentTimeMillis())
            }
            startDraftAutoSave()
        }

        // A hardware scanner on the counter feeds straight into the open sale.
        viewModelScope.launch {
            TerminalManager.scans.collect { barcode -> addByBarcode(barcode) }
        }

        // History asking us to open a receipt for editing.
        viewModelScope.launch {
            ReceiptEditBus.requestedId.collect { id ->
                if (id != null) {
                    loadForEdit(id)
                    ReceiptEditBus.consume()
                }
            }
        }

        // Restore the saved default signature and currency, as the original app did.
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                _state.update { current ->
                    current.copy(
                        currencyCode = if (current.items.isEmpty() && current.placeName.isBlank()) {
                            settings.defaultCurrency
                        } else {
                            current.currencyCode
                        },
                        signatureBase64 = current.signatureBase64
                            ?: settings.defaultSignatureBase64,
                        saveSignatureAsDefault = settings.saveSignature,
                    )
                }
            }
        }
    }

    /**
     * Writes the draft a beat after typing stops. Debounced because saving on
     * every keystroke would hammer DataStore while someone types a note.
     */
    @OptIn(FlowPreview::class)
    private fun startDraftAutoSave() {
        viewModelScope.launch {
            _state
                .debounce(DRAFT_DEBOUNCE_MILLIS)
                .distinctUntilChangedBy { it.draftFingerprint() }
                .collect { current ->
                    if (current.placeName.isBlank() && current.items.isEmpty()) {
                        DraftStore.clear(getApplication<Application>())
                        return@collect
                    }
                    DraftStore.save(getApplication<Application>(), current)
                    _state.update { it.copy(draftSavedAt = System.currentTimeMillis()) }
                }
        }
    }

    /** Everything worth persisting — excludes transient UI flags. */
    private fun ReceiptFormState.draftFingerprint(): String = listOf(
        editingId, placeName, locationAddress, customerName, customerPhone,
        customerEmail, currencyCode, category, paymentType, items, discount,
        taxPercent, cashGiven, notesPage1, notesPage2, latitude, longitude,
    ).joinToString("|")

    fun setPlaceName(value: String) = _state.update { it.copy(placeName = value) }
    fun setLocationAddress(value: String) = _state.update { it.copy(locationAddress = value) }
    fun setCustomerName(value: String) = _state.update { it.copy(customerName = value) }
    fun setCustomerPhone(value: String) = _state.update { it.copy(customerPhone = value) }
    fun setCustomerEmail(value: String) = _state.update { it.copy(customerEmail = value) }
    fun setCurrency(code: String) = _state.update { it.copy(currencyCode = code) }
    fun setCategory(category: ReceiptCategory) = _state.update { it.copy(category = category) }
    fun setPaymentType(type: PaymentType) = _state.update { it.copy(paymentType = type) }
    fun setDiscount(value: Double) = _state.update { it.copy(discount = value) }
    fun setCashGiven(value: Double) = _state.update { it.copy(cashGiven = value) }

    /** Applies a point chosen on the map, address and coordinates together. */
    fun setPickedLocation(address: String, latitude: Double, longitude: Double) =
        _state.update {
            it.copy(
                locationAddress = address,
                latitude = latitude,
                longitude = longitude,
            )
        }
    fun setTaxPercent(value: Double) = _state.update { it.copy(taxPercent = value) }
    fun setNotesPage1(value: String) = _state.update { it.copy(notesPage1 = value) }
    fun setNotesPage2(value: String) = _state.update { it.copy(notesPage2 = value) }
    fun setSaveSignatureAsDefault(value: Boolean) =
        _state.update { it.copy(saveSignatureAsDefault = value) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /** Surfaces what auto-print / auto-send actually did. */
    fun reportDelivery(description: String) =
        _state.update {
            it.copy(message = str(R.string.msg_receipt_saved_delivery, description))
        }

    /** Fills the location field from the device's current position. */
    fun useCurrentLocation() {
        _state.update { it.copy(locatingAddress = true) }
        viewModelScope.launch {
            LocationResolver.currentAddress(getApplication<Application>())
                .onSuccess { address ->
                    _state.update {
                        it.copy(locationAddress = address, locatingAddress = false)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            locatingAddress = false,
                            message = str(R.string.msg_location_failed),
                        )
                    }
                }
        }
    }

    /** Picking a member fills the three customer fields in one go. */
    fun selectMember(member: Member?) = _state.update {
        if (member == null) {
            it.copy(selectedMember = null)
        } else {
            it.copy(
                selectedMember = member,
                customerName = member.name,
                customerPhone = member.phone,
                customerEmail = member.email,
            )
        }
    }

    fun addItem(item: ReceiptItem) {
        if (item.productName.isBlank()) return
        _state.update { it.copy(items = it.items + item) }
    }

    fun removeItem(index: Int) = _state.update {
        it.copy(items = it.items.filterIndexed { i, _ -> i != index })
    }

    fun setSignature(base64: String?) = _state.update { it.copy(signatureBase64 = base64) }

    /**
     * Barcode scan: look the code up in inventory and add it as a line.
     *
     * A miss used to leave a note at the bottom of a long form that nobody
     * ever scrolled to, so scanning an unknown code looked like nothing had
     * happened. Hits now toast, and misses raise a prompt to add the product
     * on the spot — which is the whole point of scanning at a counter.
     */
    fun addByBarcode(barcode: String) {
        viewModelScope.launch {
            val product = repository.productByBarcode(barcode)

            if (product == null) {
                _state.update { it.copy(unknownBarcode = barcode) }
                _toasts.emit(str(R.string.msg_barcode_unknown, barcode))
                return@launch
            }

            addItem(
                ReceiptItem(
                    productName = product.name,
                    qty = 1.0,
                    unitPrice = product.price,
                )
            )
            _toasts.emit(str(R.string.msg_added_item, product.name))
        }
    }

    fun dismissUnknownBarcode() = _state.update { it.copy(unknownBarcode = null) }

    /**
     * Saves a scanned-but-unknown code as a real product, then puts it on the
     * receipt — so the next scan of the same item just works.
     */
    fun createProductForBarcode(barcode: String, name: String, price: Double) {
        viewModelScope.launch {
            repository.saveProduct(
                Product(name = name.trim(), barcode = barcode, price = price)
            )
            addItem(ReceiptItem(productName = name.trim(), qty = 1.0, unitPrice = price))
            _state.update { it.copy(unknownBarcode = null) }
            _toasts.emit(str(R.string.msg_saved_and_added, name.trim()))
        }
    }

    /**
     * Reads an image the user captured or picked, downscales it, and runs it
     * through the OCR parser.
     */
    fun scanReceiptFrom(uri: Uri) {
        viewModelScope.launch {
            val bitmap = decodeScaled(uri)
            if (bitmap == null) {
                _state.update { it.copy(message = str(R.string.msg_image_unreadable)) }
                return@launch
            }
            _state.update { it.copy(sourceImageUri = uri.toString()) }
            scanReceipt(bitmap)
        }
    }

    /** Bulk scan: parse each picked image in turn, merging every result. */
    fun scanReceipts(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            uris.forEach { uri ->
                decodeScaled(uri)?.let { bitmap -> scanReceipt(bitmap).join() }
            }
        }
    }

    /**
     * Gemini charges by pixels and phone photos are far larger than the parser
     * needs, so cap the long edge before uploading.
     */
    private suspend fun decodeScaled(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = getApplication<Application>().contentResolver
            val source = ImageDecoder.createSource(resolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longEdge = maxOf(info.size.width, info.size.height)
                if (longEdge > MAX_SCAN_EDGE) {
                    val scale = MAX_SCAN_EDGE.toFloat() / longEdge
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt(),
                        (info.size.height * scale).toInt(),
                    )
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        }.getOrNull()
    }

    /** Send a photographed receipt through Gemini and merge the parsed fields in. */
    fun scanReceipt(bitmap: Bitmap) =
        viewModelScope.launch {
            _state.update { it.copy(scanning = true, message = null) }
            GeminiOcrClient.parse(bitmap)
                .onSuccess { parsed ->
                    _state.update { current ->
                        current.copy(
                            scanning = false,
                            placeName = parsed.placeName.ifBlank { current.placeName },
                            locationAddress = parsed.locationAddress
                                .ifBlank { current.locationAddress },
                            currencyCode = parsed.currencyCode
                                .ifBlank { current.currencyCode },
                            category = if (parsed.category.isBlank()) current.category
                            else ReceiptCategory.from(parsed.category),
                            paymentType = if (parsed.paymentType.isBlank()) current.paymentType
                            else PaymentType.from(parsed.paymentType),
                            discount = parsed.discount.takeIf { it > 0 } ?: current.discount,
                            taxPercent = parsed.taxPercent.takeIf { it > 0 }
                                ?: current.taxPercent,
                            items = current.items + parsed.items,
                            message = str(R.string.msg_scanned_items, parsed.items.size),
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            scanning = false,
                            message = error.message ?: str(R.string.msg_scan_failed),
                        )
                    }
                }
        }

    /** Writes the receipt and clears the form, like the original Generate button. */
    fun generate(onGenerated: (Long) -> Unit = {}) {
        val current = _state.value
        if (!current.canGenerate) {
            _state.update { it.copy(message = str(R.string.msg_need_store_and_item)) }
            return
        }

        viewModelScope.launch {
            // Stamp the issuing account onto the receipt rather than reading it
            // at print time, so a receipt keeps whoever actually rang it up.
            val account = settingsStore.settings.first()

            val receipt = Receipt(
                id = current.editingId,
                issuerName = account.accountName.orEmpty(),
                issuerEmail = account.accountEmail.orEmpty(),
                placeName = current.placeName,
                locationAddress = current.locationAddress,
                memberId = current.selectedMember?.id,
                customerName = current.customerName,
                customerPhone = current.customerPhone,
                customerEmail = current.customerEmail,
                currencyCode = current.currencyCode,
                category = current.category.name,
                paymentType = current.paymentType.name,
                subtotal = current.subtotal,
                discount = current.discount,
                taxPercent = current.taxPercent,
                total = current.total,
                cashGiven = current.cashGiven,
                latitude = current.latitude,
                longitude = current.longitude,
                notesPage1 = current.notesPage1,
                notesPage2 = current.notesPage2,
                signatureBase64 = current.signatureBase64,
                sourceImageUri = current.sourceImageUri,
                itemsJson = ReceiptItemCodec.encode(current.items),
            )

            val id = repository.saveReceipt(receipt)

            if (current.saveSignatureAsDefault && current.signatureBase64 != null) {
                settingsStore.setDefaultSignature(current.signatureBase64)
            }
            settingsStore.setDefaultCurrency(current.currencyCode)

            // Keep the watch's "today" figure honest right after a sale.
            PhoneWearSyncManager.push(getApplication<Application>())

            // Push the sale to the cloud immediately when signed in, so a lost
            // phone costs at most the receipt currently being typed.
            val saved = repository.receipt(id)
            saved?.let { FirebaseSync.pushReceipt(getApplication<Application>(), it) }

            // The sale is committed — the draft has served its purpose.
            DraftStore.clear(getApplication<Application>())

            // Lets the UI run auto-print / auto-send, which need an Activity.
            saved?.let { _generated.emit(it) }

            _state.value = ReceiptFormState(
                currencyCode = current.currencyCode,
                signatureBase64 = if (current.saveSignatureAsDefault) {
                    current.signatureBase64
                } else {
                    null
                },
                saveSignatureAsDefault = current.saveSignatureAsDefault,
                message = str(R.string.msg_receipt_generated, id),
            )
            onGenerated(id)
        }
    }

    /**
     * Loads an existing receipt back into the form. [editingId] is carried so
     * Generate updates the original rather than creating a second copy.
     */
    fun loadForEdit(id: Long) {
        viewModelScope.launch {
            val receipt = repository.receipt(id) ?: run {
                _state.update { it.copy(message = str(R.string.msg_receipt_missing, id)) }
                return@launch
            }

            _state.value = ReceiptFormState(
                editingId = receipt.id,
                placeName = receipt.placeName,
                locationAddress = receipt.locationAddress,
                customerName = receipt.customerName,
                customerPhone = receipt.customerPhone,
                customerEmail = receipt.customerEmail,
                currencyCode = receipt.currencyCode,
                category = ReceiptCategory.from(receipt.category),
                paymentType = PaymentType.from(receipt.paymentType),
                items = ReceiptItemCodec.decode(receipt.itemsJson),
                discount = receipt.discount,
                taxPercent = receipt.taxPercent,
                cashGiven = receipt.cashGiven,
                latitude = receipt.latitude,
                longitude = receipt.longitude,
                notesPage1 = receipt.notesPage1,
                notesPage2 = receipt.notesPage2,
                signatureBase64 = receipt.signatureBase64,
                sourceImageUri = receipt.sourceImageUri,
                message = str(R.string.msg_editing_receipt, receipt.id),
            )
        }
    }

    fun clear() {
        _state.value = ReceiptFormState(currencyCode = _state.value.currencyCode)
    }

    private companion object {
        /** Long-edge cap for images sent to the OCR parser. */
        const val MAX_SCAN_EDGE = 1600

        /** Quiet period after typing before a draft is written. */
        const val DRAFT_DEBOUNCE_MILLIS = 1_200L
    }
}
