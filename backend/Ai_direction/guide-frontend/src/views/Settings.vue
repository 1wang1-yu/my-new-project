<template>
  <div class="settings">
    <h2>系统设置</h2>
    <a-card style="margin-bottom: 16px;">
      <a-tabs>
        <a-tab-pane tab="AI 服务配置" key="ai">
          <a-form :model="aiConfig" style="margin-top: 16px;">
            <a-form-item label="API 密钥">
              <a-input-password v-model:value="aiConfig.apiKey" placeholder="请输入API密钥" />
            </a-form-item>
            <a-form-item label="API 基础地址">
              <a-input v-model:value="aiConfig.baseUrl" placeholder="请输入API基础地址" />
            </a-form-item>
            <a-form-item label="模型名称">
              <a-input v-model:value="aiConfig.model" placeholder="请输入模型名称" />
            </a-form-item>
            <a-form-item label="温度参数">
              <a-slider v-model:value="aiConfig.temperature" :min="0" :max="1" :step="0.1" />
              <span style="margin-left: 16px;">{{ aiConfig.temperature }}</span>
            </a-form-item>
            <a-form-item>
              <a-button type="primary" @click="saveAiConfig">保存配置</a-button>
            </a-form-item>
          </a-form>
        </a-tab-pane>
        <a-tab-pane tab="数据库配置" key="db">
          <a-form :model="dbConfig" style="margin-top: 16px;">
            <a-form-item label="数据库地址">
              <a-input v-model:value="dbConfig.url" placeholder="请输入数据库地址" />
            </a-form-item>
            <a-form-item label="用户名">
              <a-input v-model:value="dbConfig.username" placeholder="请输入用户名" />
            </a-form-item>
            <a-form-item label="密码">
              <a-input-password v-model:value="dbConfig.password" placeholder="请输入密码" />
            </a-form-item>
            <a-form-item>
              <a-button type="primary" @click="saveDbConfig">保存配置</a-button>
            </a-form-item>
          </a-form>
        </a-tab-pane>
        <a-tab-pane tab="ChromaDB 配置" key="chroma">
          <a-form :model="chromaConfig" style="margin-top: 16px;">
            <a-form-item label="ChromaDB 地址">
              <a-input v-model:value="chromaConfig.baseUrl" placeholder="请输入ChromaDB地址" />
            </a-form-item>
            <a-form-item label="默认集合">
              <a-input v-model:value="chromaConfig.defaultCollection" placeholder="请输入默认集合名称" />
            </a-form-item>
            <a-form-item>
              <a-button type="primary" @click="saveChromaConfig">保存配置</a-button>
            </a-form-item>
          </a-form>
        </a-tab-pane>
        <a-tab-pane tab="系统信息" key="info">
          <a-descriptions bordered>
            <a-descriptions-item label="系统版本">1.0.0</a-descriptions-item>
            <a-descriptions-item label="后端服务状态">运行中</a-descriptions-item>
            <a-descriptions-item label="ChromaDB 状态">运行中</a-descriptions-item>
            <a-descriptions-item label="API 连接状态">正常</a-descriptions-item>
            <a-descriptions-item label="数据库连接状态">正常</a-descriptions-item>
            <a-descriptions-item label="最后更新时间">2026-04-16 18:00:00</a-descriptions-item>
          </a-descriptions>
        </a-tab-pane>
      </a-tabs>
    </a-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'

const aiConfig = reactive({
  apiKey: 'sk-e6fca972c66f43e1ac827b9553bf1842',
  baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
  model: 'qwen3.5-flash',
  temperature: 0.7
})

const dbConfig = reactive({
  url: 'jdbc:sqlserver://localhost:1433;databaseName=smart_guide_db;encrypt=false;trustServerCertificate=true',
  username: 'sa',
  password: '123456'
})

const chromaConfig = reactive({
  baseUrl: 'http://localhost:8000',
  defaultCollection: 'travel_kb'
})

const saveAiConfig = () => {
  console.log('保存AI配置:', aiConfig)
}

const saveDbConfig = () => {
  console.log('保存数据库配置:', dbConfig)
}

const saveChromaConfig = () => {
  console.log('保存ChromaDB配置:', chromaConfig)
}
</script>

<style scoped>
.settings {
  padding: 0 16px;
}
</style>