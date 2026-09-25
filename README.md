# ChatGPT Box

一个专注于 **ChatGPT / Codex 额度查看** 的 Android 小工具。

当前 V0.1 基于 `657kbps/quota-widget` 的 Codex OAuth、额度解析、后台刷新与 Glance Widget 能力进行产品化改造，目标是：
- 中文界面
- 只保留 ChatGPT / Codex
- 直接显示 5 小时 / 每周额度与重置时间
- 4×1 桌面状态条 + 2×2 额度卡片
- 后台自动刷新与手动刷新

> 当前为个人实验版本。上游仓库未在根目录提供明确 LICENSE，因此暂不作为独立开源项目重新分发源码许可；保留上游来源说明。

## V0.1

- App 名称改为 **ChatGPT Box**
- Android applicationId 改为 `com.goodnessy.chatgptbox`
- 产品界面只展示 ChatGPT / Codex
- 桌面小组件列表只暴露 Codex 相关组件
- 紧凑组件目标尺寸调整为 **4×1**
- 保留 OAuth 登录、Token 自动刷新、WorkManager 后台更新
- Debug APK 通过 GitHub Actions 自动构建，可直接安装测试

## 上游

- 原项目：`657kbps/quota-widget`
- 当前底层额度接口：`https://chatgpt.com/backend-api/wham/usage`

后续会继续重做主界面与 Widget 视觉，逐步减少对上游多平台代码的依赖。
