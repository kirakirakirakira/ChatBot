/**
 * 用户管理模块（/admin/users）的后端调用。
 *
 * 刻意不放进 api.ts：api.ts 是「聊天与登录」的接口清单，平级功能模块各开一个文件，
 * 互不干扰、也不会两个人同时改一个文件。传输层（鉴权头 / 401 兜底）仍然只有 api/client.ts 一份。
 */
import type { AdminAuditLog, AdminUser, Page, ResetPasswordResult, UserAdminOptions } from '@/types'
import { request } from '@/api/client'

/** 列表查询条件。keyword 空串等同于不传。 */
export interface AdminUserQuery {
  keyword?: string
  role?: number | null
  status?: number | null
  page?: number
  size?: number
}

export function listAdminUsers(query: AdminUserQuery = {}): Promise<Page<AdminUser>> {
  const params = new URLSearchParams()
  if (query.keyword) {
    params.set('keyword', query.keyword)
  }
  if (query.role !== null && query.role !== undefined) {
    params.set('role', String(query.role))
  }
  if (query.status !== null && query.status !== undefined) {
    params.set('status', String(query.status))
  }
  params.set('page', String(query.page ?? 0))
  params.set('size', String(query.size ?? 20))
  return request<Page<AdminUser>>('/admin/users?' + params.toString())
}

/** 角色 / 状态字典。下拉选项用它生成，前端不写死 0/1。 */
export function fetchUserAdminOptions(): Promise<UserAdminOptions> {
  return request<UserAdminOptions>('/admin/users/options')
}

export interface CreateUserBody {
  username: string
  password: string
  role?: number
  status?: number
}

export function createAdminUser(body: CreateUserBody): Promise<AdminUser> {
  return request<AdminUser>('/admin/users', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export function updateUserRole(id: number, role: number): Promise<AdminUser> {
  return request<AdminUser>('/admin/users/' + id + '/role', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ role }),
  })
}

export function updateUserStatus(id: number, status: number): Promise<AdminUser> {
  return request<AdminUser>('/admin/users/' + id + '/status', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ status }),
  })
}

export interface ResetPasswordBody {
  /** 与 generate 二选一。 */
  newPassword?: string
  generate?: boolean
}

/**
 * 管理员重置密码。目标用户的所有旧登录态立刻失效（后端写 password_changed_at），
 * 且 mustChangePassword 置 true，本人下次登录被强制改密。
 */
export function resetAdminUserPassword(id: number, body: ResetPasswordBody): Promise<ResetPasswordResult> {
  return request<ResetPasswordResult>('/admin/users/' + id + '/password', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

/** 强制下线：只作废登录态，不改密码。204 无响应体。 */
export function revokeAdminUserSessions(id: number): Promise<void> {
  return request<void>('/admin/users/' + id + '/revoke', { method: 'POST' })
}

/** 删号：级联删会话 / 消息 / 附件，不可恢复。204 无响应体。 */
export function deleteAdminUser(id: number): Promise<void> {
  return request<void>('/admin/users/' + id, { method: 'DELETE' })
}

/**
 * 管理端操作审计（GET /api/admin/audit，id 倒序）。
 * 端点是全管理端共用的（不挂在 /admin/users 下），以后的管理模块往同一张表写动作；
 * 暂时放在这个文件里，是因为目前唯一的使用方是用户管理界面。
 */
export function listAdminAudit(query: { page?: number; size?: number } = {}): Promise<Page<AdminAuditLog>> {
  const params = new URLSearchParams()
  params.set('page', String(query.page ?? 0))
  params.set('size', String(query.size ?? 20))
  return request<Page<AdminAuditLog>>('/admin/audit?' + params.toString())
}
