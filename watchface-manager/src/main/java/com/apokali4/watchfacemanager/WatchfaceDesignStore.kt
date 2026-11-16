package com.apokali4.watchfacemanager

import org.json.JSONObject

object WatchfaceDesignStore {
    private const val KEY_DESIGN = "pipboy_design_v1"

    fun save(prefs: android.content.SharedPreferences, design: WatchfaceDesign) {
        val root = JSONObject()
            .put("theme", design.theme.name)
            .put("timeFormat", design.timeFormat.name)
            .put("showSeconds", design.showSeconds)
        val elements = JSONObject()
        design.elements.forEach { (id, style) ->
            elements.put(id.name, JSONObject()
                .put("x", style.x.toDouble())
                .put("y", style.y.toDouble())
                .put("scale", style.scale.toDouble())
                .put("visible", style.visible)
                .put("color", style.colorArgb))
        }
        root.put("elements", elements)
        check(prefs.edit().putString(KEY_DESIGN, root.toString()).commit()) {
            "Не удалось сохранить настройки циферблата."
        }
    }

    fun load(prefs: android.content.SharedPreferences): WatchfaceDesign {
        val raw = prefs.getString(KEY_DESIGN, null) ?: return WatchfaceDesign.default()
        return runCatching {
            val root = JSONObject(raw)
            val defaults = WatchfaceDesign.default()
            val theme = runCatching { PipBoyTheme.valueOf(root.optString("theme")) }.getOrDefault(defaults.theme)
            val format = runCatching { TimeFormat.valueOf(root.optString("timeFormat")) }.getOrDefault(defaults.timeFormat)
            val source = root.optJSONObject("elements") ?: JSONObject()
            val elements = FaceElementId.entries.associateWith { id ->
                val base = defaults.elements.getValue(id)
                val saved = source.optJSONObject(id.name) ?: return@associateWith base
                runCatching {
                    ElementStyle(
                        x = saved.optDouble("x", base.x.toDouble()).toFloat(),
                        y = saved.optDouble("y", base.y.toDouble()).toFloat(),
                        scale = saved.optDouble("scale", base.scale.toDouble()).toFloat(),
                        visible = saved.optBoolean("visible", base.visible),
                        colorArgb = saved.optInt("color", base.colorArgb)
                    )
                }.getOrDefault(base)
            }
            WatchfaceDesign(theme, format, root.optBoolean("showSeconds", false), elements)
        }.getOrDefault(WatchfaceDesign.default())
    }

    fun clear(prefs: android.content.SharedPreferences) {
        prefs.edit().remove(KEY_DESIGN).apply()
    }
}
