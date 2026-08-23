# Immortality Totem / 永生图腾

面向 Minecraft 26.2、NeoForge 26.2.0.66、Java 25 的双端模组。

模组只加入一个物品：九个原版不死图腾以 3×3 合成为一个永生图腾。它存在于玩家原版物品栏的
任意槽位时，将生命值下限稳定在 `1.0F` 并阻止标准死亡、代码杀、`KILLED` 移除和死亡重生；
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
- `assets/immortalitytotem/models/item`：原版图腾底图与换色遮罩组合模型。
- `assets/immortalitytotem/textures/item`：透明换色遮罩。
- `data/immortalitytotem/recipe`：九图腾合成配方。
- `tools/generate_totem_overlay.py`：从本地 26.2 原版纹理确定性重建遮罩。
