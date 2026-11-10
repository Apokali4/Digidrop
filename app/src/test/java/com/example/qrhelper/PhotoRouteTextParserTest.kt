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


    @Test fun groupsThirtySixDeliveryRowsIntoTwentyTwoPhysicalStops() {
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

    @Test fun doesNotTreatRussianAbbreviationUpAsStreetOrLegalEntity() {
        val text = """
            УП ПостоваловаЕАаБАКОцЕИ ЕЕ 5
            г. Калуга, ул. Кирова, д. 12
        """.trimIndent()
        assertEquals(listOf("г. Калуга, ул. Кирова, д. 12"), PhotoRouteTextParser.extractCandidates(text, "Калуга"))
        assertEquals("", PhotoRouteTextParser.extractMetadata(text, "г. Калуга, ул. Кирова, д. 12").storeName)
    }

    @Test fun preservesFullLegibleStoreNameInsteadOfTruncatingLongLastWord() {
        assertEquals("Павильон Калуга жарит пекарня",
            PhotoRouteTextParser.cleanStoreName("Павильон Калуга жарит пекарня"))
    }

    @Test fun removesBarcodeSuffixWithoutDeletingStoreName() {
        assertEquals("Шаурма", PhotoRouteTextParser.cleanStoreName("Шаурма x1pe-6001230285"))
    }

    @Test fun rejectsOcrGarbageAndGeneratedPointLabelsAsStoreNames() {
        assertEquals("", PhotoRouteTextParser.cleanStoreName("Букпр: 2"))
        assertEquals("", PhotoRouteTextParser.cleanStoreName("Точка 20"))
        assertEquals("", PhotoRouteTextParser.cleanStoreName("крп. 178"))
    }

    @Test fun doesNotBorrowNextDeliveryBrandForCurrentAddress() {
        val text = """
            Калуга, ул. Ленина, д. 10
            Бристоль
            Калуга, ул. Победы, д. 2
        """.trimIndent()
        assertEquals("", PhotoRouteTextParser.extractMetadata(text, "Калуга, ул. Ленина, д. 10").storeName)
        assertEquals("Бристоль", PhotoRouteTextParser.extractMetadata(text, "Калуга, ул. Победы, д. 2").storeName)
    }

    @Test fun doesNotUseCompanyTextAfterAddressAsStoreName() {
        val text = """
            Калуга, ул. Ленина, д. 10
            ООО Следующая организация
            Калуга, ул. Победы, д. 2
        """.trimIndent()
        assertEquals("", PhotoRouteTextParser.extractMetadata(text, "Калуга, ул. Ленина, д. 10").storeName)
    }

    @Test fun rejectsNearbyButDifferentGeocoderAddressInsteadOfInventingRouteStop() {
        assertTrue(
            !PhotoRouteGeocodingQuery.matchesRequestedAddress(
                requestedAddress = "г. Калуга, ул. Никитина, д. 21",
                candidateStreet = "улица Ленина",
                candidateHouse = "13"
            )
        )
    }

    @Test fun acceptsGeocoderOnlyWhenStreetAndHouseBothMatch() {
        assertTrue(
            PhotoRouteGeocodingQuery.matchesRequestedAddress(
                requestedAddress = "г. Калуга, ул. Никитина, д. 21",
                candidateStreet = "улица Никитина",
                candidateHouse = "21"
            )
        )
        assertTrue(
            PhotoRouteGeocodingQuery.matchesRequestedAddress(
                requestedAddress = "Калуга, ул. Тарутинская, д. 72",
                candidateStreet = null,
                candidateHouse = null,
                candidateAddressLine = "Россия, Калужская область, Калуга, улица Тарутинская, 72"
            )
        )
    }

    @Test fun photoRouteImportOnlyAcceptsAddressesInsideNumberedCustomerRows() {
        val text = """
            Задание на отгрузку №Л00000006924
            г. Калуга, ул. Ленина, д. 13
            1 1708 Балтика ПК ООО г. Калуга, ул. Врубовая, д. 2, Бристоль
            2 1646 Альфа Рязань ООО г. Калуга, ул. Никитиной, д. 21Б
            Итого: 2 точки
        """.trimIndent()

        assertEquals(
            listOf(
                "г. Калуга, ул. Врубовая, д. 2",
                "г. Калуга, ул. Никитиной, д. 21Б"
            ),
            PhotoRouteTextParser.extractNumberedRouteAddressRows(text, "Калуга")
        )
    }

    @Test fun photoRouteImportDoesNotInventAddressFromUnnumberedStreetMention() {
        val text = """
            Маршрут № 18
            ул. Ленина, д. 13
            Итого 19 мест
            1 1708 Балтика ПК ООО г. Калуга, ул. Врубовая, д. 2
        """.trimIndent()

        assertEquals(
            listOf("г. Калуга, ул. Врубовая, д. 2"),
            PhotoRouteTextParser.extractNumberedRouteAddressRows(text, "Калуга")
        )
    }

    @Test fun photoRouteImportKeepsWrappedHouseNumberInsideItsNumberedRow() {
        val text = """
            1 1708 Балтика ПК ООО г. Калуга, ул. Врубовая,
            д. 2, Бристоль
            2 1646 Альфа Рязань ООО г. Калуга, ул. Никитиной, д. 21
        """.trimIndent()

        assertEquals(
            listOf(
                "г. Калуга, ул. Врубовая, д. 2",
                "г. Калуга, ул. Никитиной, д. 21"
            ),
            PhotoRouteTextParser.extractNumberedRouteAddressRows(text, "Калуга")
        )
    }


    @Test fun photoRouteImportRecoversUnnumberedRowsWhenTheWholeDocumentHasNoRowNumbers() {
        val text = """
            ООО Север
            г. Калуга, ул. Кирова, д. 8
            Пятёрочка
            Калуга, проспект Ленина, 14
            ИП Иванов
            Калуга, ул. Победы, д. 2
        """.trimIndent()

        assertEquals(
            listOf(
                "г. Калуга, ул. Кирова, д. 8",
                "Калуга, проспект Ленина, 14",
                "Калуга, ул. Победы, д. 2"
            ),
            PhotoRouteTextParser.extractNumberedRouteAddressRows(text, "Калуга")
        )
    }

    @Test fun photoRouteImportKeepsFirstRowWhenItsNumberIsLostButStoreNameRemains() {
        val text = """
            Задание на отгрузку
            ООО Север г. Калуга, ул. Кирова, д. 8
            2 Пятёрочка Калуга, проспект Ленина, 14
        """.trimIndent()

        assertEquals(
            listOf(
                "г. Калуга, ул. Кирова, д. 8",
                "Калуга, проспект Ленина, 14"
            ),
            PhotoRouteTextParser.extractNumberedRouteAddressRows(text, "Калуга")
        )
    }

    @Test fun photoRouteImportRecoversUnnumberedRowsBetweenNumberedRowsButSkipsHeaderAddress() {
        val text = """
            Задание на отгрузку
            г. Калуга, ул. Ленина, д. 13
            1 ООО Север г. Калуга, ул. Кирова, д. 8
            Пятёрочка Калуга, проспект Ленина, 14
            3 ИП Иванов Калуга, ул. Победы, д. 2
        """.trimIndent()

        assertEquals(
            listOf(
                "г. Калуга, ул. Кирова, д. 8",
                "Калуга, проспект Ленина, 14",
                "Калуга, ул. Победы, д. 2"
            ),
            PhotoRouteTextParser.extractNumberedRouteAddressRows(text, "Калуга")
        )
    }

    @Test fun thirtySixUnnumberedDeliveryRowsRemainThirtySixRowsAcrossTwentySevenStops() {
        val rows = buildList {
            repeat(9) { index ->
                add("ООО Магазин $index, г. Калуга, ул. Тестовая, д. ${index + 1}")
                add("ИП Получатель $index, Калуга, ул. Тестовая, д. ${index + 1}")
            }
            repeat(18) { index ->
                add("ООО Получатель $index, Калуга, ул. Новая, д. ${index + 1}")
            }
        }
        val text = rows.joinToString("\n")
        val extracted = PhotoRouteTextParser.extractNumberedRouteAddressRows(text, "Калуга")
        val grouped = groupPhotoRouteAddresses(extracted)

        assertEquals(36, extracted.size)
        assertEquals(27, grouped.size)
        assertEquals(36, grouped.sumOf { it.size })
    }

    @Test fun photoRouteImportAcceptsNumberedRowsWithoutCustomerCodes() {
        val text = """
            Маршрут на день
            1 Магазин Север г. Калуга, ул. Кирова, д. 8
            2 Пятёрочка г. Калуга, ул. Ленина, д. 14
            Итого: 2 точки
        """.trimIndent()

        assertEquals(
            listOf(
                "г. Калуга, ул. Кирова, д. 8",
                "г. Калуга, ул. Ленина, д. 14"
            ),
            PhotoRouteTextParser.extractNumberedRouteAddressRows(text, "Калуга")
        )
    }

    // [build-apk] Keep conditional OCR work covered in the APK verification run.
    @Test fun ocrUsesQuarterTurnOnlyWhenFirstPassIsWeak() {
        assertEquals(false, RussianOcrPassPlanner.shouldTryQuarterTurn(500))
        assertEquals(true, RussianOcrPassPlanner.shouldTryQuarterTurn(100))
    }

    @Test fun ocrUsesAdaptiveThresholdOnlyAsRecovery() {
        assertEquals(false, RussianOcrPassPlanner.shouldTryAdaptive(700))
        assertEquals(true, RussianOcrPassPlanner.shouldTryAdaptive(200))
    }


    @Test fun parsesRouteTableColumnsWithoutRequiringNumberedRows() {
        val text = listOf(
            "№\tКод\tКлиент\tАдрес доставки\tТелефон\tМест\tКомментарий",
            "1\t1708\tБалтика ПК ООО\tг. Калуга, ул. Врубовая, д. 2\t8 900 111 22 33\t4\tПозвонить перед приездом",
            "2\t1646\tАльфа Рязань ООО\tг. Калуга, ул. Никитиной, д. 21Б\t8 900 222 33 44\t2\tЗабрать документы"
        ).joinToString("\n")

        val rows = PhotoRouteTableParser.parse(text, "Калуга")
        assertEquals(2, rows.size)
        assertEquals("1708", rows[0].customerCode)
        assertEquals("Балтика ПК ООО", rows[0].organization)
        assertEquals("г. Калуга, ул. Врубовая, д. 2", rows[0].address)
        assertEquals("8 900 111 22 33", rows[0].phone)
        assertEquals(4, rows[0].placeCount)
        assertEquals("Позвонить перед приездом", rows[0].comment)
    }

    @Test fun tableParserInheritsColumnSchemaAcrossHeaderlessPagesAndKeepsDuplicateDeliveries() {
        val pageOne = listOf(
            "№\tКод\tОрганизация\tАдрес\tКоробки\tМеста",
            "1\t1708\tБалтика ООО\tКалуга, ул. Кирова, д. 8\t3\t5"
        ).joinToString("\n")
        val pageTwo = listOf(
            "2\t1646\tАльфа ООО\tКалуга, ул. Кирова, д. 8\t2\t4",
            "3\t1900\tСевер ООО\tКалуга, ул. Победы, д. 2\t1\t1"
        ).joinToString("\n")

        val rows = PhotoRouteTableParser.parse("$pageOne\n--- Следующая фотография ---\n$pageTwo", "Калуга")
        assertEquals(3, rows.size)
        assertEquals(2, rows.count { it.address.contains("Кирова") })
        assertEquals(2, rows[1].packageCount)
        assertEquals(4, rows[1].placeCount)
        assertEquals(3, groupPhotoRouteAddresses(rows.map { it.address }).sumOf { it.size })
        assertEquals(2, groupPhotoRouteAddresses(rows.map { it.address }).size)
    }

    @Test fun tableParserDoesNotPromoteTotalsOrHeaderAddressesIntoDeliveryRows() {
        val text = """
            Маршрут: Калуга, ул. Ленина, д. 13
            № | Клиент | Адрес доставки | Мест
            1 | Пятёрочка | Калуга, ул. Кирова, д. 8 | 3
            Итого | 1 точка | 3 места
        """.trimIndent()

        val rows = PhotoRouteTableParser.parse(text, "Калуга")
        assertEquals(1, rows.size)
        assertEquals("Пятёрочка", rows.single().organization)
    }


    @Test fun tableParserCapturesContactCashInvoiceAndPreservesUnknownColumns() {
        val text = listOf(
            "№\tКод клиента\tОрганизация\tАдрес доставки\tТелефон\tКонтактное лицо\tКоробки\tМеста\tНомер накладной\tИнкассация\tПримечание",
            "7\tA-1708\tБалтика ООО\tКалуга, ул. Кирова, д. 8\t+7 900 111 22 33\tИван Петров\t3\t5\tНК-0091\t12500 руб.\tПозвонить"
        ).joinToString("\n")

        val row = PhotoRouteTableParser.parse(text, "Калуга").single()
        assertEquals("Иван Петров", row.contactPerson)
        assertEquals("НК-0091", row.invoiceNumber)
        assertEquals("12500 руб.", row.cashCollection)
        assertEquals("Позвонить", row.comment)
        assertEquals(3, row.packageCount)
        assertEquals(5, row.placeCount)
        assertEquals(11, row.rawCells.size)
        assertEquals("A-1708", row.customerCode)
    }

    @Test fun tableParserKeepsUnmappedColumnValuesInsteadOfDiscardingThem() {
        val text = listOf(
            "№\tКлиент\tАдрес\tСумма долга\tТовар",
            "1\tМагазин Север\tКалуга, ул. Победы, д. 2\t3400\tМолоко 2.5%"
        ).joinToString("\n")

        val row = PhotoRouteTableParser.parse(text, "Калуга").single()
        assertEquals(listOf("1", "Магазин Север", "Калуга, ул. Победы, д. 2", "3400", "Молоко 2.5%"), row.rawCells)
    }


    @Test fun geometryOcrGroupsWordsByVerticalPositionAndKeepsColumnGaps() {
        val text = RussianOcrGeometryText.reconstruct(
            listOf(
                RussianOcrWord("№", 10, 10, 12, 20, 96f),
                RussianOcrWord("Адрес", 180, 11, 45, 20, 94f),
                RussianOcrWord("Коробки", 360, 10, 60, 20, 91f),
                RussianOcrWord("1", 12, 50, 8, 18, 99f),
                RussianOcrWord("Калуга", 180, 51, 48, 18, 93f),
                RussianOcrWord("12", 360, 50, 18, 18, 88f)
            )
        )
        assertEquals("№\tАдрес\tКоробки\n1\tКалуга\t12", text)
    }

    @Test fun geometryOcrKeepsWordsInReadingOrderAndDoesNotMergeSeparateRows() {
        val text = RussianOcrGeometryText.reconstruct(
            listOf(
                RussianOcrWord("Вторая", 120, 42, 42, 18, 90f),
                RussianOcrWord("Первая", 10, 10, 40, 18, 90f),
                RussianOcrWord("строка", 170, 43, 40, 18, 90f),
                RussianOcrWord("строка", 60, 11, 40, 18, 90f)
            )
        )
        assertEquals("Первая строка\nВторая строка", text)
    }

    @Test fun geometryOcrIgnoresBlankWordsAndInvalidBoxes() {
        val text = RussianOcrGeometryText.reconstruct(
            listOf(
                RussianOcrWord(" ", 0, 0, 10, 10, 90f),
                RussianOcrWord("битое", 0, 0, 0, 10, 90f),
                RussianOcrWord("Адрес", 10, 10, 40, 18, 90f)
            )
        )
        assertEquals("Адрес", text)
    }


    @Test fun geometryOcrUsesHeaderColumnAnchorsWhenBodyCellsHaveSmallGaps() {
        val text = RussianOcrGeometryText.reconstruct(
            listOf(
                RussianOcrWord("№", 10, 10, 12, 20, 96f),
                RussianOcrWord("Адрес", 140, 10, 45, 20, 94f),
                RussianOcrWord("Коробки", 270, 10, 60, 20, 91f),
                RussianOcrWord("1", 10, 50, 70, 18, 99f),
                RussianOcrWord("Калуга", 110, 50, 100, 18, 93f),
                RussianOcrWord("12", 240, 50, 30, 18, 88f)
            )
        )
        assertEquals("№\tАдрес\tКоробки\n1\tКалуга\t12", text)
    }


    @Test fun tableParserPreservesIdentifiableRowsWhenAddressWasNotRecognized() {
        val rows = PhotoRouteTableParser.parse(
            listOf(
                "№\tКлиент\tАдрес доставки\tКоробки\tПримечание",
                "17\tМагазин Центральный\t[неразборчиво]\t8\tпроверить адрес"
            ).joinToString("\n"),
            "Калуга"
        )
        assertEquals(1, rows.size)
        assertEquals("17", rows.single().pointNumber)
        assertEquals("Магазин Центральный", rows.single().organization)
        assertEquals("", rows.single().address)
        assertEquals(8, rows.single().packageCount)
        assertEquals("проверить адрес", rows.single().comment)
        assertEquals(5, rows.single().rawCells.size)
    }


    @Test fun tableParserFlagsAmbiguousCountsInsteadOfInventingNumericValues() {
        val text = listOf(
            "№\tКлиент\tАдрес доставки\tКоробки\tМеста",
            "9\tМагазин Тест\tКалуга, ул. Кирова, д. 8\t1O\t12/13"
        ).joinToString("\n")
        val row = PhotoRouteTableParser.parse(text, "Калуга").single()
        assertEquals(0, row.packageCount)
        assertEquals(0, row.placeCount)
        assertEquals(listOf("Коробки: 1O", "Места: 12/13"), row.numericReviewFields)
        assertEquals("1O", row.rawCells[3])
        assertEquals("12/13", row.rawCells[4])
    }

    @Test fun tableParserAcceptsClearCountsWithCommonUnitSuffixes() {
        val text = listOf(
            "№\tКлиент\tАдрес доставки\tКоробки\tМеста",
            "10\tМагазин Тест\tКалуга, ул. Кирова, д. 8\t3 кор.\t5 мест"
        ).joinToString("\n")
        val row = PhotoRouteTableParser.parse(text, "Калуга").single()
        assertEquals(3, row.packageCount)
        assertEquals(5, row.placeCount)
        assertEquals(emptyList<String>(), row.numericReviewFields)
    }


    @Test fun tableParserFlagsAmbiguousCashAmountsWithoutChangingOriginalText() {
        val text = listOf(
            "№\\tКлиент\\tАдрес доставки\\tИнкассация",
            "11\\tМагазин Тест\\tКалуга, ул. Кирова, д. 8\\t12З00 руб."
        ).joinToString("\\n")
        val row = PhotoRouteTableParser.parse(text, "Калуга").single()
        assertEquals("12З00 руб.", row.cashCollection)
        assertEquals(listOf("Инкассация: 12З00 руб."), row.cashReviewFields)
    }

    @Test fun tableParserAcceptsClearCashAmountsAndDoesNotFlagZeroOrNoCollection() {
        val text = listOf(
            "№\\tКлиент\\tАдрес доставки\\tИнкассация",
            "12\\tМагазин А\\tКалуга, ул. Кирова, д. 8\\t12 500 руб.",
            "13\\tМагазин Б\\tКалуга, ул. Победы, д. 2\\t0"
        ).joinToString("\\n")
        val rows = PhotoRouteTableParser.parse(text, "Калуга")
        assertEquals(emptyList<String>(), rows[0].cashReviewFields)
        assertEquals(emptyList<String>(), rows[1].cashReviewFields)
    }

}
