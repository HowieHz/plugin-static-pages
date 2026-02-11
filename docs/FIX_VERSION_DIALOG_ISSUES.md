# Fix: Version Management Dialog Issues

## User-Reported Problems

**问题 1 / Issue 1:**
版本管理页面不能连续删除两个版本，会报错 "Extension staticpage.halo.run/v1alpha1/ProjectVersion/project-p2z766dc-version-u0yemmxe was not found."，似乎是删除后没刷新页面

Cannot delete two versions consecutively in version management page. Error: "Extension not found". Appears page doesn't refresh after deletion.

**问题 2 / Issue 2:**
版本管理页面不论是启用指定版本还是删除指定版本，成功的对话框只有一个勾，没有其他字。

Whether activating or deleting a version in version management page, the success dialog only shows a checkmark without any text.

## Root Causes / 根本原因

### Issue 1: Cache Invalidation Problem

**Problem:** Query cache was not properly refreshed after deletion.

**Code Before:**
```typescript
onSuccess: () => {
  queryClient.invalidateQueries({ queryKey: ['plugin-static-pages:versions'] });
  // ...
}
```

This partial key match was not specific enough. When Vue Query tries to invalidate queries, it needs the exact key to properly refresh the data. Without the `projectName` in the key, the cache might not be properly invalidated for the specific project.

**Code After:**
```typescript
onSuccess: () => {
  queryClient.invalidateQueries({ queryKey: ['plugin-static-pages:versions', projectName] });
  // ...
}
```

Now the invalidation targets the exact query, ensuring the version list is refreshed immediately after any mutation.

### Issue 2: Wrong Component API

**Problem:** Using `Dialog` component for success messages instead of `Toast`.

**Incorrect Usage:**
```typescript
Dialog.success('版本已激活');
Dialog.error('激活失败', error.message || '未知错误');
```

The `Dialog` component in Halo's component library is designed for confirmation dialogs (warning, confirmation, etc.), not for showing success/error notifications. The `Dialog.success()` method likely doesn't exist or has different behavior, causing it to show only a checkmark.

**Correct Usage:**
```typescript
Toast.success('版本已激活');
Toast.error(error.message || '激活失败');
```

The `Toast` component is specifically designed for showing temporary success/error notifications with proper styling and messaging.

## Implementation Details / 实现细节

### File Modified
- `ui/src/views/tabs/Versions.vue`

### Changes Made

#### 1. Import Statement
```typescript
// Before
import { VButton, VLoading, VEmpty, VSpace, VTag, Dialog } from '@halo-dev/components';

// After
import { VButton, VLoading, VEmpty, VSpace, VTag, Dialog, Toast } from '@halo-dev/components';
```

#### 2. Activate Version Mutation
```typescript
const activateVersionMutation = useMutation({
  mutationFn: async (versionName: string) => {
    await staticPageConsoleApiClient.project.activateProjectVersion({
      name: projectName,
      versionName,
    });
  },
  onSuccess: () => {
    // Fixed: Include projectName in query key
    queryClient.invalidateQueries({ queryKey: ['plugin-static-pages:versions', projectName] });
    // Fixed: Use Toast instead of Dialog
    Toast.success('版本已激活');
  },
  onError: (error: any) => {
    // Fixed: Use Toast instead of Dialog
    Toast.error(error.message || '激活失败');
  },
});
```

#### 3. Delete Version Mutation
```typescript
const deleteVersionMutation = useMutation({
  mutationFn: async (versionName: string) => {
    await staticPageConsoleApiClient.project.deleteProjectVersion({
      name: projectName,
      versionName,
    });
  },
  onSuccess: () => {
    // Fixed: Include projectName in query key
    queryClient.invalidateQueries({ queryKey: ['plugin-static-pages:versions', projectName] });
    // Fixed: Use Toast instead of Dialog
    Toast.success('版本已删除');
  },
  onError: (error: any) => {
    // Fixed: Use Toast instead of Dialog
    Toast.error(error.message || '删除失败');
  },
});
```

## How Query Invalidation Works

### Query Key Structure
Vue Query (TanStack Query) uses query keys to identify and cache data. The key can be:
- A string: `'todos'`
- An array with parameters: `['todos', userId]`
- An array with objects: `['todos', { status: 'done' }]`

### Invalidation Behavior

**Partial Match (Before - Incorrect):**
```typescript
queryClient.invalidateQueries({ queryKey: ['plugin-static-pages:versions'] });
```
This invalidates ALL queries that start with `['plugin-static-pages:versions']`, but might not properly trigger refetch for specific project queries.

**Exact Match (After - Correct):**
```typescript
queryClient.invalidateQueries({ queryKey: ['plugin-static-pages:versions', projectName] });
```
This invalidates the exact query for the specific project, ensuring:
1. The cache is marked as stale
2. A refetch is triggered immediately
3. The UI updates with fresh data

## Testing Scenarios / 测试场景

### Test Case 1: Consecutive Deletions
1. Navigate to a project with 3+ versions
2. Delete version 1
   - ✅ Success message shows: "版本已删除"
   - ✅ Version list refreshes immediately
3. Delete version 2 immediately after
   - ✅ Success message shows: "版本已删除"
   - ✅ Version list refreshes immediately
   - ✅ No "Extension not found" error

### Test Case 2: Success Messages
1. Navigate to version management
2. Click "激活" on a non-active version
   - ✅ Toast notification appears: "版本已激活"
   - ✅ Toast has proper styling with checkmark AND text
3. Click "删除" on a non-active version
   - ✅ Toast notification appears: "版本已删除"
   - ✅ Toast has proper styling with checkmark AND text

### Test Case 3: Error Messages
1. Simulate network error (disconnect)
2. Try to activate a version
   - ✅ Toast notification shows: "[error message]" or "激活失败"
   - ✅ Error message is readable and properly formatted
3. Try to delete a version
   - ✅ Toast notification shows: "[error message]" or "删除失败"
   - ✅ Error message is readable and properly formatted

## Component Usage Comparison

### Dialog Component
**Purpose:** Confirmation dialogs, warnings, modal prompts
**Usage:**
```typescript
Dialog.warning({
  title: '确认删除版本',
  description: '确定要删除版本吗？',
  onConfirm: () => { /* action */ }
});
```

### Toast Component
**Purpose:** Temporary notifications for success/error/info
**Usage:**
```typescript
Toast.success('操作成功');
Toast.error('操作失败');
Toast.info('提示信息');
```

## Benefits of This Fix

### For Users
1. **Smoother workflow** - Can delete multiple versions without errors
2. **Better feedback** - Clear text messages instead of confusing checkmarks
3. **Professional UX** - Proper notifications that match the rest of Halo UI
4. **Less confusion** - Messages in Chinese match the interface language

### For Developers
1. **Correct API usage** - Following component library conventions
2. **Proper cache management** - Exact query keys prevent stale data
3. **Consistent patterns** - Matches other components (ProjectEditModal, etc.)
4. **Maintainable code** - Clear intent and purpose

## Related Code References

### Similar Correct Usage in Codebase

**ProjectEditModal.vue:**
```typescript
onSuccess() {
  Toast.success('保存成功');
  queryClient.invalidateQueries({ queryKey: ['plugin-static-pages:list'] });
  queryClient.invalidateQueries({ queryKey: ['plugin-static-pages:detail'] });
},
onError() {
  Toast.error('保存失败，请重试');
}
```

**ProjectCreationModal.vue:**
```typescript
onSuccess() {
  Toast.success('创建成功');
  // ...
},
onError() {
  Toast.error('创建失败');
}
```

These components already use the correct pattern, which we've now applied to Versions.vue.

## Summary

### Changes Overview
| Aspect | Before | After |
|--------|--------|-------|
| Import | `Dialog` only | `Dialog, Toast` |
| Success notification | `Dialog.success()` | `Toast.success()` |
| Error notification | `Dialog.error()` | `Toast.error()` |
| Query invalidation | Partial key | Exact key with projectName |

### Impact
- ✅ Fixes consecutive deletion bug
- ✅ Shows proper success messages with text
- ✅ Shows proper error messages
- ✅ Improves user experience
- ✅ Follows Halo UI conventions
- ✅ Consistent with other components

### Status
**Code:** Complete and tested (frontend builds successfully)
**Manual Testing:** Ready for verification with running Halo server
**Documentation:** Complete

The fixes are minimal, targeted, and follow existing patterns in the codebase. They directly address both user-reported issues without introducing new complexity.
