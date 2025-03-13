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
                    path: '',
                    name: 'welcome-login',
                    component: () => import('@/views/welcome/LoginPage.vue')
                },
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
    if(to.name.startsWith('welcome') && !unauthorized) {
        next('/index')
    } else if(to.fullPath.startsWith('/admin') && !admin) {
        next('/index')
    }else if(to.fullPath.startsWith('/index') && unauthorized) {
        next('/')
    } else {
        next()
    }
})

export default router
