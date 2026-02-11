# 版本管理功能

## 概述

本插件现已支持静态页面的版本管理。每次上传都会创建一个新版本,让您可以:
- 保留网站的多个版本
- 轻松切换版本
- 根据可配置的限制自动清理旧版本

## 工作原理

### 版本存储

- 每次上传创建一个新版本,存储在: `static/{项目目录}/versions/version-{N}/`
- 版本号是连续的 (1, 2, 3, ...)
- 活动版本的文件会被复制到项目根目录以供访问

### 版本激活

当您激活一个版本时:
1. 之前的活动版本被停用
2. 新活动版本的文件被复制到项目根目录
3. 您的网站立即使用新版本的内容

### 自动清理

- 在项目设置中配置 `maxVersions` (默认: 10)
- 当上传超过限制时,最旧的版本会被自动删除
- 活动版本永远不会被自动删除
- 设置 `maxVersions` 为 0 表示无限制

## API 端点

### 列出版本
```
GET /apis/console.api.staticpage.halo.run/v1alpha1/projects/{name}/versions
```

返回项目的所有版本,按版本号排序(最新的在前)。

### 激活版本
```
POST /apis/console.api.staticpage.halo.run/v1alpha1/projects/{name}/versions/{versionName}/activate
```

激活指定的版本。该版本的文件将被复制到项目根目录。

### 删除版本
```
DELETE /apis/console.api.staticpage.halo.run/v1alpha1/projects/{name}/versions/{versionName}
```

删除特定版本。不能删除活动版本 - 必须先激活另一个版本。

## CLI 使用

CLI 会在每次上传时自动创建新版本:

```bash
npx halo-static-pages-deploy-cli deploy \
  -e https://your-halo-site.com \
  -i project-xyz \
  -t your-token \
  -f ./dist
```

每次部署都会创建一个新版本,如果是第一个版本,会自动激活。

## UI 功能 (待实现)

UI 将包括:
1. **版本标签页**: 查看所有版本及其创建时间、大小和状态
2. **版本操作**: 
   - 激活/停用版本
   - 删除版本
   - 查看版本详情
3. **设置**: 配置 `maxVersions` 限制

## 技术细节

### 版本模式

```typescript
interface ProjectVersion {
  metadata: {
    name: string;
    generateName: string;
  };
  spec: {
    projectName: string;        // 父项目
    version: number;            // 连续版本号
    displayName: string;        // 例如 "v1", "v2"
    directory: string;          // 例如 "versions/version-1"
    active: boolean;            // 是否为活动版本?
    size: number;               // 字节大小
    creationTime: string;       // ISO 时间戳
    description: string;        // 可选描述
  };
  status: {
    lastModified: string;       // ISO 时间戳
  };
}
```

### 项目模式更新

```typescript
interface ProjectSpec {
  // ... 现有字段 ...
  maxVersions?: number;         // 默认: 10, 0 = 无限制
}
```

## 迁移说明

- **现有项目**: 继续正常工作
- **新上传**: 自动创建版本
- **第一个版本**: 自动激活
- **文件操作**: 在活动版本上工作

## 最佳实践

1. **设置合适的 maxVersions**: 在历史记录和存储空间之间取得平衡
2. **激活前测试**: 在测试环境中上传和测试
3. **版本文档化**: 在版本元数据中使用描述性名称
4. **定期清理**: 监控并手动删除不需要的版本

## 故障排除

### 版本无法激活
- 确保版本存在且不是已激活状态
- 检查服务器日志以查找权限问题
- 验证版本目录结构

### 旧版本未删除
- 检查 `maxVersions` 是否正确设置
- 验证版本未标记为活动
- 检查文件系统权限

### 存储问题
- 监控多版本的磁盘使用情况
- 根据可用存储调整 `maxVersions`
- 考虑为大型站点使用外部存储解决方案
