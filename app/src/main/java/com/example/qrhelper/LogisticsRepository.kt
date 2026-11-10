package com.example.qrhelper

import android.content.Context
import androidx.room.withTransaction
import com.example.qrhelper.data.CargoItemEntity
import com.example.qrhelper.data.DeliveryPointEntity
import com.example.qrhelper.data.DeliveryRouteEntity
import com.example.qrhelper.data.LogisticsDatabase
import com.example.qrhelper.data.ProductEntity
import com.example.qrhelper.data.ScanCodeEntity

/**
 * Mirrors an imported manifest into the local Room database.
 * SharedPreferences remain the compatibility store until all progress mutations
 * have been migrated to the repository in the next integration step.
 */
internal data class RouteHistorySnapshot(
    val routeId: Long,
    val routeNumber: String,
    val createdAt: Long,
    val pointCount: Int,
    val completedPointCount: Int,
    val expectedQty: Int,
    val scannedQty: Int
)

internal class LogisticsRepository(context: Context) {
    private val database = LogisticsDatabase.get(context.applicationContext)

    suspend fun replaceActiveRoute(
        items: List<RouteItem>,
        scanCodes: Set<String>? = null,
        catalog: List<ProductCatalogRecord> = emptyList()
    ) {
        val points = RouteImportMapper.map(items)
        requireCompleteRouteImport(items, points)
        database.withTransaction {
            val dao = database.dao()
            val previousRoute = dao.activeRouteOnce()
            val previousScanCodes = previousRoute?.let { dao.scanCodes(it.id).toSet() }.orEmpty()
            val codesToPersist = scanCodesForRouteReplacement(previousScanCodes, scanCodes)
            dao.deactivateRoutes()
            val routeCatalog = mapProductCatalog(items).map { product ->
                ProductEntity(barcode = product.barcode, productName = product.productName)
            }
            val restoredCatalog = catalog.map { product ->
                ProductEntity(barcode = product.barcode, productName = product.productName)
            }
            val allProducts = (routeCatalog + restoredCatalog)
                .distinctBy { it.barcode.trim() }
            if (allProducts.isNotEmpty()) dao.upsertProducts(allProducts)
            val routeId = dao.insertRoute(
                DeliveryRouteEntity(routeNumber = "Импорт ${System.currentTimeMillis()}")
            )
            points.forEach { point ->
                val pointId = dao.insertPoint(
                    DeliveryPointEntity(
                        routeId = routeId,
                        storeName = point.storeName,
                        address = point.address,
                        phone = point.phone,
                        sortOrder = point.sortOrder,
                        status = point.status,
                        latitude = point.latitude,
                        longitude = point.longitude,
                        photoDocUri = point.photoDocUri,
                        signaturePath = point.signaturePath
                    )
                )
                dao.insertCargo(
                    point.cargo.map { cargo ->
                        CargoItemEntity(
                            pointId = pointId,
                            barcode = cargo.barcode,
                            itemName = cargo.itemName,
                            expectedQty = cargo.expectedQty,
                            scannedQty = cargo.scannedQty,
                            barcodeType = cargo.barcodeType,
                            note = cargo.note,
                            ownerName = cargo.ownerName,
                            workingHours = cargo.workingHours,
                            cashNote = cargo.cashNote,
                            extraComment = cargo.extraComment,
                            timeSlot = cargo.timeSlot,
                            discrepancyQty = cargo.discrepancyQty,
                            discrepancyReason = cargo.discrepancyReason
                        )
                    }
                )
            }
            codesToPersist.forEach { code ->
                dao.insertScanCode(ScanCodeEntity(routeId = routeId, code = code))
            }
        }
    }

    /**
     * Updates the current route in place. Editing a route must not create a fake
     * archived trip every time a point is renamed, removed, or reordered.
     */
    suspend fun updateActiveRoute(items: List<RouteItem>) {
        val points = RouteImportMapper.map(items)
        // Validate before deleting the current points. Room can roll back SQL
        // failures, but it cannot protect against a logically incomplete import.
        requireCompleteRouteImport(items, points)
        database.withTransaction {
            val dao = database.dao()
            val activeRoute = dao.activeRouteOnce()
            val routeId = if (activeRoute == null) {
                dao.insertRoute(DeliveryRouteEntity(routeNumber = "Импорт ${System.currentTimeMillis()}"))
            } else {
                dao.deletePointsForRoute(activeRoute.id)
                activeRoute.id
            }
            val catalog = mapProductCatalog(items).map { product ->
                ProductEntity(barcode = product.barcode, productName = product.productName)
            }
            if (catalog.isNotEmpty()) dao.upsertProducts(catalog)
            points.forEach { point ->
                val pointId = dao.insertPoint(
                    DeliveryPointEntity(
                        routeId = routeId,
                        storeName = point.storeName,
                        address = point.address,
                        phone = point.phone,
                        sortOrder = point.sortOrder,
                        status = point.status,
                        latitude = point.latitude,
                        longitude = point.longitude,
                        photoDocUri = point.photoDocUri,
                        signaturePath = point.signaturePath
                    )
                )
                dao.insertCargo(
                    point.cargo.map { cargo ->
                        CargoItemEntity(
                            pointId = pointId,
                            barcode = cargo.barcode,
                            itemName = cargo.itemName,
                            expectedQty = cargo.expectedQty,
                            scannedQty = cargo.scannedQty,
                            barcodeType = cargo.barcodeType,
                            note = cargo.note,
                            ownerName = cargo.ownerName,
                            workingHours = cargo.workingHours,
                            cashNote = cargo.cashNote,
                            extraComment = cargo.extraComment,
                            timeSlot = cargo.timeSlot,
                            discrepancyQty = cargo.discrepancyQty,
                            discrepancyReason = cargo.discrepancyReason
                        )
                    }
                )
            }
        }
    }

    suspend fun activeRouteItems(): List<RouteItem> {
        val dao = database.dao()
        val route = dao.activeRouteOnce() ?: return emptyList()
        val items = dao.pointsOnce(route.id).flatMap { point ->
            dao.cargoOnce(point.id).map { cargo ->
                RouteItem(
                    barcode = cargo.barcode,
                    storeName = point.storeName,
                    address = point.address,
                    itemName = cargo.itemName,
                    totalBoxes = cargo.expectedQty,
                    phone = point.phone,
                    note = cargo.note,
                    isDelivered = point.status == com.example.qrhelper.data.DeliveryStatus.COMPLETED,
                    latitude = point.latitude,
                    longitude = point.longitude,
                    photoDocUri = point.photoDocUri,
                    signaturePath = point.signaturePath,
                    scannedBoxes = cargo.scannedQty,
                    ownerName = cargo.ownerName,
                    workingHours = cargo.workingHours,
                    cashNote = cargo.cashNote,
                    extraComment = cargo.extraComment,
                    timeSlot = cargo.timeSlot,
                    discrepancyQty = cargo.discrepancyQty,
                    discrepancyReason = cargo.discrepancyReason?.name
                )
            }
        }
        val catalog = mapProductCatalog(items).map { ProductEntity(it.barcode, it.productName) }
        if (catalog.isNotEmpty()) dao.upsertProducts(catalog)
        return items
    }

    suspend fun registerUniqueScan(code: String): Boolean = database.withTransaction {
        val normalized = code.trim()
        require(normalized.isNotEmpty()) { "Пустой код упаковки" }
        val dao = database.dao()
        val route = checkNotNull(dao.activeRouteOnce()) { "Нет активного рейса для регистрации скана" }
        dao.insertScanCode(ScanCodeEntity(routeId = route.id, code = normalized)) != -1L
    }

    suspend fun activeUniqueScanCodes(): Set<String> {
        val dao = database.dao()
        val route = dao.activeRouteOnce() ?: return emptySet()
        return dao.scanCodes(route.id).toSet()
    }

    suspend fun clearActiveUniqueScanCodes() {
        database.withTransaction {
            val dao = database.dao()
            val route = dao.activeRouteOnce() ?: return@withTransaction
            dao.clearScanCodes(route.id)
        }
    }

    suspend fun resetActiveRouteProgress(items: List<RouteItem>) {
        database.withTransaction {
            val dao = database.dao()
            val route = checkNotNull(dao.activeRouteOnce()) {
                "Нет активного рейса для сброса прогресса"
            }
            dao.clearScanCodes(route.id)
            syncProgressInTransaction(items)
        }
    }

    suspend fun restoreUniqueScanCodes(codes: Set<String>) {
        if (codes.isEmpty()) return
        database.withTransaction {
            val dao = database.dao()
            val route = checkNotNull(dao.activeRouteOnce()) { "Нет активного рейса для восстановления кодов" }
            codes.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { code ->
                dao.insertScanCode(ScanCodeEntity(routeId = route.id, code = code))
            }
        }
    }
    suspend fun restoreArchivedRoute(routeId: Long): List<RouteItem> {
        database.withTransaction {
            val dao = database.dao()
            dao.deactivateRoutes()
            check(dao.activateArchivedRoute(routeId) == 1) {
                "Архивный рейс не найден или уже активен"
            }
        }
        return activeRouteItems()
    }

    suspend fun allEvidencePaths(): Set<String> =
        database.dao().allEvidencePaths().filter { it.isNotBlank() }.toSet()

    suspend fun savePhotoForPoint(storeName: String, address: String, path: String): String? =
        database.withTransaction {
            val dao = database.dao()
            val route = checkNotNull(dao.activeRouteOnce()) {
                "Нет активного рейса для сохранения фото"
            }
            val point = dao.pointsOnce(route.id).firstOrNull {
                it.storeName.trim() == storeName.trim() && it.address.trim() == address.trim()
            } ?: error("Точка доставки не найдена: $storeName, $address")
            val previousPath = point.photoDocUri
            check(dao.savePhoto(point.id, path) == 1) {
                "Фото не записано в базу данных"
            }
            previousPath.takeIf { it.isNotBlank() && it != path }
        }

    suspend fun saveSignatureForPoint(storeName: String, address: String, path: String): String? =
        database.withTransaction {
            val dao = database.dao()
            val route = checkNotNull(dao.activeRouteOnce()) {
                "Нет активного рейса для сохранения подписи"
            }
            val point = dao.pointsOnce(route.id).firstOrNull {
                it.storeName.trim() == storeName.trim() && it.address.trim() == address.trim()
            } ?: error("Точка доставки не найдена: $storeName, $address")
            val previousPath = point.signaturePath
            check(dao.saveSignature(point.id, path) == 1) {
                "Подпись не записана в базу данных"
            }
            previousPath.takeIf { it.isNotBlank() && it != path }
        }

    suspend fun syncProgress(items: List<RouteItem>) {
        database.withTransaction {
            syncProgressInTransaction(items)
        }
    }

    /**
     * Registers a unique package scan and applies its cargo progress in one Room
     * transaction. A failed progress write rolls back the unique-code insert too.
     */
    suspend fun registerUniqueScanAndSync(code: String, items: List<RouteItem>): Boolean =
        database.withTransaction {
            val normalized = code.trim()
            require(normalized.isNotEmpty()) { "Пустой код упаковки" }
            val dao = database.dao()
            val route = checkNotNull(dao.activeRouteOnce()) {
                "Нет активного рейса для регистрации скана"
            }
            if (dao.insertScanCode(ScanCodeEntity(routeId = route.id, code = normalized)) == -1L) {
                return@withTransaction false
            }
            syncProgressInTransaction(items)
            true
        }

    private suspend fun syncProgressInTransaction(items: List<RouteItem>) {
        val dao = database.dao()
        val activeRoute = dao.activeRouteOnce() ?: return
        val storedPoints = dao.pointsOnce(activeRoute.id)
        val importedGroups = items.groupBy { it.storeName.trim() to it.address.trim() }

        importedGroups.forEach { (key, group) ->
            val point = storedPoints.firstOrNull {
                it.storeName.trim() == key.first && it.address.trim() == key.second
            } ?: return@forEach

            val storedCargo = dao.cargoOnce(point.id)
            group.groupBy { it.barcode.trim() to it.itemName.trim() }.forEach { (cargoKey, rows) ->
                val matchingCargo = storedCargo.filter {
                    it.barcode.trim() == cargoKey.first && it.itemName.trim() == cargoKey.second
                }
                rows.forEachIndexed { index, item ->
                    matchingCargo.getOrNull(index)?.let { cargo ->
                        dao.setScannedQty(cargo.id, item.scannedBoxes.coerceAtLeast(0))
                        val reason = item.discrepancyReason?.let {
                            runCatching { com.example.qrhelper.data.DiscrepancyReason.valueOf(it) }.getOrNull()
                        }
                        check(
                            dao.updateDiscrepancy(cargo.id, item.discrepancyQty.coerceAtLeast(0), reason) == 1
                        ) {
                            "Не удалось сохранить расхождение для товара: ${item.itemName}"
                        }
                    }
                }
            }

            // Status is derived only after the cargo rows have been persisted.
            // This prevents an optimistic UI flag or a stale aggregate from
            // completing a point while one of its own rows is still unresolved.
            val refreshedCargo = dao.cargoOnce(point.id)
            val hasDiscrepancy = refreshedCargo.any { it.discrepancyQty > 0 }
            val allRowsAccounted = refreshedCargo.isNotEmpty() && refreshedCargo.all { cargo ->
                cargo.expectedQty <= 0 ||
                    cargo.scannedQty.coerceAtLeast(0) + cargo.discrepancyQty.coerceAtLeast(0) >= cargo.expectedQty
            }
            val explicitlyDeliveredWithoutPartialScan =
                group.all { it.isDelivered } && group.all { it.scannedBoxes <= 0 }
            val status = when {
                hasDiscrepancy -> com.example.qrhelper.data.DeliveryStatus.HAS_DISCREPANCY
                explicitlyDeliveredWithoutPartialScan ->
                    com.example.qrhelper.data.DeliveryStatus.COMPLETED
                allRowsAccounted && refreshedCargo.any { it.expectedQty > 0 } ->
                    com.example.qrhelper.data.DeliveryStatus.COMPLETED
                refreshedCargo.any { it.scannedQty > 0 } -> com.example.qrhelper.data.DeliveryStatus.IN_PROGRESS
                else -> com.example.qrhelper.data.DeliveryStatus.PENDING
            }
            dao.updateStatus(point.id, status)
        }
    }

    suspend fun closePoint(storeName: String, address: String): Boolean {
        val dao = database.dao()
        val route = dao.activeRouteOnce() ?: return false
        val point = dao.pointsOnce(route.id).firstOrNull {
            it.storeName.trim() == storeName.trim() && it.address.trim() == address.trim()
        } ?: return false
        return dao.closePoint(point.id)
    }

    suspend fun archiveActiveRoute(): Boolean = database.withTransaction {
        val dao = database.dao()
        if (dao.activeRouteOnce() == null) return@withTransaction false
        dao.deactivateRoutes()
        dao.activeRouteOnce() == null
    }

    suspend fun searchProducts(query: String): List<ProductEntity> =
        database.dao().searchProducts(query.trim())

    suspend fun allProducts(): List<ProductEntity> = database.dao().allProducts()

    suspend fun restoreProducts(products: List<ProductCatalogRecord>) {
        val entities = products.map { ProductEntity(barcode = it.barcode, productName = it.productName) }
        if (entities.isNotEmpty()) database.dao().upsertProducts(entities)
    }

    suspend fun history(): List<RouteHistorySnapshot> {
        val dao = database.dao()
        return dao.archivedRoutesOnce().map { route ->
            RouteHistorySnapshot(
                routeId = route.id,
                routeNumber = route.routeNumber,
                createdAt = route.createdAt,
                pointCount = dao.pointCount(route.id),
                completedPointCount = dao.completedPointCount(route.id),
                expectedQty = dao.expectedRouteQty(route.id),
                scannedQty = dao.scannedRouteQty(route.id)
            )
        }
    }

    suspend fun syncOrder(items: List<RouteItem>) {
        database.withTransaction {
            val dao = database.dao()
            val activeRoute = dao.activeRouteOnce() ?: return@withTransaction
            val storedPoints = dao.pointsOnce(activeRoute.id)
            items.groupBy { it.storeName.trim() to it.address.trim() }
                .entries
                .forEachIndexed { index, entry ->
                    val point = storedPoints.firstOrNull {
                        it.storeName.trim() == entry.key.first && it.address.trim() == entry.key.second
                    }
                    if (point != null) dao.updateSortOrder(point.id, index)
                }
        }
    }
}
