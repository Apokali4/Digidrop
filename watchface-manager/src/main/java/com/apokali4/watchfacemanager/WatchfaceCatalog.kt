package com.apokali4.watchfacemanager

data class WatchfaceEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val packageName: String,
    val assetPath: String
)

object WatchfaceCatalog {
    val pipBoy = WatchfaceEntry(
        id = "pip-boy-3000",
        title = "Pip-Boy 3000",
        subtitle = "Ретро-футуризм · Wear OS 4",
        packageName = "com.apokali4.pipboy",
        assetPath = "watchfaces/pipboy-watchface.apk"
    )
    val items = listOf(pipBoy)
}
