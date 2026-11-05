# PawViewer

pawchive.pw 的专用浏览器客户端（Android）。

# 功能

- **浏览**：最新作品、创作者索引、收藏；可按服务（Patreon / Fanbox / Discord / Gumroad / SubscribeStar / Fantia / Boosty）切换
- **阅读**：左到右、右到左、条漫纵向三种翻页模式，支持双指缩放与音量键翻页
- **播放**：内置播放器，可直接播放帖子里的视频附件
- **下载**：按顺序逐个下载，支持暂停与续传；已下载的内容先从本地详情页进入，再离线阅读或播放
- **搜索与筛选**：标题模糊搜索（容错一个字母）、按类型（图包 / 视频）、按时间（早于 / 晚于某日）筛选；另有实验性的 AI 标签过滤
- **下载管理**：搜索、批量删除、按类型筛选
- **网络**：支持应用内代理（HTTP / SOCKS）与 DoH
- **外观**：跟随系统的深色 / 浅色模式

# 如何使用

从 [Releases](https://github.com/Achenyan/PawViewer/releases) 下载安装包，安装到手机上即可。

APK 为自签名，安装时需要在系统设置里允许「未知来源」。

# 构建

需要 JDK 17 与 Android SDK 35：

```bash
./gradlew assembleDebug
```

发布签名需自备 keystore，并在项目根目录放一个 `keystore.properties`（该文件已被 `.gitignore` 忽略，不会提交）。

# 感谢

设计思路参考
https://github.com/xiaojieonly/Ehviewer_CN_SXJ?ref=opensource

原站地址
https://pawchive.pw/

## 声明

本项目是一个**只访问 pawchive.pw 的浏览器客户端**：不托管、不存储、不分发任何作品内容；不绕过任何付费墙或访问控制；所有请求使用可识别 UA 并遵守站点公布的限速。内容版权归原作者与发布平台所有。是否可以使用、保存某内容取决于你所在地区的法律和你与内容平台的协议，请自行判断并承担相应责任。
