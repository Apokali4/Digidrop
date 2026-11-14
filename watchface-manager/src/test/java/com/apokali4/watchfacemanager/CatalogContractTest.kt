package com.apokali4.watchfacemanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogContractTest {
    @Test
    fun pipBoyPackageAndAssetRemainStableForCatalogDelivery() {
        assertEquals("com.apokali4.pipboy", WatchfaceCatalog.pipBoy.packageName)
        assertEquals("watchfaces/pipboy-watchface.apk", WatchfaceCatalog.pipBoy.assetPath)
    }

    @Test
    fun catalogStartsWithOneInstallableFace() {
        assertTrue(WatchfaceCatalog.items.isNotEmpty())
        assertTrue(WatchfaceCatalog.items.all { it.packageName.isNotBlank() && it.assetPath.isNotBlank() })
    }
}
