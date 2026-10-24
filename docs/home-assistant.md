# Home Assistant 集成

## 安装

把仓库中的目录：

```text
home-assistant/custom_components/c1d_projector
```

复制到 Home Assistant 配置目录，最终必须存在：

```text
/config/custom_components/c1d_projector/manifest.json
```

然后重启 Home Assistant。无需修改 Docker Compose，也无需安装 HACS。

## 添加设备

1. 确保 HA 与投影仪处于允许 mDNS 和局域网互访的网络。
2. 打开“设置 → 设备与服务”。
3. 在“已发现”中选择 `C1D Projector`。
4. 输入构建 C1D Home APK 时使用的 `C1D_STANDBY_TOKEN`。
5. 完成后会出现“C1D 投影仪”开关：开 = 唤醒，关 = 网络待机。

集成按 mDNS TXT 中的稳定设备 ID 建立唯一设备。投影仪重新获取 DHCP 地址时，发现回调会更新 host/port 并重新加载实体。

## 手机与语音

- 把实体放到 HA 仪表盘后，可用 Home Assistant Companion App 控制。
- 在 HA 的语音助手设置中公开该实体后，可用“打开 C1D 投影仪”和“关闭 C1D 投影仪”。
- 这里的“关闭”只代表网络待机，不是整机关机。

## 常见问题

### 没有自动发现

检查 HA 与投影仪是否同一局域网、Wi-Fi 是否启用客户端隔离、Docker 网络是否允许 mDNS。跨 VLAN 时需要 mDNS reflector/repeater。

### 发现后认证失败

HA 中输入的 Token 必须与 APK 构建时的 `C1D_STANDBY_TOKEN` 完全一致。不要把 Token 发到公开 Issue。

### 地址变化后短暂不可用

等待新的 mDNS 记录到达；集成会按稳定设备 ID 更新地址。若网络彻底屏蔽 mDNS，只能在路由器上设置 DHCP 地址保留并自行维护静态方案。
