# 网络待机实现

## 行为定义

- **开机/唤醒**：恢复 C1D 显示硬件并打开 HOME。
- **网络待机**：关闭显示硬件，但保留 Android、CPU、Wi-Fi 和局域网控制服务。
- **关机**：不属于本项目。整机断电后网络接口当然不可用。

## Android 服务

`StandbyControlService` 是非导出的前台服务，监听局域网 TCP 8765：

- `GET /api/power`：读取 `on` 或 `standby`。
- `POST /api/power`，正文含 `standby`/`off`：进入网络待机。
- `POST /api/power`，正文含 `wake`/`on`：唤醒。
- `POST /api/standby` 与 `POST /api/wake`：等价快捷入口。

所有请求都必须携带：

```http
Authorization: Bearer <private-token>
```

Token 通过 `C1D_STANDBY_TOKEN` 在构建时注入。源码没有默认 Token，也不应该提交个人 Token。

## 为什么待机后仍能响应

进入待机前，服务持有：

- `PARTIAL_WAKE_LOCK`：避免 CPU 完全休眠。
- `WIFI_MODE_FULL_HIGH_PERF`：保留 Wi-Fi 通信。
- 前台服务通知：降低后台服务被系统回收的概率。

随后 `StandbyController` 写入目标固件的 `/proc/msp/panel`，关闭显示硬件。唤醒成功后先恢复显示，再打开 HOME 并释放 Lock。

## 自动发现

服务使用 Android NSD 发布：

- 类型：`_c1d-standby._tcp.local.`
- 端口：`8765`
- TXT：`id`、`path=/api/power`、`version=1`

这让 HA 不依赖 DHCP 固定地址。如果 HA 与投影仪位于不同 VLAN，需要配置 mDNS reflector；访客网络或客户端隔离可能阻断发现。

## 安全边界

当前协议是带 Token 的明文 HTTP，适合可信家庭 LAN，不适合互联网。不要转发 8765；跨公网控制应通过 HA 自己的安全远程访问或家庭 VPN，而不是直接暴露投影仪。
