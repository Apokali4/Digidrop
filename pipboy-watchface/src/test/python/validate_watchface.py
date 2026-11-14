import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2] / "main"

class WatchFacePackageTest(unittest.TestCase):
    def test_manifest_declares_resource_only_wff_v1_for_wear_os_4(self):
        root = ET.parse(ROOT / "AndroidManifest.xml").getroot()
        app = root.find("application")
        self.assertEqual(app.attrib["{http://schemas.android.com/apk/res/android}hasCode"], "false")
        props = {p.attrib["{http://schemas.android.com/apk/res/android}name"]:
                 p.attrib["{http://schemas.android.com/apk/res/android}value"]
                 for p in app.findall("property")}
        self.assertEqual(props["com.google.wear.watchface.format.version"], "1")

    def test_watchface_has_live_clock_steps_and_battery(self):
        root = ET.parse(ROOT / "res/raw/watchface.xml").getroot()
        self.assertEqual(root.tag, "WatchFace")
        xml = (ROOT / "res/raw/watchface.xml").read_text(encoding="utf-8")
        for token in ("DigitalClock", "[STEP_COUNT]", "[BATTERY_PERCENT]", "AMBIENT"):
            self.assertIn(token, xml)

    def test_package_contains_no_executable_watchface_code(self):
        source = ROOT.parent
        self.assertFalse(list(source.rglob("*.kt")))
        self.assertFalse(list(source.rglob("*.java")))

if __name__ == "__main__":
    unittest.main()
