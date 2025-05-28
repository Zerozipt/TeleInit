import { createRouter, createWebHistory } from 'vue-router'
import { isRoleAdmin, isUnauthorized } from "@/net";

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
                    redirect: '/login'
                },
                {
                    path: 'login',
                    name: 'welcome-login',
                    component: () => import('@/views/Welcome/LoginPage.vue')
                },
                {
                    path: 'register',
                    name: 'welcome-register',
                    component: () => import('@/views/Welcome/RegisterPage.vue')
                }
            ],
        },
        {
            path: '/index',
            name: 'index',
            component: () => import('@/views/IndexView.vue'),
            redirect: '/index/chat',
            children: [
                {
                    path: 'chat',
                    name: 'index-chat',
                    component: () => import('@/views/ChatView.vue')
                },
                {
                    path: 'contacts',
                    name: 'index-contacts',
                    component: () => import('@/views/ContactsView.vue')
                },
                {
                    path: 'group/:id',
                    name: 'group-detail',
                    component: () => import('@/views/components/group/GroupDetailView.vue'),
                    props: true
                },
                {
                    path: 'settings',
                    name: 'index-settings',
                    component: () => import('@/views/SettingsView.vue')
                }
            ]
        }
    ]
})

router.beforeEach((to, from, next) => {
    const unauthorized = isUnauthorized();
    const isAdmin = isRoleAdmin();

    if(to.name && to.name.startsWith('welcome') && !unauthorized) {
        next('/index');
    } else if (to.path.startsWith('/index') && unauthorized) {
        next('/');
    } else {
        next();
    }
})

export default router
