# PawViewer

pawchive.pw 的专用浏览器客户端（Android）。UI 骨架照 EhViewer 裁剪，代码全部重写。

- 包名：`com.pawviewer`
- 版本：1.4.0（versionCode 5）
- minSdk 26 / targetSdk 35 / compileSdk 35
- 签名：v2（自签名证书 `CN=PawViewer`）
- 产物：`PawViewer-1.4.0.apk`（**22.99 MB**）
- SHA-256：`30BAA173084E77603E57A5B6794A30C9368612ABA03FFAEF649D0029F88245E6`

---

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
