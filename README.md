# 机械动力铁路施工增强（RRCE）

机械动力铁路施工增强（Create: Railroad Construction Enhanced，RRCE）是一个面向 Minecraft 1.20.1 / Fabric 的 Create 附属模组，提供以控制点勘测为基础的铁路批量施工能力：先用手持工具标定带有方向的轨道端点，再由铁路施工图按参数一次性修建直线、弯道、坡道、多条平行复线、路堑与隧道。

## 功能概览

- **控制点勘测**：五件手持工具用于添加、选择、移动、旋转和删除轨道端点；端点方向以 45° 为步进，箭头始终指向端点编号增大的方向。
- **线路规划**：由相邻端点自动推导直线、45° / 90° 转弯与 S 弯，并可横向扩展为最多 16 条平行线路，逐线选择轨道材质与弯道主端。
- **地形施工**：依据服务端地形规则判定障碍高度，低于阈值时开路堑，高于阈值时凿隧道，并保留线路端点以外的原有地形。
- **预览与事务**：施工前提供线路轮廓与施工全貌两种预览，施工支持包含方块实体数据的完整快照撤销与重做。
- **兼容性**：可选读取 Create: Steam ’n’ Rails（气鸣铁道）注册的轨道材质，并在安装 Create Unlimited 时同步其放置检查设置。

## 运行环境

| 项目 | 要求 |
|---|---|
| Minecraft | 1.20.1 |
| Fabric Loader | 0.16.9 或更高 |
| Fabric API | 必需 |
| Create Fabric | 0.5.1-j-build.1631 |
| Java | 17 或更高 |

可选兼容模组：

- **Create: Steam ’n’ Rails（气鸣铁道）**：提供额外的轨道材质。
- **Create Unlimited**：在放置检查被关闭时，同步放宽对应的坡道与弯道限制。

模组需同时安装于服务端与客户端。

## 构建

项目使用 Gradle Wrapper，从源码构建需要 JDK 17 或更高：

```powershell
.\gradlew.bat build
```

构建产物位于 `build/libs/`。

## 仓库结构

| 路径 | 说明 |
|---|---|
| `src/main/java/dev/sjimo/rrce` | 施工物品、玩家会话、配置与命令入口 |
| `src/main/java/dev/sjimo/rrce/world` | 线路几何、路基、开山、隧道与服务端地形规则 |
| `src/main/java/dev/sjimo/rrce/client` | 配置界面、预览渲染与思索章节 |
| `src/main/resources` | 模组元数据、语言文件与物品资源 |
| `src/test/java` | 几何与配置的单元测试 |
| `tools` | 物品图标与思索场景的生成脚本 |
| `docs` | 项目文档 |

## 文档

- [使用手册](docs/USER_GUIDE.md)：面向玩家的勘测、规划与施工说明。

## 许可证

本项目使用 **GNU General Public License v3.0 or later** 授权，SPDX 标识为 `GPL-3.0-or-later`。完整许可证文本见 [COPYING](COPYING)。
