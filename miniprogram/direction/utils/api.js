const { request, upload } = require('./request')

// 本地模拟智能回答
function directChat(message) {
  return new Promise((resolve, reject) => {
    // 模拟不同问题的回答
    let answer = ''
    const lowerMessage = message.toLowerCase()
    
    if (lowerMessage.includes('断桥残雪')) {
      answer = '断桥残雪是西湖十景之一，位于白堤东端。每当冬天下雪后，桥面上的积雪在阳光照射下融化，而桥洞部分的积雪则保留较长时间，形成桥身仿佛断开的奇观，因此得名"断桥残雪"。这一景观在冬日的阳光下尤为美丽，是西湖冬季的标志性景色。'
    } else if (lowerMessage.includes('西湖')) {
      answer = '西湖是中国浙江省杭州市的著名风景名胜区，也是世界文化遗产。它以其秀丽的湖光山色和众多的人文景观而闻名，包括苏堤、白堤、断桥、雷峰塔等景点。西湖的美景四季各异，春季有苏堤春晓，夏季有曲院风荷，秋季有平湖秋月，冬季有断桥残雪。'
    } else if (lowerMessage.includes('路线') || lowerMessage.includes('导览')) {
      answer = '为您推荐一条经典的西湖游览路线：从断桥出发，沿白堤步行至孤山，参观中山公园和西泠印社，然后乘船至三潭印月，接着前往雷峰塔，最后沿苏堤返回。这条路线涵盖了西湖的主要景点，大约需要4-5小时。'
    } else if (lowerMessage.includes('美食') || lowerMessage.includes('吃')) {
      answer = '西湖周边有许多特色美食，推荐尝试西湖醋鱼、龙井虾仁、叫花鸡、宋嫂鱼羹等杭州名菜。此外，杭州的小笼包、葱包桧、定胜糕等小吃也值得一试。您可以在楼外楼、知味观等老字号餐厅品尝这些美食。'
    } else {
      answer = '欢迎来到西湖景区！我是您的智能导游助手。您可以问我关于西湖的景点、历史、路线、美食等问题，我会为您提供详细的信息和建议。'
    }
    
    // 生成建议问题
    const suggestedQuestions = [
      '附近还有什么值得打卡的地方？',
      '适合拍照的时间段是什么？',
      '能帮我规划更轻松的路线吗？'
    ]
    
    // 模拟网络延迟
    setTimeout(() => {
      resolve({
        answer: answer,
        suggested_questions: suggestedQuestions,
        tts_url: '',
        emotion: 'calm',
        session_id: `session_${Date.now()}`
      })
    }, 500)
  })
}

function chat(payload) {
  return request({
    url: '/api/v1/chat',
    method: 'POST',
    data: payload,
  })
}

function recommendRoute(payload) {
  return request({
    url: '/api/v1/route/recommend',
    method: 'POST',
    data: payload,
  })
}

// 本地模拟语音转文字
function asr(payload) {
  return new Promise((resolve, reject) => {
    // 模拟语音转文字结果
    setTimeout(() => {
      resolve({
        text: '断桥残雪是什么景观？',
        confidence: 0.95
      })
    }, 500)
  })
}

// 本地模拟文字转语音
function tts(payload) {
  return new Promise((resolve, reject) => {
    // 模拟文字转语音结果
    setTimeout(() => {
      resolve({
        audio_url: 'https://example.com/audio.mp3',
        duration_ms: 5000,
        lip_sync_data: []
      })
    }, 500)
  })
}

// 本地模拟知识库上传
function uploadKnowledge(filePath, formData) {
  return new Promise((resolve, reject) => {
    // 模拟知识库上传结果
    setTimeout(() => {
      resolve({
        doc_id: 'doc_123456',
        chunk_count: 10,
        index_status: 'completed'
      })
    }, 1000)
  })
}

// 本地模拟数据大屏
function dashboard(dateRange = 'today') {
  return new Promise((resolve, reject) => {
    // 模拟数据大屏数据
    setTimeout(() => {
      resolve({
        service_count: 2847,
        satisfaction: 4.8,
        accuracy_rate: 93.2,
        avg_response_ms: 2300,
        top_questions: ['断桥残雪是什么景观？', '西湖有哪些景点？', '推荐一条西湖游览路线'],
        sentiment_distribution: {
          positive: 72,
          neutral: 21,
          negative: 7
        }
      })
    }, 500)
  })
}

// 本地模拟情绪报告
function sentimentReport(startDate, endDate) {
  return new Promise((resolve, reject) => {
    // 模拟情绪报告数据
    setTimeout(() => {
      resolve({
        positive_rate: 72,
        negative_keywords: ['人多', '排队', '贵'],
        suggestions: ['增加景点容量', '优化排队流程', '调整价格策略'],
        trend_data: [65, 68, 70, 72, 71, 73, 72]
      })
    }, 500)
  })
}

// 本地模拟系统配置
function systemConfig() {
  return new Promise((resolve, reject) => {
    // 模拟系统配置数据
    setTimeout(() => {
      resolve({
        scenic_name: '西湖景区',
        avatar_name: '小导',
        base_url: 'http://localhost:8081'
      })
    }, 500)
  })
}

module.exports = {
  chat,
  directChat,
  recommendRoute,
  asr,
  tts,
  uploadKnowledge,
  dashboard,
  sentimentReport,
  systemConfig,
}
