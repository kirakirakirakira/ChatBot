import { markRaw } from 'vue'
import type { RouteRecordRaw } from 'vue-router'
import AppShell from '@/layouts/AppShell.vue'
import IconChat from '@/components/icons/IconChat.vue'
import IconUsers from '@/components/icons/IconUsers.vue'
import LoginView from '@/views/LoginView.vue'
import ChatView from '@/views/ChatView.vue'
import ForbiddenView from '@/views/ForbiddenView.vue'
import NotFoundView from '@/views/NotFoundView.vue'
import { FORBIDDEN_PATH, HOME_PATH, LOGIN_PATH } from './paths'

/**
 * 路由 meta 的类型契约。
 *
 * 这张表就是「怎么加一个和聊天平级的功能模块」的答案：新模块只需要在下面加一条
 * 带 moduleId / title / icon / order 的记录，导航条会自动长出图标、守卫会自动拦权限，
 * **ModuleNav.vue 和 guards.ts 一行都不用改**。
 */
declare module 'vue-router' {
  interface RouteMeta {
    /** 模块唯一标识。有它 = 这是一个平级功能模块，会出现在左侧导航条上。 */
    moduleId?: string
    /** 导航文案，同时用于 document.title。 */
    title?: string
    /** 导航图标组件。放进 meta 前必须 markRaw()，否则会被 reactive 代理、白白增加开销。 */
    icon?: unknown
    /** 导航排序，越小越靠前：聊天 10，管理台类往后排（90+）。 */
    order?: number
    /** 需要管理员。守卫据此拦成 403，导航据此对普通用户隐藏。 */
    requiresAdmin?: boolean
    /** true = 不进导航条（403 / 404 这类兜底页）。 */
    hidden?: boolean
    /** true = 不需要登录。目前只有登录页。 */
    public?: boolean
  }
}

/**
 * 聊天用**静态 import**、后面的管理台用 () => import()：
 * 聊天是落地页，懒加载会多一次请求并在首屏闪一下空白；
 * 管理台代码只有管理员需要，拆开能让普通用户的 bundle 小一点。
 */
export const routes: RouteRecordRaw[] = [
  {
    path: LOGIN_PATH,
    name: 'login',
    component: LoginView,
    // 登录页刻意不挂在 AppShell 下：未登录的人不该看到任何模块导航
    meta: { public: true, title: '登录' },
  },
  {
    path: '/',
    component: AppShell,
    children: [
      { path: '', redirect: HOME_PATH },
      {
        path: 'chat',
        name: 'chat',
        component: ChatView,
        meta: { moduleId: 'chat', title: '聊天', icon: markRaw(IconChat), order: 10 },
      },
      {
        // 第一个和聊天平级的功能模块。懒加载：普通用户不下载管理台代码。
        path: 'admin/users',
        name: 'admin-users',
        component: () => import('@/views/admin/UsersView.vue'),
        meta: { moduleId: 'admin-users', title: '用户管理', icon: markRaw(IconUsers), order: 90, requiresAdmin: true },
      },
      {
        // 审计是用户管理模块的子页面，不是平级模块：hidden 不进导航条，从 UsersView 的链接进入
        path: 'admin/audit',
        name: 'admin-audit',
        component: () => import('@/views/admin/AuditView.vue'),
        meta: { title: '操作记录', requiresAdmin: true, hidden: true },
      },
      {
        path: FORBIDDEN_PATH.slice(1),
        name: 'forbidden',
        component: ForbiddenView,
        meta: { title: '无权限', hidden: true },
      },
      {
        // 兜底页也放在外壳里：登录状态下打错地址，还能用左边导航条回去，不用手改 URL
        path: ':pathMatch(.*)*',
        name: 'not-found',
        component: NotFoundView,
        meta: { title: '页面不存在', hidden: true },
      },
    ],
  },
]
