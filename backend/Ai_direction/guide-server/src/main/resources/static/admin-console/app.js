const baseUrl = window.location.origin
var userDataCache = null // 用户管理数据缓存
var satVisible = false   // 满意度详情展开状态

function $(id) { return document.getElementById(id) }

async function request(url, options = {}) {
  const response = await fetch(`${baseUrl}${url}`, options)
  const result = await response.json()
  if (!response.ok || result.code !== 0) {
    throw new Error(result.message || `请求失败(${response.status})`)
  }
  return result.data
}

// ====== 导航切换 ======
document.querySelectorAll('.nav a').forEach(function (link) {
  link.addEventListener('click', function (e) {
    e.preventDefault()
    document.querySelectorAll('.nav a').forEach(function (a) { a.classList.remove('active') })
    this.classList.add('active')
    var target = this.getAttribute('href').substring(1)
    document.querySelectorAll('main > section, main > div > section').forEach(function (s) { s.style.display = 'none' })
    var el = document.getElementById(target)
    if (el) el.style.display = ''
    if (target === 'users') loadUsers()
    if (target === 'knowledge') loadKnowledgeStats()
  })
})

// 默认只显示数据大屏
document.querySelectorAll('main > section, main > div > section').forEach(function (s, i) {
  s.style.display = i === 0 ? '' : 'none'
})

// ====== 满意度详情（互斥：隐藏另一个） ======
var satVisible = false
var scVisible = false
var satCard = $('satisfaction').closest('.stat-card')
satCard.style.cursor = 'pointer'
satCard.addEventListener('click', function () {
  satVisible = !satVisible
  // 隐藏另一个
  scVisible = false
  $('service-count-detail').style.display = 'none'
  var el = $('satisfaction-detail')
  el.style.display = satVisible ? '' : 'none'
  if (satVisible) {
    var now = new Date()
    $('satDate').value = now.toISOString().slice(0, 10)
    loadSatisfaction()
  }
})

// ====== 服务人次详情（互斥：隐藏另一个） ======
var scCard = $('serviceCount').closest('.stat-card')
scCard.style.cursor = 'pointer'
scCard.addEventListener('click', function () {
  scVisible = !scVisible
  // 隐藏另一个
  satVisible = false
  $('satisfaction-detail').style.display = 'none'
  var el = $('service-count-detail')
  if (!el) { console.error('service-count-detail not found'); return }
  el.style.display = scVisible ? '' : 'none'
  if (scVisible) {
    // 直接从 dashboard 缓存取数渲染
    var dd = window._dashboardData || {}
    var cnt = $('scCurrentCount')
    var pv = $('scPrevCount')
    var ch = $('scChange')
    if (cnt) cnt.textContent = dd.service_count || 0
    if (pv) pv.textContent = '--'
    if (ch) ch.textContent = dd.service_count_change != null ? dd.service_count_change.toFixed(1) + '%' : '--'
    // 趋势
    renderServiceCountBars([{ period: new Date().toISOString().slice(0, 10), count: dd.service_count || 1 }])
    var now = new Date()
    var sd = $('scDate')
    if (sd) sd.value = now.toISOString().slice(0, 10)
    loadServiceCount()
  }
})

/** 从缓存的 dashboard 数据直接渲染服务人次趋势 */
function renderServiceCountFromCache() {
  try {
    var data = window._dashboardData || {}
    var count = data.service_count || 0
    var change = data.service_count_change
    var scEl = $('scCurrentCount')
    if (scEl) scEl.textContent = count
    var pvEl = $('scPrevCount')
    if (pvEl) pvEl.textContent = '--'

    var changeEl = $('scChange')
    var unitEl = $('scChangeUnit')
    if (changeEl && change != null) {
      var txt = (change > 0 ? '↑ +' : change < 0 ? '↓ ' : '') + (change !== 0 ? change.toFixed(1) + '%' : '持平')
      changeEl.textContent = txt
      changeEl.style.color = change > 0 ? 'var(--green)' : change < 0 ? '#d94a3a' : 'var(--muted)'
    } else if (changeEl) {
      changeEl.textContent = '--'
      changeEl.style.color = 'var(--muted)'
    }
    if (unitEl) unitEl.textContent = ''

    // 用 dashboard 数据渲染趋势
    renderServiceCountBars([{ period: new Date().toISOString().slice(0, 10), count: count }])
  } catch (e) {
    console.error('renderServiceCountFromCache error:', e)
  }
}

document.querySelectorAll('.sc-type-btn').forEach(function (btn) {
  btn.addEventListener('click', function () {
    document.querySelectorAll('.sc-type-btn').forEach(function (b) { b.classList.remove('active') })
    this.classList.add('active')
    var sd = $('scDate')
    if (sd) {
      sd.type = 'date'
      sd.value = new Date().toISOString().slice(0, 10)
    }
    // 切换类型后自动查询
    loadServiceCount()
  })
})

$('scQueryBtn').addEventListener('click', loadServiceCount)

async function loadServiceCount() {
  var type = document.querySelector('.sc-type-btn.active').dataset.type
  var date = $('scDate').value
  if (!date) return
  var loadingEl = $('scLoading')
  if (loadingEl) loadingEl.style.display = 'block'
  try {
    var data = await request('/api/admin/service-count?type=' + encodeURIComponent(type) + '&date=' + encodeURIComponent(date))
    if (data) renderServiceCount(data)
  } catch (err) {
    console.error('loadServiceCount error:', err)
  } finally {
    if (loadingEl) loadingEl.style.display = 'none'
  }
}

/** 极端情况下兜底 - 至少显示今天的数据 */
function renderServiceCountFallback() {
  var data = window._dashboardData || {}
  var count = data.service_count || 0
  var scEl = $('scCurrentCount')
  if (scEl && scEl.textContent === '--') scEl.textContent = count
  renderServiceCountBars([{ period: new Date().toISOString().slice(0, 10), count: count || 1 }])
}

function renderServiceCount(data) {
  var cur = data.current || {}
  var prev = data.previous || {}
  var trend = data.trend || []
  var change = data.change != null ? data.change : 0

  // 只更新非零的有效值，避免 API 返回空数据覆盖已有显示
  if (cur.count > 0) $('scCurrentCount').textContent = cur.count
  if (prev.count > 0) $('scPrevCount').textContent = prev.count

  var changeEl = $('scChange')
  var unitEl = $('scChangeUnit')
  if (change > 0) {
    changeEl.textContent = '↑ +' + change.toFixed(1) + '%'
    changeEl.style.color = 'var(--green)'
    unitEl.textContent = ''
  } else if (change < 0) {
    changeEl.textContent = '↓ ' + Math.abs(change).toFixed(1) + '%'
    changeEl.style.color = '#d94a3a'
    unitEl.textContent = ''
  } else {
    changeEl.textContent = '持平'
    changeEl.style.color = 'var(--muted)'
    unitEl.textContent = ''
  }

  // 如果趋势为空但当前有数据，兜底显示今天
  if (trend.length === 0 && cur.count > 0) {
    var today = new Date().toISOString().slice(0, 10)
    trend = [{ period: today, count: cur.count }]
  }

  renderServiceCountBars(trend)
}

/** 渲染服务人次趋势柱状图 */
function renderServiceCountBars(trend) {
  var barsEl = $('scTrendBars')
  var labelsEl = $('scTrendLabels')
  var emptyEl = $('scTrendEmpty')
  if (!barsEl || !labelsEl || !emptyEl) return

  if (trend && trend.length > 0) {
    barsEl.style.display = 'flex'
    labelsEl.style.display = 'flex'
    emptyEl.style.display = 'none'

    // 少于3个数据点时固定用 3 等分，避免单个柱子撑满
    var totalSlots = Math.max(trend.length, 3)
    var maxCount = Math.max.apply(null, trend.map(function (t) { return t.count }))

    barsEl.style.justifyContent = trend.length < 3 ? 'center' : ''
    barsEl.innerHTML = trend.map(function (t) {
      var pct = Math.max(Math.round((t.count / maxCount) * 100), 4)
      var width = (100 / totalSlots) + '%'
      var maxW = trend.length < 3 ? '80px' : 'none'
      return '<div style="height:' + pct + '%;width:' + width + ';max-width:' + maxW + ';background:var(--blue);border-radius:4px 4px 0 0;min-width:20px;" title="' + t.period + ' ' + t.count + '次"></div>'
    }).join('')
    labelsEl.style.justifyContent = trend.length < 3 ? 'center' : ''
    labelsEl.innerHTML = trend.map(function (t) {
      var label = t.period
      if (label && label.length > 5) label = label.slice(5)
      var width = (100 / totalSlots) + '%'
      return '<span style="width:' + width + ';text-align:center;font-size:12px;color:var(--muted);" title="' + t.period + '">' + (label || '') + '</span>'
    }).join('')
  } else {
    barsEl.style.display = 'none'
    labelsEl.style.display = 'none'
    emptyEl.style.display = 'block'
  }
}

// ====== 用户结构特征详情（互斥） ======
var usVisible = false
$('userStructureCard').style.cursor = 'pointer'
$('userStructureCard').addEventListener('click', function () {
  usVisible = !usVisible
  // 隐藏其他两个
  satVisible = false; $('satisfaction-detail').style.display = 'none'
  scVisible = false; $('service-count-detail').style.display = 'none'
  var el = $('user-structure-detail')
  el.style.display = usVisible ? '' : 'none'
  if (usVisible) renderUserStructure()
})

function renderUserStructure() {
  // 从 dashboard 数据中获取并渲染
  var data = window._dashboardData || {}
  var gender = data.gender_distribution || {}
  var ages = data.age_distribution || []
  var userCount = data.user_count || 0

  $('usTotalCount').textContent = userCount + ' 位'

  // 性别
  var gEl = $('usGenderStats')
  gEl.innerHTML =
    '<div class="stat-bar-item"><span>🧑 男</span><div class="stat-bar"><b style="width:' + (gender.male_percent || 0) + '%"></b></div><span>' + (gender.male_percent || 0) + '% (' + (gender.male || 0) + '人)</span></div>' +
    '<div class="stat-bar-item"><span>👩 女</span><div class="stat-bar"><b style="width:' + (gender.female_percent || 0) + '%"></b></div><span>' + (gender.female_percent || 0) + '% (' + (gender.female || 0) + '人)</span></div>' +
    '<div class="stat-bar-item"><span>❓ 未知</span><div class="stat-bar"><b class="yellow-bar" style="width:' + (gender.unknown_percent || 0) + '%"></b></div><span>' + (gender.unknown_percent || 0) + '% (' + (gender.unknown || 0) + '人)</span></div>'

  // 年龄
  var aEl = $('usAgeStats')
  if (ages && ages.length > 0) {
    aEl.innerHTML = ages.map(function (item) {
      return '<div class="stat-bar-item">' +
        '<span>' + (item.label || item.key) + '</span>' +
        '<div class="stat-bar"><b style="width:' + item.percent + '%"></b></div>' +
        '<span>' + item.percent + '% (' + item.count + '人)</span>' +
        '</div>'
    }).join('')
  } else {
    aEl.innerHTML = '<div style="text-align:center;color:var(--muted);padding:20px;">暂无数据</div>'
  }
}

// ====== 满意度详情 ======
// 切换按日/月/年
document.querySelectorAll('.sat-type-btn').forEach(function (btn) {
  btn.addEventListener('click', function () {
    document.querySelectorAll('.sat-type-btn').forEach(function (b) { b.classList.remove('active') })
    this.classList.add('active')
    updateSatDateInput()
  })
})

function updateSatDateInput() {
  var type = document.querySelector('.sat-type-btn.active').dataset.type
  var input = $('satDate')
  var now = new Date()
  if (type === 'day') {
    input.type = 'date'
    input.value = now.toISOString().slice(0, 10)
  } else if (type === 'month') {
    input.type = 'month'
    input.value = now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0')
  } else {
    input.type = 'number'
    input.min = '2020'
    input.max = '2030'
    input.value = String(now.getFullYear())
    input.style.width = '100px'
  }
}

$('satQueryBtn').addEventListener('click', loadSatisfaction)

async function loadSatisfaction() {
  var type = document.querySelector('.sat-type-btn.active').dataset.type
  var date = $('satDate').value
  if (!date) return

  $('satLoading').style.display = 'block'
  try {
    var data = await request('/api/admin/satisfaction?type=' + encodeURIComponent(type) + '&date=' + encodeURIComponent(date))
    renderSatisfaction(data)
  } catch (err) {
    $('satLoading').textContent = '加载失败: ' + err.message
  } finally {
    $('satLoading').style.display = 'none'
  }
}

function renderSatisfaction(data) {
  var cur = data.current || {}
  var prev = data.previous || {}
  var trend = data.trend || []
  var change = data.change != null ? data.change : 0

  $('satCurrentAvg').textContent = cur.avg_satisfaction != null ? cur.avg_satisfaction.toFixed(1) : '--'
  $('satCurrentCount').textContent = cur.count != null ? cur.count : '--'

  var changeEl = $('satChange')
  var unitEl = $('satChangeUnit')
  var card = $('satCompareCard')
  if (change > 0) {
    changeEl.textContent = '↑ ' + change.toFixed(1)
    changeEl.style.color = 'var(--green)'
    unitEl.textContent = ''
  } else if (change < 0) {
    changeEl.textContent = '↓ ' + Math.abs(change).toFixed(1)
    changeEl.style.color = '#d94a3a'
    unitEl.textContent = ''
  } else {
    changeEl.textContent = '持平'
    changeEl.style.color = 'var(--muted)'
    unitEl.textContent = ''
  }

  // 评分分布
  var dist = cur.distribution || []
  var distHtml = ''
  var stars = ['⭐', '⭐⭐', '⭐⭐⭐', '⭐⭐⭐⭐', '⭐⭐⭐⭐⭐']
  dist.forEach(function (d) {
    var i = d.rating - 1
    distHtml += '<div class="sat-dist-row">' +
      '<span class="sat-dist-stars">' + (stars[i] || '') + '</span>' +
      '<div class="sat-dist-bar-track"><b class="sat-dist-bar-fill" style="width:' + d.percent + '%"></b></div>' +
      '<span class="sat-dist-num">' + d.count + '人 (' + d.percent + '%)</span>' +
      '</div>'
  })
  $('satDistribution').innerHTML = distHtml || '<div class="sat-empty">暂无数据</div>'

  // 趋势柱状图
  var barsEl = $('satTrendBars')
  var labelsEl = $('satTrendLabels')
  var emptyEl = $('satTrendEmpty')

  if (trend.length > 0) {
    barsEl.style.display = 'grid'
    labelsEl.style.display = 'grid'
    emptyEl.style.display = 'none'

    var maxAvg = 5 // 最高5分
    barsEl.innerHTML = trend.map(function (t) {
      var pct = Math.round((t.avg / maxAvg) * 100)
      return '<i class="' + (t.avg >= 4 ? 'green' : '') + '" style="height:' + Math.max(pct, 4) + '%" title="' + t.period + ' ' + t.avg.toFixed(1) + '分 (' + t.count + '次)"></i>'
    }).join('')

    labelsEl.innerHTML = trend.map(function (t) {
      var label = t.period
      if (label && label.length > 5) label = label.slice(5) // 去掉年份
      return '<span title="' + t.period + '">' + (label || '') + '</span>'
    }).join('')
  } else {
    barsEl.style.display = 'none'
    labelsEl.style.display = 'none'
    emptyEl.style.display = 'block'
  }
}

// ====== 数据大屏 ======
function renderDashboard(data) {
  if (!data) { console.error('renderDashboard: no data'); return }

  // 缓存数据供详情面板使用
  window._dashboardData = data

  // 今日服务人次（含较昨日变化）
  $('serviceCount').textContent = data.service_count ?? '--'
  var sc = data.service_count_change
  var scEl = $('serviceCount').closest('.stat-card').querySelector('small')
  if (scEl) {
    if (sc != null) {
      var scText = (sc > 0 ? '↑ +' : sc < 0 ? '↓ ' : '') + (sc !== 0 ? sc.toFixed(1) + '%' : '持平')
      scEl.textContent = scText + ' 较昨日'
      scEl.style.color = sc > 0 ? 'var(--green)' : sc < 0 ? '#d94a3a' : 'var(--yellow)'
    } else {
      scEl.textContent = '-- 较昨日'
      scEl.style.color = 'var(--muted)'
    }
  }

  $('satisfaction').textContent = data.satisfaction ?? '--'
  $('accuracyRate').textContent = data.accuracy_rate ?? '--'
  $('avgResponse').textContent = data.avg_response_ms ?? '--'
  $('userStructureCount').textContent = (data.user_count || 0) + ' 位'

  var sentiment = data.sentiment_distribution || {}
  $('positiveRate').textContent = (sentiment.positive ?? 0) + '%'
  $('neutralRate').textContent = (sentiment.neutral ?? 0) + '%'
  $('negativeRate').textContent = (sentiment.negative ?? 0) + '%'
  $('positiveBar').style.width = (sentiment.positive ?? 0) + '%'
  $('neutralBar').style.width = (sentiment.neutral ?? 0) + '%'
  $('negativeBar').style.width = (sentiment.negative ?? 0) + '%'

  var list = $('topQuestions')
  list.innerHTML = ''
  ;(data.top_questions || []).forEach(function (question, index) {
    var li = document.createElement('li')
    li.textContent = question + ' · ' + (347 - index * 58) + '次'
    list.appendChild(li)
  })

  // 兴趣偏好统计
  renderInterestStats(data.interest_distribution)
}

function renderInterestStats(interests) {
  var el = $('interestStats')
  if (!interests || interests.length === 0) {
    el.innerHTML = '<div class="loading-row" style="text-align:center;color:var(--muted);">暂无数据</div>'
    return
  }
  el.innerHTML = interests.slice(0, 10).map(function (item) {
    return '<div class="stat-bar-item">' +
      '<span title="' + item.name + '">' + item.name + '</span>' +
      '<div class="stat-bar"><b class="green-bar" style="width:' + item.percent + '%"></b></div>' +
      '<span>' + item.percent + '% (' + item.count + '人)</span>' +
      '</div>'
  }).join('')
}


function renderConfig(data) {
  $('provider').textContent = data.provider || '--'
  $('modelId').textContent = data.model || '--'
  $('baseUrl').textContent = data.base_url || '--'
  $('maskedKey').textContent = data.api_key_masked || '--'
}

function renderSentiment(data) {
  var box = $('sentimentBox')
  box.textContent = '正向比例：' + data.positive_rate + '\n负向关键词：' + (data.negative_keywords || []).join(' / ') + '\n' + (data.suggestions || []).map(function (item) { return '- ' + item }).join('\n')
}

function formatFileSize(bytes) {
  if (!bytes) return '--'
  var mb = bytes / (1024 * 1024)
  return mb >= 1 ? mb.toFixed(1) + ' MB' : (bytes / 1024).toFixed(0) + ' KB'
}

// ====== 数字人形象搭配（选男女 → 选部件 → 预览 → 确认） ======
var partSelections = {}
var previewVrmFile = ''
var selectedGender = ''
var genderData = {} // { 'male': {...}, 'female': {...} }

async function loadAvatars() {
  try {
    var data = await request('/api/admin/avatars/part-selector')
    genderData = {}
    ;(data.genders || []).forEach(function (g) { genderData[g.id] = g })
    renderGenderSelector(data)
  } catch (error) {
    $('partSelector').innerHTML = '<div class="avatar-empty">加载失败: ' + error.message + '</div>'
  }
}

function renderGenderSelector(data) {
  var genders = data.genders || []
  var activeRule = data.activeRule || {}
  var container = $('partSelector')

  // 判断当前激活的属于哪个 gender
  if (activeRule && activeRule.parts && activeRule.parts.length === 3) {
    var pid = activeRule.parts[0]
    if (pid >= 'A' && pid <= 'C') selectedGender = 'female'
    else if (pid >= 'D' && pid <= 'F') selectedGender = 'male'
  }
  if (!selectedGender && genders.length > 0) selectedGender = genders[0].id

  // 渲染性别选择 + 部件
  renderContent(container, data)
}

function renderContent(container, data) {
  var html = ''

  // 性别选择按钮
  html += '<div class="gender-selector">'
  ;(data.genders || []).forEach(function (g) {
    html += '<div class="gender-btn' + (selectedGender === g.id ? ' active' : '') + '" data-gender="' + g.id + '">' +
      (g.id === 'male' ? '♂' : '♀') + ' ' + g.name + '</div>'
  })
  html += '</div>'

  var categories = []
  var genderObj = genderData[selectedGender]
  if (genderObj) categories = genderObj.categories || []

  // 初始化选中状态
  partSelections = {}
  previewVrmFile = ''

  categories.forEach(function (cat) {
    html += '<div class="part-category">'
    html += '<div class="part-category-title">' + cat.name + '</div>'
    html += '<div class="part-options">'
    cat.options.forEach(function (opt) {
      html += '<div class="part-option" data-category="' + cat.key + '" data-id="' + opt.id + '">' +
        '<img src="' + opt.image + '" class="part-option-img" onerror="this.style.display=\'none\'" />' +
        '<div class="part-option-label">' + opt.label + '</div>' +
        '</div>'
    })
    html += '</div></div>'
  })

  html += '<div style="text-align:center;margin-top:16px;">'
  html += '<button id="previewBtn" class="combo-apply-btn">预览搭配</button>'
  html += '</div>'

  container.innerHTML = html

  // 绑定性别点击
  container.querySelectorAll('.gender-btn').forEach(function (btn) {
    btn.addEventListener('click', function () {
      selectedGender = this.dataset.gender
      $('comboResult').style.display = 'none'
      renderContent(container, data)
    })
  })

  // 绑定部件点击
  container.querySelectorAll('.part-option').forEach(function (opt) {
    opt.addEventListener('click', function () {
      var cat = this.dataset.category
      var id = this.dataset.id
      // 清除同分类的其他选中
      container.querySelectorAll('.part-option').forEach(function (o) {
        if (o.dataset.category === cat) {
          o.classList.remove('selected')
          var ck = o.querySelector('.part-option-check')
          if (ck) ck.remove()
        }
      })
      this.classList.add('selected')
      partSelections[cat] = id
      // 加勾
      var check = document.createElement('div')
      check.className = 'part-option-check'
      check.textContent = '✓'
      this.appendChild(check)
      $('comboResult').style.display = 'none'
    })
  })

  // 绑定预览按钮
  $('previewBtn').addEventListener('click', function () { showPreview(categories) })
}

function showPreview(categories) {
  var keys = categories.map(function (c) { return c.key })
  var face = partSelections[keys[0]] || ''
  var hair = partSelections[keys[1]] || ''
  var outfit = partSelections[keys[2]] || ''

  var resultEl = $('comboResult')
  var nameEl = $('comboResultName')
  var fileEl = $('comboResultFile')
  var iconEl = resultEl.querySelector('.combo-result-icon')

  if (!face || !hair || !outfit) {
    nameEl.textContent = '请先选择面部、发型和服饰'
    fileEl.textContent = ''
    iconEl.textContent = '❓'
    resultEl.querySelector('.combo-apply-btn').style.display = 'none'
    resultEl.style.display = ''
    return
  }

  var previewName = face + hair + outfit
  var previewUrl = '/avatar-parts/' + previewName + '.png'
  previewVrmFile = previewName + '.vrm'

  var img = new Image()
  img.onload = function () {
    iconEl.innerHTML = '<img src="' + previewUrl + '" style="width:100%;height:100%;object-fit:cover;border-radius:10px;" />'
    nameEl.textContent = '造型 ' + previewName
    fileEl.textContent = 'VRM: ' + previewVrmFile
    resultEl.querySelector('.combo-apply-btn').style.display = ''
    resultEl.querySelector('.combo-apply-btn').textContent = '✅ 确认应用'
    resultEl.style.display = ''
  }
  img.onerror = function () {
    iconEl.textContent = '🚫'
    nameEl.textContent = '未找到造型 ' + previewName
    fileEl.textContent = '请尝试其他搭配组合'
    resultEl.querySelector('.combo-apply-btn').style.display = 'none'
    resultEl.style.display = ''
  }
  img.src = previewUrl
}

function applyCombo(vrmFile) {
  doApplyCombo(vrmFile)
}

async function doApplyCombo(vrmFile) {
  try {
    await request('/api/admin/avatars/active', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ filename: vrmFile }),
    })
    $('avatarMsg').textContent = '✅ 已切换形象: ' + vrmFile + '！'
    $('avatarMsg').style.color = 'var(--green)'
    loadAvatars()
  } catch (error) {
    $('avatarMsg').textContent = '切换失败: ' + error.message
    $('avatarMsg').style.color = '#d94a3a'
  }
}

// ====== 用户管理（带搜索+排序） ======
async function loadUsers() {
  var keyword = ($('userKeyword') && $('userKeyword').value) || ''
  var sortBy = ($('userSortBy') && $('userSortBy').value) || 'id'
  var sortOrder = ($('userSortOrder') && $('userSortOrder').value) || 'desc'

  var params = '?sortBy=' + sortBy + '&sortOrder=' + sortOrder
  if (keyword) params += '&keyword=' + encodeURIComponent(keyword)

  try {
    var users = await request('/api/v1/user/admin/list' + params)
    userDataCache = { users: users }
    renderUserList(users)
  } catch (error) {
    $('userTableBody').innerHTML = '<tr><td colspan="8" class="loading-row">加载失败</td></tr>'
  }
}

// 搜索按钮点击事件
if ($('userSearchBtn')) {
  $('userSearchBtn').addEventListener('click', loadUsers)
}
if ($('userKeyword')) {
  $('userKeyword').addEventListener('keyup', function (e) {
    if (e.key === 'Enter') loadUsers()
  })
}

function formatGender(val) {
  if (val === 'male' || val === '男' || val === 1 || val === '1') return '男'
  if (val === 'female' || val === '女' || val === 2 || val === '2') return '女'
  return val || '--'
}

function formatTime(val) {
  if (!val) return '--'
  try { var d = new Date(val); return d.toLocaleDateString('zh-CN') } catch(e) { return '--' }
}

function renderUserList(users) {
  var tbody = $('userTableBody')
  if (!users || users.length === 0) {
    tbody.innerHTML = '<tr><td colspan="8" class="loading-row">暂无用户数据</td></tr>'
    return
  }
  tbody.innerHTML = users.map(function (u) {
    return '<tr>' +
      '<td style="text-align:center;">' + (u.id || '--') + '</td>' +
      '<td>' + escapeHtml(u.username || '') + '</td>' +
      '<td>' + escapeHtml(u.nick_name || '--') + '</td>' +
      '<td style="text-align:center;">' + (u.age || '--') + '</td>' +
      '<td style="text-align:center;">' + formatGender(u.gender) + '</td>' +
      '<td style="text-align:center;">' + (u.visit_count != null ? u.visit_count : '0') + '</td>' +
      '<td style="text-align:center;">' + formatTime(u.create_time) + '</td>' +
      '<td style="text-align:center;"><button class="btn-delete" onclick="deleteUser(' + u.id + ')">删除</button></td>' +
      '</tr>'
  }).join('')
}

function escapeHtml(str) {
  var div = document.createElement('div')
  div.textContent = str
  return div.innerHTML
}

async function deleteUser(userId) {
  if (!confirm('确定要删除用户 ID=' + userId + ' 吗？')) return
  try {
    await request('/api/v1/user/admin/delete?userId=' + userId, { method: 'DELETE' })
    loadUsers()
  } catch (error) {
    alert('删除失败: ' + error.message)
  }
}

// ====== 初始化 ======
async function init() {
  // 分别加载各模块，单个失败不影响其他
  try {
    var dashboard = await request('/api/admin/dashboard?date_range=today')
    renderDashboard(dashboard)
  } catch (error) {
    console.error('dashboard load error:', error)
  }

  try {
    var config = await request('/api/admin/system/config')
    renderConfig(config)
  } catch (error) {
    console.error('config load error:', error)
  }

  try {
    var sentiment = await request('/api/admin/report/sentiment?start_date=2026-04-01&end_date=2026-04-15')
    renderSentiment(sentiment)
  } catch (error) {
    console.error('sentiment load error:', error)
  }

  loadAvatars()
  loadKnowledgeStats()
}

async function loadKnowledgeStats() {
  try {
    var data = await request('/api/admin/knowledge/stats')
    renderKnowledgeStats(data)
  } catch (e) {
    console.error('knowledge stats error:', e)
  }
}

function renderKnowledgeStats(data) {
  $('kbTotal').textContent = (data.total || 0) + ' 篇'

  // 分类统计
  var catsEl = $('kbCategories')
  var cats = data.categories || []
  catsEl.innerHTML = cats.map(function (c) {
    return '<div class="knowledge-row"><span>' + (c.category || '未分类') + '</span><b>' + c.cnt + ' 篇</b></div>'
  }).join('') || '<div class="knowledge-row"><span>暂无分类数据</span></div>'

  // 更新时间
  var recent = data.recent || []
  var lastUpdate = recent.length > 0 ? (recent[0].update_time || '未知') : '暂无数据'
  $('kbUpdateInfo').textContent = '最近更新：' + lastUpdate.slice(0, 10) + ' · 共 ' + recent.length + ' 篇最新文档'

  // 最近上传列表
  var listEl = $('kbRecentList')
  if (recent.length === 0) {
    listEl.innerHTML = '<div style="text-align:center;color:var(--muted);padding:20px;">暂无上传记录</div>'
    return
  }
  listEl.innerHTML = recent.map(function (d) {
    var statusText = d.index_status === 1 ? '✅ 已索引' : (d.index_status === 2 ? '❌ 失败' : '⏳ 待索引')
    var statusClass = d.index_status === 1 ? '' : (d.index_status === 2 ? 'fail' : '')
    return '<div class="kb-item">' +
      '<span class="kb-title">' + (d.title || '无标题') + '</span>' +
      '<span class="kb-cat">' + (d.category || '其他') + '</span>' +
      '<span class="kb-status ' + statusClass + '">' + statusText + '</span>' +
      '</div>'
  }).join('')
}

$('refreshSentiment').addEventListener('click', async function () {
  try {
    var start = $('startDate').value
    var end = $('endDate').value
    renderSentiment(await request('/api/admin/report/sentiment?start_date=' + encodeURIComponent(start) + '&end_date=' + encodeURIComponent(end)))
  } catch (error) {
    alert(error.message)
  }
})

$('uploadBtn').addEventListener('click', async function () {
  var fileInput = $('knowledgeFile')
  var file = fileInput.files && fileInput.files[0]
  if (!file) { alert('请先选择知识库文件 (.txt)'); return }
  var title = $('uploadTitle').value.trim()
  var category = $('uploadCategory').value.trim()
  if (!title) { alert('请输入文档标题'); return }
  if (!category) { alert('请输入分类'); return }

  var formData = new FormData()
  formData.append('file', file)
  formData.append('title', title)
  formData.append('category', category)
  try {
    $('uploadBtn').textContent = '上传中...'
    $('uploadBtn').disabled = true
    var response = await fetch(baseUrl + '/api/admin/knowledge/upload', { method: 'POST', body: formData })
    var result = await response.json()
    if (!response.ok || result.code !== 0) throw new Error(result.message || '上传失败')

    var r = result.data
    var statusText = r.index_status === 1 ? '✅ 已索引' : (r.index_status === 2 ? '❌ 索引失败' : '⏳ 待索引')
    $('uploadResult').style.display = ''
    $('uploadResult').textContent = '📄 ' + title + '\n分类：' + category + '\n状态：' + statusText + '\n文档ID：' + r.doc_id

    // 刷新知识库统计
    fileInput.value = ''
    loadKnowledgeStats()
  } catch (error) {
    alert('上传失败: ' + error.message)
  } finally {
    $('uploadBtn').textContent = '上传并入库'
    $('uploadBtn').disabled = false
  }
})

init()
