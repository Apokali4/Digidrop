package com.example.qrhelper

import com.example.qrhelper.data.CargoBarcodeType
import com.example.qrhelper.data.DeliveryStatus
import java.util.Locale

internal data class CargoImportPlan(
    val barcode: String,
    val itemName: String,
    val expectedQty: Int,
    val scannedQty: Int,
    val barcodeType: CargoBarcodeType,
    val note: String,
    val ownerName: String,
    val workingHours: String,
    val timeSlot: String,
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
    val hasDiscrepancy = items.any { it.discrepancyQty > 0 }
    val allRowsAccounted = items.all { item ->
        val expected = item.totalBoxes.coerceAtLeast(0)
        val accounted = item.scannedBoxes.coerceAtLeast(0) + item.discrepancyQty.coerceAtLeast(0)
        if (expected == 0) item.isDelivered else (item.isDelivered && item.scannedBoxes <= 0) || accounted >= expected
    }
    return when {
        hasDiscrepancy -> DeliveryStatus.HAS_DISCREPANCY
        allRowsAccounted -> DeliveryStatus.COMPLETED
        items.any { it.scannedBoxes > 0 || it.isDelivered } -> DeliveryStatus.IN_PROGRESS
        else -> DeliveryStatus.PENDING
    }
}

/**
 * A physical address is one navigation stop. Every supplier/customer row at that
 * address remains a separate cargo/delivery entry inside the same Room point.
 */
internal object RouteImportMapper {
    private val legalEntityPattern = Regex(
        """(?iu)\b(?:ИП|ООО|АО|ПАО|ОАО|ЗАО|ЧУП|УП|ГУП|МУП|ТОО|ОГРН|ИНН)\b"""
    )
    private val genericStoreNames = setOf("", "точка", "точкабезназвания", "магазин", "получатель")
    private val supplierBrandPattern = Regex("""(?iu)\b(?:кока[ -]?кола|coca[ -]?cola|пепси|pepsi|балтика|хайнекен|heineken)\b""")

    fun map(items: List<RouteItem>): List<PointImportPlan> =
        // One physical address is one route stop. Supplier, client and cargo
        // rows remain separate cargo entries inside that stop.
        items.filter { it.address.isNotBlank() }
            .groupBy { PhotoRouteTextParser.canonicalAddressKey(it.address) }
            .values
            .mapIndexed { index, group ->
                val status = deliveryStatusForItems(group)
                val representative = group.first()
                PointImportPlan(
                    storeName = group.firstNotNullOfOrNull { physicalStoreLabel(it).takeIf(String::isNotBlank) }
                        ?: group.firstOrNull { it.storeName.isNotBlank() }?.storeName.orEmpty(),
                    address = representative.address,
                    phone = group.firstOrNull { it.phone.isNotBlank() }?.phone.orEmpty(),
                    latitude = group.firstNotNullOfOrNull { it.latitude },
                    longitude = group.firstNotNullOfOrNull { it.longitude },
                    photoDocUri = group.firstOrNull { it.photoDocUri.isNotBlank() }?.photoDocUri.orEmpty(),
                    signaturePath = group.firstOrNull { it.signaturePath.isNotBlank() }?.signaturePath.orEmpty(),
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
                            timeSlot = item.timeSlot,
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

    private fun physicalStoreLabel(item: RouteItem): String {
        val suppliedName = item.storeName.trim()
        val meaningful = suppliedName.takeIf {
            normalizeIdentity(it) !in genericStoreNames && !legalEntityPattern.containsMatchIn(it) &&
                !supplierBrandPattern.containsMatchIn(it)
        }
        return meaningful ?: storeNameFromAddress(item.address)
    }

    /** Handles route sheets where the shop brand is printed after the house number. */
    private fun storeNameFromAddress(address: String): String {
        val street = Regex(
            """(?iu)(?:ул\.?|улица|пр\.?|проспект|пер\.?|переулок|ш\.?|шоссе|наб\.?|набережная|пл\.?|площадь|б-р|бул\.?)"""
        ).find(address) ?: return ""
        val house = Regex("""(?iu)(?:[ ,]*(?:д\.?|дом)\s*)?\d{1,4}[а-яa-z]?(?:(?:[/\-]|к)\d{1,4}[а-яa-z]?)?""")
            .find(address, street.range.last + 1) ?: return ""
        val suffix = address.substring(house.range.last + 1)
            .trim(' ', ',', ';', '|', '.', ':', '-', '—')
        return suffix.takeIf {
            it.isNotBlank() && !legalEntityPattern.containsMatchIn(it) &&
                !Regex("""(?iu)\b(?:телефон|тел\.?|ип|ооо|режим|доставить|до\s*\d)\b""").containsMatchIn(it)
        }.orEmpty()
    }

    private fun normalizeIdentity(value: String): String {
        if (value.isBlank()) return ""
        return value.lowercase(Locale("ru", "RU"))
            .replace("ё", "е")
            .replace(Regex("""(?iu)\bи\b"""), "")
            .replace(Regex("""[^\p{L}\p{N}]"""), "")
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

/**
 * Refuse a destructive route replacement if the mapper would discard any row.
 * This validation runs before the Room transaction deactivates the old route.
 */
internal fun requireCompleteRouteImport(
    items: List<RouteItem>,
    points: List<PointImportPlan>
) {
    require(items.isNotEmpty()) { "Нельзя заменить маршрут пустым списком" }
    require(items.all { it.address.isNotBlank() }) {
        "В маршрутном списке есть строки без адреса точки доставки"
    }
    require(points.isNotEmpty() && points.sumOf { it.cargo.size } == items.size) {
        "Не все записи доставки удалось включить в маршрут"
    }
}
