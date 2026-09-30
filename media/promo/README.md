# Android 宣传视频素材

面向 B站项目介绍，横屏 1920×1080、30 fps、60 秒，以中文字幕和真实应用界面为主。

| 时间 | 内容 |
| --- | --- |
| 0–5 秒 | JLU Schedule，开源、无广告 |
| 5–14 秒 | 一周课表，周次、时间和地点 |
| 14–22 秒 | 今日课程时间轴 |
| 22–31 秒 | 手动加课、删除课程、多课表管理 |
| 31–39 秒 | 教务导入与校内、校外入口 |
| 39–47 秒 | 暖色、海蓝、薄荷和明暗切换 |
| 47–55 秒 | 绩点估算、成绩查询和考试安排 |
| 55–60 秒 | GitHub 项目地址与正式版下载二维码 |

`assets/` 中的画面来自独立 `Codex_JLU_Promo` 模拟器，用 ADB 截取。课程、教师、成绩、考程均为虚构演示数据，没有校园账号或真实个人成绩。准备脚本在写入前验证模拟器名称，避免修改原有测试设备的数据。

B站投稿用的大字封面见 `cover-bilibili-v2.png`；标题、配乐候选、官方试听链接及投稿简介见 [MUSIC_AND_TITLE.md](MUSIC_AND_TITLE.md)。封面采用内置 imagegen，生成提示词保存在 `cover-bilibili-v2.prompt.txt`。

导出文件在 `build/promo/`：

- `JLU_Schedule_Promo_60s.mp4`：带字幕及原创合成器音乐的成片。
- `cover.png`：视频封面。
- `subtitles.srt`：可编辑字幕。
- `storyboard.png`：八段分镜预览。
- `original_music.wav`：用 `render_promo.py` 合成的键盘及轻打击乐，没有使用外部音频样本。

使用 Python 的 Pillow、numpy、qrcode、imageio-ffmpeg，以及 Windows 微软雅黑与 Segoe UI 字体。运行：

```powershell
python media/promo/render_promo.py --preview-only
python media/promo/render_promo.py
```

现有画面可直接重新渲染。`prepare_demo.py` 与 `capture_demo.py` 用于重新采集专用演示模拟器中的界面。视频按真实功能介绍，不展示或宣传隐藏的原生学业完成页面，不承诺验证码自动完成或当前不可用的国内镜像下载。
