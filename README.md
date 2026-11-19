# 毛豆通知（Maodou Notify）· Android 原生 App

依据 PRD《毛豆通知 v0.1》实现的 Android 原生通知管家：**把每条通知存进可搜索的本地历史，用离线 AI 挡骚扰，其余交给自定义规则**。

- **技术栈**：Kotlin + Jetpack Room + Material 3 + 协程 + ViewBinding
- **版本**：**v1.3.0**（versionCode 4）· 视觉重制版 · 最低 Android 8.0（API 26），目标 Android 15（API 35）
- **隐私**：通知内容 AES-256-GCM 加密存本地（密钥在 Android Keystore），零上传；唯一外发通道是用户显式配置的 Webhook
- **构建**：JDK 17 + Gradle 8.14 + Android SDK 35，`gradle assembleDebug` 已真实编译通过（BUILD SUCCESSFUL）

---

## 视觉设计规范（UI 重制）

界面以「简洁、柔和、精致」为目标重建，功能与交互保持不变：

- **令牌化**：间距走 4 的倍数台阶（`dimens.xml`），圆角 8/10/14/18/22，卡片统一 18dp 圆角 + 1dp 发丝描边、无投影。
- **语义色**：背景/表面/文字/分隔/描边全部由 `values-night` 覆盖，浅色与深色之间不残留对方颜色；四套配色（经典蓝/青绿/紫罗兰/珊瑚橙）通过 `?attr/colorPrimary*` 贯穿筛选胶囊、开关、导航、图表与弹窗。
- **图标**：全部重绘为同一套 24dp 网格、1.7dp 描边、圆头圆角的线性图标；导航「历史=时钟 / 规则=漏斗 / 分析=柱状 / 设置=滑块」语义可区分；启动图标改为蓝渐变 + 白色铃铛 + 薄荷勾选。
- **层级**：页面统一「26sp 大标题 + 12sp 副标题 + 卡片分组」，列表卡片按「应用图标 / 应用名 / 时间 / 标题 / 正文 / 结果徽章」建立阅读顺序，日期分隔为 11.5sp 次级标签。
- **组件**：搜索框与输入框为 14dp 圆角描边样式；筛选为 34dp 胶囊（选中=品牌浅底+品牌字，未选=白底+发丝边）；开关选中=品牌轨+白滑块，未选=浅灰轨+白滑块；危险操作为浅红底红字。
- **弹窗**：统一走 `ThemeOverlay.Maodou.Dialog`，表面色与页面一致，单选点为主色，不再出现 Material 默认的偏色底。

## 更新记录

### v1.3.0 · 视觉重制（2026-11-19）
- 统一设计令牌：间距/圆角/字号/组件尺寸集中到 `dimens.xml`；卡片 18dp 圆角 + 1dp 发丝描边、无投影。
- 修复深浅色冲突：背景/表面/文字/分隔/描边全部由 `values-night` 覆盖，弹窗、开关、进度条、徽章统一走语义色。
- 配色联动：四套配色通过 `?attr/colorPrimary*` 贯穿筛选胶囊、开关、底部导航、图表与小部件，切换后无残留蓝。
- 图标全套重绘：24dp 网格 / 1.7dp 描边 / 圆头圆角；导航语义拆分为 时钟·漏斗·柱状·滑块；启动图标改为蓝渐变 + 白铃铛 + 薄荷勾选。
- 页面骨架重做：四页统一「26sp 标题 + 12sp 副标题 + 卡片分组」；历史列表显示真实应用图标，统计页新增排行空态，空态改为柔和插画。
- 修复 targetSdk 35 边缘到边缘导致状态栏与顶部内容重叠。
- 功能与交互保持不变。

### v1.2.0 · PRD 功能全覆盖
- 补齐 FR-E4 桌面小部件、FR-E6 多款主题，PRD 30 项功能全部落地。

### v1.1.0 · 按 PRD 补齐功能
- 规则条件扩展（星期/充电/锁屏）、验证码提取、离线 AI 学习、应用锁、规则导入导出、CSV 导出等。

---

## 一、如何构建

**Android Studio（推荐）**：Open 本目录 → 同步 Gradle → Run。首次打开按引导授予「通知使用权」。

**命令行**：
```bash
# 修改 local.properties 中 sdk.dir 为本机 SDK 路径
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

工程已含 Gradle Wrapper 8.14。`local.properties` 指向构建机 SDK 路径，换机器需改。

---

## 二、已实现功能 ↔ PRD 编号映射（v1.3.0：30/30 全覆盖）

### V1.0（MVP）
| 编号 | 功能 | 实现位置 |
| --- | --- | --- |
| FR-H1 | 全量记录 + AES 加密 + 滚动清理（保留窗口/上限可配） | `NotificationMonitorService` + `CryptoHelper` + `Daos` |
| FR-H2 | 多维搜索（App/关键字/正文/包名/规则原因/类型） | `HistoryFragment` |
| FR-H3 | 时间轴回溯（按日分组 + 日历跳转 + 当日正序） | `NotificationAdapter` + `HistoryFragment` |
| FR-H4 | 一键恢复通知（标注来源） | `NotificationDetailActivity` + `NotificationBridge` |
| FR-H5 | 撤回留存（到达时快照，如实说明边界） | `onNotificationRemoved` + 详情页 |
| FR-R1/R2 | 规则引擎（首条命中）+ 条件（App/关键词/时段/星期/充电/锁屏） | `RuleEngine` |
| FR-R3 | 10 种动作（见下表） | `ActionExecutor` |
| FR-R4 | 规则增删改查 + 启停 + 排序 + 复制 | `RulesFragment` + `RuleAdapter` |
| FR-P1/P2 | 数据不出设备 + 全离线 AI | `CryptoHelper` + `SpamScorer` |
| FR-P4 | 权限引导 + 权限收回检测 + 漏记透明提示 | `MainActivity` + `NotificationMonitorService` |

### V1.1
| 编号 | 功能 | 状态 |
| --- | --- | --- |
| FR-A1~A4 | 离线骚扰打分 + 一键标注 + 充电/闲置学习 + 开关阈值/重置 | 已实现 |
| FR-E1 | 5 秒撤销按钮 | 已实现 |
| FR-E2 | 智能静音（阈值/窗口/时长可配 + 手动解除） | 已实现 |
| FR-E3 | 验证码提取 + 一键复制 | 已实现 |
| FR-E5 | 指纹/面容应用锁（BiometricPrompt + 有效期） | 已实现 |
| FR-E8 | 排除 App（两档语义 + 配置 UI） | 已实现 |
| FR-R5 | 4 类预设规则模板 | 已实现 |
| FR-S1 | 近期一览（7/30 天 + 保留/过滤分口径） | 已实现 |
| FR-H6 | 导出 CSV（时间范围筛选 + UTF-8 BOM） | 已实现 |
| FR-H7 | 历史概览计数（保留/过滤/已学习） | 已实现 |
| FR-P3 | 清空数据 + 独立重置模型入口 | 已实现 |

### V1.2（本次补齐）
| 编号 | 功能 | 实现位置 |
| --- | --- | --- |
| **FR-E4** | **桌面小部件**：今日总数/已过滤统计 + 快捷入口（进入历史）+ 一键暂停/恢复过滤；系统定时刷新（30min）+ 通知落库/开关变更主动刷新 | `MaodouWidgetProvider` + `widget_maodou.xml` + `Prefs.filterPaused` |
| **FR-E6** | **多款主题**：跟随系统/浅色/深色 3 种模式 + 4 套配色（经典蓝/青绿/紫罗兰/珊瑚橙）；切换即时生效并记忆 | `ThemeHelper` + `themes.xml`/`values-night` + 设置页 |
| FR-E7 | 规则导入导出 JSON（重名冲突校验） | `RulesFragment` |
| FR-S2 | 日报趋势图（点击排行直达规则创建） | `StatsFragment` |
| FR-R3-10 | Webhook 失败重试（3 次指数退避）+ 配置页隐私明示 | `WebhookSender` + `RuleEditActivity` |

### 10 种规则动作实现情况
| 动作 | 状态 | 说明 |
| --- | --- | --- |
| 消除 | 完整 | 用系统 sbn.key 精确 cancelNotification |
| 静音 / 推迟 / 朗读 / 来电提醒 / 打开 | 完整 | 系统能力范围内实现 |
| 替换 / 更改铃声 | 诚实降级 | 系统不允许改写他人通知内容/铃声，明确记录未执行，不伪装成功 |
| 点击按钮 | 降级 | 需无障碍权限，不启用并说明 |
| Webhook | 完整 | POST JSON（含重试），唯一外发通道，需显式配置 URL |

---

## 三、工程结构
```
app/src/main/
├── AndroidManifest.xml
├── java/com/maodou/notify/
│   ├── MaodouApp.kt
│   ├── data/       Entities / Daos / AppDatabase(v2迁移) / CryptoHelper
│   ├── engine/     RuleEngine / SpamScorer / ActionExecutor / WebhookSender
│   ├── listener/   NotificationMonitorService / KeepAliveService / BootReceiver
│   │               NotificationBridge / UndoReceiver / CodeCopyReceiver / MaodouWidgetProvider
│   ├── ui/         MainActivity / History / Rules / Stats / Settings / 详情页 / 编辑页
│   └── util/       Prefs / PermissionHelper / AppLockHelper / ThemeHelper
└── res/            布局 / 矢量图标 / 主题(4套×深浅) / 小部件 / 字符串
```

---

## 四、使用须知
1. **必须授予通知使用权**，否则无法监听；国产 ROM（小米/OPPO/荣耀/华为）需允许后台自启，否则被杀导致漏记（App 透明提示漏记时段）。
2. **AI 默认关闭**，在设置中开启，阈值默认 80%。
3. **主题**：设置 → 外观，可切换深浅模式与 4 套配色，切换即生效并记忆。
4. **桌面小部件**：桌面长按 → 添加毛豆通知小部件，查看今日统计、点「暂停/恢复过滤」快捷切换。
5. **隐私**：内容仅存本机；无 Webhook 配置时零上行。
6. **Debug 签名**：正式上架需替换签名配置并做 ROM 兼容测试。
7. **升级**：DB v1→v2 已内置 Migration，老用户数据保留。

---

## 五、验证记录
- 构建：JDK 17 + Gradle 8.14 + SDK 35，`gradle assembleDebug` **BUILD SUCCESSFUL**（2026-11-19）
- APK：`com.maodou.notify` v1.3.0（versionCode 4），v2 签名校验通过，minSdk 26 / targetSdk 35，6.5MB
- 小部件 receiver 已注册（aapt 校验 Manifest 含 `MaodouWidgetProvider` + `APPWIDGET_UPDATE`）
