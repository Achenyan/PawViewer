import io, re

p = r"D:\work_zone\pawviewer\README.md"
s = io.open(p, encoding="utf-8-sig").read()

usage = """## 如何使用

从 [Releases](https://github.com/Achenyan/PawViewer/releases) 下载安装包，安装到手机上即可。

版本号约定：对外发布的版本每次 +0.1，内部调整版本每次 +0.0.1。

感谢：[Ehviewer_CN_SXJ](https://github.com/xiaojieonly/Ehviewer_CN_SXJ?ref=opensource) 提供的设计思路。

---
"""

if "## 如何使用" not in s:
    # 在第一个 "---" 之前插入
    idx = s.find("---")
    if idx >= 0:
        # 去掉可能紧跟的空行
        s = s[:idx] + usage + s[idx + 3:].lstrip("\r\n")
        print("inserted before first ---")
    else:
        print("no --- found")

s = s.replace("## 0. 1.8.0 更新：筛选体验 + 彩蛋 + AI 筛选",
              "## 0. 1.1.0 更新（内部编号 1.8.0）：筛选体验 + 彩蛋 + AI 筛选")

io.open(p, "w", encoding="utf-8", newline="").write(s)
print("ok, has usage:", "## 如何使用" in s)
