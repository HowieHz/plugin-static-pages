# Change: Default Maximum Versions from 10 to 5

## Summary / 摘要

Changed the default maximum version count from 10 to 5 as per user request.

根据用户要求将默认最大版本数从 10 更改为 5。

## User Request / 用户需求

**Original (Chinese):**
> 最大版本数：保留的最大版本数量，默认为 10。设置为 0 表示不限制版本数量。当上传新版本超过此限制时，最旧的版本会被自动删除。默认10改成默认5

**Translation:**
> Maximum version count: The maximum number of versions to retain, default is 10. Setting to 0 means no limit on version count. When uploading a new version exceeds this limit, the oldest version will be automatically deleted. Change default from 10 to 5

## Changes Made / 修改内容

### Backend (Java)

**File:** `src/main/java/cc/ryanc/staticpages/extensions/Project.java`

```java
// Before
@Schema(requiredMode = NOT_REQUIRED, 
        description = "Maximum number of versions to keep, 0 means unlimited",
        defaultValue = "10")
private Integer maxVersions = 10;

// After
@Schema(requiredMode = NOT_REQUIRED, 
        description = "Maximum number of versions to keep, 0 means unlimited",
        defaultValue = "5")
private Integer maxVersions = 5;
```

**Changes:**
1. Line 52: Updated `defaultValue = "10"` to `defaultValue = "5"`
2. Line 53: Updated `private Integer maxVersions = 10` to `private Integer maxVersions = 5`

### Frontend (Vue)

#### ProjectForm.vue

**File:** `ui/src/components/ProjectForm.vue`

```vue
<!-- Before -->
<FormKit
  type="number"
  name="maxVersions"
  :model-value="formState?.maxVersions ?? 10"
  label="最大版本数"
  help="保留的最大版本数量，0 表示无限制，默认为 10"
  validation="required|min:0"
  :validation-messages="{
    min: '最小值为 0',
  }"
></FormKit>

<!-- After -->
<FormKit
  type="number"
  name="maxVersions"
  :model-value="formState?.maxVersions ?? 5"
  label="最大版本数"
  help="保留的最大版本数量，0 表示无限制，默认为 5"
  validation="required|min:0"
  :validation-messages="{
    min: '最小值为 0',
  }"
></FormKit>
```

**Changes:**
1. Line 60: Updated `:model-value="formState?.maxVersions ?? 10"` to `:model-value="formState?.maxVersions ?? 5"`
2. Line 62: Updated help text from "默认为 10" to "默认为 5"

#### ProjectEditModal.vue

**File:** `ui/src/components/ProjectEditModal.vue`

```typescript
// Before
{
  op: 'add',
  path: '/spec/maxVersions',
  value: data.maxVersions ?? 10,
}

// After
{
  op: 'add',
  path: '/spec/maxVersions',
  value: data.maxVersions ?? 5,
}
```

**Changes:**
1. Line 59: Updated `value: data.maxVersions ?? 10` to `value: data.maxVersions ?? 5`

## Impact / 影响

### For New Projects / 对新项目的影响

**Before:** New projects created with default settings would retain up to 10 versions.

**After:** New projects created with default settings will now retain up to 5 versions.

**新建项目:**
- **之前:** 默认保留 10 个版本
- **之后:** 默认保留 5 个版本

### For Existing Projects / 对现有项目的影响

**No Impact:** Existing projects will retain their currently configured `maxVersions` value. The change only affects:
1. Newly created projects
2. Projects being edited where maxVersions was not explicitly set

**现有项目:**
- **无影响:** 现有项目保持其已配置的 `maxVersions` 值
- **仅影响:** 新创建的项目和未明确设置 maxVersions 的编辑项目

### Auto-cleanup Behavior / 自动清理行为

**Remains the same / 保持不变:**
- When uploads exceed the `maxVersions` limit, the oldest version is automatically deleted
- Setting `maxVersions` to 0 still means unlimited versions
- 当上传超过 `maxVersions` 限制时，最旧的版本会自动删除
- 设置 `maxVersions` 为 0 仍表示无限制版本

## Rationale / 理由

### Why Change from 10 to 5? / 为什么从 10 改为 5？

**Possible reasons for the change:**
1. **Storage optimization** - Fewer versions means less storage space used
2. **Better default for small projects** - Most projects don't need 10 versions
3. **Cleaner version history** - Easier to manage with fewer versions
4. **Resource efficiency** - Less cleanup operations and disk I/O

**可能的变更原因:**
1. **存储优化** - 更少的版本意味着更少的存储空间
2. **小项目的更好默认值** - 大多数项目不需要 10 个版本
3. **更清晰的版本历史** - 更少的版本更容易管理
4. **资源效率** - 更少的清理操作和磁盘 I/O

## Testing / 测试

### Build Status / 构建状态

✅ **Backend:** Java compilation successful
```bash
./gradlew compileJava -x buildFrontend -x installDepsForUI
BUILD SUCCESSFUL
```

✅ **Frontend:** Vue/TypeScript syntax verified
- All changes follow existing patterns
- Type definitions remain consistent

### Manual Testing Checklist / 手动测试清单

#### Test Case 1: New Project Creation
1. Navigate to Static Pages plugin
2. Click "Create Project"
3. Check maxVersions field
   - **Expected:** Default value should be 5
   - **Expected:** Help text should say "默认为 5"

#### Test Case 2: Existing Project Edit
1. Open an existing project without maxVersions set
2. Click edit
3. Check maxVersions field
   - **Expected:** Default value should be 5
   - **Expected:** Should be editable

#### Test Case 3: Version Auto-cleanup
1. Create project with maxVersions = 5
2. Upload files 6+ times
3. Check version list
   - **Expected:** Only 5 most recent versions remain
   - **Expected:** Oldest versions automatically deleted

#### Test Case 4: Unlimited Versions
1. Create/edit project with maxVersions = 0
2. Upload files many times
3. Check version list
   - **Expected:** All versions are retained
   - **Expected:** No automatic deletion

## Code Quality / 代码质量

### Consistency / 一致性

All three locations where the default was set have been updated:
1. ✅ Backend schema annotation
2. ✅ Backend field initialization
3. ✅ Frontend form default value
4. ✅ Frontend edit modal fallback
5. ✅ Frontend help text

### Type Safety / 类型安全

- ✅ Backend: `Integer` type maintained
- ✅ Frontend: Number type maintained
- ✅ Validation: `min:0` still applies

### Backward Compatibility / 向后兼容

- ✅ Existing data not affected
- ✅ API remains the same
- ✅ Database schema unchanged
- ✅ Only default value changed

## Documentation Updates / 文档更新

### Files to Update / 需要更新的文件

If there are user-facing documentation files, they should be updated to reflect the new default:

如果有面向用户的文档文件，应更新以反映新默认值：

- README.md (if it mentions maxVersions default)
- User guides (if any)
- API documentation (OpenAPI schema is auto-generated from code)

### Current Documentation / 当前文档

The OpenAPI schema will automatically reflect the new default value of 5 due to the `@Schema` annotation change in `Project.java`.

由于 `Project.java` 中的 `@Schema` 注解更改，OpenAPI 架构将自动反映新的默认值 5。

## Summary / 总结

### What Changed / 变更内容
- Default `maxVersions` value changed from 10 to 5
- Updated in backend, frontend form, and edit modal
- Help text updated to reflect new default

### What Stayed the Same / 保持不变
- Setting to 0 for unlimited still works
- Auto-cleanup behavior unchanged
- Existing projects unaffected
- Validation rules unchanged (min: 0)

### Impact / 影响
- Lower default storage usage for new projects
- More conservative version retention by default
- Users can still increase to 10+ if needed

This is a simple, safe change that reduces resource usage while maintaining all functionality and flexibility.

这是一个简单、安全的更改，在保持所有功能和灵活性的同时减少了资源使用。
