import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'Dashboard',
      component: () => import('../views/Dashboard.vue')
    },
    {
      path: '/knowledge',
      name: 'Knowledge',
      component: () => import('../views/Knowledge.vue')
    },
    {
      path: '/avatar',
      name: 'Avatar',
      component: () => import('../views/Avatar.vue')
    },
    {
      path: '/data',
      name: 'Data',
      component: () => import('../views/Data.vue')
    },
    {
      path: '/settings',
      name: 'Settings',
      component: () => import('../views/Settings.vue')
    },
    {
      path: '/tourist',
      name: 'Tourist',
      component: () => import('../views/Tourist.vue')
    }
  ]
})

export default router