import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2] / "main"
ANDROID = "{http://schemas.android.com/apk/res/android}"


class WatchFacePackageTest(unittest.TestCase):
    def setUp(self):
        self.manifest = ET.parse(ROOT / "AndroidManifest.xml").getroot()
        self.watchface_path = ROOT / "res/raw/watchface.xml"
        self.watchface = ET.parse(self.watchface_path).getroot()

    def test_manifest_declares_wff_v1_for_wear_os_4(self):
        app = self.manifest.find("application")
        props = {
            p.attrib[ANDROID + "name"]: p.attrib[ANDROID + "value"]
            for p in app.findall("property")
        }
        self.assertEqual(props["com.google.wear.watchface.format.version"], "1")

    def test_exactly_four_selectable_color_themes(self):
        configurations = self.watchface.find("UserConfigurations")
        self.assertIsNotNone(configurations)
        colors = configurations.findall("ColorConfiguration/ColorOption")
        self.assertEqual([option.attrib["id"] for option in colors], ["0", "1", "2", "3"])

    def test_exactly_two_visual_states_main_and_aod(self):
        scene = self.watchface.find("Scene")
        groups = {group.attrib.get("name"): group for group in scene.findall("Group")}
        self.assertEqual(set(groups), {"main_screen", "always_on_display"})
        main = groups["main_screen"]
        aod = groups["always_on_display"]
        self.assertTrue(any(v.attrib.get("mode") == "AMBIENT" for v in main.findall("Variant")))
        self.assertTrue(any(v.attrib.get("mode") == "INTERACTIVE" for v in aod.findall("Variant")))

    def test_main_face_contains_activity_weather_and_both_batteries(self):
        xml = self.watchface_path.read_text(encoding="utf-8")
        for token in ("[STEP_COUNT]", "heart_rate", "weather", "floors",
                      "watch_battery", "phone_battery", "vault_boy"):
            self.assertIn(token, xml)
        self.assertIn("CAL", xml)

    def test_aod_contains_time_date_weather_and_both_batteries(self):
        xml = self.watchface_path.read_text(encoding="utf-8")
        for token in ("aod_watch_battery", "aod_phone_battery", "aod_weather",
                      "[HOUR_0_23]", "[MINUTE]", "[DAY]", "[MONTH_S]"):
            self.assertIn(token, xml)

    def test_complication_slot_ids_are_unique_and_within_wff_limit(self):
        slots = self.watchface.findall(".//ComplicationSlot")
        ids = [slot.attrib["slotId"] for slot in slots]
        self.assertLessEqual(len(slots), 8)
        self.assertEqual(len(ids), len(set(ids)))

    def test_vault_boy_asset_exists(self):
        self.assertTrue((ROOT / "res/drawable/vault_boy.png").is_file())


if __name__ == "__main__":
    unittest.main()
