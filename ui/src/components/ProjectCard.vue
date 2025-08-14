<script lang="ts" setup>
import { ProjectStatusPhaseEnum, type Project } from '@/api/generated';
import { VAvatar, VButton, VSpace, VStatusDot } from '@halo-dev/components';
import { computed, ref } from 'vue';
import ProjectEditModal from './ProjectEditModal.vue';

const props = withDefaults(
  defineProps<{
    project: Project;
  }>(),
  {}
);

function handleOpen() {
  window.open(`/${props.project.spec.directory}`, '_blank');
}

const editModalVisible = ref(false);

const errorMessage = computed(() => {
  if (props.project.status?.phase === ProjectStatusPhaseEnum.Ready) {
    return;
  }

  const condition = props.project.status?.conditions?.[0];

  if (!condition) {
    return;
  }

  return condition.message;
});
</script>

<template>
  <ProjectEditModal v-if="editModalVisible" :project="project" @close="editModalVisible = false" />

  <div
    class=":uno: group flex flex-col cursor-pointer rounded-lg bg-white px-4 py-3 shadow transition-all space-y-4 hover:ring-1"
  >
    <div class=":uno: flex flex-none items-center gap-4">
      <VAvatar :src="project.spec.icon" :alt="project.spec.title" size="xs" />

      <RouterLink
        :to="{
          name: 'StaticPageProjectDetail',
          params: { name: project.metadata.name },
        }"
        class=":uno: line-clamp-1 text-base font-semibold hover:text-gray-600 hover:underline"
      >
        {{ project.spec.title }}
      </RouterLink>

      <VStatusDot v-if="!!project.metadata.deletionTimestamp" animate state="warning" />

      <VStatusDot v-if="errorMessage" v-tooltip="errorMessage" animate state="warning" />
    </div>
    <ul class=":uno: flex-1 text-sm text-gray-600 space-y-2">
      <li>{{ project.spec.description }}</li>
      <li class=":uno: line-clamp-1">/{{ project.spec.directory }}</li>
    </ul>
    <div class=":uno: flex flex-none justify-end">
      <VSpace>
        <VButton
          size="sm"
          @click="
            $router.push({
              name: 'StaticPageProjectDetail',
              params: { name: project.metadata.name },
            })
          "
        >
          详情
        </VButton>
        <VButton size="sm" @click="handleOpen">访问</VButton>
        <VButton size="sm" @click="editModalVisible = true">设置</VButton>
      </VSpace>
    </div>
  </div>
</template>
