<template>
  <div class="app">
    <a-layout style="min-height: 100vh">
      <a-layout-sider theme="dark" width="200">
        <div class="logo">
          <span style="color: white; font-size: 18px; font-weight: bold;">智慧导游</span>
        </div>
        <a-menu
          mode="inline"
          :selectedKeys="[currentRoute]"
          theme="dark"
          style="height: 100%; border-right: 0"
        >
          <a-menu-item key="/">
            <template #icon>
              <home-outlined />
            </template>
            数据大屏
          </a-menu-item>
          <a-menu-item key="/knowledge">
            <template #icon>
              <book-outlined />
            </template>
            知识库管理
          </a-menu-item>
          <a-menu-item key="/avatar">
            <template #icon>
              <user-outlined />
            </template>
            数字人配置
          </a-menu-item>
          <a-menu-item key="/data">
            <template #icon>
              <area-chart-outlined />
            </template>
            数据分析
          </a-menu-item>
          <a-menu-item key="/settings">
            <template #icon>
              <setting-outlined />
            </template>
            系统设置
          </a-menu-item>
          <a-menu-item key="/tourist">
            <template #icon>
              <user-outlined />
            </template>
            游客端
          </a-menu-item>
        </a-menu>
      </a-layout-sider>
      <a-layout>
        <a-layout-header style="background: #fff; padding: 0 24px; display: flex; align-items: center; justify-content: space-between;">
          <h1 style="margin: 0; font-size: 20px; font-weight: bold;">智慧导游管理平台</h1>
          <div>
            <a-dropdown>
              <a-button>
                管理员 <down-outlined />
              </a-button>
              <template #overlay>
                <a-menu>
                  <a-menu-item>
                    <template #icon>
                      <user-outlined />
                    </template>
                    个人中心
                  </a-menu-item>
                  <a-menu-item>
                    <template #icon>
                      <logout-outlined />
                    </template>
                    退出登录
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>
        </a-layout-header>
        <a-layout-content style="margin: 24px; background: #fff; padding: 24px; border-radius: 8px;">
          <router-view />
        </a-layout-content>
      </a-layout>
    </a-layout>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import {
  HomeOutlined,
  BookOutlined,
  UserOutlined,
  AreaChartOutlined,
  SettingOutlined,
  DownOutlined,
  LogoutOutlined
} from '@ant-design/icons-vue'

const route = useRoute()
const currentRoute = computed(() => route.path)
</script>

<style scoped>
.app {
  height: 100vh;
}

.logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #001529;
}
</style>