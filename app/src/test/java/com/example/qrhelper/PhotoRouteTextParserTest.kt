package com.example.qrhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoRouteTextParserTest {
    @Test fun cameraAndPhotoImportDoNotResetSavedPlannerSession() {
        assertEquals(false, shouldResetPhotoRouteSession("camera"))
        assertEquals(false, shouldResetPhotoRouteSession(null))
        assertEquals(true, shouldResetPhotoRouteSession("new"))
    }

    @Test fun extractsRussianStreetAndHouseAddressLines() {
        val text = """
            Маршрут № 18
            ООО Ромашка
            г. Калуга, ул. Кирова, д. 12
            Телефон 8 900 000 00 00
            проспект Ленина, 45к2
        """.trimIndent()
        assertEquals(
            listOf("г. Калуга, ул. Кирова, д. 12", "проспект Ленина, 45к2"),
            PhotoRouteTextParser.extractCandidates(text)
        )
    }

    @Test fun removesDuplicateAddressLines() {
        val text = "ул. Ленина, 10\nУЛ. ЛЕНИНА 10\nул. Победы, 2"
        assertEquals(2, PhotoRouteTextParser.extractCandidates(text).size)
    }

    @Test fun ignoresLinesWithoutAddressSignals() {
        assertEquals(emptyList<String>(), PhotoRouteTextParser.extractCandidates("Маршрут № 5\nИванов И.И.\n8 900 000 00 00"))
    }
    @Test fun downsamplesLargePhotosToSafeOcrSize() {
        assertEquals(1, PhotoOcrImageSizing.sampleSize(1600, 1200))
        assertEquals(2, PhotoOcrImageSizing.sampleSize(4800, 3200))
        assertEquals(4, PhotoOcrImageSizing.sampleSize(9600, 6400))
    }
    @Test fun extractsAddressFromNoisyOcrRowAndDropsBusinessTextAfterHouse() {
        val text = """47 ] 68 ТАЙДЫНОВ Расим Джалил Оглы "По |. Калуга, ул. Советская, д. 2, Шашлычная, РЦ |Нгс акции, забрать 5755,5"""
        assertEquals(
            listOf("Калуга, ул. Советская, д. 2"),
            PhotoRouteTextParser.extractCandidates(text, "Калуга")
        )
    }

    @Test fun keepsHouseBuildingSuffix() {
        assertEquals(
            listOf("проспект Ленина, 45к2"),
            PhotoRouteTextParser.extractCandidates("проспект Ленина, 45к2")
        )
    }

    @Test fun extractsPointContactAndDeliveryMetadataFromNearbyOcrLines() {
        val text = """
            ООО Ромашка
            г. Калуга, ул. Кирова, д. 12
            ИП Иванов Иван Иванович
            Телефон 8 900 123 45 67
            Время работы 09:00-18:00
            Наличные забрать у кассира
            Позвонить перед въездом
        """.trimIndent()
        val metadata = PhotoRouteTextParser.extractMetadata(text, "г. Калуга, ул. Кирова, д. 12")
        assertEquals("ООО Ромашка", metadata.storeName)
        assertEquals("ИП Иванов Иван Иванович", metadata.ownerName)
        assertEquals("8 900 123 45 67", metadata.phone)
        assertEquals("09:00-18:00", metadata.workingHours)
        assertEquals("Наличные забрать у кассира", metadata.cashNote)
        assertEquals("Позвонить перед въездом", metadata.comment)
    }

    @Test fun extractsStoreAndLegalEntityFromTheSameNoisyAddressRow() {
        val text = """
            Тов Балтика ООО
            Калуга, ул. Врубовая, д. 2163, Бристоль
            ИП Петров Петр Петрович
            Режим работы 09:00-21:00
        """.trimIndent()
        val metadata = PhotoRouteTextParser.extractMetadata(text, "Калуга, ул. Врубовая, д. 2163")
        assertEquals("Бристоль", metadata.storeName)
        assertEquals("ИП Петров Петр Петрович", metadata.ownerName)
        assertEquals("09:00-21:00", metadata.workingHours)
    }

    @Test fun recognizesChupAndPaoLegalForms() {
        val text = """
            ЧУП Магазин Север
            г. Калуга, ул. Кирова, д. 8
            ПАО Торговый дом
        """.trimIndent()
        val metadata = PhotoRouteTextParser.extractMetadata(text, "г. Калуга, ул. Кирова, д. 8")
        assertEquals("ЧУП Магазин Север", metadata.ownerName)
    }

    @Test fun metadataIncludesStoreSuffixAndLegalFormNearby() {
        val text = """
            Тов Балтика ООО
            Калуга, ул. Врубовая, д. 2163, Бристоль
            ИП Петров Петр Петрович
            Режим работы 09:00-21:00
        """.trimIndent()
        val metadata = PhotoRouteTextParser.extractMetadata(text, "Калуга, ул. Врубовая, д. 2163")
        assertEquals("Бристоль", metadata.storeName)
        assertEquals("ИП Петров Петр Петрович", metadata.ownerName)
        assertEquals("09:00-21:00", metadata.workingHours)
    }

    @Test fun recognizesChupLegalForm() {
        val text = "ЧУП Магазин Север\nг. Калуга, ул. Кирова, д. 8"
        val metadata = PhotoRouteTextParser.extractMetadata(text, "г. Калуга, ул. Кирова, д. 8")
        assertEquals("ЧУП Магазин Север", metadata.ownerName)
    }

    @Test fun extractsRouteHeaderFieldsFromTopOfRouteSheet() {
        val text = """
            Маршрут № 42-А
            Водитель: Иванов Иван Иванович
            Транспортное средство: Газель Next
            Гос. номер: А123ВС40
            Калуга, ул. Кирова, д. 12
        """.trimIndent()
        assertEquals(PhotoRouteHeader("Иванов Иван Иванович", "Газель Next", "А123ВС40", "42-А"), PhotoRouteHeaderParser.parse(text))
    }


    @Test fun deduplicatesSameStreetAndHouseAcrossOcrPunctuationVariants() {
        val text = """
            Калуга, ул. В.Никитиной, В-21
            Калуга. ул-В-Никитиной, д-21
            Калуга, ул. Тарутинская, д. 72
        """.trimIndent()
        assertEquals(2, PhotoRouteTextParser.extractCandidates(text, "Калуга").size)
    }

    @Test fun recognizesDeliveryDeadlineAndOpeningTimeAsInstructions() {
        val text = """
            Красное и Белое
            Калуга, ул. Тарутинская, д. 168
            Доставить до 10:00
            Магазин открывается с 12:00
        """.trimIndent()
        val metadata = PhotoRouteTextParser.extractMetadata(text, "Калуга, ул. Тарутинская, д. 168")
        assertEquals("Красное и Белое", metadata.storeName)
        assertEquals("до 10:00", metadata.workingHours)
        assertEquals("Магазин открывается с 12:00", metadata.comment)
    }

    @Test fun joinsStreetAndHouseWhenOcrWrapsOneAddressAcrossTwoLines() {
        val text = """
            Калуга, ул. Тарутинская,
            д. 168
            Доставить до 10:00
        """.trimIndent()
        assertEquals(
            listOf("Калуга, ул. Тарутинская, д. 168"),
            PhotoRouteTextParser.extractCandidates(text, "Калуга")
        )
    }
    @Test fun keepsMetadataFromAdjacentDeliveryPointsSeparate() {
        val text = """
            Красное и Белое
            Калуга, ул. Ленина, д. 10
            Доставить до 10:00
            Бристоль
            Калуга, ул. Победы, д. 2
            Открывается с 12:00
        """.trimIndent()

        val first = PhotoRouteTextParser.extractMetadata(text, "Калуга, ул. Ленина, д. 10")
        val second = PhotoRouteTextParser.extractMetadata(text, "Калуга, ул. Победы, д. 2")

        assertEquals("Красное и Белое", first.storeName)
        assertEquals("Бристоль", second.storeName)
        assertEquals("до 10:00", first.workingHours)
        assertEquals("с 12:00", second.workingHours)
        assertEquals("", second.comment)
    }
    @Test fun extractsHeaderFieldsFromRealisticShipmentTaskOcr() {
        val text = """
            Задание на отгрузку №Л00000006924 (07.11.2026)
            Транспортное средство: ГАЗон №К347ХВ32
            Водитель: Морозов Сергей Николаевич
        """.trimIndent()
        assertEquals(
            PhotoRouteHeader(
                driver = "Морозов Сергей Николаевич",
                vehicle = "ГАЗон",
                vehicleNumber = "К347ХВ32",
                routeNumber = "Л00000006924"
            ),
            PhotoRouteHeaderParser.parse(text)
        )
    }


    @Test fun recoversVehiclePlateWhenOcrSplitsLastDigits() {
        val text = """
            Задание на отгрузку №Л00000006924
            Транспортное средство: ГАЗон К347ХВ2 13
            Водитель: Морозов Сергей Николаевич
        """.trimIndent()
        assertEquals(
            PhotoRouteHeader("Морозов Сергей Николаевич", "ГАЗон", "К347ХВ213", "Л00000006924"),
            PhotoRouteHeaderParser.parse(text)
        )
    }

    @Test fun appliesKnownStoreHoursOnlyWhenTheRouteSheetHasNoExplicitHours() {
        val redWhite = PhotoRouteTextParser.extractMetadata(
            "Красное и Белое\nКалуга, ул. Кирова, д. 12",
            "Калуга, ул. Кирова, д. 12"
        )
        val bristol = PhotoRouteTextParser.extractMetadata(
            "Бристоль\nКалуга, ул. Ленина, д. 8",
            "Калуга, ул. Ленина, д. 8"
        )
        assertEquals("09:00–22:00", redWhite.workingHours)
        assertEquals("08:00–22:00", bristol.workingHours)
    }

    @Test fun explicitDeliveryHoursOverrideKnownStoreDefaults() {
        val metadata = PhotoRouteTextParser.extractMetadata(
            "Красное и Белое\nКалуга, ул. Кирова, д. 12\nДоставить до 10:00",
            "Калуга, ул. Кирова, д. 12"
        )
        assertEquals("до 10:00", metadata.workingHours)
    }

    @Test fun stripsApartmentAndTechnicalBarcodeNoiseFromStoreName() {
        val metadata = PhotoRouteTextParser.extractMetadata(
            "Калуга, ул. Ленина, д. 10, Шаурма яя x1pe-6001230285",
            "Калуга, ул. Ленина, д. 10"
        )
        assertEquals("Шаурма", metadata.storeName)
    }

    @Test fun doesNotMistakeApartmentNumberForStoreName() {
        val metadata = PhotoRouteTextParser.extractMetadata(
            "Калуга, ул. Ленина, д. 10, квартира 136",
            "Калуга, ул. Ленина, д. 10"
        )
        assertEquals("", metadata.storeName)
    }

    @Test fun preservesLegitimateDigitsInStoreName() {
        val metadata = PhotoRouteTextParser.extractMetadata(
            "Калуга, ул. Ленина, д. 10, Магнит 24",
            "Калуга, ул. Ленина, д. 10"
        )
        assertEquals("Магнит 24", metadata.storeName)
    }

    @Test fun recognizesStoreBrandWhenOcrDropsTheLastLetters() {
        val metadata = PhotoRouteTextParser.extractMetadata(
            "Калуга, ул. Тарутинская, д. 168, БРИСТО",
            "Калуга, ул. Тарутинская, д. 168"
        )
        assertEquals("Бристоль", metadata.storeName)
    }

    @Test fun doesNotUseBuildingAbbreviationAsStoreName() {
        val metadata = PhotoRouteTextParser.extractMetadata(
            "Калуга, ул. Ленина, д. 10, крп. 178",
            "Калуга, ул. Ленина, д. 10"
        )
        assertEquals("", metadata.storeName)
    }


    @Test fun usesSoleProprietorNameWhenStoreNameIsMissing() {
        val metadata = PhotoRouteTextParser.extractMetadata(
            """
                ИП Иванов Иван Иванович
                Калуга, ул. Ленина, д. 10
                Телефон 8 900 123 45 67
            """.trimIndent(),
            "Калуга, ул. Ленина, д. 10"
        )
        assertEquals("ИП Иванов Иван Иванович", metadata.storeName)
        assertEquals("ИП Иванов Иван Иванович", metadata.ownerName)
    }


    @Test fun preservesBuildingAndStructureInExtractedAddressAndGroupingKey() {
        val first = PhotoRouteTextParser.extractCandidates(
            "г. Калуга, ул. В. Никитиной, д. 21 Б, корп. 2, Красное и Белое",
            "Калуга"
        ).single()
        val second = PhotoRouteTextParser.extractCandidates(
            "г. Калуга, ул. В. Никитиной, д. 21 Б, корп. 3, Красное и Белое",
            "Калуга"
        ).single()

        assertEquals("г. Калуга, ул. В. Никитиной, д. 21 Б, корп. 2", first)
        assertEquals("г. Калуга, ул. В. Никитиной, д. 21 Б, корп. 3", second)
        assertTrue(PhotoRouteTextParser.canonicalAddressKey(first) != PhotoRouteTextParser.canonicalAddressKey(second))
    }

    @Test fun groupsPunctuationVariantsOfSameBuildingAndStructureTogether() {
        val first = PhotoRouteTextParser.canonicalAddressKey("г. Калуга, ул. Болдина, д. 9, корп. 1")
        val second = PhotoRouteTextParser.canonicalAddressKey("Калуга ул Болдина 9 корпус 1")
        assertEquals(first, second)
    }


    @Test fun groupsThirtySixDeliveryRowsIntoTwentyThreePhysicalStops() {
        val addresses = buildList {
            repeat(5) { add("г. Калуга, ул. Болдина, д. 9, корп. 1") }
            repeat(5) { add("Калуга, улица Болдина 9 корпус 1") }
            repeat(21) { index -> add("г. Калуга, ул. Тестовая, д. ${index + 1}") }
            repeat(5) { index -> add("Калуга, ул. Тестовая, д. ${index + 1}") }
        }

        val groups = groupPhotoRouteAddresses(addresses)

        assertEquals(36, groups.sumOf { it.size })
        assertEquals(22, groups.size)
        assertEquals(10, groups.first().size)
    }

}
