import { request } from '@/api/client'
import type { CurrentUser, SaveMemoryBody, UserMemory } from '@/types'

/**
 * AI 记忆管理。和其他平级模块一样单独成文件，fetch / 401 兜底仍走 client.ts。
 * 所有接口都是「只能管自己」：路径固定 /users/me/memories，不接受 userId。
 */

/** 列出自己的记忆。MVP 上限 100 条，不分页。 */
export function fetchMemories(): Promise<UserMemory[]> {
  return request<UserMemory[]>('/users/me/memories')
}

/** 手动新增一条记忆。相同内容再次添加会被后端复活并转为手动。 */
export function createMemory(body: SaveMemoryBody): Promise<UserMemory> {
  return request<UserMemory>('/users/me/memories', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

/** 编辑一条记忆；编辑后来源变成「手动」，自动抽取不能再覆盖它。 */
export function updateMemory(id: number, body: SaveMemoryBody): Promise<UserMemory> {
  return request<UserMemory>(`/users/me/memories/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

/** 删除单条记忆。204 无响应体。 */
export function deleteMemory(id: number): Promise<void> {
  return request<void>(`/users/me/memories/${id}`, { method: 'DELETE' })
}

/** 清空全部记忆。204 无响应体。 */
export function clearMemories(): Promise<void> {
  return request<void>('/users/me/memories', { method: 'DELETE' })
}

/** 开关 AI 记忆；返回整份用户信息，调用方直接覆盖本地登录态。 */
export function updateMemorySettings(enabled: boolean): Promise<CurrentUser> {
  return request<CurrentUser>('/users/me/memory-settings', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled }),
  })
}
