# Zero IDE

Zero IDE 是一个基于 Java 21、Gradle、JavaFX 的小型插件化文本编辑器，界面风格参考 VSCode。项目重点演示插件 API、反射加载、`URLClassLoader` 隔离、事件通知、生命周期管理、文件 I/O 和基础依赖注入。

## 技术栈

- Java 21 LTS
- Gradle 多模块构建
- JavaFX `TextArea` 编辑器界面
- 插件 API + 反射 + `URLClassLoader`
- `plugin.json` 插件描述文件
- Spring `GenericApplicationContext` 管理核心服务
- Jackson 解析插件描述
- SLF4J + Logback 日志
- JUnit 5 测试

## 模块结构

```text
zero-ide
├── editor-api                 # 插件 API、事件、核心服务接口
├── editor-core                # JavaFX 应用、插件管理器、事件总线、文件服务
└── plugins
    ├── word-count-plugin      # 字数统计状态栏插件
    ├── markdown-tools-plugin  # Markdown 菜单与大纲插件
    └── java-snippets-plugin   # Java 代码片段插件
```

## 运行

先确保本机有 JDK 21 或更高版本。项目按 Java 21 目标版本编译。

```bash
# Windows
.\gradlew.bat runIde

# macOS / Linux
./gradlew runIde
```

`runIde` 会先把示例插件 jar 构建并复制到：

```text
build/runtime/plugins
```

然后启动 JavaFX 编辑器。启动时核心应用会扫描该目录，读取每个 jar 根目录下的 `plugin.json`，再按依赖关系加载插件。

如果你已经全局安装了 Gradle，也可以使用：

```bash
gradle runIde
```

## 插件描述文件

每个插件 jar 需要在根目录包含 `plugin.json`：

```json
{
  "id": "word-count",
  "name": "Word Count",
  "version": "0.1.0",
  "entryClass": "com.zeroide.plugins.wordcount.WordCountPlugin",
  "dependencies": []
}
```

插件入口类需要实现：

```java
public interface Plugin {
    void onLoad(EditorContext context) throws Exception;
    void onUnload() throws Exception;
}
```

## 核心设计

- `EditorContext` 暴露编辑器服务、事件总线和 UI 服务。
- `DynamicPluginManager` 负责扫描 jar、读取描述文件、校验依赖、反射创建插件实例、加载和卸载插件。
- `DefaultEventBus` 使用观察者模式，插件可以订阅 `TextChangedEvent`、`FileOpenedEvent`、`FileSavedEvent` 等事件。
- `JavaFxUiService` 允许插件添加状态栏项、菜单项和信息弹窗。
- 卸载插件时会调用 `onUnload()`，移除 UI 扩展，并关闭对应 `URLClassLoader`。

## 测试

```bash
.\gradlew.bat test
```

## 后续扩展方向

- 用 RichTextFX 替换 JavaFX `TextArea`，实现真正的语法高亮。
- 增加插件权限模型，限制不可信插件访问核心资源。
- 增加插件依赖版本范围和冲突检测。
- 增加 Git 插件、代码补全插件、宏录制插件。
- 使用 `jlink` 或 `jpackage` 打包桌面应用。
