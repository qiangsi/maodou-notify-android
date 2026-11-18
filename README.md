# 毛豆通知（Maodou Notify）· Android 原生 App

依据 PRD《毛豆通知 v0.1》实现的 Android 原生通知管家：**把每条通知存进可搜索的本地历史，用离线 AI 挡骚扰，其余交给自定义规则**。

- **技术栈**：Kotlin + Jetpack Room + Material 3 + 协程 + ViewBinding
- **版本**：**v1.1.0**（versionCode 2）· 最低 Android 8.0（API 26），目标 Android 15（API 35）
- **隐私**：通知内容 AES-256-GCM 加密存本地（密钥在 Android Keystore），零上传；唯一外发通道是用户显式配置的 Webhook
- **构建**：JDK 17 + Gradle 8.14 + Android SDK 35，`gradle assembleDebug` 已真实编译通过（BUILD SUCCESSFUL）

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

## 二、已实现功能 ↔ PRD 编号映射（v1.1.0）

### V1.0（MVP）

| 编号 | 功能 | 实现位置 |
| --- | --- | --- |
| FR-H1 | 全量记录 + AES 加密 + 滚动清理（最旧优先，保留窗口/上限可配） | `NotificationMonitorService` + `CryptoHelper` + `Daos` |
| FR-H2 | 多维搜索（App/关键字/正文/包名/规则原因/类型） | `HistoryFragment` |
| FR-H3 | 时间轴回溯（按日分组 + 日历跳转 + 当日正序） | `NotificationAdapter` + `HistoryFragment` |
| FR-H4 | 一键恢复通知（标注来源） | `NotificationDetailActivity` + `NotificationBridge` |
| FR-H5 | 撤回留存（到达时快照，如实说明边界，不虚构撤回结论） | `onNotificationRemoved` + 详情页 |
| FR-R1/R2 | 规则引擎（首条命中）+ 条件（App/关键词/时段/星期/充电/锁屏） | `RuleEngine` |
| FR-R3 | 10 种动作（见下表） | `ActionExecutor` |
| FR-R4 | 规则增删改查 + 启停 + 排序 + 复制 | `RulesFragment` + `RuleAdapter` |
| FR-P1/P2 | 数据不出设备 + 全离线 AI | `CryptoHelper` + `SpamScorer` |
| FR-P4 | 权限引导 + 权限收回检测 + 漏记透明提示 | `MainActivity` + `NotificationMonitorService` |

### V1.1（本次补齐）

| 编号 | 功能 | 状态 |
| --- | --- | --- |
| FR-A1~A4 | 离线骚扰打分 + 一键标注 + 充电/闲置学习 + 开关阈值/重置 | ✅ |
| FR-E1 | 5 秒撤销按钮 | ✅ |
| FR-E2 | 智能静音（阈值/窗口/时长可配 + 手动解除） | ✅ |
| FR-E3 | 验证码提取 + 一键复制 | ✅ |
| FR-E5 | 指纹/面容应用锁（BiometricPrompt + 有效期） | ✅ |
| FR-E8 | 排除 App（两档语义 + 配置 UI） | ✅ |
| FR-R5 | 4 类预设规则模板 | ✅ |
| FR-S1 | 近期一览（7/30 天 + 保留/过滤分口径） | ✅ |
| FR-H6 | 导出 CSV（时间范围筛选 + UTF-8 BOM） | ✅ |
| FR-H7 | 历史概览计数（保留/过滤/已学习） | ✅ |
| FR-P3 | 清空数据 + 独立重置模型入口 | ✅ |

### V1.2（部分提前实现）

| 编号 | 功能 | 状态 |
| --- | --- | --- |
| FR-E7 | 规则导入导出 JSON（重名冲突校验） | ✅ |
| FR-S2 | 日报趋势图（点击排行直达规则创建） | ✅ |
| FR-R3-10 | Webhook 失败重试（3 次指数退避）+ 配置页隐私明示 | ✅ |
| FR-E4 桌面小部件 / FR-E6 多配色主题 | 未实现（P2，跟随系统深浅色已支持） |

### 10 种规则动作实现情况

| 动作 | 状态 | 说明 |
| --- | --- | --- |
| 消除 | ✅ 完整 | 用系统 `sbn.key` 精确 `cancelNotification`（已修复此前用数据库 id 导致失效的 P0 bug） |
| 静音 / 推迟 / 朗读 / 来电提醒 / 打开 | ✅ 完整 | 系统能力范围内实现 |
| 替换 / 更改铃声 | ⚠️ 诚实降级 | 系统不允许改写他人通知内容/铃声，明确记录未执行，不伪装成功 |
| 点击按钮 | ⚠️ 降级 | 需无障碍权限，不启用并说明 |
| Webhook | ✅ 完整 | POST JSON（含重试），唯一外发通道，需显式配置 URL |

> ⚠️ 项为 Android 对第三方应用的客观限制，代码如实记录，符合 PRD「规则可解释」原则。

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
│   │               NotificationBridge / UndoReceiver / CodeCopyReceiver
│   ├── ui/         MainActivity / History / Rules / Stats / Settings / 详情页 / 编辑页
│   └── util/       Prefs / PermissionHelper / AppLockHelper
└── res/            布局 / 矢量图标 / 主题 / 字符串
```

---

## 四、使用须知

1. **必须授予通知使用权**，否则无法监听。
2. **国产 ROM（小米/OPPO/荣耀/华为）需允许后台自启**，否则被杀导致漏记（App 会透明提示漏记时段）。
3. **AI 默认关闭**，在设置中开启，阈值默认 80%。
4. **隐私**：内容仅存本机；无 Webhook 配置时零上行。
5. **Debug 签名**：正式上架需替换签名配置并做 ROM 兼容测试。
6. **升级**：DB 从 v1 升 v2 已内置 Migration，老用户数据保留。

---

## 五、验证记录

- 构建：JDK 17 + Gradle 8.14 + SDK 35，`gradle assembleDebug` **BUILD SUCCESSFUL**（2026-11-18）
- APK：`com.maodou.notify` v1.1.0（versionCode 2），v2 签名校验通过，minSdk 26 / targetSdk 35
