"""Render a 60 s, 1080p promo from authentic demo-device screenshots.

Requires Pillow, numpy, qrcode and imageio-ffmpeg. No external music samples.
Output stays in build/promo so large video exports are not committed to Git.
"""
import argparse
import functools
import math
import pathlib
import subprocess
import wave

import imageio_ffmpeg
import numpy as np
import qrcode
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / "media/promo/assets"
OUTPUT = ROOT / "build/promo"
W, H, FPS, DURATION = 1920, 1080, 30, 60
INK = "#26313B"
MUTED = "#64706D"
ACCENT = "#D76136"
TEAL = "#347F74"
STARTS = [0, 5, 14, 22, 31, 39, 47, 55]
ENDS = STARTS[1:] + [60]
CAPTIONS = [
    "你的大学生活，可以从一张清楚的课表开始。",
    "周次、节次和上课地点，放在同一张课表里。",
    "今日课程按时间排好，打开就能查看。",
    "手动补课、删除课程，多份课表也能管理。",
    "连接教务页面，导入你的课程安排。",
    "切换颜色与明暗，让课表看着顺眼。",
    "查成绩、算绩点、看考试，学习工具顺手打开。",
    "JLU Schedule，开源、无广告。现在就来试试。",
]


@functools.lru_cache(None)
def font(size, bold=False, english=False):
    if english:
        path = "C:/Windows/Fonts/seguisb.ttf" if bold else "C:/Windows/Fonts/segoeui.ttf"
    else:
        path = "C:/Windows/Fonts/msyhbd.ttc" if bold else "C:/Windows/Fonts/msyh.ttc"
    return ImageFont.truetype(path, size)


def text(draw, xy, value, size=30, color=INK, bold=False, english=False):
    draw.text(xy, value, fill=color, font=font(size, bold, english), anchor="lt")


def block(draw, x, y, lines, size=72, color=INK, bold=True, gap=25, max_width=900):
    for i, line in enumerate(lines):
        width = draw.textlength(line, font=font(size, bold))
        if width > max_width:
            raise ValueError(f"Text exceeds layout: {line} ({width} > {max_width})")
        text(draw, (x, y + i*(size+gap)), line, size, color, bold)


def pills(draw, x, y, values, fill="#EEE8DD", color=INK, size=25):
    for value in values:
        width = int(draw.textlength(value, font=font(size))) + 46
        draw.rounded_rectangle((x, y, x+width, y+54), radius=27, fill=fill)
        text(draw, (x+23, y+13), value, size, color)
        x += width + 16


@functools.lru_cache(None)
def phone(name, width):
    screen = Image.open(ASSETS / f"{name}.png").convert("RGB")
    height = round(screen.height * width / screen.width)
    screen = screen.resize((width, height), Image.Resampling.LANCZOS)
    border, margin = 10, 35
    layer = Image.new("RGBA", (width+2*(border+margin), height+2*(border+margin)+10))
    draw = ImageDraw.Draw(layer)
    draw.rounded_rectangle((margin, margin+15, margin+width+2*border, margin+height+2*border+15),
                           radius=40, fill=(31, 42, 50, 45))
    layer = layer.filter(ImageFilter.GaussianBlur(15))
    draw = ImageDraw.Draw(layer)
    draw.rounded_rectangle((margin, margin, margin+width+2*border, margin+height+2*border),
                           radius=38, fill="#202B31", outline="#55616A", width=2)
    mask = Image.new("L", (width, height))
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, width, height), radius=29, fill=255)
    layer.paste(screen, (margin+border, margin+border), mask)
    return layer


@functools.lru_cache(None)
def app_icon(size):
    source = Image.open(ROOT / "app/src/main/res/drawable-nodpi/ic_launcher_foreground.png").convert("RGBA")
    icon = Image.new("RGBA", (size, size))
    ImageDraw.Draw(icon).rounded_rectangle((0, 0, size-1, size-1), radius=size//4, fill="#E8753C")
    source = source.crop((220, 245, 840, 855)).resize((size, size), Image.Resampling.LANCZOS)
    icon.alpha_composite(source)
    return icon


@functools.lru_cache(None)
def focus_card(name, crop, width):
    source = Image.open(ASSETS / f"{name}.png").convert("RGB").crop(crop)
    height = round(source.height * width / source.width)
    source = source.resize((width, height), Image.Resampling.LANCZOS)
    layer = Image.new("RGBA", (width+64, height+64))
    draw = ImageDraw.Draw(layer)
    draw.rounded_rectangle((26, 32, width+38, height+44), radius=28, fill=(38, 49, 59, 70))
    layer = layer.filter(ImageFilter.GaussianBlur(13))
    draw = ImageDraw.Draw(layer)
    draw.rounded_rectangle((22, 22, width+42, height+42), radius=28, fill="white", outline="#D5DCCF", width=2)
    mask = Image.new("L", (width, height))
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, width, height), radius=20, fill=255)
    layer.paste(source, (32, 32), mask)
    return layer


def background(index):
    colors = ["#F8F6F0", "#F3F8F5", "#FAF5EB", "#F8F4EF", "#F5F7F5", "#F2F5F3", "#F5F7F6", "#F8F6F0"]
    img = Image.new("RGB", (W, H), colors[index])
    d = ImageDraw.Draw(img)
    d.ellipse((1040, -215, 2040, 785), fill="#E8EFE9")
    d.ellipse((1510, 370, 2250, 1110), fill="#EEE9DE")
    d.line((144, 174, 780, 174), fill="#DDDCD4", width=2)
    img.paste(app_icon(48), (144, 94), app_icon(48))
    text(d, (210, 105), "JLU Schedule", 30, bold=True, english=True)
    text(d, (144, 903), "面向吉大学生的开源课表应用", 22, MUTED)
    text(d, (1460, 1044), "演示数据 · Android", 17, MUTED)
    return img


class Scene:
    def __init__(self, index):
        self.index = index
        self.bg = background(index)
        self.copy = Image.new("RGBA", (W, H))
        self.phones = []
        d = ImageDraw.Draw(self.copy)
        labels = ["让这一周，清楚一点", "01  /  一周课表", "02  /  今日课程", "03  /  课程管理",
                  "04  /  教务导入", "05  /  个性化", "06  /  学习工具", "从这一周，开始"]
        text(d, (144, 216), labels[index], 25, ACCENT, True)
        if index == 0:
            block(d, 144, 298, ["课表清楚一点。", "大学生活，从容一点。"], 70, max_width=850)
            text(d, (144, 512), "为吉大学生做的轻量课表", 33, MUTED)
            pills(d, 144, 605, ["开源", "无广告", "Android"])
            self.phones = [("timetable_warm", 390, 1160, 37)]
        elif index == 1:
            block(d, 144, 298, ["周次、时间、教室。", "一眼，就有安排。"], 70, max_width=900)
            block(d, 148, 520, ["整周课程集中看", "查课不用来回翻网页。"], 32, MUTED, False, 17)
            pills(d, 144, 666, ["周次切换", "课程详情", "教务导入"])
            self.phones = [("timetable_mint", 390, 1160, 37)]
        elif index == 2:
            block(d, 144, 298, ["今天上什么？", "打开就知道。"], 76, max_width=850)
            block(d, 148, 527, ["时间轴排列今日课程", "时间、地点，一起带上。"], 32, MUTED, False, 17)
            d.rounded_rectangle((144, 665, 765, 790), radius=25, fill="#F0E6D4")
            text(d, (177, 692), "一天的安排", 25, MUTED)
            text(d, (177, 733), "上课前，心里有数。", 30, INK, True)
            self.phones = [("today", 390, 1160, 37)]
        elif index == 3:
            block(d, 144, 298, ["课程有变化，", "课表跟着你走。"], 72, max_width=850)
            block(d, 148, 525, ["补一门课，整理一份课表。", "需要删掉的课程，也能自己处理。"], 29, MUTED, False, 19)
            pills(d, 144, 667, ["手动加课", "删除课程", "多课表管理"])
            self.phones = [("course_detail", 390, 1160, 37)]
        elif index == 4:
            block(d, 144, 298, ["从教务到课表，", "少一点手动整理。"], 72, max_width=860)
            block(d, 148, 525, ["支持校内 / 校外入口", "导入当前课表，或新建一份。"], 31, MUTED, False, 19)
            pills(d, 144, 667, ["网页导入", "一键导入"])
            text(d, (148, 775), "登录与验证码按教务页面完成", 23, MUTED)
            self.phones = [("import_options", 390, 1160, 37)]
        elif index == 5:
            block(d, 144, 298, ["你的课表，", "你的颜色。"], 72, max_width=490)
            block(d, 148, 532, ["暖色 · 海蓝 · 薄荷", "浅色和深色", "随心切换。"], 29, MUTED, False, 19, 490)
            self.phones = [("timetable_warm", 310, 668, 80),
                           ("timetable_ocean_dark", 310, 1058, 113),
                           ("timetable_mint", 310, 1448, 146)]
        elif index == 6:
            block(d, 144, 298, ["学习工具，", "顺手就能打开。"], 69, max_width=730)
            block(d, 148, 522, ["成绩查询 · 绩点估算", "考试安排 · 考试倒计时"], 29, MUTED, False, 19)
            text(d, (148, 742), "绩点按所选规则估算，以学院规定为准", 22, MUTED)
            self.phones = [("gpa", 330, 950, 96), ("exams", 330, 1390, 124)]
        else:
            text(d, (144, 308), "JLU Schedule", 96, INK, True, True)
            text(d, (144, 446), "把一周安排，握在手里。", 54, INK, True)
            pills(d, 144, 565, ["开源", "无广告", "Android"])
            text(d, (148, 706), "github.com/JFyuhong/JLU_schedule", 30, TEAL, False, True)
            text(d, (148, 766), "访问项目 / 下载 Android 正式版", 26, MUTED)
            qr = qrcode.make("https://github.com/JFyuhong/JLU_schedule/releases/latest").convert("RGB")
            qr = qr.resize((300, 300), Image.Resampling.NEAREST)
            d.rounded_rectangle((1320, 290, 1696, 766), radius=30, fill="white", outline="#DDDCD4", width=2)
            self.copy.paste(qr, (1358, 330))
            text(d, (1404, 673), "扫码下载", 30, INK, True)
        # Separate footer subtitles from feature copy and screenshots.
        d.rounded_rectangle((340, 971, 1580, 1030), radius=20, fill=(255, 255, 255, 245))
        caption_width = d.textlength(CAPTIONS[index], font=font(29))
        text(d, ((W-caption_width)/2, 986), CAPTIONS[index], 29, INK)

    def render(self, local):
        canvas = self.bg.copy().convert("RGBA")
        progress = min(1, max(0, (local-0.12)/0.7))
        ease = 1 - (1-progress)**3
        copy = self.copy
        if ease < 1:
            copy = self.copy.copy()
            copy.putalpha(copy.getchannel("A").point(lambda x: round(x*ease)))
        canvas.alpha_composite(copy, (0, round(18*(1-ease))))
        for i, (name, width, x, y) in enumerate(self.phones):
            settle = 1 - (1-min(1, max(0, local/0.9)))**3
            bob = math.sin(local*0.8+i*1.4)*5
            canvas.alpha_composite(phone(name, width), (round(x+55*(1-settle)), round(y+bob)))
        # Magnify the actual app sheet/dialog after establishing the phone view.
        if self.index in (3, 4) and local > 3.2:
            amount = min(1, (local-3.2)/0.6)
            if self.index == 3:
                focus = focus_card("course_detail", (34, 1227, 1046, 2294), 640).copy()
                position = (994, 213)
            else:
                focus = focus_card("import_options", (28, 925, 1052, 1474), 700).copy()
                position = (964, 348)
            focus.putalpha(focus.getchannel("A").point(lambda x: round(x*amount)))
            canvas.alpha_composite(focus, (position[0], position[1]+round(22*(1-amount))))
        d = ImageDraw.Draw(canvas)
        for i in range(8):
            x = 144 + i*24
            d.rounded_rectangle((x, 996, x+13, 1009), radius=6,
                                fill=ACCENT if i == self.index else "#D5D7CF")
        return canvas.convert("RGB")


SCENES = []


def frame(t):
    index = max(i for i, start in enumerate(STARTS) if t >= start)
    local = t-STARTS[index]
    image = SCENES[index].render(local)
    if index > 0 and local < 0.36:
        previous = SCENES[index-1].render(ENDS[index-1]-STARTS[index-1])
        image = Image.blend(previous, image, local/0.36)
    return image


def music():
    """An original, restrained 96 BPM keyboard/percussion bed, no sampled audio."""
    sr = 48000
    audio = np.zeros((DURATION*sr, 2), dtype=np.float64)
    rng = np.random.default_rng(730)
    beat = 60/96
    chords = [[62, 66, 69, 73], [59, 62, 66, 69], [55, 59, 62, 66], [57, 61, 64, 71]]

    def add(start, sound, amplitude, pan=0):
        pos = int(start*sr)
        if pos >= len(audio):
            return
        sound = sound[:len(audio)-pos]
        audio[pos:pos+len(sound), 0] += sound*amplitude*math.sqrt((1-pan)/2)
        audio[pos:pos+len(sound), 1] += sound*amplitude*math.sqrt((1+pan)/2)

    def key(note, length=1.7):
        t = np.arange(round(length*sr))/sr
        freq = 440*2**((note-69)/12)
        env = np.minimum(t/0.009, 1)*np.exp(-2.8*t)
        return env*(np.sin(2*np.pi*freq*t)+0.24*np.sin(2*np.pi*2*freq*t)+0.08*np.sin(2*np.pi*3*freq*t))

    for bar in range(24):
        start = bar*4*beat
        chord = chords[bar % 4]
        for i, note in enumerate(chord):
            add(start+0.01*i, key(note, 3.2), 0.065, (i-1.5)*0.25)
        if bar >= 2:
            for q, pick in enumerate([0, 2, 1, 3]):
                add(start+q*beat, key(chord[pick]+12, 1.3), 0.07, -0.35 if q%2==0 else 0.35)
        if 4 <= bar < 22:
            for q in range(4):
                t = np.arange(int(sr*0.13))/sr
                kick = np.sin(2*np.pi*(48*t+12*(1-np.exp(-25*t))/25))*np.exp(-30*t)
                if q%2 == 0:
                    add(start+q*beat, kick, 0.16)
                noise = rng.normal(0, 1, int(sr*0.05))
                # A quiet synthetic shaker, no external sound samples.
                noise = np.diff(noise, prepend=0)*np.exp(-np.arange(len(noise))/(sr*0.009))
                add(start+(q+0.5)*beat, noise, 0.012, 0.4)
    times = np.arange(len(audio))/sr
    fade = np.minimum(1, times/1.5) * np.minimum(1, (DURATION-times)/2.7)
    audio *= fade[:, None]
    peak = np.max(np.abs(audio))
    audio *= 0.54/max(peak, 0.001)
    path = OUTPUT / "original_music.wav"
    with wave.open(str(path), "wb") as f:
        f.setnchannels(2)
        f.setsampwidth(2)
        f.setframerate(sr)
        f.writeframes((np.clip(audio, -1, 1)*32767).astype("<i2").tobytes())
    return path


def timestamp(seconds):
    ms = int(round(seconds*1000))
    return f"{ms//3600000:02}:{ms//60000%60:02}:{ms//1000%60:02},{ms%1000:03}"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--preview-only", action="store_true")
    parser.add_argument("--silent", action="store_true")
    args = parser.parse_args()
    OUTPUT.mkdir(parents=True, exist_ok=True)
    SCENES.extend(Scene(i) for i in range(8))
    # Still previews show the settled layout of every scene before encoding.
    sheet = Image.new("RGB", (1920, 2160), "white")
    for i, start in enumerate(STARTS):
        preview = frame(start+min(5, ENDS[i]-start-1))
        preview.save(OUTPUT / f"scene_{i+1:02}.png")
        sheet.paste(preview.resize((960, 540)), ((i%2)*960, (i//2)*540))
    sheet.save(OUTPUT / "storyboard.png")
    frame(3).save(OUTPUT / "cover.png")
    subtitle = "\n\n".join(f"{i+1}\n{timestamp(a)} --> {timestamp(b)}\n{caption}"
                           for i, (a, b, caption) in enumerate(zip(STARTS, ENDS, CAPTIONS)))
    (OUTPUT / "subtitles.srt").write_text(subtitle+"\n", encoding="utf-8")
    if args.preview_only:
        print("Scene previews and storyboard ready.")
        return
    audio = music()
    output = OUTPUT / ("JLU_Schedule_Promo_60s_silent.mp4" if args.silent else "JLU_Schedule_Promo_60s.mp4")
    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    command = [ffmpeg, "-y", "-hide_banner", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24",
               "-s", f"{W}x{H}", "-r", str(FPS), "-i", "pipe:0"]
    if not args.silent:
        command += ["-i", str(audio), "-af", "loudnorm=I=-18:TP=-2:LRA=7", "-c:a", "aac", "-ar", "48000", "-b:a", "192k"]
    command += ["-c:v", "libx264", "-preset", "fast", "-crf", "18", "-pix_fmt", "yuv420p", "-t", str(DURATION),
                "-movflags", "+faststart", str(output)]
    process = subprocess.Popen(command, stdin=subprocess.PIPE)
    try:
        for n in range(DURATION*FPS):
            process.stdin.write(frame(n/FPS).tobytes())
            if n%150 == 0:
                print(f"Rendered {n/FPS:.0f} / {DURATION} seconds", flush=True)
    finally:
        process.stdin.close()
    if process.wait() != 0:
        raise SystemExit("ffmpeg failed")
    print(f"Exported {output}")


if __name__ == "__main__":
    main()
