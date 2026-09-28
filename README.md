# 机械动力铁路施工增强（RRCE）

![RRCE Icon](common\src\main\resources\icon.png)

机械动力铁路施工增强（Create: Railroad Construction Enhanced，RRCE）是一个面向 Minecraft 1.20.1 / Fabric、Forge 和 Minecraft 1.21.1 / NeoForge 的 Create 附属模组，提供以控制点勘测为基础的铁路批量施工能力：先用手持工具标定带有方向的轨道端点，再由铁路施工图按参数一次性修建直线、弯道、坡道、多条平行复线、路堑与隧道。

## 功能概览

- **控制点勘测**：五件手持工具用于添加、选择、移动、旋转和删除轨道端点；四件快速规划工具可从已选端点延伸弯道、坡道或转弯。端点方向以 45° 为步进，箭头始终指向端点编号增大的方向。
- **线路规划**：由相邻端点自动推导直线、45° / 90° 转弯与 S 弯，并可横向扩展为最多 16 条平行线路，逐线选择轨道材质与弯道主端。
- **地形施工**：依据服务端地形规则判定障碍高度，低于阈值时开路堑，高于阈值时凿隧道，并保留线路端点以外的原有地形。
- **预览与事务**：施工前提供线路轮廓与施工全貌两种预览，施工支持包含方块实体数据的完整快照撤销与重做。
- **兼容性**：可选读取 Create: Steam ’n’ Rails（气鸣铁道）注册的轨道材质，并在安装 Create Unlimited 时同步其放置检查设置。

## 运行环境

| Minecraft | 加载器 | Create 构建基准 | 游戏 Java |
|---|---|---|---|
| 1.20.1 | Fabric Loader 0.19.5、Fabric API | 0.5.1-j-build.1631 | 17 |
| 1.20.1 | Fabric Loader 0.19.5、Fabric API | 6.0.8.1+build.1744 | 17 |
| 1.20.1 | Forge 47.4.0 | 0.5.1.j | 17 |
| 1.20.1 | Forge 47.4.0 | 6.0.8 | 17 |
| 1.21.1 | NeoForge 21.1.219 | 6.0.10 | 21 |

每一行使用独立产物，不能混用加载器、Minecraft 版本或 Create 代际。Create 新代际的正式版本号为 6.0，而不是 0.6；1.21.1 没有 Create 0.5 目标，也不提供 Fabric 1.21.1 产物。

可选兼容模组：

- **Create: Steam ’n’ Rails（气鸣铁道）**：提供额外的轨道材质。
- **Create Unlimited**：在放置检查被关闭时，同步放宽对应的坡道与弯道限制。

模组需同时安装于服务端与客户端。

## 构建

项目使用 Gradle Wrapper，从源码构建使用 JDK 25；Gradle 自动获取各目标所需的 Java 17 / 21 工具链。下述命令构建全部五个版本目标，可使用 `-Ptarget=mc1201-forge-create6` 等目标名称单独构建。产物文件名包含模组版本、加载器、Minecraft 版本与 Create 代际：

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
| `docs` | 项目文档 |

## 文档

- [使用手册](docs/USER_GUIDE.md)：面向玩家的勘测、规划与施工说明。

## 许可证

本项目使用 **GNU General Public License v3.0 or later** 授权，SPDX 标识为 `GPL-3.0-or-later`。完整许可证文本见 [COPYING](COPYING)。
