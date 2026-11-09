package com.example.qrhelper

import android.content.Context
import androidx.room.withTransaction
import com.example.qrhelper.data.CargoItemEntity
import com.example.qrhelper.data.DeliveryPointEntity
import com.example.qrhelper.data.DeliveryRouteEntity
import com.example.qrhelper.data.LogisticsDatabase
import com.example.qrhelper.data.ProductEntity

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

    suspend fun replaceActiveRoute(items: List<RouteItem>) {
        val points = RouteImportMapper.map(items)
        database.withTransaction {
            val dao = database.dao()
            dao.deactivateRoutes()
            val catalog = mapProductCatalog(items).map { product ->
                ProductEntity(barcode = product.barcode, productName = product.productName)
            }
            if (catalog.isNotEmpty()) dao.upsertProducts(catalog)
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
                            discrepancyQty = cargo.discrepancyQty,
                            discrepancyReason = cargo.discrepancyReason
                        )
                    }
                )
            }
        }
    }

    /**
     * Updates the current route in place. Editing a route must not create a fake
     * archived trip every time a point is renamed, removed, or reordered.
     */
    suspend fun updateActiveRoute(items: List<RouteItem>) {
        val points = RouteImportMapper.map(items)
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
                    discrepancyQty = cargo.discrepancyQty,
                    discrepancyReason = cargo.discrepancyReason?.name
                )
            }
        }
        val catalog = mapProductCatalog(items).map { ProductEntity(it.barcode, it.productName) }
        if (catalog.isNotEmpty()) dao.upsertProducts(catalog)
        return items
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

    suspend fun savePhotoForPoint(storeName: String, address: String, path: String) {
        val dao = database.dao()
        val route = checkNotNull(dao.activeRouteOnce()) {
            "Нет активного рейса для сохранения фото"
        }
        val point = dao.pointsOnce(route.id).firstOrNull {
            it.storeName.trim() == storeName.trim() && it.address.trim() == address.trim()
        } ?: error("Точка доставки не найдена: $storeName, $address")
        check(dao.savePhoto(point.id, path) == 1) {
            "Фото не записано в базу данных"
        }
    }

    suspend fun saveSignatureForPoint(storeName: String, address: String, path: String) {
        val dao = database.dao()
        val route = checkNotNull(dao.activeRouteOnce()) {
            "Нет активного рейса для сохранения подписи"
        }
        val point = dao.pointsOnce(route.id).firstOrNull {
            it.storeName.trim() == storeName.trim() && it.address.trim() == address.trim()
        } ?: error("Точка доставки не найдена: $storeName, $address")
        check(dao.saveSignature(point.id, path) == 1) {
            "Подпись не записана в базу данных"
        }
    }

    suspend fun syncProgress(items: List<RouteItem>) {
        database.withTransaction {
            val dao = database.dao()
            val activeRoute = dao.activeRouteOnce() ?: return@withTransaction
            val storedPoints = dao.pointsOnce(activeRoute.id)
            val importedGroups = items.groupBy { it.storeName.trim() to it.address.trim() }

            importedGroups.forEach { (key, group) ->
                val point = storedPoints.firstOrNull {
                    it.storeName.trim() == key.first && it.address.trim() == key.second
                } ?: return@forEach

                val status = deliveryStatusForItems(group)
                dao.updateStatus(point.id, status)

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
                            dao.updateDiscrepancy(cargo.id, item.discrepancyQty.coerceAtLeast(0), reason)
                        }
                    }
                }
            }
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

    suspend fun archiveActiveRoute() {
        database.dao().deactivateRoutes()
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
