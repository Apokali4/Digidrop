package com.example.qrhelper

import com.example.qrhelper.data.CargoBarcodeType
import com.example.qrhelper.data.DeliveryStatus

internal data class CargoImportPlan(
    val barcode: String,
    val itemName: String,
    val expectedQty: Int,
    val scannedQty: Int,
    val barcodeType: CargoBarcodeType,
    val note: String,
    val ownerName: String,
    val workingHours: String,
    val cashNote: String,
    val extraComment: String,
    val discrepancyQty: Int,
    val discrepancyReason: com.example.qrhelper.data.DiscrepancyReason?
)

internal data class PointImportPlan(
    val storeName: String,
    val address: String,
    val phone: String,
    val latitude: Double?,
    val longitude: Double?,
    val photoDocUri: String,
    val signaturePath: String,
    val sortOrder: Int,
    val status: DeliveryStatus,
    val cargo: List<CargoImportPlan>
)

internal fun deliveryStatusForItems(items: List<RouteItem>): DeliveryStatus {
    if (items.isEmpty()) return DeliveryStatus.PENDING
    val expected = items.sumOf { it.totalBoxes.coerceAtLeast(0) }
    val scanned = items.sumOf { it.scannedBoxes.coerceAtLeast(0) }
    return when {
        items.any { it.discrepancyQty > 0 } -> DeliveryStatus.HAS_DISCREPANCY
        items.all { it.isDelivered } -> DeliveryStatus.COMPLETED
        expected > 0 && scanned >= expected -> DeliveryStatus.COMPLETED
        items.any { it.isDelivered } || scanned > 0 -> DeliveryStatus.IN_PROGRESS
        else -> DeliveryStatus.PENDING
    }
}

internal object RouteImportMapper {
    fun map(items: List<RouteItem>): List<PointImportPlan> =
        items.groupBy { it.storeName.trim() to it.address.trim() }
            .entries
            .mapIndexed { index, entry ->
                val group = entry.value
                val status = deliveryStatusForItems(group)
                PointImportPlan(
                    storeName = entry.key.first,
                    address = entry.key.second,
                    phone = group.firstOrNull()?.phone.orEmpty(),
                    latitude = group.firstOrNull()?.latitude,
                    longitude = group.firstOrNull()?.longitude,
                    photoDocUri = group.firstOrNull()?.photoDocUri.orEmpty(),
                    signaturePath = group.firstOrNull()?.signaturePath.orEmpty(),
                    sortOrder = index,
                    status = status,
                    cargo = group.map { item ->
                        CargoImportPlan(
                            barcode = item.barcode.trim(),
                            itemName = item.itemName,
                            expectedQty = item.totalBoxes.coerceAtLeast(0),
                            scannedQty = item.scannedBoxes.coerceAtLeast(0),
                            barcodeType = barcodeType(item.barcode),
                            note = item.note,
                            ownerName = item.ownerName,
                            workingHours = item.workingHours,
                            cashNote = item.cashNote,
                            extraComment = item.extraComment,
                            discrepancyQty = item.discrepancyQty.coerceAtLeast(0),
                            discrepancyReason = item.discrepancyReason?.let {
                                runCatching { com.example.qrhelper.data.DiscrepancyReason.valueOf(it) }.getOrNull()
                            }
                        )
                    }
                )
            }

    private fun barcodeType(raw: String): CargoBarcodeType {
        val value = raw.trim()
        return when {
            Regex("00\\d{18}").containsMatchIn(value) -> CargoBarcodeType.SSCC
            Regex("01\\d{14}").containsMatchIn(value) -> CargoBarcodeType.GS1_128
            value.all(Char::isDigit) && value.length == 12 -> CargoBarcodeType.UPC_A
            value.all(Char::isDigit) && value.length == 13 -> CargoBarcodeType.EAN_13
            value.startsWith("http", ignoreCase = true) -> CargoBarcodeType.QR_CODE
            else -> CargoBarcodeType.DATA_MATRIX
        }
    }
}
