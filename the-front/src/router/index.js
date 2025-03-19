import { createRouter, createWebHistory } from 'vue-router'
import {isRoleAdmin, isUnauthorized} from "@/net";

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [
        {
            path: '/',
            name: 'welcome',
            component: () => import('@/views/WelcomeView.vue'),
            children: [
                {
                    path: 'login',
                    name: 'welcome-login',
                    component: () => import('@/views/Welcome/LoginPage.vue')
                },
                {
                    path: 'register',
                    name: 'welcome-register',
                    component: () => import('@/views/Welcome/RegisterPage.vue')
                },
                {
                    path: 'forget',
                    name: 'welcome-forget',
                    component: () => import('@/views/Welcome/ForgetPage.vue')
                }
            ],
        },
        {
            path: '/index',
            name: 'indexPage',
            component: () => import('@/views/IndexView.vue'),
        }
        ]
})

router.beforeEach((to, from, next) => {
    const unauthorized = isUnauthorized(), admin = isRoleAdmin()
    if(to.name.startsWith('welcome') && !unauthorized) { //如果路由是welcome开头，并且授权，则跳转到index页面
        next('/index')
    } else if(to.fullPath.startsWith('/admin') && !admin) { //如果路由是admin开头，并且未授权，则跳转到index页面
        next('/index')
    }else if(to.fullPath.startsWith('/index') && unauthorized) { //如果路由是index开头，并且未授权，则跳转到welcome页面
        next('/')
    } else {
        next()
    }
})

export default router
