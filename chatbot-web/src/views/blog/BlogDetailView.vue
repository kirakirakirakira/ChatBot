<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { deleteBlogPost, getBlogPost } from '@/api/blog'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import MarkdownContent from '@/components/MarkdownContent.vue'
import { currentUser } from '@/auth'
import { formatDateTime } from '@/lib/format'
import type { BlogPost } from '@/types'

/**
 * 博客详情（/blog/:id）。
 * 正文一律交给 MarkdownContent：它内部是 markdown-it（html:false）+ DOMPurify，
 * 用户输入的 HTML / javascript: 链接不会变成可执行内容。这里**不要**自己写 v-html。
 */
const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)

const post = ref<BlogPost | null>(null)
const loading = ref(true)
const error = ref('')
const busy = ref(false)
const showDelete = ref(false)

const isMine = computed(() => post.value !== null && post.value.authorId === currentUser.value?.id)
const isPublished = computed(() => post.value?.status === 'PUBLISHED')

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    post.value = await getBlogPost(id)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})

async function confirmDelete(): Promise<void> {
  if (post.value === null || busy.value) {
    return
  }
  busy.value = true
  error.value = ''
  try {
    await deleteBlogPost(post.value.id)
    showDelete.value = false
    await router.push('/blog')
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}

function back(): void {
  // 固定回博客列表而不是 router.back()：新标签页直接打开时历史里没有「上一页」，
  // 从外站跳进来时 back() 还会离开本站。用户点「返回博客」要的就是列表。
  void router.push('/blog')
}
</script>

<template>
  <div class="detail-page">
    <div class="detail-inner">
      <button class="back-link" type="button" @click="back">← 返回博客</button>

      <div v-if="error" class="alert-error">{{ error }}</div>
      <div v-if="loading" class="detail-loading">加载中…</div>

      <article v-else-if="post" class="post">
        <div v-if="!isPublished" class="draft-banner">
          {{ post.statusLabel }} · 仅自己可见
        </div>

        <h1 class="post-title">{{ post.title }}</h1>

        <div class="post-meta">
          <span class="author">{{ post.authorName }}</span>
          <span class="dot">·</span>
          <span>{{ formatDateTime(post.publishedAt ?? post.createdAt) }}</span>
          <span v-if="post.updatedAt !== post.createdAt" class="dot">·</span>
          <span v-if="post.updatedAt !== post.createdAt">编辑于 {{ formatDateTime(post.updatedAt) }}</span>
          <span class="spacer"></span>
          <div v-if="isMine" class="post-actions">
            <RouterLink class="btn-ghost mini" :to="`/blog/${post.id}/edit`">编辑</RouterLink>
            <button class="btn-ghost mini danger" type="button" @click="showDelete = true">删除</button>
          </div>
        </div>

        <hr class="rule" />
        <MarkdownContent class="post-content" :content="post.content" />
      </article>
    </div>

    <ConfirmDialog
      v-if="showDelete && post"
      title="删除文章"
      :text="'将永久删除「' + post.title + '」，无法恢复。'"
      confirm-label="永久删除"
      danger
      :busy="busy"
      @close="showDelete = false"
      @confirm="confirmDelete"
    />
  </div>
</template>

<style scoped>
.detail-page {
  height: 100%;
  overflow-y: auto;
  padding: 28px 32px 56px;
}

.detail-inner {
  max-width: 760px;
  margin: 0 auto;
}

.back-link {
  margin-bottom: 18px;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-size: 13px;
  cursor: pointer;
}

.back-link:hover {
  color: var(--accent);
}

.detail-loading {
  padding: 44px 0;
  text-align: center;
  color: var(--text-muted);
  font-size: 14px;
}

.draft-banner {
  margin-bottom: 16px;
  padding: 9px 12px;
  border-radius: var(--radius-m);
  background: color-mix(in srgb, #b7791f 10%, transparent);
  border: 1px solid color-mix(in srgb, #b7791f 30%, transparent);
  color: #8a5a12;
  font-size: 12.5px;
}

.post-title {
  margin: 0 0 12px;
  font-size: 28px;
  line-height: 1.35;
}

.post-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  color: var(--text-muted);
  font-size: 12.5px;
}

.author {
  color: var(--text);
  font-weight: 600;
}

.dot {
  opacity: 0.6;
}

.spacer {
  flex: 1;
}

.post-actions {
  display: flex;
  gap: 4px;
}

.rule {
  margin: 18px 0 22px;
  border: none;
  border-top: 1px solid var(--border);
}

.post-content {
  font-size: 15px;
  line-height: 1.8;
}

.mini {
  padding: 3px 9px;
  font-size: 12px;
}

.danger,
.danger:hover:not(:disabled) {
  color: var(--danger);
  border-color: color-mix(in srgb, var(--danger) 35%, transparent);
}
</style>