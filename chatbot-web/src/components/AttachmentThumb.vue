<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import type { AttachmentRef } from '@/types'
import { fetchAttachmentUrl } from '@/api'

const props = defineProps<{
  attachment: AttachmentRef
  /** 边长（px）。气泡里用小图，输入框待发送区用更小的。 */
  size?: number
}>()

/**
 * 图片地址的两种来源：
 * 1. attachment.url 已有值 —— 本地刚选的图，直接用预览地址，不必再向后端要一遍；
 *    这个 objectURL 归创建它的一方（ChatView）管，本组件刻意不 revoke，否则消息气泡里的图会当场变空白。
 * 2. 只有 id —— 历史消息，本组件自己 fetch 成 blob 再转 objectURL，卸载时自己 revoke。
 * 之所以必须走 fetch 而不能 <img src="/api/attachments/1">：img 带不了 Authorization 头，
 * 而后端刻意不做 ?token= 兜底（那等于把长期凭证写进 URL、访问日志和浏览器历史）。
 */
const url = ref(props.attachment.url ?? '')
const failed = ref(false)
const loaded = ref(false)
let ownedUrl = false

onMounted(async () => {
  if (url.value || props.attachment.id === null) {
    loaded.value = true
    return
  }
  try {
    url.value = await fetchAttachmentUrl(props.attachment.id)
    ownedUrl = true
    loaded.value = true
  } catch {
    failed.value = true
  }
})

onBeforeUnmount(() => {
  if (ownedUrl && url.value) {
    URL.revokeObjectURL(url.value)
  }
})

const px = (n: number): string => n + 'px'
</script>

<template>
  <a
    class="thumb"
    :class="{ failed }"
    :href="url || undefined"
    target="_blank"
    rel="noreferrer"
    :style="{ width: px(size ?? 132), height: px(size ?? 132) }"
    :title="failed ? '图片加载失败' : attachment.fileName"
    @click="url ? undefined : ($event.preventDefault())"
  >
    <img v-if="url" :src="url" :alt="attachment.fileName" loading="lazy" />
    <span v-else-if="failed" class="state">加载失败</span>
    <span v-else class="state">…</span>
  </a>
</template>

<style scoped>
.thumb {
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  flex-shrink: 0;
  border: 1px solid var(--border);
  border-radius: var(--radius-m);
  background: var(--panel-2);
  cursor: zoom-in;
  transition: border-color 120ms ease, transform 120ms ease;
}

.thumb:hover {
  border-color: color-mix(in srgb, var(--accent) 55%, transparent);
}

.thumb:active {
  transform: scale(0.98);
}

.thumb.failed {
  cursor: default;
  border-color: color-mix(in srgb, var(--danger) 35%, transparent);
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.state {
  font-size: 11.5px;
  color: var(--text-muted);
}
</style>
