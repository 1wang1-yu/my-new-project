const baseUrl = window.location.origin

async function request(url, options = {}) {
  const response = await fetch(`${baseUrl}${url}`, options)
  const result = await response.json()
  if (!response.ok || result.code !== 0) {
    throw new Error(result.message || `请求失败(${response.status})`)
  }
  return result.data
}

function renderDashboard(data) {
  document.getElementById('serviceCount').textContent = data.service_count ?? '--'
  document.getElementById('satisfaction').textContent = data.satisfaction ?? '--'
  document.getElementById('accuracyRate').textContent = data.accuracy_rate ?? '--'
  document.getElementById('avgResponse').textContent = data.avg_response_ms ?? '--'

  const sentiment = data.sentiment_distribution || {}
  document.getElementById('positiveRate').textContent = `${sentiment.positive ?? 0}%`
  document.getElementById('neutralRate').textContent = `${sentiment.neutral ?? 0}%`
  document.getElementById('negativeRate').textContent = `${sentiment.negative ?? 0}%`
  document.getElementById('positiveBar').style.width = `${sentiment.positive ?? 0}%`
  document.getElementById('neutralBar').style.width = `${sentiment.neutral ?? 0}%`
  document.getElementById('negativeBar').style.width = `${sentiment.negative ?? 0}%`

  const list = document.getElementById('topQuestions')
  list.innerHTML = ''
  ;(data.top_questions || []).forEach((question, index) => {
    const li = document.createElement('li')
    li.textContent = `${question} · ${347 - index * 58}次`
    list.appendChild(li)
  })
}

function renderConfig(data) {
  document.getElementById('provider').textContent = data.provider || '--'
  document.getElementById('modelId').textContent = data.model || '--'
  document.getElementById('baseUrl').textContent = data.base_url || '--'
  document.getElementById('maskedKey').textContent = data.api_key_masked || '--'
}

function renderSentiment(data) {
  const box = document.getElementById('sentimentBox')
  box.textContent = `正向比例：${data.positive_rate}\n负向关键词：${(data.negative_keywords || []).join(' / ')}\n${(data.suggestions || []).map(item => `- ${item}`).join('\n')}`
}

function renderUploadResult(data) {
  document.getElementById('uploadResult').textContent = `doc_id：${data.doc_id}\nchunk_count：${data.chunk_count}\nindex_status：${data.index_status}`
}

async function init() {
  try {
    const [dashboard, config, sentiment] = await Promise.all([
      request('/api/admin/dashboard?date_range=today'),
      request('/api/admin/system/config'),
      request('/api/admin/report/sentiment?start_date=2026-04-01&end_date=2026-04-15'),
    ])
    renderDashboard(dashboard)
    renderConfig(config)
    renderSentiment(sentiment)
  } catch (error) {
    alert(error.message)
  }
}

document.getElementById('refreshSentiment').addEventListener('click', async () => {
  try {
    const start = document.getElementById('startDate').value
    const end = document.getElementById('endDate').value
    const data = await request(`/api/admin/report/sentiment?start_date=${encodeURIComponent(start)}&end_date=${encodeURIComponent(end)}`)
    renderSentiment(data)
  } catch (error) {
    alert(error.message)
  }
})

document.getElementById('uploadBtn').addEventListener('click', async () => {
  const fileInput = document.getElementById('knowledgeFile')
  const file = fileInput.files && fileInput.files[0]
  if (!file) {
    alert('请先选择知识库文件')
    return
  }

  const formData = new FormData()
  formData.append('file', file)
  formData.append('title', document.getElementById('uploadTitle').value)
  formData.append('category', document.getElementById('uploadCategory').value)

  try {
    const response = await fetch(`${baseUrl}/api/admin/knowledge/upload`, {
      method: 'POST',
      body: formData,
    })
    const result = await response.json()
    if (!response.ok || result.code !== 0) {
      throw new Error(result.message || '上传失败')
    }
    renderUploadResult(result.data)
    const dashboard = await request('/api/admin/dashboard?date_range=today')
    renderDashboard(dashboard)
  } catch (error) {
    alert(error.message)
  }
})

init()
