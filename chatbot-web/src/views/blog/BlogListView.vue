<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { deleteBlogPost, listBlogPosts, listMyBlogPosts } from '@/api/blog'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import { currentUser } from '@/auth'
import { formatDateTime } from '@/lib/format'
import type { BlogPostSummary } from '@/types'

/**
 * 博客列表（/blog）——所有登录用户都能看，所有登录用户都能写。
 *
 * 「全部文章」只看已发布；「我的文章」看自己的所有状态（草稿 / 待审 / 驳回 / 下架都在这里），
 * 两个 tab 共用一套分页和卡片，避免把同一个列表画两遍。
 * 状态标签只在「我的文章」里显示：别人的未发布文章根本不会出现在列表里。
 */
const PAGE_SIZE = 10

type Tab = 'all' | 'mine'
const tab = ref<Tab>('all')
const posts = ref<BlogPostSummary[]>([])
const page = ref(0)
const total = ref(0)
const loading = ref(true)
const error = ref('')
const router = useRouter()

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const result = tab.value === 'all'
      ? await listBlogPosts({ page: page.value, size: PAGE_SIZE })
      : await listMyBlogPosts({ page: page.value, size: PAGE_SIZE })
    posts.value = result.items
    total.value = result.total
    // 删掉当前页最后一篇时退回上一页，别停在空表上
    if (posts.value.length === 0 && page.value > 0) {
      page.value -= 1
      await load()
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})

function switchTab(next: Tab): void {
  if (tab.value === next) {
    return
  }
  tab.value = next
  page.value = 0
  void load()
}

function gotoPage(target: number): void {
  if (target < 0 || target >= totalPages.value) {
    return
  }
  page.value = target
  void load()
}

function open(post: BlogPostSummary): void {
  void router.push(`/blog/${post.id}`)
}

/** 只有作者本人能看到编辑 / 删除按钮；后端仍然会再校验一次（前端判断只负责界面）。 */
function mine(post: BlogPostSummary): boolean {
  return post.authorId === currentUser.value?.id
}

const deleting = ref<BlogPostSummary | null>(null)
const busy = ref(false)

async function confirmDelete(): Promise<void> {
  const post = deleting.value
  if (post === null || busy.value) {
    return
  }
  busy.value = true
  error.value = ''
  try {
    await deleteBlogPost(post.id)
    deleting.value = null
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="blog-page">
    <header class="blog-head">
      <div>
        <h1 class="blog-title">博客</h1>
        <p class="blog-sub">共 {{ total }} 篇</p>
      </div>
      <RouterLink class="btn-primary write-link" to="/blog/new">写文章</RouterLink>
    </header>

    <div class="tabs">
      <button class="tab" :class="{ active: tab === 'all' }" type="button" @click="switchTab('all')">全部文章</button>
      <button class="tab" :class="{ active: tab === 'mine' }" type="button" @click="switchTab('mine')">我的文章</button>
    </div>

    <div v-if="error" class="alert-error">{{ error }}</div>
    <div v-if="loading" class="blog-loading">加载中…</div>

    <div v-else-if="posts.length === 0" class="empty">
      {{ tab === 'all' ? '还没有文章。' : '你还没有写过文章。' }}
    </div>

    <div v-else class="post-list">
      <article v-for="post in posts" :key="post.id" class="post-card" @click="open(post)">
        <div class="post-top">
          <h2 class="post-heading">{{ post.title }}</h2>
          <span v-if="tab === 'mine'" class="status-tag" :class="post.status.toLowerCase()">{{ post.statusLabel }}</span>
        </div>
        <p class="post-summary">{{ post.summary || '暂无摘要' }}</p>
        <div class="post-meta">
          <span class="author">{{ post.authorName }}</span>
          <span class="dot">·</span>
          <span>{{ formatDateTime(post.publishedAt ?? post.createdAt) }}</span>
          <!-- 未发布的文章只有作者能看，卡片上必须说清楚，否则会误以为「已经发出去了」 -->
          <span v-if="tab === 'mine' && post.status !== 'PUBLISHED'" class="private-hint">仅自己可见</span>
          <span class="spacer"></span>
          <div v-if="tab === 'mine' && mine(post)" class="post-actions" @click.stop>
            <RouterLink class="btn-ghost mini" :to="`/blog/${post.id}/edit`">编辑</RouterLink>
            <button class="btn-ghost mini danger" type="button" @click="deleting = post">删除</button>
          </div>
        </div>
      </article>
    </div>

    <footer class="blog-pager">
      <button class="btn-ghost mini" type="button" :disabled="page === 0 || loading" @click="gotoPage(page - 1)">上一页</button>
      <span class="pager-text">第 {{ page + 1 }} / {{ totalPages }} 页</span>
      <button class="btn-ghost mini" type="button" :disabled="page + 1 >= totalPages || loading" @click="gotoPage(page + 1)">下一页</button>
    </footer>

    <ConfirmDialog
      v-if="deleting"
      title="删除文章"
      :text="'将永久删除「' + deleting.title + '」，无法恢复。'"
      confirm-label="永久删除"
      danger
      :busy="busy"
      @close="deleting = null"
      @confirm="confirmDelete"
    />
  </div>
</template>

<style scoped>
.blog-page {
  height: 100%;
  overflow-y: auto;
  padding: 28px 32px 40px;
}

.blog-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  max-width: 860px;
  margin: 0 auto 16px;
}

.blog-title {
  margin: 0;
  font-size: 20px;
}

.blog-sub {
  margin: 2px 0 0;
  font-size: 12.5px;
  color: var(--text-muted);
}

.write-link {
  text-decoration: none;
  white-space: nowrap;
}

.tabs {
  display: flex;
  gap: 4px;
  max-width: 860px;
  margin: 0 auto 14px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border);
}

.tab {
  padding: 6px 12px;
  border: none;
  border-radius: var(--radius-s);
  background: transparent;
  color: var(--text-muted);
  font-size: 13.5px;
  cursor: pointer;
}

.tab:hover {
  color: var(--text);
  background: color-mix(in srgb, var(--text) 5%, transparent);
}

.tab.active {
  color: var(--accent);
  background: var(--accent-soft);
  font-weight: 600;
}

.blog-loading,
.empty {
  max-width: 860px;
  margin: 0 auto;
  padding: 44px 0;
  text-align: center;
  color: var(--text-muted);
  font-size: 14px;
}

.post-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-width: 860px;
  margin: 0 auto;
}

.post-card {
  padding: 16px 18px;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: var(--radius-m);
  cursor: pointer;
  transition: border-color 120ms ease, transform 120ms ease;
}

.post-card:hover {
  border-color: color-mix(in srgb, var(--accent) 40%, var(--border));
  transform: translateY(-1px);
}

.post-top {
  display: flex;
  align-items: center;
  gap: 10px;
}

.post-heading {
  flex: 1;
  min-width: 0;
  margin: 0;
  font-size: 16px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.post-summary {
  margin: 7px 0 10px;
  color: var(--text-muted);
  font-size: 13.5px;
  line-height: 1.65;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.post-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-muted);
  font-size: 12.5px;
}

.author {
  color: var(--text);
  font-weight: 550;
}

.dot {
  opacity: 0.6;
}

.private-hint {
  padding: 0 7px;
  border-radius: var(--radius-pill);
  background: var(--panel-2);
  border: 1px solid var(--border);
  font-size: 11px;
}

.spacer {
  flex: 1;
}

.post-actions {
  display: flex;
  gap: 4px;
}

.status-tag {
  flex-shrink: 0;
  padding: 1px 8px;
  border-radius: var(--radius-pill);
  background: var(--panel-2);
  border: 1px solid var(--border);
  color: var(--text-muted);
  font-size: 11.5px;
}

.status-tag.published {
  background: var(--accent-soft);
  border-color: color-mix(in srgb, var(--accent) 30%, transparent);
  color: var(--accent);
}

.status-tag.pending {
  color: #b7791f;
  border-color: color-mix(in srgb, #b7791f 35%, transparent);
}

.status-tag.rejected,
.status-tag.hidden {
  color: var(--danger);
  border-color: color-mix(in srgb, var(--danger) 30%, transparent);
}

.blog-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  max-width: 860px;
  margin: 18px auto 0;
}

.pager-text {
  color: var(--text-muted);
  font-size: 12.5px;
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