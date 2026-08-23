
# NeoForge 26.2 示例模组

本项目在官方 MDK 基础上为核心逻辑补充了中文注释，并提供了一个能够完整显示、放置和掉落的最小示例。建议按下面的顺序阅读：

1. `ExampleMod.java`：入口、延迟注册、两类事件总线、创造模式标签页和生命周期线程。
2. `Config.java`：配置声明、校验、加载/重载事件，以及供业务代码读取的不可变快照。
3. `ExampleModClient.java`：物理侧隔离、客户端配置界面和客户端主线程调度。
4. `src/main/resources/assets/examplemod`：语言、方块状态、方块模型和 26.2 客户端物品模型。
5. `src/main/resources/data/examplemod`：服务端数据资源；当前示例包含方块掉落表。

常用命令（Windows）：

```powershell
.\gradlew.bat runClient
.\gradlew.bat runServer
.\gradlew.bat build
```

注册表内容、客户端资源和服务端数据是三层不同的工作：Java 注册成功并不代表模型、翻译或掉落表会自动生成。扩展示例方块/物品时，应同步补齐相应资源，或改用数据生成器批量生成。

# Installation information

This template repository can be directly cloned to get you started with a new
mod. Simply create a new repository cloned from this one, by following the
instructions provided by [GitHub](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-repository-from-a-template).

Once you have your clone, simply open the repository in the IDE of your choice. The usual recommendation for an IDE is either IntelliJ IDEA or Eclipse.

If at any point you are missing libraries in your IDE, or you've run into problems you can
run `gradlew --refresh-dependencies` to refresh the local cache. `gradlew clean` to reset everything 
{this does not affect your code} and then start the process again.

## Mapping Names
By default, the MDK is configured to use the official mapping names from Mojang for methods and fields 
in the Minecraft codebase. These names are covered by a specific license. All modders should be aware of this
license. For the latest license text, refer to the mapping file itself, or the reference copy here:
https://github.com/NeoForged/NeoForm/blob/main/Mojang.md

## Additional Resources
Community Documentation: https://docs.neoforged.net/  
NeoForged Discord: https://discord.neoforged.net/
