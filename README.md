# Zero IDE

Zero IDE 是一个基于 Java 21、Gradle、JavaFX 的小型插件化文本编辑器，界面风格参考 VSCode。项目重点演示插件 API、反射加载、`URLClassLoader` 隔离、事件通知、生命周期管理、文件 I/O 和基础依赖注入。

## 技术栈

- Java 21 LTS
- Gradle 多模块构建
- JavaFX + RichTextFX 编辑器界面
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
├── vscode-compat              # VS Code .vsix 静态贡献解析与导入适配层
└── plugins
    ├── word-count-plugin      # 字数统计状态栏插件
    ├── java-language-plugin   # Java 文件类型与语法高亮插件
    ├── markdown-language-plugin # Markdown 文件类型与语法高亮插件
    ├── json-language-plugin   # JSON 文件类型与语法高亮插件
    ├── json-tools-plugin      # JSON 格式化与压缩插件
    ├── todo-explorer-plugin   # TODO/FIXME 列表插件
    ├── markdown-tools-plugin  # Markdown 菜单与大纲插件
    ├── java-snippets-plugin   # Java 代码片段插件
    ├── file-tree-plugin       # 文件树插件
    ├── search-plugin          # 当前文件搜索与替换插件
    ├── git-plugin             # Git 状态、日志、差异插件
    ├── terminal-plugin        # 终端命令插件
    └── markdown-preview-plugin # Markdown 预览插件
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

插件可以在界面中通过 `Plugins -> Manage Plugins` 打开管理面板，查看已加载插件并单独卸载。卸载依赖插件时，依赖它的插件会自动一起卸载。

状态栏会显示当前文件语言，例如 `Lang: Java`、`Lang: Markdown`、`Lang: JSON` 或 `Lang: Plain Text`。点击该状态项可以手动切换当前文件的高亮语言。

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

- `EditorContext` 暴露编辑器服务、事件总线、UI 服务、语言服务、高亮服务和片段服务。
- `DynamicPluginManager` 负责扫描 jar、读取描述文件、校验依赖、反射创建插件实例、加载和卸载插件。
- `DefaultEventBus` 使用观察者模式，插件可以订阅 `TextChangedEvent`、`FileOpenedEvent`、`FileSavedEvent` 等事件。
- `JavaFxUiService` 允许插件添加状态栏项、菜单项、工具面板和信息弹窗。
- `LanguageService`、`HighlightingService` 和 `SnippetService` 允许插件动态注册文件类型、语法高亮和代码片段。
- 卸载插件时会调用 `onUnload()`，移除 UI 扩展，并关闭对应 `URLClassLoader`。

## VS Code 插件兼容层

`vscode-compat` 模块提供第一阶段 VS Code 插件兼容能力。当前目标是“看懂并导入一部分静态资源插件”，不是完整运行 VS Code 插件。

已支持：

- 读取 `.vsix` 中的 `extension/package.json` 或根目录 `package.json`。
- 解析 VS Code 插件基础元数据：`name`、`displayName`、`version`、`publisher`、`description`、`engines.vscode`、`main`、`browser`、`activationEvents`。
- 解析静态贡献：`contributes.commands`、`contributes.languages`、`contributes.grammars`、`contributes.snippets`、`contributes.themes`。
- 将静态贡献映射为 `VsCodeStaticContributionPlan`，供后续接入 Zero IDE 的语言、片段、主题服务。
- 安装 `.vsix` 到本地目录，并防护不安全压缩包路径。

当前限制：

- 带 `main` 或 `browser` JavaScript 入口的 VS Code 插件会被标记为 executable extension，目前只支持读取它的静态贡献。
- 暂未实现 Node.js Extension Host，也未实现 `vscode` JavaScript API shim。
- 暂未把 TextMate grammar、snippet 和 theme 直接渲染到编辑器 UI，当前只完成解析和映射层。

后续可以在此基础上继续接入：

- `LanguageService` / `SnippetService` / `ThemeService` 注册表。
- 插件管理 UI，展示 VS Code 插件兼容状态。
- 实验性 Node.js extension host，用于支持少量简单 JS 插件。

## 测试

```bash
.\gradlew.bat test
```

## 后续扩展方向

- 增加更多语言插件，例如 JSON、Python、YAML。
- 增加插件权限模型，限制不可信插件访问核心资源。
- 增加插件依赖版本范围和冲突检测。
- 增加代码补全插件、宏录制插件。
- 使用 `jlink` 或 `jpackage` 打包桌面应用。
