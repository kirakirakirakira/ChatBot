import { createRouter, createWebHistory } from 'vue-router'
import { installGuards } from './guards'
import { routes } from './routes'

/**
 * history 模式（URL 里没有 #）。
 *
 * 开发期 Vite 自带回退，怎么刷都不会 404；**生产部署必须让静态服务器把未命中的路径
 * 回退到 index.html**（nginx: try_files $uri /index.html），否则直接访问 /admin/users
 * 或在该页按刷新会拿到服务器的 404。这是 history 模式唯一需要运维配合的地方。
 */
const router = createRouter({
  history: createWebHistory(),
  routes,
  // 换模块时把窗口滚动位置归零：聊天和管理台都是长内容，留着上一个页面的位置会很怪。
  // 聊天消息区是自己内部的滚动容器，不受这里影响。
  scrollBehavior: () => ({ top: 0 }),
})

installGuards(router)

export default router
