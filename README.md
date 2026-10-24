# C1D Home、网络待机与 Home Assistant

这个仓库提供 C1D 的第三方 HOME、不断网的显示待机/唤醒能力，以及配套 Home Assistant 自定义集成。

它解决的不是“远程关机”，也不是单纯“黑屏”：待机时关闭显示面板/背光相关硬件，同时用前台服务和 CPU/Wi-Fi Lock 保留局域网控制接口；Home Assistant 发出唤醒请求后恢复显示并返回 HOME。

```text
Home Assistant 开关
  -> Zeroconf/mDNS 发现 _c1d-standby._tcp.local
  -> Bearer Token HTTP（局域网 8765）
  -> C1D Home 前台服务
  -> /proc/msp/panel 显示电源控制
```

## 与 ADB/HOME 设置仓库的关系

首次打开固件调试入口、建立标准 ADB、安装 APK、切换或恢复 HOME，请先使用：[c1d-adb-home-setup](https://github.com/HenryGitG/c1d-adb-home-setup)。

本仓库从“ADB 已可用”开始，负责：

- `android/home-launcher/`：C1D Home v2.9 和网络待机服务。
- `home-assistant/custom_components/c1d_projector/`：HA v1.0.1 自定义集成。
- `docs/network-standby.md`：待机服务的实现与安全边界。
- `docs/home-assistant.md`：安装和使用 HA 集成。
- `docs/architecture.md`：两个仓库及运行时组件关系。

## 快速开始

1. 按 [Android 构建说明](android/home-launcher/README.md)生成一个随机 Token 并构建 APK。
2. 使用配套仓库的 ADB 流程安装 APK、切换 HOME。
3. 把 `home-assistant/custom_components/c1d_projector/` 复制到 HA 的 `/config/custom_components/`。
4. 重启 HA，在“设置 → 设备与服务”的“已发现”中添加 `C1D Projector`。
5. 输入构建 APK 时使用的同一个 Token。

## 验证状态

- C1D Home v2.9：在目标 C1D 真机安装并作为默认 HOME 运行。
- 待机与唤醒：真机验证 HTTP 200，显示硬件关闭后局域网服务仍可发现并唤醒。
- DHCP 地址变化：集成通过稳定设备 ID 和 mDNS 更新地址，不依赖固定 IP。
- Home Assistant 自定义集成：完成源码、结构和本地协议验证；不同 HA 版本仍建议在自己的实例中做一次端到端验证。

## 安全

服务只供可信家庭局域网使用。不要把 8765 映射到公网，不要提交 Token、HA secrets、设备地址或备份。详见 [SECURITY.md](SECURITY.md)。

## License

MIT。Android HOME 源自 `Gathin23/cleanyourtv`；版权与字体说明见 [NOTICE.md](NOTICE.md)。
