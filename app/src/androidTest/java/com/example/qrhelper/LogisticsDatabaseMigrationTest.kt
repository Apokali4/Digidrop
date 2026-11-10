package com.example.qrhelper

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.qrhelper.data.LogisticsDatabase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LogisticsDatabaseMigrationTest {
    @Test
    fun migrationMovesLegacyStoreHoursToDeliveryPointWithoutTouchingDeliveryWindows() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE delivery_points (id INTEGER NOT NULL PRIMARY KEY, storeName TEXT NOT NULL)"
                    )
                    db.execSQL(
                        "CREATE TABLE cargo_items (id INTEGER NOT NULL PRIMARY KEY, pointId INTEGER NOT NULL, workingHours TEXT NOT NULL, timeSlot TEXT NOT NULL DEFAULT '')"
                    )
                    db.execSQL("INSERT INTO delivery_points (id, storeName) VALUES (1, 'Магазин Север')")
                    db.execSQL("INSERT INTO delivery_points (id, storeName) VALUES (2, 'Магазин Юг')")
                    db.execSQL("INSERT INTO cargo_items (id, pointId, workingHours, timeSlot) VALUES (1, 1, '08:00-22:00', '09:00-11:00')")
                    db.execSQL("INSERT INTO cargo_items (id, pointId, workingHours, timeSlot) VALUES (2, 1, '08:00-22:00', '13:00-15:00')")
                    db.execSQL("INSERT INTO cargo_items (id, pointId, workingHours, timeSlot) VALUES (3, 2, '', '')")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        try {
            val database = helper.writableDatabase
            LogisticsDatabase.MIGRATION_5_6.migrate(database)

            database.query("SELECT workingHours FROM delivery_points WHERE id = 1").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("08:00-22:00", cursor.getString(0))
            }
            database.query("SELECT workingHours FROM delivery_points WHERE id = 2").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("", cursor.getString(0))
            }
            database.query("SELECT timeSlot FROM cargo_items WHERE id = 1").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("09:00-11:00", cursor.getString(0))
            }
            database.query("SELECT timeSlot FROM cargo_items WHERE id = 2").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("13:00-15:00", cursor.getString(0))
            }
        } finally {
            helper.close()
        }
    }
}
