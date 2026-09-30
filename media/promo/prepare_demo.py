"""Create fictional app data only on the dedicated Codex_JLU_Promo emulator."""
import argparse
import datetime as dt
import json
import pathlib
import subprocess
import time

ROOT = pathlib.Path(__file__).resolve().parents[2]
PACKAGE = "cn.jlu.schedule"


def run(serial, *args, data=None):
    result = subprocess.run(["adb", "-s", serial, *args], input=data, capture_output=True, check=True)
    return result.stdout


def write(serial, path, content):
    run(serial, "shell", f"run-as {PACKAGE} sh -c 'cat > {path}'", data=content.encode("utf-8"))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", default="emulator-5556")
    parser.add_argument("--theme", default="warm", choices=["warm", "ocean", "mint"])
    parser.add_argument("--dark", action="store_true")
    parser.add_argument("--install", action="store_true")
    args = parser.parse_args()
    name = run(args.serial, "emu", "avd", "name").decode().splitlines()[0].strip()
    if name != "Codex_JLU_Promo":
        raise SystemExit("Refusing to write demo data to any existing personal emulator.")
    if args.install:
        run(args.serial, "install", "-r", str(ROOT / "app/build/outputs/apk/debug/app-debug.apk"))
    run(args.serial, "shell", "am", "force-stop", PACKAGE)
    for folder in ["files/timetables", "files/tools", "shared_prefs"]:
        run(args.serial, "shell", "run-as", PACKAGE, "mkdir", "-p", folder)

    classes = [
        ("高等数学", "张老师", "MONDAY", 1, 2, "逸夫楼201"),
        ("大学英语", "李老师", "MONDAY", 5, 6, "三教305"),
        ("线性代数", "王老师", "TUESDAY", 3, 4, "逸夫楼302"),
        ("数据结构", "赵老师", "TUESDAY", 7, 8, "实验楼201"),
        ("程序设计", "陈老师", "WEDNESDAY", 1, 2, "实验楼302"),
        ("大学英语", "李老师", "WEDNESDAY", 3, 4, "三教305"),
        ("数据结构", "赵老师", "WEDNESDAY", 5, 6, "实验楼201"),
        ("体育", "孙老师", "WEDNESDAY", 7, 8, "体育馆"),
        ("高等数学", "张老师", "THURSDAY", 3, 4, "逸夫楼201"),
        ("程序设计", "陈老师", "THURSDAY", 7, 8, "实验楼302"),
        ("线性代数", "王老师", "FRIDAY", 1, 2, "逸夫楼302"),
        ("大学物理", "周老师", "FRIDAY", 5, 6, "三教201"),
        ("摄影入门", "林老师", "SATURDAY", 3, 4, "艺术楼101"),
    ]
    courses = []
    for course, teacher, day, start, end, location in classes:
        meeting = dict(weekday=day, startSection=start, endSection=end,
                       weekRules=[dict(startWeek=1, endWeek=16, parity="ALL")], location=location)
        existing = next((c for c in courses if c["courseName"] == course), None)
        if existing:
            existing["meetings"].append(meeting)
        else:
            courses.append(dict(courseName=course, teacher=teacher, semester="2026-2027-1",
                                credit=2.0, rawWeekText="1-16周", meetings=[meeting]))
    now = int(time.time() * 1000)
    profiles = [dict(id=id_, name=name_, coursesFile=f"courses_{id_}.json", createdAt=now,
                     updatedAt=now, semesterStartDate="2026-09-07")
                for id_, name_ in [("demo-autumn", "秋季学期 · 演示"), ("demo-study", "自习计划 · 演示")]]
    write(args.serial, "files/timetables/meta.json", json.dumps(dict(activeId="demo-autumn", profiles=profiles), ensure_ascii=False))
    write(args.serial, "files/timetables/courses_demo-autumn.json", json.dumps(courses, ensure_ascii=False))
    write(args.serial, "files/timetables/courses_demo-study.json", "[]")
    gpa = [dict(id=f"demo-{i}", name=name_, gradeType="PERCENT", score=score, level="", credit=credit, included=True)
           for i, (name_, score, credit) in enumerate([
               ("高等数学", 92, 4), ("大学英语", 90, 2), ("程序设计", 94, 3),
               ("线性代数", 88, 3), ("大学物理", 87, 3), ("体育", 95, 1)])]
    grades = [dict(courseCode=f"DEMO{i:03}", name=c["name"], credit=c["credit"],
                   scoreText=str(int(c["score"])), semesterCode="2025-2026-2", isCustom=True)
              for i, c in enumerate(gpa)]
    write(args.serial, "files/tools/gpa_courses.json", json.dumps(gpa, ensure_ascii=False))
    write(args.serial, "files/tools/grades.json", json.dumps(grades, ensure_ascii=False))
    exams = []
    for i, (name_, days, location) in enumerate([("高等数学", 14, "逸夫楼201"), ("大学英语", 18, "三教305")]):
        date = dt.datetime.now().replace(hour=9, minute=0, second=0, microsecond=0) + dt.timedelta(days=days)
        exams.append(dict(id=f"demo-exam-{i}", courseName=name_, courseCode=f"DEMO{i:03}",
                          examTimeText=date.strftime("%Y-%m-%d 09:00-11:00"), location=location,
                          seatNumber="12号", examType="演示考试", timestamp=int(date.timestamp()*1000), isCustom=True))
    write(args.serial, "files/tools/exams.json", json.dumps(exams, ensure_ascii=False))
    preferences = f'''<?xml version="1.0" encoding="utf-8" standalone="yes" ?>
<map>
    <string name="theme_color">{args.theme}</string>
    <string name="dark_mode">{"dark" if args.dark else "light"}</string>
    <string name="default_open_page">timetable</string>
    <float name="timetable_font_scale" value="1.0" />
    <boolean name="show_non_current_courses" value="false" />
    <boolean name="daily_reminder_enabled" value="false" />
</map>'''
    write(args.serial, "shared_prefs/app_settings.xml", preferences)
    run(args.serial, "shell", "settings", "put", "global", "window_animation_scale", "0.5")
    run(args.serial, "shell", "settings", "put", "global", "transition_animation_scale", "0.5")
    run(args.serial, "shell", "settings", "put", "global", "animator_duration_scale", "0.5")
    run(args.serial, "shell", "am", "start", "-n", f"{PACKAGE}/.MainActivity")
    print(f"Fictional demo ready on {args.serial}: {args.theme}, dark={args.dark}")


if __name__ == "__main__":
    main()
