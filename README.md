# 毛豆通知（Maodou Notify）· Android 原生 App

依据 PRD《毛豆通知 v0.1》（三份中选功能最完整、验收最明确的一版）实现的 Android 原生工程。

- **技术栈**：Kotlin + Jetpack Room + Material 3 + 协程，原生 ViewBinding 架构
- **最低版本**：Android 8.0（API 26），目标 Android 15（API 35）
- **隐私**：通知内容 AES-256-GCM 加密存本地（密钥在 Android Keystore），零上传；唯一外发通道是用户显式配置的 Webhook
- **可直接安装**：`../毛豆通知-v1.0.apk`（Debug 签名，已用 Gradle 8.14 真实编译通过）

---

## 一、如何构建

### 方式 A：Android Studio（推荐）
1. 打开 Android Studio → Open → 选择本目录 `MaodouNotify`
2. 等待 Gradle 同步完成
3. 连接手机或启动模拟器 → 点击 Run（▶）
4. 首次打开按引导授予「通知使用权」

### 方式 B：命令行
```bash
# 需 JDK 17 + Android SDK 35
# 修改 local.properties 中 sdk.dir 为本机 SDK 路径
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

> 本工程已包含 Gradle Wrapper（8.14）。`local.properties` 当前指向构建机的 SDK 路径，换机器后需改为本机路径（`sdk.dir=/你的/Android/Sdk`）。

---

## 二、已实现功能 ↔ PRD 编号映射

### V1.0（MVP）— 全部实现

| PRD 编号 | 功能 | 实现位置 |
| --- | --- | --- |
| FR-H1 | 全量通知记录 + 本地加密 | `NotificationMonitorService` + `CryptoHelper` + Room |
| FR-H2 | 多维搜索（App/关键字/正文/包名） | `HistoryFragment` |
| FR-H3 | 时间轴回溯（按时间倒序，可按日期查看） | `NotificationAdapter` + 历史列表 |
| FR-H4 | 一键恢复通知 | `NotificationDetailActivity` + `NotificationBridge.showRestored` |
| FR-H5 | 撤回/删除消息留存 | `onNotificationRemoved` 记录移除原因，详情页明示快照 |
| FR-R1 | 规则模型与匹配引擎（首条命中即执行） | `RuleEngine` |
| FR-R2 | 条件维度：App/关键字/时间段/星期/充电/锁屏 | `RuleEngine.matches` |
| FR-R3 | 规则动作集（10 种，见下表） | `ActionExecutor` |
| FR-R4 | 规则增删改 + 启停 | `RuleEditActivity` + `RulesFragment` |
| FR-P1 | 数据不出设备 + 本地加密 | `CryptoHelper`（AES-GCM/Keystore） |
| FR-P2 | 全离线 AI | `SpamScorer`（纯本地，无网络） |
| FR-P4 | 权限引导与状态自检 | `MainActivity.checkPermissions` + `PermissionHelper` |

### 10 种规则动作实现情况

| 动作 | 状态 | 说明 |
| --- | --- | --- |
| 1 消除 | ✅ 完整 | 调用 `cancelNotification` 从通知栏移除 |
| 2 静音 | ✅ 完整 | 临时切换铃音模式（时长可配） |
| 3 推迟 | ✅ 完整 | Handler 定时后以本 App 名义重发 |
| 4 替换 | ⚠️ 记录 | 记录处理结果；受系统限制无法原地改写他 App 通知内容 |
| 5 更改铃声 | ⚠️ 记录 | 记录处理结果；系统不允许第三方改他 App 通知铃声 |
| 6 朗读 | ✅ 完整 | 系统 TTS 朗读标题+正文 |
| 7 来电提醒 | ✅ 完整 | 全屏 Intent + 高优先级持续通知 |
| 8 点击按钮 | ⚠️ 降级 | 需无障碍/系统签名权限，不启用并明确说明 |
| 9 打开通知 | ✅ 完整 | 启动目标 App |
| 10 Webhook | ✅ 完整 | POST JSON 到用户 URL（唯一外发通道，需显式配置） |

> ⚠️ 项为 Android 系统对第三方应用的客观限制，代码中均做了「明确记录 + 不伪装成功」处理，与 PRD「规则可解释」原则一致。

### V1.1 / V1.2 部分提前实现

| PRD 编号 | 功能 | 状态 |
| --- | --- | --- |
| FR-A1/A2 | 离线骚扰打分 + 一键标注（是骚扰/是正常） | ✅ 已实现（`SpamScorer` + 标注即写入学习样本） |
| FR-A3 | 离线学习 | ✅ 轻量实现（基于标注样本的 App 偏置 + 高频词） |
| FR-A4 | AI 开关与阈值 | ✅ 设置页可调 |
| FR-E1 | 5 秒撤销按钮 | ✅ `UndoReceiver` |
| FR-E2 | 智能静音 | ✅ 同 App 60 秒内 ≥5 条自动静音 30 秒 |
| FR-E8 | 排除 App | ✅ 设置中可选（预留字段，UI 可扩展） |
| FR-S1 | 近期一览（App 通知排行） | ✅ 分析页近 7 天排行 |
| FR-H6 | 导出 CSV | ✅ UTF-8 BOM，Excel/WPS 中文不乱码 |
| FR-P3 | 清空数据 | ✅ 二次确认 |

### 未实现（需后续版本，均已在 PRD 中标注为 P2/待定）

- FR-H7 历史概览计数、FR-E4 桌面小部件、FR-E5 指纹/面容锁、FR-E6 多款主题、FR-E7 规则导入导出
- FR-R5 预设规则模板、FR-S2 日报图表、FR-E3 验证码「复制」按钮（系统限制，暂以普通通知呈现）
- 联系人级来电提醒、社区规则仓库、多语言

---

## 三、工程结构

```
MaodouNotify/
├── app/src/main/
│   ├── AndroidManifest.xml          # 组件与权限声明
│   ├── java/com/maodou/notify/
│   │   ├── MaodouApp.kt             # Application，初始化数据库/渠道
│   │   ├── data/                    # 实体、DAO、Database、加密工具
│   │   │   ├── Entities.kt / Daos.kt / AppDatabase.kt / CryptoHelper.kt
│   │   ├── engine/                  # 规则引擎、AI 打分、动作执行、Webhook
│   │   │   ├── RuleEngine.kt / SpamScorer.kt / ActionExecutor.kt / WebhookSender.kt
│   │   ├── listener/                # 通知监听、保活、开机恢复、撤销
│   │   │   ├── NotificationMonitorService.kt / KeepAliveService.kt / BootReceiver.kt / NotificationBridge.kt / UndoReceiver.kt
│   │   ├── ui/                      # 历史/规则/分析/设置四个模块
│   │   └── util/                    # 权限、设置
│   └── res/                         # 布局、图标、主题、字符串
└── build.gradle.kts / settings.gradle.kts / gradle.properties
```

---

## 四、使用须知

1. **必须授予通知使用权**：首次打开会弹窗引导，或在「设置 → 通知使用权」中开启。
2. **保活**：国产 ROM（小米/OPPO/荣耀等）需在系统设置中允许本 App 后台运行与自启动，否则可能被杀导致漏记。
3. **AI 默认关闭**：在「设置 → 离线 AI」中开启，阈值默认 80%，可按需调整。
4. **隐私**：所有通知内容仅存本机；打开 App 不会产生任何通知内容上行流量（Webhook 除外，且需你主动配置 URL）。
5. **Debug 签名**：当前 APK 为调试版，正式上架需替换签名配置并做兼容测试。

---

## 五、验证记录

- 构建工具：JDK 17.0.20 + Gradle 8.14 + Android SDK 35
- `gradle assembleDebug`：**BUILD SUCCESSFUL**（2026-11-18）
- 产物：`app/build/outputs/apk/debug/app-debug.apk`（6.2 MB）
