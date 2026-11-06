<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createBlogPost, getBlogPost, updateBlogPost } from '@/api/blog'
import MarkdownContent from '@/components/MarkdownContent.vue'

/**
 * 写 / 编辑文章（/blog/new 与 /blog/:id/edit）。
 *
 * 编辑器刻意用「textarea + 预览切换」而不是富文本：项目已经有一套安全的 Markdown 渲染
 * （MarkdownContent），再引一个富文本库等于把 XSS 过滤、粘贴清洗、图片上传全部重新做一遍。
 * 预览和详情走同一个渲染器，作者看到的样式就是读者看到的样式。
 */
const route = useRoute()
const router = useRouter()

const editingId = computed<number | null>(() => {
  const raw = route.params.id
  if (typeof raw !== 'string') {
    return null
  }
  const id = Number(raw)
  return Number.isInteger(id) && id > 0 ? id : null
})

const title = ref('')
const summary = ref('')
const content = ref('')
const preview = ref(false)
const loading = ref(false)
/** 编辑模式下拉取成功后才允许渲染表单：拉取失败（不是自己的文章 → 404）时不能留一张空表单让人误发新文章。 */
const ready = ref(false)
const saving = ref(false)
const error = ref('')

const isEditing = computed(() => editingId.value !== null)
const canSave = computed(() => !saving.value && title.value.trim().length > 0 && content.value.trim().length > 0)

onMounted(async () => {
  const id = editingId.value
  if (id === null) {
    ready.value = true
    return
  }
  loading.value = true
  try {
    const post = await getBlogPost(id)
    title.value = post.title
    summary.value = post.summary ?? ''
    content.value = post.content
    ready.value = true
  } catch (e) {
    // 不是自己的文章后端返回 404，和「文章不存在」同一句文案，界面不用区分
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
})

async function save(): Promise<void> {
  if (!canSave.value) {
    return
  }
  saving.value = true
  error.value = ''
  try {
    const body = {
      title: title.value.trim(),
      summary: summary.value.trim(),
      content: content.value,
    }
    const saved = isEditing.value && editingId.value !== null
      ? await updateBlogPost(editingId.value, body)
      : await createBlogPost(body)
    await router.push(`/blog/${saved.id}`)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    saving.value = false
  }
}

function cancel(): void {
  // 与详情页同一口径：取消固定回列表，不依赖浏览器历史
  void router.push('/blog')
}
</script>

<template>
  <div class="editor-page">
    <div class="editor-inner">
      <header class="editor-head">
        <div>
          <h1 class="editor-title">{{ isEditing ? '编辑文章' : '写文章' }}</h1>
         </div>
        <div class="head-actions">
          <button class="btn-ghost" type="button" :disabled="saving" @click="cancel">取消</button>
          <button class="btn-primary" type="button" :disabled="!canSave" @click="save">
            {{ saving ? '保存中…' : (isEditing ? '保存修改' : '发布') }}
          </button>
        </div>
      </header>

      <div v-if="error" class="alert-error">{{ error }}</div>
      <div v-if="loading" class="editor-loading">加载中…</div>

      <template v-else-if="!isEditing || ready">
        <label class="field">
          <span class="field-label">标题 <span class="counter">{{ title.length }} / 100</span></span>
          <input v-model="title" class="field-input title-input" type="text" maxlength="100" />
        </label>

        <label class="field">
          <span class="field-label">摘要（可选）<span class="counter">{{ summary.length }} / 200</span></span>
          <input
            v-model="summary"
            class="field-input"
            type="text"
            maxlength="200"
            placeholder="留空自动提取"
          />
        </label>

        <div class="editor-bar">
          <span class="bar-label">正文 <span class="counter">{{ content.length }} / 50000</span></span>
          <div class="mode-switch">
            <button class="mode" :class="{ active: !preview }" type="button" @click="preview = false">编辑</button>
            <button class="mode" :class="{ active: preview }" type="button" @click="preview = true">预览</button>
          </div>
        </div>

        <textarea
          v-if="!preview"
          v-model="content"
          class="content-input"
          maxlength="50000"
        ></textarea>
        <div v-else class="preview-box">
          <MarkdownContent v-if="content.trim()" :content="content" />
          <p v-else class="preview-empty">还没有内容可预览。</p>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.editor-page {
  height: 100%;
  overflow-y: auto;
  padding: 28px 32px 56px;
}

.editor-inner {
  max-width: 860px;
  margin: 0 auto;
}

.editor-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.editor-title {
  margin: 0;
  font-size: 20px;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.editor-loading {
  padding: 44px 0;
  text-align: center;
  color: var(--text-muted);
  font-size: 14px;
}

.title-input {
  font-size: 16px;
  font-weight: 600;
}

.counter {
  margin-left: 6px;
  color: var(--text-muted);
  font-size: 11.5px;
  font-weight: normal;
}

.editor-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 16px 0 8px;
}

.bar-label {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text-muted);
}

.mode-switch {
  display: flex;
  gap: 2px;
  padding: 2px;
  background: var(--panel-2);
  border: 1px solid var(--border);
  border-radius: var(--radius-s);
}

.mode {
  padding: 3px 12px;
  border: none;
  border-radius: 5px;
  background: transparent;
  color: var(--text-muted);
  font-size: 12.5px;
  cursor: pointer;
}

.mode.active {
  background: var(--panel);
  color: var(--text);
  box-shadow: 0 1px 2px rgb(0 0 0 / 8%);
}

.content-input {
  width: 100%;
  min-height: 380px;
  padding: 14px 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius-m);
  background: var(--panel);
  color: var(--text);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13.5px;
  line-height: 1.7;
  resize: vertical;
  box-sizing: border-box;
}

.content-input:focus {
  outline: none;
  border-color: color-mix(in srgb, var(--accent) 55%, var(--border));
}

.preview-box {
  min-height: 380px;
  padding: 18px 20px;
  border: 1px solid var(--border);
  border-radius: var(--radius-m);
  background: var(--panel);
  box-sizing: border-box;
}

.preview-empty {
  margin: 0;
  color: var(--text-muted);
  font-size: 13.5px;
}
</style>