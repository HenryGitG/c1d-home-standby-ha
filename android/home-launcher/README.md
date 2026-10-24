# C1D Home v2.9

Android TV HOME 应用，基于 MIT 许可的 [CleanYourTV](https://github.com/Gathin23/cleanyourtv) 修改，加入 C1D 投影仪适配和带鉴权的局域网待机/唤醒服务。

## 构建

使用 JDK 17。先生成仅供自己家庭使用的 64 位十六进制令牌：

```bash
export C1D_STANDBY_TOKEN="$(openssl rand -hex 32)"
./gradlew assembleRelease
```

也可用 Gradle 属性传入：

```bash
./gradlew assembleRelease -PC1D_STANDBY_TOKEN=<64-hex-token>
```

令牌会在构建时写入 APK 资源，但不会写入源码仓库。把同一令牌输入 Home Assistant 集成。请妥善保管最终 APK，因为能读取 APK 的人也可能提取令牌。

默认包名为 `com.gathin.tvhome`。Release APK 位于 `app/build/outputs/apk/release/app-release.apk`。安装、切换和恢复 HOME 的流程见配套仓库的 [安装与替换 HOME](https://github.com/HenryGitG/c1d-adb-home-setup/blob/main/docs/replace-home.md)。

## 设备限制

待机控制通过 C1D 固件提供的 `/proc/msp/panel` 接口关闭或恢复显示硬件。该路径、权限和指令不是 Android 标准 API，只在目标 C1D 固件上验证。不要假设它适用于其他投影仪或电视。

## 上游与许可

MIT 许可及上游版权保留在 [LICENSE](LICENSE)。Outfit 字体许可见 [FONT-LICENSE-OFL.txt](FONT-LICENSE-OFL.txt)。
