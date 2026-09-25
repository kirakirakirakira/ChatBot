/**
 * 路由路径常量。
 *
 * 单独一个文件、不塞进 routes.ts：routes.ts 要 import 各个视图组件，
 * 而视图（403 / 404 页）又要用这些常量做「回到聊天」的链接，放一起就是循环依赖。
 */

/** 登录页。全站唯一 meta.public 的路由。 */
export const LOGIN_PATH = '/login'

/** 登录后的默认落地页，也是模块导航里的第一项。 */
export const HOME_PATH = '/chat'

/** 已登录但权限不够（普通用户访问管理员模块）时落这里。 */
export const FORBIDDEN_PATH = '/403'
