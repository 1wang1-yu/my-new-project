var api = require('../../utils/api')
var util = require('../../utils/util')
var i18n = require('../../utils/i18n')
var Avatar3D = require('../../utils/avatar-3d').Avatar3D

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
    t: {},
    greeting: '',
    sessionId: (function(){
      var sid = createSessionId()
      // 同步到全局，路线页面可以读取
      try { var app = getApp(); if(app) app.globalData.sessionId = sid } catch(e){}
      return sid
    })(),
    userId: 10001,
    scenicName: '灵山胜境',
    avatarName: '小导·景区数字导游',
    currentAvatarFile: 'Aa1.vrm',
    subtitle: '您好，请问有什么可以帮您？',
    message: '',
    interestsText: '历史文化,自然风光,摄影打卡',
    interestTags: ['历史文化', '自然风光', '摄影打卡'],
    durationMinutes: 180,
    messages: [],
    suggestedQuestions: i18n.getLang() === 'en'
        ? ['What are the must-see spots?', 'Recommend a tour route', 'Any good food nearby?']
        : ['有哪些必去的景点？', '推荐一条合适的路线', '附近有什么好吃的？'],
    routeResult: [],
    loading: false,
    errorText: '',
    asrText: '',
    ttsResult: null,
    isRecording: false,
    isPlaying: false,
    showEmojiPanel: false,
    emojiCategories: [
      { name: '表情', emojis: ['😊','😂','🤩','😍','😎','🥰','😄','😉','😆','🤗','😌','😏','🙂','😇','🤔','😅'] },
      { name: '手势', emojis: ['👍','👏','🙏','💪','🤝','✌️','👌','🤞','🫶','🙌','👋','🤙'] },
      { name: '心情', emojis: ['❤️','💚','💙','🧡','💛','💜','🤍','💗','💖','🫀','✨','🌟','💫','🔥','🎉','🎊'] },
      { name: '旅行', emojis: ['🏔️','🌊','🌸','🌲','☀️','🌈','🏯','⛩️','🗺️','📍','🚶','📸','🎒','⛺','🌅','🍃'] },
      { name: '美食', emojis: ['🍜','🍵','🍦','🎂','🍰','🍉','🥢','🍚','☕','🧋','🍡','🍱'] },
      { name: '动物', emojis: ['🐼','🐱','🐶','🐰','🦊','🐨','🐯','🐮','🐷','🐸','🐵','🐔'] },
    ],
    showRating: false,
    ratingValue: 0,
    pendingNav: null,
  },

  onLoad: function () {
    var app = getApp()
    var baseUrl = util.loadBaseUrl((app && app.globalData && app.globalData.baseUrl) || 'http://192.168.3.42:8081')
    this.setData({
      baseUrl: baseUrl,
      scenicName: i18n.t('scenicName'),
      avatarName: (app && app.globalData && app.globalData.avatarName) || '小导·景区数字导游',
      interestTags: util.splitInterests(this.data.interestsText),
    })
    this.syncBaseUrl(baseUrl)
    this.getRoute()
    this.loadHistory()
    this.applyTranslations()

    var self = this
    innerAudioContext.onEnded(function () {
      self.stopLipSync()
      self.setData({ isPlaying: false })
    })
    innerAudioContext.onStop(function () {
      self.stopLipSync()
    })

    // 连接 WebSocket，后台切换形象时实时通知
    this._avatarWs = null
    this._wsReconnectTimer = null
    this._lastAvatarFile = wx.getStorageSync('guide_avatar_file') || ''
    this.connectAvatarWs(baseUrl)

  },

  onShow: function () {
    this.applyTranslations()
    // 同步后台激活的形象（管理员在后台切换后小程序自动生效）
    var self = this
    api.fetchActiveAvatar().then(function (activeFile) {
      if (activeFile) {
        var oldFile = wx.getStorageSync('guide_avatar_file') || ''
        if (activeFile !== oldFile) {
          wx.setStorageSync('guide_avatar_file', activeFile)
          console.log('后台已切换形象:', oldFile, '->', activeFile)
          // 如果 3D 已就绪，重新加载模型
          if (avatar3d) {
            avatar3d.dispose()
            avatar3d = null
            is3dReady = false
          }
        }
      }
    }).catch(function (err) {
      console.warn('获取后台形象失败(不影响本地):', err)
    })

    // 如果 3D 尚未就绪，延迟重试（reLaunch 后备用方案）
    if (!is3dReady || !avatar3d) {
      var self = this
      setTimeout(function () { self.initAvatar3D() }, 1200)
      return
    }
    var savedFile = wx.getStorageSync('guide_avatar_file') || 'Aa1.vrm'
    if (savedFile !== this.data.currentAvatarFile) {
      console.log('形象已切换:', this.data.currentAvatarFile, '->', savedFile)
      if (avatar3d) avatar3d.dispose()
      is3dReady = false
      avatar3d = null
      this.initAvatar3D()
    }
  },

  onReady: function () {
    var self = this
    setTimeout(function () { self.initAvatar3D() }, 600)
  },

  onUnload: function () {
    // 关闭 WebSocket
    if (this._avatarWs) {
      try { this._avatarWs.close() } catch (e) {}
      this._avatarWs = null
    }
    if (this._wsReconnectTimer) {
      clearTimeout(this._wsReconnectTimer)
      this._wsReconnectTimer = null
    }
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
    if (is3dReady) return
    var self = this
    var query = wx.createSelectorQuery().in(this)
    query.select('#glCanvas')
      .fields({ node: true, size: true })
      .exec(function (res) {
        if (!res || !res[0] || !res[0].node) {
          console.warn('3D Canvas 节点获取失败')
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
          var avatarFile = wx.getStorageSync('guide_avatar_file') || 'Aa1.vrm'
          var modelUrl = self.data.baseUrl + '/avatars/' + avatarFile + '?t=' + Date.now()
          self.setData({ currentAvatarFile: avatarFile })
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
  /** 粗略的中文字 → 口型映射 */
  charToViseme: function (ch) {
    // 开口音（a/o 等）
    if ("啊阿大他她那哪把爬妈发哈啦哇呀丫压卡帕啪嗒打".indexOf(ch) >= 0) return 'A'
    if ("八达拉塔萨查沙炸杂擦".indexOf(ch) >= 0) return 'A'
    if (ch >= '㐀' && ch <= '䶿') return 'A'  // CJK扩展A区 → 默认开口
    // 圆唇音（o/uo 等）
    if ("哦喔噢我多说做过国火活落扩所左捉桌".indexOf(ch) >= 0) return 'O'
    if ("波破莫佛窝".indexOf(ch) >= 0) return 'O'
    // 微笑音（i/ü 等）
    if ("一以你里地立力利击西系几七其期机记给".indexOf(ch) >= 0) return 'I'
    if ("去取需女绿律序许".indexOf(ch) >= 0) return 'I'
    if ("小叫叫笑跳条标票秒".indexOf(ch) >= 0) return 'I'
    // 收唇音（u 等）
    if ("不出入五无务图度图书住主注柱粗组族".indexOf(ch) >= 0) return 'U'
    if ("度努路古苦湖互土".indexOf(ch) >= 0) return 'U'
    if ("春顺准润混顿寸孙".indexOf(ch) >= 0) return 'U'
    // 半开口（e/ei 等）
    if ("了着个和得很的这那什么么车社设" .indexOf(ch) >= 0) return 'E'
    if ("北飞给类非配每内".indexOf(ch) >= 0) return 'E'
    // 标点 → rest
    if ("，。！？、；：…—～·（）【】《》".indexOf(ch) >= 0) return 'rest'
    // 按Unicode范围粗估（常用汉字在4E00-9FFF之间）
    var code = ch.charCodeAt(0)
    if (code >= 0x4E00 && code <= 0x9FFF) {
      var idx = code - 0x4E00
      // 用哈希值分配口型，避免相邻字相同口型
      var mod = idx % 5
      return ['A', 'O', 'I', 'U', 'E'][mod]
    }
    return 'A'
  },

  startLipSync: function (text, durationMs) {
    this.stopLipSync()
    if (!is3dReady || !avatar3d) return

    avatar3d.setSpeaking(true)
    var self = this

    // 口型振荡参数
    var CYCLE_MIN = 350   // 最短周期(ms)
    var CYCLE_MAX = 600   // 最长周期(ms)
    var OPEN_MIN = 0.06   // 波谷开度
    var OPEN_MAX = 0.80   // 波峰开度
    var BASELINE = 0.25   // 基线偏移(0~1)，越大嘴巴闭合越少

    // 周期：字数多时周期长(语速慢)，字数少时周期短(语速快)
    var avgCycle = Math.min(CYCLE_MAX, Math.max(CYCLE_MIN, durationMs / Math.max(text.length, 3) * 1.2))
    // 叠加一个快周期产生自然颤动感
    var fastCycle = avgCycle / 3.5

    // 预计算每个字的口型
    var charVisemes = []
    for (var i = 0; i < text.length; i++) {
      charVisemes.push(this.charToViseme(text[i]))
    }

    function drive() {
      try {
        var elapsed = innerAudioContext.currentTime * 1000
        // 主振荡
        var phase = (elapsed / avgCycle) * Math.PI * 2
        // 叠加颤动
        var fastPhase = (elapsed / fastCycle) * Math.PI * 2
        // 主波 + 10%颤动波
        var wave = Math.sin(phase) * 0.9 + Math.sin(fastPhase) * 0.1
        // 归一化到 0~1
        var norm = Math.min(1, Math.max(0, (wave + 1.1) / 2.2))
        // 基线偏移：嘴巴不会完全闭合
        var open = OPEN_MIN + (OPEN_MAX - OPEN_MIN) * Math.min(1, BASELINE + (1 - BASELINE) * norm)

        // 根据当前时间找到对应的字 → 选择口型
        var totalDur = (innerAudioContext.duration || 0) * 1000
        var viseme = 'A'
        if (totalDur > 0 && charVisemes.length > 0) {
          var charIdx = Math.min(Math.floor(elapsed / totalDur * charVisemes.length), charVisemes.length - 1)
          if (charIdx >= 0) {
            viseme = charVisemes[charIdx]
          }
        }

        // 音频开头200ms渐入，结尾200ms渐出
        if (totalDur > 0) {
          if (elapsed < 200) {
            open *= elapsed / 200
          } else if (elapsed > totalDur - 200) {
            open *= Math.max(0, (totalDur - elapsed) / 200)
          }
        }

        if (avatar3d) avatar3d.setViseme(viseme, Math.min(open, 1))

        // 继续驱动直到音频结束
        if (totalDur > 0 && elapsed < totalDur + 100) {
          lipTimer = setTimeout(drive, 30)
        } else {
          if (avatar3d) avatar3d.setSpeaking(false)
          if (avatar3d) avatar3d.setViseme('rest', 0.02)
          lipTimer = null
        }
      } catch (e) {
        console.error('lip drive error:', e)
        lipTimer = null
      }
    }

    // 等音频真正开始播放后再驱动
    var waiter = setInterval(function () {
      if (innerAudioContext.currentTime > 0 || !self.data.isPlaying) {
        clearInterval(waiter)
        if (self.data.isPlaying) drive()
      }
    }, 50)
  },

  /** 使用后端返回的 viseme 口型时间轴驱动 */
  startLipSyncWithViseme: function (visemes, durationMs) {
    this.stopLipSync()
    if (!is3dReady || !avatar3d || !visemes || visemes.length < 2) return

    avatar3d.setSpeaking(true)
    var self = this
    var endTime = durationMs || 3000

    function driveViseme() {
      try {
        var elapsed = innerAudioContext.currentTime * 1000
        if (elapsed < 0 || elapsed > endTime + 200) {
          if (avatar3d) avatar3d.setViseme('rest', 0.02)
          if (elapsed > endTime + 200) {
            if (avatar3d) avatar3d.setSpeaking(false)
            lipTimer = null
            return
          }
          lipTimer = setTimeout(driveViseme, 30)
          return
        }

        // 找到当前时间对应的 viseme 帧
        var v = null
        for (var i = visemes.length - 1; i >= 0; i--) {
          if (visemes[i].time <= elapsed) {
            v = visemes[i]
            break
          }
        }
        if (v) {
          avatar3d.setViseme(v.viseme || 'A', Math.min(v.open || 0.3, 1))
        }
        lipTimer = setTimeout(driveViseme, 30)
      } catch (e) {
        console.error('viseme drive error:', e)
        lipTimer = null
      }
    }

    var waiter = setInterval(function () {
      if (innerAudioContext.currentTime > 0 || !self.data.isPlaying) {
        clearInterval(waiter)
        if (self.data.isPlaying) driveViseme()
      }
    }, 50)
  },

  stopLipSync: function () {
    if (lipTimer) {
      clearTimeout(lipTimer)
      lipTimer = null
    }
    if (avatar3d) {
      avatar3d.setSpeaking(false)
      avatar3d.setViseme('rest', 0.02)
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

  // ========== 表情面板 ==========
  toggleEmojiPanel: function () {
    this.setData({ showEmojiPanel: !this.data.showEmojiPanel })
  },

  insertEmoji: function (e) {
    var emoji = e.currentTarget.dataset.emoji
    var current = this.data.message || ''
    this.setData({
      message: current + emoji,
      showEmojiPanel: false,
    })
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

    // 递增播报屏蔽锁 - 旧 TTS 回调检测到此锁就不会播放
    this._ttsLock = (this._ttsLock || 0) + 1
    var currentLock = this._ttsLock
    console.log('[DEBUG] sendChat lock=' + currentLock + ' msg=' + msg.substring(0, 20))
    // 终止上一次未完成的请求
    if (this._currentChatTask) {
      console.log('[DEBUG] abort previous task')
      try { this._currentChatTask.abort() } catch (e) { }
      this._currentChatTask = null
    }
    if (this.data.isPlaying) {
      console.log('[DEBUG] stop previous audio')
      innerAudioContext.stop()
      this.stopLipSync()
      this.setData({ isPlaying: false })
    }

    // 检测是否想去某个景点（支持多种提问方式）
    var navSpot = null
    var navMatch = msg.match(/^(?:我想去|去|导航到|我要去|怎么去|如何去|带我去|去往|要到|找一下|帮我找)(.+?)(?:看看|怎么走|$)/)
    if (navMatch) {
      navSpot = navMatch[1].trim()
      // 后台查坐标缓存到 data
      var self = this
      api.fetchAttractionCoordinate(navSpot).then(function (data) {
        if (data && data.latitude && data.longitude) {
          self.setData({
            pendingNav: {
              name: data.name || navSpot,
              latitude: parseFloat(data.latitude),
              longitude: parseFloat(data.longitude),
            }
          })
        }
      }).catch(function () {})
    }

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

    var profile = {}
    try {
      profile = wx.getStorageSync('guide_user_profile') || {}
    } catch (e) { /* ignore */ }

    // 获取 GPS 位置（先试缓存，没有再实时定位）
    var lat = wx.getStorageSync('guide_last_lat') || null
    var lng = wx.getStorageSync('guide_last_lng') || null
    if (!lat || !lng) {
      wx.getLocation({
        type: 'gcj02',
        success: function (r) {
          lat = r.latitude
          lng = r.longitude
          wx.setStorageSync('guide_last_lat', lat)
          wx.setStorageSync('guide_last_lng', lng)
          doChat(lat, lng)
        },
        fail: function () { doChat(null, null) }
      })
    } else {
      doChat(lat, lng)
    }

    function doChat(lat, lng) {
      self._currentChatTask = api.chatStream({
        user_id: self.data.userId,
        session_id: self.data.sessionId,
        message: msg,
        input_type: 'text',
        language: i18n.getLang(),
        age: profile.age || 0,
        gender: profile.gender || '',
        location: profile.location || '',
        latitude: lat,
        longitude: lng,
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
        // 如果有待导航景点，附加到消息中
        var nav = self.data.pendingNav
        if (nav && nav.name) {
          msgs[placeholderIdx].navSpot = nav
          self.setData({ messages: msgs, pendingNav: null })
        } else {
          self.setData({ messages: msgs })
        }
      }

      var resultMeta = meta || doneMeta
      var responseEmotion = 'calm'
      if (resultMeta) {
        responseEmotion = resultMeta.emotion || 'calm'
        self.setData({
          sessionId: resultMeta.session_id || self.data.sessionId,
          suggestedQuestions: resultMeta.suggested_questions || self.data.suggestedQuestions,
        })
        // 同步 sessionId 到全局，路线页面可读取
        try { var app = getApp(); if(app) app.globalData.sessionId = self.data.sessionId } catch(e){}
      } else {
        // 解析答案中的建议追问作为兜底
        var answer = msgs[placeholderIdx] ? msgs[placeholderIdx].content : ''
        var sug = self.extractSuggestedFromAnswer(answer)
        if (sug.length > 0) self.setData({ suggestedQuestions: sug })
      }

      var answerText = msgs[placeholderIdx] ? msgs[placeholderIdx].content : ''
      self.saveHistory()
      console.log('[DEBUG] stream complete check lock: currentLock=' + currentLock + ' _ttsLock=' + (self._ttsLock || 0) + ' hasAnswer=' + (!!answerText))
      if (answerText && currentLock === (self._ttsLock || 0)) {
        self.setData({ subtitle: '讲解完成' })
        console.log('[DEBUG] stream done -> speakText, lock=' + currentLock + ' msg_len=' + answerText.length)
        self.speakText(answerText, responseEmotion)
      }
    })
    } // doChat end

    // 保存 task 引用以便 onUnload 时 abort（已在 doChat 中赋值）
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
  /** 请求录音权限 */
  requestRecordPermission: function () {
    var self = this
    return new Promise(function (resolve, reject) {
      wx.getSetting({
        success: function (res) {
          if (res.authSetting['scope.record'] === false) {
            // 用户已拒绝过，引导打开权限
            wx.showModal({
              title: '需要麦克风权限',
              content: '请在设置中开启麦克风权限，以便使用语音输入功能',
              success: function (modal) {
                if (modal.confirm) {
                  wx.openSetting({
                    success: function (setting) {
                      if (setting.authSetting['scope.record']) {
                        resolve()
                      } else {
                        reject(new Error('麦克风权限未开启'))
                      }
                    },
                    fail: function () { reject(new Error('打开设置失败')) }
                  })
                } else {
                  reject(new Error('用户取消了授权'))
                }
              }
            })
          } else {
            // 未授权或已授权——尝试发起授权
            wx.authorize({
              scope: 'scope.record',
              success: function () { resolve() },
              fail: function () {
                // 授权失败但可能是用户临时拒绝，仍尝试录音
                resolve()
              }
            })
          }
        },
        fail: function () { resolve() }
      })
    })
  },

  /** 开始录音（同步设置状态，异步请求权限） */
  startRecording: function () {
    // 停止正在播放的语音讲解
    if (this.data.isPlaying) {
      this.stopTts()
    }
    this.setData({ errorText: '', isRecording: true, _recordStartTime: Date.now() })
    this._pendingStop = false
    var self = this

    this.requestRecordPermission().then(function () {
      // 用户在权限请求期间已经松手了 → 不启动录音
      if (self._pendingStop) {
        self._pendingStop = false
        self.setData({ isRecording: false, _recordStartTime: 0 })
        return
      }

      if (!self.recorderManager) {
        self.recorderManager = wx.getRecorderManager()

        self.recorderManager.onStop(function (res) {
          var elapsed = Date.now() - (self.data._recordStartTime || 0)
          self.setData({ isRecording: false, _recordStartTime: 0 })
          if (elapsed < 1000) {
            self.setData({ errorText: '录音时间太短，请长按说话（至少1秒）' })
            return
          }
          self.sendAudioToAsr(res.tempFilePath)
        })

        self.recorderManager.onError(function (err) {
          console.error('录音失败:', err)
          self.setData({ isRecording: false, errorText: '录音失败: ' + (err.errMsg || '麦克风可能被占用') })
        })
      }

      self.recorderManager.start({
        format: 'aac',
        duration: 15000,
        sampleRate: 16000,
        numberOfChannels: 1,
        encodeBitRate: 64000,
      })
    }).catch(function (err) {
      self.setData({ isRecording: false, errorText: err.message || '无法启动录音' })
    })
  },

  stopRecording: function () {
    if (this.recorderManager && this.data.isRecording) {
      this.recorderManager.stop()
    } else {
      // 录音还没实际开始（权限请求中），标记为需要停止
      this._pendingStop = true
    }
  },

  cancelRecording: function () {
    if (this.recorderManager && this.data.isRecording) {
      this.recorderManager.stop()
    }
    this.setData({ isRecording: false, _recordStartTime: 0 })
    this._pendingStop = true
  },

  sendAudioToAsr: function (tempFilePath) {
    var self = this
    this.setLoading(true)

    wx.getFileSystemManager().readFile({
      filePath: tempFilePath,
      encoding: 'base64',
      success: function (res) {
        api.asr({ audio_base64: res.data, format: 'aac' }).then(function (data) {
          var text = data.text || ''
          self.setData({ asrText: text })
          if (text && text.trim()) {
            self.setData({ message: text.trim() })
            // sendChat 内部会管理 loading，此处不再 setLoading(false)
            self.sendChat()
          } else {
            self.setData({ errorText: '未能识别到语音内容，请重试' })
            self.setLoading(false)
          }
        }).catch(function (err) {
          console.error('ASR error:', err)
          var msg = err.message || ''
          if (msg.indexOf('发音超时') >= 0 || msg.indexOf('Data') >= 0 || msg.indexOf('volume') >= 0) {
            self.setData({ errorText: '未检测到有效语音，请靠近麦克风重试' })
          } else if (msg.indexOf('失败') >= 0) {
            self.setData({ errorText: '语音识别失败，请重试' })
          } else {
            self.setData({ errorText: '语音识别出错: ' + msg })
          }
          self.setLoading(false)
        })
      },
      fail: function (err) {
        console.error('读取音频文件失败:', err)
        self.setData({ errorText: '读取录音文件失败' })
        self.setLoading(false)
      }
    })
  },

  // ========== 语音播报 ==========
  /** 播报指定文本（自动调用 TTS + 口型同步） */
  speakText: function (text, emotion) {
    var self = this
    var lock = this._ttsLock || 0
    console.log('[DEBUG] speakText ENTER lock=' + lock + ' text_len=' + text.length)
    this.syncBaseUrl()
    this.setLoading(true)
    var ttsEmotion = emotion || 'calm'
    api.tts({
      text: text,
      voice_id: i18n.getLang() === 'en' ? '1050' : '101001',
      speed: 1,
      emotion: ttsEmotion,
      language: i18n.getLang()
    }).then(function (data) {
      var curLock = self._ttsLock || 0
      console.log('[DEBUG] TTS RESPONSE lock=' + lock + ' curLock=' + curLock)
      if (lock !== curLock) { console.log('[DEBUG] TTS SKIP'); self.setLoading(false); return }
      console.log('[DEBUG] TTS LOCK OK, continue')
      if (data.error) {
        self.setData({ errorText: '语音合成失败: ' + data.error })
        self.setLoading(false)
        return
      }
      if (data.audio_base64) {
        console.log('[DEBUG] TTS got audio, writing file...')
        self.setData({ ttsResult: data, isPlaying: true })
        var durMs = data.duration_ms || Math.max(text.length * 250, 1000)

        var filePath = wx.env.USER_DATA_PATH + '/tts_' + Date.now() + '.mp3'
        wx.getFileSystemManager().writeFile({
          filePath: filePath,
          data: data.audio_base64,
          encoding: 'base64',
          success: function () {
            console.log('[DEBUG] TTS file written, playing...')
            innerAudioContext.src = filePath
            self.startLipSync(text, durMs)
            innerAudioContext.play()
            console.log('[DEBUG] TTS play() called')
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
    wx.switchTab({ url: '/pages/dashboard/index' })
  },

  // ========== WebSocket 实时接收后台形象切换 ==========
  connectAvatarWs: function (baseUrl) {
    var self = this
    // 将 http:// 转换为 ws://
    var wsUrl = baseUrl.replace(/^http/, 'ws') + '/ws/avatar'
    console.log('WebSocket 连接:', wsUrl)

    try {
      var ws = wx.connectSocket({ url: wsUrl, timeout: 10000 })
      this._avatarWs = ws

      ws.onOpen(function () {
        console.log('WebSocket 已连接')
      })

      ws.onMessage(function (res) {
        try {
          var data = JSON.parse(res.data)
          if (data.type === 'avatar_change' && data.filename) {
            console.log('收到形象切换通知:', data.filename)
            var cached = wx.getStorageSync('guide_avatar_file') || ''
            if (data.filename !== cached) {
              wx.setStorageSync('guide_avatar_file', data.filename)
              self._lastAvatarFile = data.filename
              if (avatar3d) {
                avatar3d.dispose()
                avatar3d = null
                is3dReady = false
                setTimeout(function () { self.initAvatar3D() }, 300)
              }
            }
          }
        } catch (e) {
          console.warn('WebSocket 消息解析失败:', e)
        }
      })

      ws.onClose(function () {
        console.log('WebSocket 已关闭')
        self._avatarWs = null
        // 5 秒后自动重连
        self._wsReconnectTimer = setTimeout(function () {
          if (!self._avatarWs) self.connectAvatarWs(baseUrl)
        }, 5000)
      })

      ws.onError(function (err) {
        console.warn('WebSocket 错误:', err)
      })
    } catch (e) {
      console.warn('WebSocket 连接失败(不影响本地使用):', e)
    }
  },

  // ========== 结束对话 & 评分 ==========
  showRatingPopup: function () {
    try {
      console.log('showRatingPopup called, loading=' + this.data.loading)
      // 强制解除 loading 状态，确保弹窗能弹出
      if (this.data.loading) {
        this.setData({ loading: false })
        wx.hideNavigationBarLoading()
      }
      // 停止语音播报
      if (this.data.isPlaying) {
        innerAudioContext.stop()
        this.stopLipSync()
        this.setData({ isPlaying: false })
      }
      if (avatar3d) {
        avatar3d.setSpeaking(false)
      }
      this.setData({ showRating: true, ratingValue: 0 })
      console.log('showRating set to true')
    } catch (e) {
      console.error('showRatingPopup error:', e)
    }
  },

  hideRatingPopup: function () {
    this.setData({ showRating: false, ratingValue: 0 })
  },

  noop: function () {},

  selectRating: function (e) {
    var val = parseInt(e.currentTarget.dataset.value)
    this.setData({ ratingValue: val })
  },

  submitRating: function () {
    var val = this.data.ratingValue
    if (val === 0) return

    var self = this
    var sessionKey = this.data.sessionId

    // 调后端提交评分
    api.endSession(sessionKey, val).then(function () {
      console.log('评分提交成功:', sessionKey, val)
    }).catch(function (err) {
      console.warn('评分提交失败(不影响本地保存):', err)
    })

    // 保存本次聊天记录
    this.saveHistory(true)

    // 清空对话，开始新会话
    wx.showToast({ title: '评分完成，感谢反馈！', icon: 'success' })
    self.setData({
      messages: [],
      sessionId: createSessionId(),
      showRating: false,
      ratingValue: 0,
      suggestedQuestions: i18n.getLang() === 'en'
        ? ['What are the must-see spots?', 'Recommend a tour route', 'Any good food nearby?']
        : ['有哪些必去的景点？', '推荐一条合适的路线', '附近有什么好吃的？'],
    })
  },

  startNewChat: function () {
    var messages = this.data.messages
    if (messages && messages.length > 0) {
      this.saveHistory(true)
    }
    if (avatar3d) {
      avatar3d.setSpeaking(false)
    }
    this.setData({
      messages: [],
      sessionId: createSessionId(),
      suggestedQuestions: i18n.getLang() === 'en'
        ? ['What are the must-see spots?', 'Recommend a tour route', 'Any good food nearby?']
        : ['有哪些必去的景点？', '推荐一条合适的路线', '附近有什么好吃的？'],
    })
    wx.showToast({ title: this.data.t.newChatStarted || '新对话已开始', icon: 'success' })
  },

  /** 点击导航按钮：停止语音 + 导航 */
  onNavTap: function (e) {
    var nav = e.currentTarget.dataset.nav
    if (!nav) return
    // 停止语音解说
    if (typeof innerAudioContext !== 'undefined' && innerAudioContext) {
      innerAudioContext.stop()
    }
    if (avatar3d && avatar3d.stopSpeaking) {
      avatar3d.stopSpeaking()
    }
    // 导航
    wx.openLocation({
      latitude: nav.latitude,
      longitude: nav.longitude,
      name: nav.name,
      address: '灵山胜境',
      scale: 18,
    })
  },

  // ========== 多语言 ==========
  applyTranslations: function () {
    var lang = i18n.getLang()
    var en = lang === 'en'
    var t = i18n.getMessages(lang)
    var profile = wx.getStorageSync('guide_user_profile') || {}
    if (profile.nickName) { t.scenicName = profile.nickName }
    this.setData({
      t: t,
      greeting: i18n.greeting(lang),
      scenicName: t.scenicName,
      suggestedQuestions: en
        ? ['What are the best attractions?', 'Recommend a tour route', 'Any good food nearby?']
        : ['有哪些必去的景点？', '推荐一条合适的路线', '附近有什么好吃的？'],
    })
  },

  // ========== 聊天记录持久化 ==========
  loadHistory: function () {
    try {
      var stored = wx.getStorageSync(util.getHistoryKey())
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
      var stored = wx.getStorageSync(util.getHistoryKey()) || []
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
      wx.setStorageSync(util.getHistoryKey(), stored)
    } catch (e) {
      console.warn('保存聊天记录失败', e)
    }
  },
})
