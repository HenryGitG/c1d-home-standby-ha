# 架构与仓库边界

## 首次配置

```text
c1d-adb-home-setup
  系统远程协助（所有者授权）
    -> 无特权设置助手
    -> C1D 固件调试入口
    -> 标准局域网 ADB
    -> 安装 APK / 设置默认 HOME
```

远程协助只用于首次引导；它不是本项目的长期控制平面，也没有被重新实现或公开。

## 日常运行

```text
c1d-home-standby-ha

手机 / 语音助手 / HA 仪表盘
             |
             v
Home Assistant c1d_projector 集成
             |
      mDNS + HTTP Bearer Token
             |
             v
C1D Home 的 StandbyControlService
             |
       StandbyController
             |
             v
C1D 固件 /proc/msp/panel
```

Home Assistant 与 Android 项目在部署上独立：HA 跑在 Docker/NAS/主机，Android Home 跑在投影仪。运行时二者通过局域网协议协作，因此功能上互相依赖，但不需要把文件放进同一容器或同一 Android 包。

## 为什么不用固定 IP

Android 服务发布 `_c1d-standby._tcp.local.` DNS-SD 记录，TXT 包含稳定 Android ID、API 路径和协议版本。HA 集成按稳定设备 ID 识别同一台投影仪，并在 DHCP 地址变化后更新连接地址。
