<div align="center">

# ⏰ SleepDurationAlarm

### 检测 OPPO Watch 入睡状态后，按计划睡眠时长创建一次性闹钟

**Android · LSPosed · OPPO Health · OPPO Clock · Sleep Automation**

<p>
  <strong>导航</strong><br/>
  <a href="#项目简介">项目简介</a> ·
  <a href="#兼容性">兼容性</a> ·
  <a href="#工作方式">工作方式</a> ·
  <a href="#使用方法">使用方法</a> ·
  <a href="#构建">构建</a> ·
  <a href="#免责声明">免责声明</a>
</p>

![Platform](https://img.shields.io/badge/Platform-Android-111827)
![Module](https://img.shields.io/badge/Module-LSPosed-0F766E)
![SDK](https://img.shields.io/badge/SDK-35-FF5722)
![Release](https://img.shields.io/github/v/release/V0idream/SleepDurationAlarm)
![License](https://img.shields.io/github/license/V0idream/SleepDurationAlarm)

<p>
  <a href="https://github.com/V0idream/SleepDurationAlarm/releases/latest">
    <img src="https://img.shields.io/github/downloads/V0idream/SleepDurationAlarm/total?label=Download&style=for-the-badge" alt="Download SleepDurationAlarm" />
  </a>
</p>

</div>

---

<a id="项目简介"></a>

## 📌 项目简介

**SleepDurationAlarm** 是一个带配置界面的 LSPosed 模块。它直接捕捉 OPPO Watch 下发给 OPPO 健康的入睡状态，并按“实际入睡时间 + 计划睡眠时长”在 OPPO 时钟中创建一次性闹钟。

计划时长、启用状态和闹钟名称均可在模块界面中修改。v1.0.1 使用显式配置桥把 UI 设置同步到健康 `:transport` 进程，避免跨进程偏好读取失败后固定使用默认 8 小时。

<a id="兼容性"></a>

## 🧩 兼容性

- 模块版本：`1.0.1`
- Android：12 及以上
- OPPO 健康：`6.4.6_cb99e90_260626`
- 健康包名：`com.heytap.health`
- OPPO 时钟：`16.19.0`
- 时钟包名：`com.coloros.alarmclock`
- LSPosed 作用域：仅 `OPPO 健康`

其他健康或时钟版本未经验证。应用更新导致内部类名或公开闹钟入口变化后，模块可能失效。

<a id="工作方式"></a>

## ⚙️ 工作方式

入睡状态入口：

```text
SleepModeManager.t(SleepModelSettings)
→ SleepModelSettings.isStartNow=true
```

闹钟创建流程：

1. UI 保存计划并通过显式配置桥同步到健康 `:transport` 进程。
2. 收到真实入睡状态后读取当前计划时长。
3. 计算“入睡时刻 + 计划时长”，向上取整到下一整分钟。
4. 通过 OPPO 时钟公开的 `SET_ALARM` 接口创建闹钟。
5. 设置 `DELETE_AFTER_USE=1`，闹钟响铃后自动删除。

手机免打扰状态不会触发闹钟。

<a id="使用方法"></a>

## 🚀 使用方法

1. 从 [Releases](https://github.com/V0idream/SleepDurationAlarm/releases/latest) 下载并安装 APK。
2. 打开“睡眠时长闹钟”，设置启用状态、睡眠时长和闹钟名称，然后点击“保存计划”。
3. 在 LSPosed 中启用模块，作用域仅勾选“OPPO 健康”。
4. 重启 OPPO 健康相关进程或重启手机。
5. 可先使用“测试创建 2 分钟后的闹钟”验证 OPPO 时钟接口。

正常触发日志：

```text
[SleepDurationAlarm] Config received: enabled=true, duration=7h30m
[SleepDurationAlarm] Sleep state received: startNow=true
[SleepDurationAlarm] Alarm request sent to module bridge
```

<a id="构建"></a>

## 🛠️ 构建

需要 Android Gradle Plugin 8.7.3、Gradle 8.10.2、JDK 17 或更高版本以及 Android SDK 35。

```powershell
gradle --offline --no-daemon clean :app:assembleRelease
```

输出文件：

```text
app/build/outputs/apk/release/app-release.apk
```

<a id="免责声明"></a>

## ⚠️ 免责声明

本项目仅供个人研究与自动化使用，与 OPPO、欢太、Google 或 LSPosed 项目无关。请仅在本人设备与已授权环境中使用。

---

## ☕ 支持项目 / Support

如果项目对你有帮助，欢迎点一个 Star。进一步支持方式见 [支持页面](./docs/support.md)。
