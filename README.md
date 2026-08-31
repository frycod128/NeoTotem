# Immortality Totem / 永生图腾

面向 Minecraft 26.2、NeoForge 26.2.0.66、Java 25 的双端模组。

模组只加入一个物品：永生图腾。
它存在于玩家原版物品栏的任意槽位时，将生命值下限稳定在 `1.0F` 并阻止标准死亡、代码杀、`KILLED` 移除和死亡重生；
移出物品栏后立即恢复原版逻辑。非玩家生物手持时，它使用原版死亡保护组件，行为等同普通不死图腾。

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
