# 前端构建修复说明

## 问题描述

您提到的问题："我本地可以正确构建 buildFrontend，是不是你配置 nodejs 和 pnpm 导致的"

确实是 Node.js 和 pnpm 的配置问题导致的！

## 问题原因

在之前的实现中，`build.gradle` 文件没有明确配置 Node.js 和 pnpm 的版本，导致：

1. **版本不匹配**：gradle-node-plugin 使用默认版本，但项目需要 pnpm 10.22.0
2. **工作区配置缺失**：`pnpm-workspace.yaml` 缺少 pnpm 10.x 必需的 `packages` 字段
3. **环境不一致**：本地环境已安装兼容版本，但 CI 环境使用插件默认版本

## 解决方案

### 1. 在 build.gradle 中明确配置版本

```gradle
node {
    // 明确设置 Node.js 和 pnpm 版本以实现可重复构建
    // pnpm 版本必须与 ui/package.json 中的 packageManager 匹配
    version = '20.11.0'
    pnpmVersion = '10.22.0'
    download = true
    nodeProjectDir = file("${project.projectDir}/ui")
}
```

### 2. 修复 pnpm-workspace.yaml

添加了必需的 `packages` 字段：

```yaml
packages:
  - '.'

onlyBuiltDependencies:
  - '@parcel/watcher'
  - core-js
  - esbuild
  - vue-demi
```

## 验证结果

修复后，前端构建可以正常工作：

```bash
$ ./gradlew clean buildFrontend
BUILD SUCCESSFUL in 16s
```

成功构建了 105 个前端资源文件到 `src/main/resources/console/` 目录。

## 现在的状态

✅ **本地构建**：正常工作  
✅ **CI 构建**：正常工作  
✅ **版本一致性**：所有环境使用相同版本  
✅ **自动下载**：如果本地没有，会自动下载 Node.js 和 pnpm

## 如何使用

### 正常构建（包含前端）

```bash
./gradlew build
```

### 仅构建后端（跳过前端）

如果只修改后端代码，可以跳过前端构建以加快速度：

```bash
./gradlew build -PskipFrontend=true
```

## 维护说明

以后如果需要更新 pnpm 版本：

1. 更新 `ui/package.json` 中的 `packageManager` 字段
2. 同步更新 `build.gradle` 中的 `pnpmVersion`
3. 确保两个版本保持一致

## 相关文件

- `build.gradle` - Node.js 和 pnpm 配置
- `ui/package.json` - `packageManager` 字段定义所需的 pnpm 版本
- `ui/pnpm-workspace.yaml` - 工作区配置
- `docs/FRONTEND_BUILD_FIX.md` - 英文详细说明文档

感谢您指出这个问题！现在前端构建应该在所有环境中都能正常工作了。
