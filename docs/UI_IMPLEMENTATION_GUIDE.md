# UI Implementation Guide for Version Management

This document provides guidance for implementing the version management UI in the plugin.

## Overview

The backend API for version management is complete. The UI needs to be implemented to provide a user-friendly interface for managing versions.

## Required UI Components

### 1. Versions Tab in ProjectDetail View

**Location**: `ui/src/views/ProjectDetail.vue`

Add a new tab called "版本管理" (Version Management) to the existing tabs array:

```typescript
const tabs: Tab[] = [
  {
    id: 'detail',
    label: '详情',
    component: markRaw(Detail),
  },
  {
    id: 'files',
    label: '文件管理',
    component: markRaw(Files),
  },
  {
    id: 'versions',
    label: '版本管理',
    component: markRaw(Versions), // New component
  },
];
```

### 2. Versions Component

**Location**: `ui/src/views/tabs/Versions.vue`

Create a new component that displays and manages versions:

```vue
<script lang="ts" setup>
import { staticPageApiClient } from '@/api';
import type { ProjectVersion } from '@/api/generated';
import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query';
import { useRoute } from 'vue-router';
import { VButton, VLoading, VEmpty, VSpace, VTag } from '@halo-dev/components';
import { Dialog } from '@halo-dev/components';

const route = useRoute();
const queryClient = useQueryClient();
const projectName = route.params.name as string;

// Query versions
const { data: versions, isLoading } = useQuery<ProjectVersion[]>({
  queryKey: ['plugin-static-pages:versions', projectName],
  queryFn: async () => {
    const { data } = await staticPageApiClient.project.listProjectVersions({
      name: projectName,
    });
    return data;
  },
});

// Activate version mutation
const activateVersionMutation = useMutation({
  mutationFn: async (versionName: string) => {
    await staticPageApiClient.project.activateProjectVersion({
      name: projectName,
      versionName,
    });
  },
  onSuccess: () => {
    queryClient.invalidateQueries(['plugin-static-pages:versions']);
    Dialog.success('版本已激活');
  },
});

// Delete version mutation
const deleteVersionMutation = useMutation({
  mutationFn: async (versionName: string) => {
    await staticPageApiClient.project.deleteProjectVersion({
      name: projectName,
      versionName,
    });
  },
  onSuccess: () => {
    queryClient.invalidateQueries(['plugin-static-pages:versions']);
    Dialog.success('版本已删除');
  },
});

function handleActivate(version: ProjectVersion) {
  Dialog.confirm({
    title: '确认激活版本',
    description: `确定要激活版本 ${version.spec.displayName} 吗?当前网站内容将被替换为该版本。`,
    confirmText: '确认',
    cancelText: '取消',
    onConfirm: () => {
      activateVersionMutation.mutate(version.metadata.name);
    },
  });
}

function handleDelete(version: ProjectVersion) {
  if (version.spec.active) {
    Dialog.warning('无法删除活动版本,请先激活其他版本。');
    return;
  }
  
  Dialog.confirm({
    title: '确认删除版本',
    description: `确定要删除版本 ${version.spec.displayName} 吗?此操作不可恢复。`,
    confirmText: '删除',
    cancelText: '取消',
    type: 'danger',
    onConfirm: () => {
      deleteVersionMutation.mutate(version.metadata.name);
    },
  });
}

function formatDate(dateString: string) {
  return new Date(dateString).toLocaleString('zh-CN');
}

function formatSize(bytes: number) {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
}
</script>

<template>
  <VLoading v-if="isLoading" />
  
  <VEmpty v-else-if="!versions || versions.length === 0"
    message="暂无版本"
    description="上传文件后将自动创建版本"
  />
  
  <div v-else class="version-list">
    <div v-for="version in versions" 
         :key="version.metadata.name"
         class="version-item">
      <div class="version-header">
        <div class="version-info">
          <h3>{{ version.spec.displayName }}</h3>
          <VTag v-if="version.spec.active" theme="success">当前版本</VTag>
        </div>
        <VSpace>
          <VButton v-if="!version.spec.active"
                   size="sm"
                   @click="handleActivate(version)">
            激活
          </VButton>
          <VButton v-if="!version.spec.active"
                   size="sm"
                   type="danger"
                   @click="handleDelete(version)">
            删除
          </VButton>
        </VSpace>
      </div>
      
      <div class="version-details">
        <div>创建时间: {{ formatDate(version.spec.creationTime) }}</div>
        <div v-if="version.spec.size">大小: {{ formatSize(version.spec.size) }}</div>
        <div v-if="version.spec.description">{{ version.spec.description }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.version-list {
  padding: 1rem;
}

.version-item {
  border: 1px solid #e5e7eb;
  border-radius: 0.5rem;
  padding: 1rem;
  margin-bottom: 1rem;
}

.version-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.5rem;
}

.version-info {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.version-info h3 {
  margin: 0;
  font-size: 1.125rem;
  font-weight: 600;
}

.version-details {
  color: #6b7280;
  font-size: 0.875rem;
}

.version-details > div {
  margin-bottom: 0.25rem;
}
</style>
```

### 3. Add maxVersions Setting to Project Settings

**Location**: `ui/src/components/ProjectEditModal.vue`

Add a new form field for maxVersions:

```vue
<FormKit
  type="number"
  name="maxVersions"
  label="最大版本数"
  help="保留的最大版本数量,0 表示无限制,默认为 10"
  validation="required|min:0"
/>
```

## API Client Updates

The API client needs to be regenerated after the backend changes. Run:

```bash
cd ui
pnpm install
pnpm run openapi:generate
```

This will generate the necessary TypeScript types and API client methods for version management.

## Generated API Methods

After regeneration, these methods should be available:

```typescript
// List versions
staticPageApiClient.project.listProjectVersions({
  name: string
}): Promise<ProjectVersion[]>

// Activate version
staticPageApiClient.project.activateProjectVersion({
  name: string,
  versionName: string
}): Promise<ProjectVersion>

// Delete version
staticPageApiClient.project.deleteProjectVersion({
  name: string,
  versionName: string
}): Promise<void>
```

## Testing the UI

1. Upload a file to create the first version (should auto-activate)
2. Upload again to create a second version
3. Navigate to the Versions tab
4. Test activating different versions
5. Test deleting old versions
6. Verify that the active version cannot be deleted
7. Test the maxVersions setting by uploading multiple times

## Notes

- The UI should refresh the version list after operations
- Use appropriate loading states during API calls
- Show clear error messages for failed operations
- Confirm destructive actions (delete, activate) with dialogs
- Display active version prominently with a badge/tag
- Format dates and file sizes in user-friendly way
- Consider adding pagination if many versions exist

## Future Enhancements

- Add version comparison view
- Show version diff
- Add version notes/changelog
- Export/download specific versions
- Rollback to previous version with one click
- Version search and filtering
