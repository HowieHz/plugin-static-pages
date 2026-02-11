<script lang="ts" setup>
import { staticPageConsoleApiClient } from '@/api';
import type { ProjectVersion } from '@/api/generated';
import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query';
import { useRoute } from 'vue-router';
import { VButton, VLoading, VEmpty, VSpace, VTag, Dialog, Toast } from '@halo-dev/components';

const route = useRoute();
const queryClient = useQueryClient();
const projectName = route.params.name as string;

// Query versions
const { data: versions, isLoading } = useQuery<ProjectVersion[]>({
  queryKey: ['plugin-static-pages:versions', projectName],
  queryFn: async () => {
    const { data } = await staticPageConsoleApiClient.project.listProjectVersions({
      name: projectName,
    });
    return data;
  },
});

// Activate version mutation
const activateVersionMutation = useMutation({
  mutationFn: async (versionName: string) => {
    await staticPageConsoleApiClient.project.activateProjectVersion({
      name: projectName,
      versionName,
    });
  },
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ['plugin-static-pages:versions', projectName] });
    Toast.success('版本已激活');
  },
  onError: (error: any) => {
    Toast.error(error.message || '激活失败');
  },
});

// Delete version mutation
const deleteVersionMutation = useMutation({
  mutationFn: async (versionName: string) => {
    await staticPageConsoleApiClient.project.deleteProjectVersion({
      name: projectName,
      versionName,
    });
  },
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ['plugin-static-pages:versions', projectName] });
    Toast.success('版本已删除');
  },
  onError: (error: any) => {
    Toast.error(error.message || '删除失败');
  },
});

function handleActivate(version: ProjectVersion) {
  Dialog.warning({
    title: '确认激活版本',
    description: `确定要激活版本 ${version.spec.displayName} 吗？当前网站内容将被替换为该版本。`,
    confirmText: '确认',
    cancelText: '取消',
    onConfirm: () => {
      activateVersionMutation.mutate(version.metadata.name);
    },
  });
}

function handleDelete(version: ProjectVersion) {
  if (version.spec.active) {
    Dialog.warning({
      title: '无法删除',
      description: '无法删除活动版本，请先激活其他版本。',
    });
    return;
  }

  Dialog.warning({
    title: '确认删除版本',
    description: `确定要删除版本 ${version.spec.displayName} 吗？此操作不可恢复。`,
    confirmText: '删除',
    cancelText: '取消',
    type: 'danger',
    onConfirm: () => {
      deleteVersionMutation.mutate(version.metadata.name);
    },
  });
}

function formatDate(dateString?: string) {
  if (!dateString) return '-';
  return new Date(dateString).toLocaleString('zh-CN');
}

function formatSize(bytes?: number) {
  if (!bytes || bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i];
}
</script>

<template>
  <VLoading v-if="isLoading" />

  <VEmpty
    v-else-if="!versions || versions.length === 0"
    message="暂无版本"
    description="上传文件后将自动创建版本"
  />

  <div v-else class=":uno: p-4">
    <div
      v-for="version in versions"
      :key="version.metadata.name"
      class=":uno: border border-gray-200 rounded-lg p-4 mb-4"
    >
      <div class=":uno: flex justify-between items-center mb-2">
        <div class=":uno: flex items-center gap-2">
          <h3 class=":uno: m-0 text-lg font-semibold">
            {{ version.spec.displayName || `版本 ${version.spec.version}` }}
          </h3>
          <VTag v-if="version.spec.active" theme="success">当前版本</VTag>
        </div>
        <VSpace>
          <VButton
            v-if="!version.spec.active"
            size="sm"
            @click="handleActivate(version)"
            :loading="activateVersionMutation.isPending"
          >
            激活
          </VButton>
          <VButton
            v-if="!version.spec.active"
            size="sm"
            type="danger"
            @click="handleDelete(version)"
            :loading="deleteVersionMutation.isPending"
          >
            删除
          </VButton>
        </VSpace>
      </div>

      <div class=":uno: text-gray-600 text-sm space-y-1">
        <div>创建于 {{ formatDate(version.spec.creationTime) }}</div>
        <div v-if="version.spec.size">大小: {{ formatSize(version.spec.size) }}</div>
        <div v-if="version.spec.description" class=":uno: text-gray-500">
          {{ version.spec.description }}
        </div>
      </div>
    </div>
  </div>
</template>
