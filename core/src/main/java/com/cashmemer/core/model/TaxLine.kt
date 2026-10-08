package com.cashmemer.core.model

/** One tax or service charge on a receipt, e.g. "GST" at 17%. */
data class TaxLine(val name: String, val percent: Double)
