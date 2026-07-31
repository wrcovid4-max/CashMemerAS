package com.cashmemer.ui.receipts

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import com.cashmemer.location.LocationResolver
import com.cashmemer.sync.FirebaseSync
import com.cashmemer.wear.PhoneWearSyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything the new-receipt form holds while it is being filled in. */
data class ReceiptFormState(
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
    val notesPage1: String = "",
    val notesPage2: String = "",
    val signatureBase64: String? = null,
    val sourceImageUri: String? = null,
    val saveSignatureAsDefault: Boolean = true,
    val scanning: Boolean = false,
    val locatingAddress: Boolean = false,
    val message: String? = null,
    val draftSavedAt: Long? = null,
) {
    val subtotal: Double get() = items.sumOf { it.lineTotal }
    val taxAmount: Double get() = (subtotal - discount).coerceAtLeast(0.0) * taxPercent / 100.0
    val total: Double get() = (subtotal - discount).coerceAtLeast(0.0) + taxAmount
    val canGenerate: Boolean get() = placeName.isNotBlank() && items.isNotEmpty()
}

class ReceiptFormViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CashMemerRepository.get(application)
    private val settingsStore = SettingsStore(application)

    private val _state = MutableStateFlow(ReceiptFormState())
    val state: StateFlow<ReceiptFormState> = _state.asStateFlow()

    val members: StateFlow<List<Member>> = repository.observeMembers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val products: StateFlow<List<Product>> = repository.observeProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
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

    fun setPlaceName(value: String) = _state.update { it.copy(placeName = value) }
    fun setLocationAddress(value: String) = _state.update { it.copy(locationAddress = value) }
    fun setCustomerName(value: String) = _state.update { it.copy(customerName = value) }
    fun setCustomerPhone(value: String) = _state.update { it.copy(customerPhone = value) }
    fun setCustomerEmail(value: String) = _state.update { it.copy(customerEmail = value) }
    fun setCurrency(code: String) = _state.update { it.copy(currencyCode = code) }
    fun setCategory(category: ReceiptCategory) = _state.update { it.copy(category = category) }
    fun setPaymentType(type: PaymentType) = _state.update { it.copy(paymentType = type) }
    fun setDiscount(value: Double) = _state.update { it.copy(discount = value) }
    fun setTaxPercent(value: Double) = _state.update { it.copy(taxPercent = value) }
    fun setNotesPage1(value: String) = _state.update { it.copy(notesPage1 = value) }
    fun setNotesPage2(value: String) = _state.update { it.copy(notesPage2 = value) }
    fun setSaveSignatureAsDefault(value: Boolean) =
        _state.update { it.copy(saveSignatureAsDefault = value) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

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
                            message = error.message ?: "Could not find your location",
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

    /** Barcode scan: look the code up in inventory and add it as a line. */
    fun addByBarcode(barcode: String) {
        viewModelScope.launch {
            val product = repository.productByBarcode(barcode)
            if (product == null) {
                _state.update { it.copy(message = "No product for barcode $barcode") }
            } else {
                addItem(
                    ReceiptItem(
                        productName = product.name,
                        qty = 1.0,
                        unitPrice = product.price,
                    )
                )
            }
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
                _state.update { it.copy(message = "Could not read that image") }
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
                            message = "Scanned ${parsed.items.size} item(s)",
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(scanning = false, message = error.message ?: "Scan failed")
                    }
                }
        }

    /** Writes the receipt and clears the form, like the original Generate button. */
    fun generate(onGenerated: (Long) -> Unit = {}) {
        val current = _state.value
        if (!current.canGenerate) {
            _state.update { it.copy(message = "Add a store name and at least one item") }
            return
        }

        viewModelScope.launch {
            val receipt = Receipt(
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
            repository.receipt(id)?.let { saved ->
                FirebaseSync.pushReceipt(getApplication<Application>(), saved)
            }

            _state.value = ReceiptFormState(
                currencyCode = current.currencyCode,
                signatureBase64 = if (current.saveSignatureAsDefault) {
                    current.signatureBase64
                } else {
                    null
                },
                saveSignatureAsDefault = current.saveSignatureAsDefault,
                message = "Receipt #$id generated",
            )
            onGenerated(id)
        }
    }

    fun clear() {
        _state.value = ReceiptFormState(currencyCode = _state.value.currencyCode)
    }

    private companion object {
        /** Long-edge cap for images sent to the OCR parser. */
        const val MAX_SCAN_EDGE = 1600
    }
}
