/**
 * 博客模块的后端调用。传输层（Authorization、401 兜底）统一走 @/api/client，
 * 这里只描述接口与请求体。按项目约定，新模块不往 api.ts 里堆。
 */
import type { BlogPost, BlogPostSummary, Page, SaveBlogPostBody } from '@/types'
import { request } from '@/api/client'

/** 列表分页参数：page 从 0 起；size 默认 10、上限 100（超限后端 400）。 */
export interface BlogPageQuery {
  page?: number
  size?: number
}

function queryString(query?: BlogPageQuery): string {
  const params = new URLSearchParams()
  if (query?.page != null) {
    params.set('page', String(query.page))
  }
  if (query?.size != null) {
    params.set('size', String(query.size))
  }
  const qs = params.toString()
  return qs ? `?${qs}` : ''
}

/** 已发布文章列表：所有登录用户可见。 */
export function listBlogPosts(query?: BlogPageQuery): Promise<Page<BlogPostSummary>> {
  return request<Page<BlogPostSummary>>('/blog/posts' + queryString(query))
}

/** 我的文章：所有状态（草稿 / 待审 / 驳回 / 下架 / 已发布），只返回自己的。 */
export function listMyBlogPosts(query?: BlogPageQuery): Promise<Page<BlogPostSummary>> {
  return request<Page<BlogPostSummary>>('/blog/mine' + queryString(query))
}

/** 详情。未发布的文章只有作者本人能打开，其他人 404。 */
export function getBlogPost(id: number): Promise<BlogPost> {
  return request<BlogPost>(`/blog/posts/${id}`)
}

/** 新建。v1 所有启用用户都能直接发布，返回的文章状态由后端 BlogPolicy 决定。 */
export function createBlogPost(body: SaveBlogPostBody): Promise<BlogPost> {
  return request<BlogPost>('/blog/posts', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

/** 编辑自己的文章。别人的文章后端返回 404。 */
export function updateBlogPost(id: number, body: SaveBlogPostBody): Promise<BlogPost> {
  return request<BlogPost>(`/blog/posts/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

/** 删除自己的文章，204 无响应体。 */
export function deleteBlogPost(id: number): Promise<void> {
  return request<void>(`/blog/posts/${id}`, { method: 'DELETE' })
}