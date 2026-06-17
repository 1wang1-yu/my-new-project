const { request, upload, DEFAULT_BASE_URL } = require('./request')

// 调用后端 AI 智能问答接口
function chat(payload) {
  return request({
    url: '/api/v1/chat',
    method: 'POST',
    data: payload,
  })
}

// 流式智能问答：当前微信小程序对 SSE 支持不稳定，统一走普通接口
// 后端已做模型/提示词/上下文优化，响应速度已有明显提升
function chatStream(payload, onChunk, onDone) {
  chat(payload).then(function (data) {
    var meta = { session_id: data.session_id, suggested_questions: data.suggested_questions }
    if (onChunk) onChunk(data.answer, meta)
    if (onDone) onDone(null, meta)
  }).catch(function (err) {
    if (onDone) onDone(err, null)
  })
}

function recommendRoute(payload) {
  return request({
    url: '/api/v1/route/recommend',
    method: 'POST',
    data: payload,
  })
}

// 调用后端语音转文字接口（真实）
function asr(payload) {
  return request({
    url: '/api/v1/asr',
    method: 'POST',
    data: payload,
  })
}

// 调用后端文字转语音接口（真实）
function tts(payload) {
  return request({
    url: '/api/v1/tts',
    method: 'POST',
    data: payload,
  })
}

function uploadKnowledge(filePath, formData) {
  return request({
    url: '/api/v1/admin/knowledge/upload',
    method: 'POST',
    data: formData,
  })
}

function dashboard(dateRange = 'today') {
  return request({
    url: '/api/v1/admin/dashboard',
    method: 'GET',
    data: { dateRange },
  })
}

function sentimentReport(startDate, endDate) {
  return request({
    url: '/api/v1/admin/sentiment',
    method: 'GET',
    data: { startDate, endDate },
  })
}

function systemConfig() {
  return request({
    url: '/api/v1/admin/config',
    method: 'GET',
  })
}

module.exports = {
  chat,
  chatStream,
  recommendRoute,
  asr,
  tts,
  uploadKnowledge,
  dashboard,
  sentimentReport,
  systemConfig,
}
