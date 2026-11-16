package com.apokali4.watchfacemanager

enum class PipBoyTheme(val colorArgb: Int) {
    GREEN(0xFF9BBC0F.toInt()),
    AMBER(0xFFFFB000.toInt()),
    BLUE(0xFF65C7FF.toInt()),
    WHITE(0xFFE8E8E8.toInt())
}

enum class FaceElementId(val title: String) {
    TIME("Время"),
    DATE("Дата"),
    WEATHER("Погода"),
    HEART_RATE("Пульс"),
    STEPS("Шаги"),
    CALORIES("Калории"),
    WATCH_BATTERY("Батарея часов"),
    PHONE_BATTERY("Батарея телефона"),
    VAULT_BOY("Vault Boy")
}

data class ElementStyle(
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val scale: Float = 1f,
    val visible: Boolean = true,
    val colorArgb: Int = PipBoyTheme.GREEN.colorArgb
) {
    init {
        require(x in 0f..1f) { "Положение X должно быть в диапазоне 0..1." }
        require(y in 0f..1f) { "Положение Y должно быть в диапазоне 0..1." }
        require(scale in 0.5f..2.5f) { "Масштаб должен быть в диапазоне 0.5..2.5." }
    }
}

data class WatchfaceDesign(
    val theme: PipBoyTheme = PipBoyTheme.GREEN,
    val timeFormat: TimeFormat = TimeFormat.H24,
    val showSeconds: Boolean = false,
    val elements: Map<FaceElementId, ElementStyle>
) {
    fun updateElement(id: FaceElementId, style: ElementStyle): WatchfaceDesign {
        require(elements.containsKey(id)) { "Элемент $id отсутствует в макете." }
        return copy(elements = elements + (id to style))
    }

    fun withTheme(theme: PipBoyTheme): WatchfaceDesign =
        copy(theme = theme, elements = elements.mapValues { (_, style) -> style.copy(colorArgb = theme.colorArgb) })

    companion object {
        fun default(): WatchfaceDesign {
            val color = PipBoyTheme.GREEN.colorArgb
            return WatchfaceDesign(
                elements = mapOf(
                    FaceElementId.TIME to ElementStyle(x = 0.5f, y = 0.22f, scale = 1.35f, colorArgb = color),
                    FaceElementId.DATE to ElementStyle(x = 0.5f, y = 0.34f, colorArgb = color),
                    FaceElementId.WEATHER to ElementStyle(x = 0.5f, y = 0.48f, colorArgb = color),
                    FaceElementId.HEART_RATE to ElementStyle(x = 0.25f, y = 0.68f, colorArgb = color),
                    FaceElementId.STEPS to ElementStyle(x = 0.5f, y = 0.68f, colorArgb = color),
                    FaceElementId.CALORIES to ElementStyle(x = 0.75f, y = 0.68f, colorArgb = color),
                    FaceElementId.WATCH_BATTERY to ElementStyle(x = 0.28f, y = 0.88f, colorArgb = color),
                    FaceElementId.PHONE_BATTERY to ElementStyle(x = 0.72f, y = 0.88f, colorArgb = color),
                    FaceElementId.VAULT_BOY to ElementStyle(x = 0.5f, y = 0.52f, scale = 0.85f, colorArgb = color)
                )
            )
        }
    }
}

enum class TimeFormat {
    H12,
    H24
}
