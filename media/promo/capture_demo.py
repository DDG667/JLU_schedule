"""Capture genuine Android UI on the dedicated fictional demo device."""
import argparse
import pathlib
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / "media/promo/assets"
SERIAL = "emulator-5556"


def adb(*args):
    return subprocess.run(["adb", "-s", SERIAL, *args], capture_output=True, check=True).stdout


def hierarchy():
    adb("shell", "uiautomator", "dump", "/sdcard/promo-window.xml")
    return ET.fromstring(adb("shell", "cat", "/sdcard/promo-window.xml").decode("utf-8"))


def tap(resource):
    nodes = hierarchy().iter("node")
    node = next((n for n in nodes if n.get("resource-id") == f"cn.jlu.schedule:id/{resource}"), None)
    if node is None:
        raise RuntimeError(f"Visible control not found: {resource}")
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(2)


def shot(name):
    time.sleep(1)
    ASSETS.mkdir(parents=True, exist_ok=True)
    (ASSETS / f"{name}.png").write_bytes(adb("exec-out", "screencap", "-p"))
    print(f"Captured {name}", flush=True)


def back():
    adb("shell", "input", "keyevent", "4")
    time.sleep(1)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("phase", choices=["core", "tools", "theme", "shot"])
    parser.add_argument("--name", default="preview")
    args = parser.parse_args()
    name = adb("emu", "avd", "name").decode().splitlines()[0].strip()
    if name != "Codex_JLU_Promo":
        raise SystemExit("Dedicated demo emulator required.")
    if args.phase == "core":
        shot("timetable_warm")
        tap("nav_today")
        shot("today")
        tap("nav_timetable")
        adb("shell", "input", "tap", "480", "400")
        time.sleep(2)
        shot("course_detail")
        back()
        adb("shell", "input", "tap", "1005", "132")
        time.sleep(2)
        shot("import_options")
        back()
    elif args.phase == "tools":
        tap("nav_tools")
        shot("tools")
        tap("rowGradeInquiry")
        shot("grades")
        header = next(n for n in hierarchy().iter("node") if n.get("resource-id") == "cn.jlu.schedule:id/gradeHeader")
        assert int(re.findall(r"\d+", header.get("bounds"))[1]) >= 60, "Grade header overlaps system bar"
        back()
        tap("rowGpaCalculator")
        shot("gpa")
        back()
        tap("rowExamSchedule")
        shot("exams")
        header = next(n for n in hierarchy().iter("node") if n.get("resource-id") == "cn.jlu.schedule:id/examHeader")
        assert int(re.findall(r"\d+", header.get("bounds"))[1]) >= 60, "Exam header overlaps system bar"
        back()
        tap("nav_settings")
        shot("settings")
        tap("nav_timetable")
    else:
        shot(args.name)


if __name__ == "__main__":
    main()
