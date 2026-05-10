<template>
  <div class="tourist">
    <h2>智能导游</h2>
    <a-card style="margin-bottom: 16px;">
      <div class="chat-container">
        <div class="chat-messages">
          <div v-for="(message, index) in messages" :key="index" class="message" :class="message.role">
            <div class="message-content">{{ message.content }}</div>
          </div>
        </div>
        <div class="chat-input">
          <a-input
            v-model:value="inputMessage"
            placeholder="请输入您的问题..."
            @keyup.enter="sendMessage"
          />
          <div class="input-actions">
            <a-button @click="startRecording" v-if="!isRecording">
              <audio-outlined />
            </a-button>
            <a-button danger @click="stopRecording" v-else>
              <stop-outlined />
            </a-button>
            <a-button type="primary" @click="sendMessage">发送</a-button>
          </div>
        </div>
      </div>
    </a-card>
    <a-card style="margin-bottom: 16px;">
      <a-tabs>
        <a-tab-pane tab="路线推荐" key="route">
          <a-form :model="routeForm" style="margin-top: 16px;">
            <a-form-item label="兴趣标签">
              <a-select
                v-model:value="routeForm.interests"
                mode="tags"
                placeholder="请选择或输入兴趣标签"
              >
                <a-select-option value="历史">历史</a-select-option>
                <a-select-option value="文化">文化</a-select-option>
                <a-select-option value="自然">自然</a-select-option>
                <a-select-option value="美食">美食</a-select-option>
                <a-select-option value="购物">购物</a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="游玩时长（分钟）">
              <a-input-number v-model:value="routeForm.durationMinutes" :min="30" :max="360" />
            </a-form-item>
            <a-form-item>
              <a-button type="primary" @click="getRoute">获取推荐路线</a-button>
            </a-form-item>
          </a-form>
          <div v-if="recommendedRoute" class="route-result">
            <h3>{{ recommendedRoute.name }}</h3>
            <p>预计时长: {{ recommendedRoute.estimated_time }}分钟</p>
            <h4>景点顺序:</h4>
            <ul>
              <li v-for="(stop, index) in recommendedRoute.stops" :key="index">{{ index + 1 }}. {{ stop }}</li>
            </ul>
            <h4>亮点:</h4>
            <ul>
              <li v-for="(highlight, index) in recommendedRoute.highlights" :key="index">{{ highlight }}</li>
            </ul>
          </div>
        </a-tab-pane>
        <a-tab-pane tab="语音助手" key="voice">
          <div class="voice-assistant">
            <a-button type="primary" @click="playVoice">播放语音讲解</a-button>
            <a-button @click="stopVoice" style="margin-left: 16px;">停止播放</a-button>
            <div style="margin-top: 16px;">
              <a-slider v-model:value="voiceSpeed" :min="0.5" :max="2" :step="0.1" />
              <span style="margin-left: 16px;">语音速度: {{ voiceSpeed }}</span>
            </div>
          </div>
        </a-tab-pane>
      </a-tabs>
    </a-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { AudioOutlined, StopOutlined } from '@ant-design/icons-vue'

const messages = ref([
  {
    role: 'assistant',
    content: '您好！我是智能导游助手，有什么可以帮您的吗？'
  }
])

const inputMessage = ref('')
const isRecording = ref(false)

const routeForm = reactive({
  interests: [],
  durationMinutes: 120
})

const recommendedRoute = ref(null)
const voiceSpeed = ref(1.0)

const sendMessage = () => {
  if (inputMessage.value.trim() === '') return
  
  // 添加用户消息
  messages.value.push({
    role: 'user',
    content: inputMessage.value
  })
  
  // 模拟AI回复
  setTimeout(() => {
    messages.value.push({
      role: 'assistant',
      content: '这是一个智能回复，实际应用中会调用后端API获取真实回复。'
    })
  }, 1000)
  
  inputMessage.value = ''
}

const startRecording = () => {
  isRecording.value = true
  console.log('开始录音')
}

const stopRecording = () => {
  isRecording.value = false
  console.log('停止录音')
  // 这里可以添加语音转文字的逻辑
}

const getRoute = () => {
  console.log('获取推荐路线:', routeForm)
  // 模拟路线推荐
  recommendedRoute.value = {
    name: 'AI 定制漫游线',
    stops: ['游客中心', '主景点区', '观景平台', '文创商店'],
    estimated_time: routeForm.durationMinutes,
    highlights: [
      '贴合兴趣：' + routeForm.interests.join('、'),
      '节奏舒适，适合小程序语音导览',
      '已结合默认景区知识模板'
    ]
  }
}

const playVoice = () => {
  console.log('播放语音讲解')
}

const stopVoice = () => {
  console.log('停止播放')
}
</script>

<style scoped>
.tourist {
  padding: 0 16px;
}

.chat-container {
  height: 400px;
  display: flex;
  flex-direction: column;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.message {
  margin-bottom: 16px;
  max-width: 80%;
  padding: 8px 12px;
  border-radius: 8px;
}

.message.user {
  align-self: flex-end;
  background: #e6f7ff;
  margin-left: auto;
}

.message.assistant {
  align-self: flex-start;
  background: #f0f0f0;
}

.chat-input {
  padding: 16px;
  display: flex;
  align-items: center;
}

.chat-input input {
  flex: 1;
  margin-right: 16px;
}

.input-actions {
  display: flex;
  gap: 8px;
}

.route-result {
  margin-top: 16px;
  padding: 16px;
  background: #fafafa;
  border-radius: 8px;
}

.voice-assistant {
  padding: 16px;
  background: #fafafa;
  border-radius: 8px;
}
</style>