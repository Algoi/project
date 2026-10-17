import {createRouter,createWebHistory,type RouterHistory} from 'vue-router'
import {useAuthStore} from './stores/auth'
import {roleHome} from './shared/roles'
export {roles,roleHome} from './shared/roles'
import {appLayout,statusPage} from './shared/shell'
import {loginPage} from './features/identity'
import {routes as messagesRoutes} from './features/messages'
import {routes as addressesRoutes} from './features/addresses'
import {routes as financeRoutes} from './features/finance'
import {routes as homeRoutes} from './features/home'
import {routes as identityRoutes} from './features/identity'
import {routes as sitesRoutes} from './features/sites'
import {routes as catalogRoutes} from './features/catalog'
import {routes as auditRoutes} from './features/audit'
import {routes as eldersRoutes} from './features/elders'
import {routes as planningRoutes} from './features/planning'
import {routes as recommendationRoutes} from './features/recommendation'
import {routes as orderingRoutes} from './features/ordering'
import {routes as noticesRoutes} from './features/notices'
import {routes as reviewsRoutes} from './features/reviews'
import {routes as ticketsRoutes} from './features/tickets'
import {routes as reportingRoutes} from './features/reporting'
export function createAppRouter(history:RouterHistory=createWebHistory()){
const router=createRouter({history,routes:[{path:'/login',component:loginPage,meta:{public:true}},{path:'/403',component:statusPage,props:{kind:'forbidden'},meta:{public:true}},{path:'/session-unavailable',component:statusPage,props:{kind:'unavailable'},meta:{public:true}},{path:'/',component:appLayout,children:[...messagesRoutes,...addressesRoutes,...financeRoutes,...homeRoutes,...identityRoutes,...sitesRoutes,...catalogRoutes,...auditRoutes,...eldersRoutes,...planningRoutes,...recommendationRoutes,...orderingRoutes,...noticesRoutes,...reviewsRoutes,...ticketsRoutes,...reportingRoutes]},{path:'/:pathMatch(.*)*',component:statusPage,props:{kind:'missing'},meta:{public:true}}]})
  router.beforeEach(async to => {
    const auth = useAuthStore()
    if (to.meta.public) return true
    try { await auth.restore() }
    catch (error) {
      return (error as { status?: number }).status === 401 ? '/login' : '/session-unavailable'
    }
    if (!auth.user) return '/login'
    if (to.meta.home) return roleHome(auth.activeRole ? [auth.activeRole] : auth.user.roles)
    if (to.meta.role && !auth.user.roles.includes(String(to.meta.role))) return '/403'
    if (auth.user.deniedPermissions?.includes(`menu:${to.path}`)) return '/403'
    if (to.meta.role) auth.activeRole = String(to.meta.role)
    return true
  })
  return router
}
