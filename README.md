# PawViewer

pawchive.pw 的专用浏览器客户端（Android）。UI 骨架照 EhViewer 裁剪，代码全部重写。

- 包名：`com.pawviewer`
- 版本：1.1.0（versionCode 12）
- minSdk 26 / targetSdk 35 / compileSdk 35
- 签名：v2（自签名证书 `CN=PawViewer`）
- 产物：`PawViewer-1.1.0.apk`（**25.47 MB**）
- SHA-256：`DCDD754D226C9B8FCBD4A75F4EE40411F8EB2AB2DD5900828F563ACA76716BEB`

## 如何使用

从 [Releases](https://github.com/Achenyan/PawViewer/releases) 下载安装包，安装到手机上即可。

版本号约定：对外发布的版本每次 +0.1，内部调整版本每次 +0.0.1。

感谢：[Ehviewer_CN_SXJ](https://github.com/xiaojieonly/Ehviewer_CN_SXJ?ref=opensource) 提供的设计思路。

---
## 0. 1.1.0 更新（内部编号 1.8.0）：筛选体验 + 彩蛋 + AI 筛选

1. **时间选择条改成主题色底板**，右侧加了小一号的「←点击以选择时间节点」提示。
2. **两个彩蛋**：时间筛选到「没有作品的未来」时显示主题色文案「你想要的终会到来」；筛到「没有作品的过去」时显示「你或许需要回到过去来创作你想要的作品」。（日期选择器不限过去/未来。）
3. **AI 筛选（实验性功能）**：只加在「最新」和「作者作品」的筛选面板里；勾选后过滤掉标题或正文里带独立大写 `AI` 的作品。

## 0.1 1.7.0 更新：搜索 / 批量删除 / 筛选

**下载管理右上角 ⋮ 三个选项**
1. **搜索**：按项目名模糊匹配，容错一个字母（`aiex` 也能搜到 `alex`），完全匹配排前面、容错的排后面。
2. **删除**：底部弹出「全选 / 取消 / 删除」条；点项目可勾选/取消，全选后再点变「全不选」；删除会同时删掉数据库记录和磁盘文件。
3. **分类筛选**：全部 / 图包 / 视频（按目录里是否存在视频文件判断）。

**设置里新增「删除未完成的项目」**：清理下载目录中不对应任何下载记录的残留文件。

**「最新」和「作者作品」页新增筛选按钮**：
- 类型：全部 / 图包 / 视频（按帖子附件里有没有视频判断）
- 时间：不限 / 早于某日 / 晚于某日（日期选择器）

## 0.1 1.6.0 更新：下载可暂停 / 续传

1. **严格串行**：一次只跑一个任务，任务内文件按顺序下。
2. **暂停保留进度**：点「取消」= 暂停，已下好的文件全部保留，列表显示「已暂停（进度已保留）」，点「继续」从断点接着下（已存在的成品文件直接跳过）。
3. **图片不留半张**：图片先写 `xxx.part`，下完再改名；取消/失败会删掉半张，下次重下。
4. **视频断点续传**：视频直接写 `xxx.part` 并用 HTTP Range 续传，取消/失败都保留，下次从断点继续。
5. **边下边看**：任务进行中也能点进去看已经下载好的部分（本地详情页只列成品文件，忽略 `.part`）。
6. 进程被杀后残留的「下载中」任务，重启时自动回到等待队列。

## 0.1 1.5.1 更新

1. **下载项先开本地详情页**：点已完成的任务 → 本地详情页（封面 / 标题 / 文件清单）→ 从那里「阅读」进翻页器、「播放」进播放器，和在线帖子的操作路径一致。
2. **内置播放器**（Media3 / ExoPlayer）：本地文件直接播；网络视频先用应用内的 Cronet 流式缓存到本地再播，带「正在缓冲 x%」。
3. **任意位置向右滑即可拉出抽屉**，不再只能从屏幕左缘滑。
4. 下载范围扩大到**图片 + 视频**（原来只下图片）。
5. **统一的网络失败提示**：任何因网络失败的操作（列表 / 详情 / 收藏 / 创作者索引 / 阅读器原图 / 播放器 / 下载 / 登录）都会提示「请尝试开启代理解决此问题」；4xx（未登录、不存在等）保留服务端原话、不加此提示。登录也会区分「网络失败」和「密码错误」。

> ⚠️ 重要发现（关于免代理）：只有 `pawchive.pw` 和 `img.pawchive.pw` 这两台 Cloudflare 前端支持 HTTP/3。
> 原图/视频所在的 `t1.pawchive.pw`、`file.pawchive.pw` **没有 HTTPS RR、不支持 h3**，只能走 TCP，因此仍会被按 SNI 阻断。
> 也就是说：**免代理可以浏览列表、看详情、看缩略图；但看原图和视频仍需在设置里配代理**（阅读器加载原图失败时会提示这一点）。
## 1. 这一版的重点：不挂代理也能用了

### 1.1 根因：不是 DNS，是 GFW 按 SNI 阻断

实测证据（宿主机直连，全程无代理）：

| 测试 | 结果 |
|---|---|
| 系统 DNS 解析 pawchive.pw | `104.21.95.170 / 172.67.146.57`（正确的 Cloudflare IP，无污染） |
| TCP 443 连接 | 通 |
| TLS 握手，SNI=`pawchive.pw` | **0.19 秒被 RST** |
| 同一 IP，SNI=`www.cloudflare.com` | **200** |
| 域名 HTTPS RR | `alpn="h3,h2"` 且带 `ech=...` |
| 同一网络下 Chrome 直连 | 能打开 |

结论：IP 可达、TCP 可达、TLS 本身可用，只有 **`pawchive.pw` 这个 SNI 字符串**会触发重置。所以 DoH、hosts 这类 DNS 方案完全无效。

### 1.2 解决：走 HTTP/3（QUIC），SNI 在加密载荷里

引入 **Cronet（Chromium 网络栈）** 并把 `pawchive.pw / img / file / t1` 四个域都加了 QUIC hint：

- 实测 `https://pawchive.pw/api/v1/app_version` → **200 / h3**
- 实测 `https://img.pawchive.pw/...jpg` → **200 / h3**

QUIC 的 ClientHello（含 SNI）是加密的，中间设备看不到域名，连接不会被重置。

### 1.3 引擎选择策略

```
配置了应用内代理  → OkHttp（代理对 QUIC 无效）
未配置代理        → Cronet + QUIC
Cronet 初始化失败 → 自动退回 OkHttp
Cronet 请求异常   → 单次退回 OkHttp
```

也就是说：**免代理是默认路径**，代理仍然保留（有些网络 UDP 443 被封，那时配代理即可），DoH/hosts 也留着做兜底。

### 1.4 代价：包体变大

Cronet 原生库让 APK 从 5.37 MB 涨到 **22.99 MB**（已裁到 `arm64-v8a / armeabi-v7a / x86_64`）。这是免代理能力的直接成本。

---

## 2. 其他改动

### 2.1 三种阅读方向

设置 → 阅读 → 翻页方向：从左到右 / 从右到左（日漫）/ **从上到下（条漫）**。条漫是独立的竖向 `RecyclerView`，图片按屏宽铺满；横向两种用 `ViewPager2`（RTL 用 layoutDirection 翻转）。

### 2.2 主题色 #63A4A8

主色、选中态、抽屉图标、进度条全部替换；文字用深色 `#00201F` 保证对比度。图标是 `Pw` 白字 + 主题色底（`tools/make_icon.py` 可重跑）。

### 2.3 登录：改成原生表单 + 修复 Cookie 捕获

- 旧版用 WebView 打开登录页，走系统 DNS → 被 SNI 阻断时会落到别的站点（就是你看到的"劫持到色情站"）。现在改成原生表单直接 `POST /account/login`，走应用内网络。
- 接入 Cronet 后发现：登录的 **302 响应上的 Set-Cookie** 必须显式收下，否则会话丢失、登录必然失败。已在 `onRedirectReceived` 里捕获，并补了一个手写的 Set-Cookie 解析器（Flask 的 `SameSite/Expires` 会被 OkHttp 的解析器判为非法）。
- 实测新注册账号登录成功：抽屉头部显示「已登录站点账号」，收藏接口返回 200。

### 2.4 修掉的一个严重 bug

帖子详情接口的 `tags` 字段有时返回字符串 `"{uma}"` 而不是数组，Gson 解析直接抛异常 → 详情页**一直静默退回列表数据**（没有正文、附件数不准）。已用 `JsonElement` 兜底，详情页能正常加载完整内容。

---

## 3. 定位与责任边界

| 做法 | 实现位置 |
|---|---|
| 首次启动弹出带遮罩的声明对话框 | `MainActivity.showDisclaimerDialog` |
| 不托管、不缓存服务端内容 | `core/net/Http.kt` |
| 每个帖子可「在浏览器中打开」 | `PostFragment.openInBrowser` |
| 注册为 `pawchive.pw` 的链接处理器 | `AndroidManifest.xml` + `MainActivity.handleIntent` |
| 全局可识别 User-Agent | `Prefs.userAgent` |
| 严格限速：接口 1 req/s、原图 1 req/s、并发 ≤5 | `core/net/RateLimit.kt` |
| 不绕过登录/付费墙 | `LoginActivity` + `Http.postForm` |
| 下载只能对单个帖子手动触发，串行、前台服务 | `core/DownloadService.kt` |

---

## 4. 架构

```
core/
  net/    Http.kt         Cronet（QUIC）为主 + OkHttp 兜底；CookieJar；限速；表单登录
          NetConfig.kt    Dns（hosts + DoH）与 Proxy
          RateLimit.kt    令牌桶 + Semaphore
          PawApi.kt       REST + 流式 /creators + login
          PawHtml.kt      帖子网页 → 签名直链表
  media/  MediaResolver.kt
  img/    ImageLoader.kt   自研：LruCache + 磁盘缓存 + 降采样
  db/     Db.kt
  Repo.kt / DownloadService.kt
ui/
  MainActivity.kt          DrawerLayout + 自维护 Fragment 栈
  RecentFragment / CreatorsFragment / FavoritesFragment
  CreatorFragment / PostFragment / DownloadsFragment / SettingsFragment / AboutFragment
  ReaderActivity.kt        ViewPager2（横）+ RecyclerView（条漫）+ ZoomImageView
  LoginActivity.kt         原生登录表单
```

借鉴 EhViewer：抽屉 + 列表 + 详情三段式、阅读器交互、自研图片解码、下载队列、Cookie 仓库、DNS/代理双保险。
没照搬：EH 的 HTML 解析器、分页令牌、画廊模型、标签翻译库、reCAPTCHA。

**本项目独有的难点**：①原图直链是带签名、约 6 小时过期的 `t1.pawchive.pw/f/...?e=&s=`，要先抓帖子网页解出映射；②站点被 SNI 阻断，需要走 HTTP/3。

---

## 5. 构建

需要 JDK 17+ 与 Android SDK（platform 35 / build-tools 35）。

```bash
# 1) 指定 SDK 路径（或在 Android Studio 里打开项目）
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

# 2) 打包
./gradlew assembleRelease        # Windows: gradlew.bat assembleRelease
```

签名：仓库**不包含**任何密钥。要出正式包，自己生成一个并把参数写进 `keystore.properties`（该文件已被 `.gitignore` 忽略）：

```properties
storeFile=keystore/your.jks
storePassword=******
keyAlias=your_alias
keyPassword=******
```

没有 `keystore.properties` 时，`assembleRelease` 会产出未签名包；日常调试用 `./gradlew assembleDebug` 即可。

AGP 9.3.1 / Kotlin 2.2.10 / Gradle 9.5.0。依赖：OkHttp、Cronet-embedded、Gson、jsoup、coroutines、Material、AndroidX。

---

## 6. 测试

```
:app:testDebugUnitTest    20 passed / 0 failed / 1 skipped
```

真机（MuMU / Android 15 / x86_64）**在完全关闭 VPN、不配任何代理**的情况下验证：

| 项 | 结果 |
|---|---|
| 帖子流 + 缩略图 | ✅ |
| 帖子详情（封面 / 正文 / 19 个附件） | ✅ |
| 阅读器 18 页大图 | ✅ |
| 条漫模式 / 横向 LTR / RTL / 音量键 | ✅ |
| 原生登录（新账号）→ 抽屉显示已登录 | ✅ |
| 收藏接口（带 Cookie） | ✅ 200 |
| 下载 + `.nomedia` | ✅ |
| 应用内代理（配了就优先走代理） | ✅ |
| 新主题色 / 新图标 | ✅ |

---

## 7. 已知限制

1. **包体 23 MB**：Cronet 原生库占大头。
2. **UDP 443 被封的网络**仍需要代理（此时可在设置里配 HTTP/SOCKS5）。
3. **DoH 未在真实污染网络复现**：代理路径已验证；DoH 与 hosts 走同一套 `Dns`。
4. **视频不能内播**：点击视频附件跳浏览器。
5. **首次建创作者索引**要拉 15 MB，1 req/s 下十几秒。

---

## 8. 声明

本项目是一个**只访问 pawchive.pw 的浏览器客户端**：不托管、不存储、不分发任何作品内容；不绕过任何付费墙或访问控制；所有请求使用可识别 UA 并遵守站点公布的限速。内容版权归原作者与发布平台所有。是否可以使用、保存某内容取决于你所在地区的法律和你与内容平台的协议，请自行判断并承担相应责任。
