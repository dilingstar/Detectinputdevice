# DetectInputDevice (安卓输入设备检测工具)

一款轻量级的 Android 应用程序，用于查看系统中的输入设备、读取已插入的 USB 设备信息，并支持实时监听当前正在触发的输入事件。

本项目非常适合用于外设调试、Android 游戏手柄适配测试，或者仅仅是为了了解你的 Android 设备底层到底连接了哪些虚拟/物理输入节点。

## ✨ 主要功能

* **🔍 深度设备检测 (输入设备)**
    * **框架级 (API):** 通过 `InputDevice.getDeviceIds()` 获取 App 能感知到的所有合法输入设备，并解析其键鼠类型、运动轴 (Motion Ranges) 等详细数据。
    * **内核级尝试:** 尝试读取 `/proc/bus/input/devices` 和 `/sys/bus/usb/devices`（注：受限于 Android 系统的安全机制，非 Root 设备通常会提示权限拒绝 `Permission denied`，这是正常现象）。
* **🔌 USB 设备读取**
    * 通过 `UsbManager` 获取当前连接的 USB 设备列表（需手机支持并处于 OTG 主机模式）。
    * 解析并展示 USB 设备的 VendorId、ProductId、DeviceClass、接口 (Interfaces) 以及端点 (Endpoints) 信息。
* **📡 实时事件监听**
    * 提供 30 秒倒计时的实时输入监控功能。
    * 全局拦截并打印 `MotionEvent`（触摸屏）、`KeyEvent`（实体按键/键盘）和 `GenericMotionEvent`（鼠标/摇杆/手柄）的原始底层数据。
* **💾 日志持久化**
    * 自动保存测试日志，下次打开 App 依然可以查看之前的检测结果。支持一键清空面板。

## ⚠️ 注意事项与已知局限

* **数据准确性提示：** 本工具直接输出 Android 系统 API 抛出的原始数据（Raw Data）。部分设备由于厂商底层驱动的魔改，或者 Android 框架的抽象映射，输出的原始数据可能不够直观或在某些字段上**不是绝对准确**的。
* **OTG 限制：** 读取 USB 设备信息时，手机必须支持并开启 OTG 功能。
* **权限限制：** 获取内核级输入节点（如 `/proc/...` 路径）在 Android 10+ 系统中对无 Root 权限的 App 是严格限制的，App 会正确捕获并提示 `EACCES` 错误。

## 🚀 下载与安装

1. 前往本项目的 **[Releases 页面](https://github.com/dilingstar/Detectinputdevice/releases)**。
2. 下载最新版本的 `.apk` 安装包。
3. 传输到你的 Android 手机上，允许“安装未知来源应用”并完成安装。
4. 打开 App 即可开始检测（建议使用真机测试 OTG 和真实外设）。

## 🤖 代码

本项目核心代码逻辑由 AI 辅助生成，并由开发者测试、整合与发布。

