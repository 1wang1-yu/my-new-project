var api = require('../../utils/api')
var util = require('../../utils/util')
var Avatar3D = require('../../utils/avatar-3d').Avatar3D
var buildLipSequence = require('../../utils/lip-sync').buildLipSequence

function createSessionId() {
  return 'session_' + Date.now()
}

var innerAudioContext = wx.createInnerAudioContext()

var avatar3d = null
var lipTimer = null
var is3dReady = false

Page({
  data: {
    baseUrl: '',
    sessionId: createSessionId(),
    userId: 10001,
    scenicName: '灵山胜境',
    avatarName: '小导·景区数字导游',
    subtitle: '您好，请问有什么可以帮您？',
    message: '',
    interestsText: '历史文化,自然风光,摄影打卡',
    interestTags: ['历史文化', '自然风光', '摄影打卡'],
    durationMinutes: 180,
    messages: [],
    suggestedQuestions: ['有什么好玩的景点？', '推荐一条游览路线', '附近有什么美食'],
    routeResult: [],
    loading: false,
    errorText: '',
    asrText: '',
    ttsResult: null,
    isRecording: false,
    isPlaying: false,
  },

  onLoad: function () {
    var app = getApp()
    var baseUrl = util.loadBaseUrl((app && app.globalData && app.globalData.baseUrl) || 'http://127.0.0.1:8081')
    this.setData({
      baseUrl: baseUrl,
      scenicName: (app && app.globalData && app.globalData.scenicName) || '灵山胜境',
      avatarName: (app && app.globalData && app.globalData.avatarName) || '小导·景区数字导游',
      interestTags: util.splitInterests(this.data.interestsText),
    })
    this.syncBaseUrl(baseUrl)
    this.getRoute()
    this.loadHistory()

    var self = this
    innerAudioContext.onEnded(function () {
      self.stopLipSync()
      self.setData({ isPlaying: false })
    })
    innerAudioContext.onStop(function () {
      self.stopLipSync()
    })
  },

  onReady: function () {
    var self = this
    setTimeout(function () {
      self.initAvatar3D()
    }, 400)
  },

  onUnload: function () {
    if (this._currentChatTask) {
      try { this._currentChatTask.abort() } catch (e) { }
      this._currentChatTask = null
    }
    if (this.recorderManager) {
      this.recorderManager.stop()
    }
    innerAudioContext.stop()
    this.stopLipSync()
    if (avatar3d) {
      avatar3d.dispose()
      avatar3d = null
    }
    is3dReady = false
  },

  // ========== 3D 头像初始化 ==========
  initAvatar3D: function () {
    var self = this
    var query = wx.createSelectorQuery().in(this)
    query.select('#glCanvas')
      .fields({ node: true, size: true })
      .exec(function (res) {
        if (!res || !res[0] || !res[0].node) {
          console.warn('3D Canvas 节点获取失败，500ms 后重试')
          setTimeout(function () { self.initAvatar3D() }, 500)
          return
        }
        var canvas = res[0].node
        var w = res[0].width
        var h = res[0].height
        if (!w || !h) {
          var sys = wx.getSystemInfoSync()
          w = 280 / 750 * sys.windowWidth
          h = 320 / 750 * sys.windowWidth
        }

        try {
          var modelUrl = self.data.baseUrl + '/aimath.vrm?t=' + Date.now()
          avatar3d = new Avatar3D()
          avatar3d.init(canvas, w, h, modelUrl)
          avatar3d.startLoop()
          is3dReady = true
          console.log('3D 头像已就绪, modelUrl:', modelUrl)
        } catch (e) {
          console.error('3D 头像初始化失败:', e)
        }
      })
  },

  // ========== 口型同步 (音频 currentTime 驱动) ==========
  startLipSync: function (text, durationMs) {
    this.stopLipSync()
    if (!is3dReady || !avatar3d) return

    var seq = buildLipSequence(text, durationMs)
    if (!seq || seq.length < 2) return

    avatar3d.setSpeaking(true)
    var self = this

    // 音频就绪后用真实时长重建口型序列，对齐实际语速
    var onCanplay = function () {
      var realDur = innerAudioContext.duration * 1000
      if (realDur > 500 && Math.abs(realDur - durationMs) > 300) {
        seq = buildLipSequence(text, realDur)
      }
    }
    innerAudioContext.onCanplay(onCanplay)
    this._lipCanplayCleanup = function () {
      innerAudioContext.offCanplay(onCanplay)
    }

    function drive() {
      var currentTime = innerAudioContext.currentTime * 1000
      var viseme = 'rest'
      var open = 0.02

      for (var i = 0; i < seq.length - 1; i++) {
        if (currentTime >= seq[i].time && currentTime < seq[i + 1].time) {
          var dur = seq[i + 1].time - seq[i].time
          var e = currentTime - seq[i].time
          var t = dur > 0 ? e / dur : 0
          open = seq[i].open + (seq[i + 1].open - seq[i].open) * t
          viseme = seq[i].viseme
          break
        }
      }
      if (currentTime >= seq[seq.length - 1].time) {
        open = 0.02
        viseme = 'rest'
      }

      if (avatar3d) avatar3d.setViseme(viseme, open)

      // 不检查 paused（play() 可能尚未执行），靠 onEnded/onStop 收尾
      if (currentTime < seq[seq.length - 1].time + 300) {
        lipTimer = setTimeout(drive, 40)
      } else {
        if (avatar3d) avatar3d.setSpeaking(false)
        lipTimer = null
      }
    }

    drive()
  },

  stopLipSync: function () {
    if (lipTimer) {
      clearTimeout(lipTimer)
      lipTimer = null
    }
    if (this._lipCanplayCleanup) {
      this._lipCanplayCleanup()
      this._lipCanplayCleanup = null
    }
    if (avatar3d) {
      avatar3d.setSpeaking(false)
    }
  },

  // ========== 工具方法 ==========
  syncBaseUrl: function (baseUrl) {
    var app = getApp()
    var targetUrl = baseUrl || this.data.baseUrl
    if (app && app.globalData) {
      app.globalData.baseUrl = targetUrl
    }
    util.saveBaseUrl(targetUrl)
  },

  onFieldChange: function (e) {
    var field = e.currentTarget.dataset.field
    var value = e.detail.value
    var payload = {}
    payload[field] = value
    if (field === 'interestsText') {
      payload.interestTags = util.splitInterests(value)
    }
    this.setData(payload)
  },

  onMessageInput: function (e) {
    this.setData({ message: e.detail.value })
  },

  useSuggestedQuestion: function (e) {
    var question = e.currentTarget.dataset.question
    this.setData({ message: question })
    this.sendChat()
  },

  setLoading: function (loading) {
    this.setData({ loading: loading })
    if (loading) {
      wx.showNavigationBarLoading()
    } else {
      wx.hideNavigationBarLoading()
    }
  },

  // ========== 发送文字消息（流式）==========
  sendChat: function () {
    var msg = this.data.message.trim()
    if (!msg) return

    this.syncBaseUrl()
    this.setLoading(true)
    this.setData({ errorText: '', message: '' })

    var nextMessages = this.data.messages.concat({ role: 'user', content: msg })
    // 占位——流式文本将逐步填充
    var placeholderIdx = nextMessages.length
    nextMessages.push({ role: 'assistant', content: '', streaming: true })
    this.setData({ messages: nextMessages })

    var self = this
    var doneMeta = null

    var task = api.chatStream({
      user_id: this.data.userId,
      session_id: this.data.sessionId,
      message: msg,
      input_type: 'text'
    }, function (chunk, meta) {
      // 收到文本片段：追加到占位消息
      var msgs = self.data.messages
      if (placeholderIdx < msgs.length) {
        msgs[placeholderIdx].content += chunk
        if (meta) doneMeta = meta
        self.setData({ messages: msgs })
      }
    }, function (err, meta) {
      self.setLoading(false)
      if (err) {
        console.error('chat stream error:', err)
        self.setData({ errorText: err.errMsg || '智能问答调用失败' })
        return
      }
      // 流结束——更新元数据并自动播报
      var msgs = self.data.messages
      if (placeholderIdx < msgs.length) {
        msgs[placeholderIdx].streaming = false
        self.setData({ messages: msgs })
      }

      var resultMeta = meta || doneMeta
      if (resultMeta) {
        self.setData({
          sessionId: resultMeta.session_id || self.data.sessionId,
          suggestedQuestions: resultMeta.suggested_questions || self.data.suggestedQuestions,
        })
      } else {
        // 解析答案中的建议追问作为兜底
        var answer = msgs[placeholderIdx] ? msgs[placeholderIdx].content : ''
        var sug = self.extractSuggestedFromAnswer(answer)
        if (sug.length > 0) self.setData({ suggestedQuestions: sug })
      }

      var answerText = msgs[placeholderIdx] ? msgs[placeholderIdx].content : ''
      self.saveHistory()
      if (answerText) {
        self.setData({ subtitle: '讲解完成' })
        self.speakText(answerText)
      }
    })

    // 保存 task 引用以便 onUnload 时 abort
    this._currentChatTask = task
  },

  /** 从答案文本中提取建议追问（兜底） */
  extractSuggestedFromAnswer: function (text) {
    if (!text) return []
    var marker = text.indexOf('建议追问：')
    if (marker < 0) return []
    var suffix = text.substring(marker + 5).trim()
    return suffix.split(/[｜|]/).map(function (s) { return s.trim() }).filter(function (s) { return s.length > 0 }).slice(0, 3)
  },

  // ========== 语音录制 (ASR) ==========
  startRecording: function () {
    this.setData({ isRecording: true, errorText: '' })
    var self = this

    if (!this.recorderManager) {
      this.recorderManager = wx.getRecorderManager()

      this.recorderManager.onStop(function (res) {
        self.setData({ isRecording: false })
        self.sendAudioToAsr(res.tempFilePath)
      })

      this.recorderManager.onError(function (err) {
        console.error('录音失败:', err)
        self.setData({ isRecording: false, errorText: '录音失败: ' + (err.errMsg || '未知错误') })
      })
    }

    this.recorderManager.start({
      format: 'mp3',
      duration: 30000,
    })
  },

  stopRecording: function () {
    if (this.recorderManager && this.data.isRecording) {
      this.recorderManager.stop()
    }
  },

  toggleRecording: function () {
    if (this.data.isRecording) {
      this.stopRecording()
    } else {
      this.startRecording()
    }
  },

  sendAudioToAsr: function (tempFilePath) {
    this.setLoading(true)
    var self = this
    new Promise(function (resolve, reject) {
      wx.getFileSystemManager().readFile({
        filePath: tempFilePath,
        encoding: 'base64',
        success: function (res) { resolve(res.data) },
        fail: function (err) { reject(err) },
      })
    }).then(function (fileData) {
      return api.asr({ audio_base64: fileData, format: 'mp3' })
    }).then(function (data) {
      var text = data.text || ''
      self.setData({ asrText: text })
      if (text && text.trim()) {
        self.setData({ message: text.trim() })
        return self.sendChat()
      } else {
        self.setData({ errorText: '未能识别到语音内容，请重试' })
      }
    }).catch(function (err) {
      console.error('ASR error:', err)
      self.setData({ errorText: '语音识别失败: ' + (err.message || '未知错误') })
    }).finally(function () {
      self.setLoading(false)
    })
  },

  // ========== 语音播报 ==========
  /** 播报指定文本（自动调用 TTS + 口型同步） */
  speakText: function (text) {
    var self = this
    this.syncBaseUrl()
    this.setLoading(true)

    api.tts({
      text: text,
      voice_id: '101001',
      speed: 1,
      emotion: 'calm',
    }).then(function (data) {
      if (data.error) {
        self.setData({ errorText: '语音合成失败: ' + data.error })
        self.setLoading(false)
        return
      }

      if (data.audio_base64) {
        self.setData({ ttsResult: data, isPlaying: true })
        var durMs = data.duration_ms || Math.max(text.length * 250, 1000)

        var filePath = wx.env.USER_DATA_PATH + '/tts_' + Date.now() + '.mp3'
        wx.getFileSystemManager().writeFile({
          filePath: filePath,
          data: data.audio_base64,
          encoding: 'base64',
          success: function () {
            innerAudioContext.src = filePath
            self.startLipSync(text, durMs)
            innerAudioContext.play()
          },
          fail: function (err) {
            console.error('音频写入失败:', err)
            self.setData({ errorText: '音频播放失败', isPlaying: false })
            self.stopLipSync()
            self.setLoading(false)
          },
        })
      } else if (data.audio_url && data.audio_url.indexOf('mock.guide.local') === -1) {
        self.setData({ ttsResult: data, isPlaying: true })
        var durMs2 = data.duration_ms || Math.max(text.length * 250, 1000)
        self.startLipSync(text, durMs2)
        innerAudioContext.src = data.audio_url
        innerAudioContext.play()
      } else {
        self.setData({ errorText: '语音合成未返回音频，请检查 API Key 配置' })
        self.setLoading(false)
      }
    }).catch(function (err) {
      console.error('TTS error:', err)
      self.setData({ errorText: '语音合成失败: ' + (err.message || '未知错误') })
      self.setLoading(false)
    })
  },

  /** 手动点击播报按钮（播报最新 AI 回复） */
  getTts: function () {
    var messages = this.data.messages
    var latestAssistant = null
    for (var i = messages.length - 1; i >= 0; i--) {
      if (messages[i].role === 'assistant') {
        latestAssistant = messages[i]
        break
      }
    }
    if (!latestAssistant) {
      this.setData({ errorText: '暂无内容可以播报' })
      return
    }
    this.speakText(latestAssistant.content)
  },

  stopTts: function () {
    innerAudioContext.stop()
    this.stopLipSync()
    this.setData({ isPlaying: false })
  },

  // ========== 路线推荐 ==========
  getRoute: function () {
    this.syncBaseUrl()
    var self = this
    var interests = util.splitInterests(this.data.interestsText)
    api.recommendRoute({
      user_id: this.data.userId,
      interests: interests,
      duration_minutes: this.data.durationMinutes,
    }).then(function (data) {
      self.setData({
        routeResult: data.routes || [],
        interestTags: interests,
      })
    }).catch(function (err) {
      console.error('route error:', err)
      self.setData({
        routeResult: [
          { name: '经典游览路线', stops: ['入口广场', '主景点区', '观景平台', '文创商店'], estimated_time: 240, highlights: ['主景点区', '观景平台'] }
        ],
      })
    })
  },

  openDashboard: function () {
    wx.navigateTo({ url: '/pages/dashboard/index' })
  },

  startNewChat: function () {
    var messages = this.data.messages
    if (messages && messages.length > 0) {
      this.saveHistory(true)
    }
    this.setData({
      messages: [],
      sessionId: createSessionId(),
      suggestedQuestions: ['有什么好玩的景点？', '推荐一条游览路线', '附近有什么美食'],
    })
    wx.showToast({ title: '新对话已开始', icon: 'success' })
  },

  // ========== 聊天记录持久化 ==========
  loadHistory: function () {
    try {
      var stored = wx.getStorageSync('guide_chat_history')
      var history = stored || []
      if (history.length > 0) {
        var last = history[history.length - 1]
        if (last.messages && last.messages.length > 0) {
          this.setData({ messages: last.messages })
        }
      }
    } catch (e) {
      console.warn('加载聊天记录失败', e)
    }
  },

  saveHistory: function (forceNew) {
    try {
      var messages = this.data.messages
      if (!messages || messages.length === 0) return
      var stored = wx.getStorageSync('guide_chat_history') || []
      var now = new Date()
      var dateStr = now.getFullYear() + '-' +
        ('0' + (now.getMonth() + 1)).slice(-2) + '-' +
        ('0' + now.getDate()).slice(-2)
      var timeStr = ('0' + now.getHours()).slice(-2) + ':' +
        ('0' + now.getMinutes()).slice(-2)
      var last = !forceNew && stored[stored.length - 1]
      if (last && last.date === dateStr) {
        last.time = timeStr
        last.timestamp = now.getTime()
        last.messages = messages.slice()
      } else {
        stored.push({
          id: 'h_' + now.getTime(),
          date: dateStr,
          time: timeStr,
          timestamp: now.getTime(),
          scenicName: this.data.scenicName,
          messages: messages.slice()
        })
      }
      wx.setStorageSync('guide_chat_history', stored)
    } catch (e) {
      console.warn('保存聊天记录失败', e)
    }
  },
})
