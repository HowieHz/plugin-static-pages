<script lang="ts" setup>
import * as monaco from 'monaco-editor';
import { computed, onUnmounted, ref, shallowRef, watch, watchEffect } from 'vue';

const SUPPORTED_LANGUAGES: Record<string, string> = {
  md: 'markdown',
  html: 'html',
  htm: 'html',
  css: 'css',
  js: 'javascript',
  json: 'json',
  xml: 'xml',
};

const props = withDefaults(defineProps<{ path: string }>(), {});
const modelValue = defineModel({ type: String, default: '' });

const editorRef = ref<HTMLElement | null>(null);
const editor = shallowRef<monaco.editor.IStandaloneCodeEditor | null>(null);

const currentLanguage = computed(() => {
  const ext = props.path.split('.').pop();
  if (!ext) {
    return 'plaintext';
  }
  return SUPPORTED_LANGUAGES[ext] ?? 'plaintext';
});

watchEffect(() => {
  if (editorRef.value && !editor.value) {
    editor.value = monaco.editor.create(editorRef.value, {
      value: modelValue.value,
      automaticLayout: true,
      language: currentLanguage.value,
      mouseWheelZoom: true,
    });
    editor.value.setValue(modelValue.value);
    editor.value.onDidChangeModelContent(() => {
      const value = editor.value?.getValue() || '';
      if (value !== modelValue.value) {
        modelValue.value = value;
      }
    });
  }
});

watch(
  () => currentLanguage.value,
  (newLang) => {
    if (editor.value) {
      monaco.editor.setModelLanguage(editor.value.getModel()!, newLang);
    }
  },
  { immediate: true }
);

watch(
  () => modelValue.value,
  (newVal) => {
    if (editor.value && editor.value.getValue() !== newVal) {
      editor.value.setValue(newVal);
    }
  },
  {
    immediate: true,
  }
);

onUnmounted(() => {
  editor.value?.dispose();
  editor.value = null;
});
</script>

<template>
  <div ref="editorRef" style="height: 100%"></div>
</template>
