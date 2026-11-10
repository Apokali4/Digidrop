package com.example.qrhelper

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.qrhelper.data.CargoItemEntity
import com.example.qrhelper.data.DeliveryPointEntity
import com.example.qrhelper.data.DeliveryRouteEntity
import com.example.qrhelper.data.LogisticsDatabase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LogisticsDatabasePersistenceTest {
    @Test
    fun storeHoursAndDeliveryWindowSurviveDatabaseReopenAsDifferentFields() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "digidrop-hours-roundtrip-test.db"
        context.deleteDatabase(databaseName)

        val firstOpen = Room.databaseBuilder(
            context,
            LogisticsDatabase::class.java,
            databaseName
        ).allowMainThreadQueries().build()

        try {
            val dao = firstOpen.dao()
            val routeId = dao.insertRoute(DeliveryRouteEntity(routeNumber = "TEST"))
            val pointId = dao.insertPoint(
                DeliveryPointEntity(
                    routeId = routeId,
                    storeName = "Магазин Север",
                    address = "Калуга, ул. Победы, 2",
                    workingHours = "08:00-22:00"
                )
            )
            dao.insertCargo(
                listOf(
                    CargoItemEntity(
                        pointId = pointId,
                        barcode = "4601234567890",
                        itemName = "Молоко",
                        expectedQty = 2,
                        timeSlot = "09:00-11:00"
                    )
                )
            )
        } finally {
            firstOpen.close()
        }

        val reopened = Room.databaseBuilder(
            context,
            LogisticsDatabase::class.java,
            databaseName
        ).allowMainThreadQueries().build()
        try {
            val dao = reopened.dao()
            val restoredRoute = checkNotNull(dao.activeRouteOnce())
            val restoredPoint = dao.pointsOnce(restoredRoute.id).single()
            val restoredCargo = dao.cargoOnce(restoredPoint.id).single()

            assertEquals("08:00-22:00", restoredPoint.workingHours)
            assertEquals("09:00-11:00", restoredCargo.timeSlot)
            assertEquals("", restoredCargo.workingHours)
        } finally {
            reopened.close()
            context.deleteDatabase(databaseName)
        }
    }
}
