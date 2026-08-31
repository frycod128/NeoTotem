# Immortality Totem / 永生图腾

面向 Minecraft 26.2、NeoForge 26.2.0.66、Java 25 的双端模组。

模组只加入一个物品：永生图腾。
它存在于玩家原版物品栏的任意槽位时，将生命值下限稳定在 `1.0F` 并阻止标准死亡、代码杀、`KILLED` 移除和死亡重生；
移出物品栏后立即恢复原版逻辑。非玩家生物手持时，它使用原版死亡保护组件，行为等同普通不死图腾。

在主手按住右键可启动三秒引导：引导期间玩家拥有满额击退抗性和 -20% 移动速度，
并持续播放紫水晶叮铃声；完整长按 60 tick 后会在原地生成 flash 粒子并传送到当前出生点。
引导开始时立即增加 20 点 Exhaustion，并施加/刷新 6 秒虚弱 V；
引导结束（包括中途打断和正常完成）时叠加 6 秒失明 I，重复触发会累加 3 秒失明剩余时间。
中途松开右键或切换物品会立即打断引导，但不会撤回开始时的消耗度和虚弱效果。
完整长按并成功传送到出生点后，图腾会进入引导时长三倍的 180 tick 物品冷却；
该冷却只禁止主手右键重新启动引导，不影响持有、掉落或其他常规功能。

完整需求、调用链、兼容性边界和测试矩阵见 [IMMORTALITY_DESIGN.md](IMMORTALITY_DESIGN.md)。

## 构建

```powershell
.\gradlew.bat build
```

产物位于 `build/libs/immortalitytotem-1.0.0.jar`。客户端与服务端都应安装同一版本。

## 开发运行

```powershell
.\gradlew.bat runClient
.\gradlew.bat runServer
```

## 资源结构

- `assets/immortalitytotem/items`：26.2 客户端物品定义。
- `assets/immortalitytotem/models/item`：材质包底图与独立几何动效遮罩组合模型。
- `assets/immortalitytotem/textures/item`：从原版图腾右眼四格喷向右上方的八帧抽帧像素火焰遮罩。
- `data/immortalitytotem/recipe`：合成配方。
- `tools/generate_totem_overlay.py`：不读取原版纹理，确定性生成几何动画遮罩和 `.png.mcmeta`。
