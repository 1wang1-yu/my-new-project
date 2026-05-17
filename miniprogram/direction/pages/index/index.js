const api = require('../../utils/api')
const util = require('../../utils/util')

function createSessionId() {
  return `session_${Date.now()}`
}

// 创建音频上下文（全局只创建一次）
const innerAudioContext = wx.createInnerAudioContext()

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

  onLoad() {
    const app = getApp()
    const baseUrl = util.loadBaseUrl((app && app.globalData && app.globalData.baseUrl) || 'http://127.0.0.1:8081')
    this.setData({
      baseUrl,
      scenicName: app.globalData.scenicName || '灵山胜境',
      avatarName: app.globalData.avatarName || '小导·景区数字导游',
      interestTags: util.splitInterests(this.data.interestsText),
    })
    this.syncBaseUrl(baseUrl)
    this.getRoute()
  },

  onUnload() {
    // 页面卸载时停止录音和播放
    if (this.recorderManager) {
      this.recorderManager.stop()
    }
    innerAudioContext.stop()
  },

  syncBaseUrl(baseUrl) {
    const app = getApp()
    const targetUrl = baseUrl || this.data.baseUrl
    if (app && app.globalData) {
      app.globalData.baseUrl = targetUrl
    }
    util.saveBaseUrl(targetUrl)
  },

  onFieldChange(e) {
    const { field } = e.currentTarget.dataset
    const value = e.detail.value
    const payload = { [field]: value }
    if (field === 'interestsText') {
      payload.interestTags = util.splitInterests(value)
    }
    this.setData(payload)
  },

  onMessageInput(e) {
    this.setData({ message: e.detail.value })
  },

  useSuggestedQuestion(e) {
    const question = e.currentTarget.dataset.question
    this.setData({ message: question })
    this.sendChat()
  },

  setLoading(loading) {
    this.setData({ loading })
    if (loading) {
      wx.showNavigationBarLoading()
    } else {
      wx.hideNavigationBarLoading()
    }
  },

  // ========== 发送文字消息 ==========
  async sendChat() {
    const msg = this.data.message.trim()
    if (!msg) return

    this.syncBaseUrl()
    this.setLoading(true)
    this.setData({ errorText: '', message: '' })

    const nextMessages = this.data.messages.concat({
      role: 'user',
      content: msg,
    })
    this.setData({ messages: nextMessages })

    try {
      const data = await api.chat({
        user_id: this.data.userId,
        session_id: this.data.sessionId,
        message: msg,
        input_type: 'text'
      })

      this.setData({
        sessionId: data.session_id || this.data.sessionId,
        subtitle: '讲解完成',
        messages: nextMessages.concat({
          role: 'assistant',
          content: data.answer,
        }),
        suggestedQuestions: data.suggested_questions || this.data.suggestedQuestions,
      })
    } catch (err) {
      console.error('chat error:', err)
      this.setData({ errorText: err.message || '智能问答调用失败' })
    } finally {
      this.setLoading(false)
    }
  },

  // ========== 语音录制（ASR） ==========
  startRecording() {
    this.setData({ isRecording: true, errorText: '' })

    // 初始化录音管理器
    if (!this.recorderManager) {
      this.recorderManager = wx.getRecorderManager()

      this.recorderManager.onStop((res) => {
        this.setData({ isRecording: false })
        // 录音完成后，转为 Base64 发送给后端 ASR
        this.sendAudioToAsr(res.tempFilePath)
      })

      this.recorderManager.onError((err) => {
        console.error('录音失败:', err)
        this.setData({ isRecording: false, errorText: '录音失败: ' + (err.errMsg || '未知错误') })
      })
    }

    this.recorderManager.start({
      format: 'mp3',
      duration: 30000, // 最长30秒
    })
  },

  stopRecording() {
    if (this.recorderManager && this.data.isRecording) {
      this.recorderManager.stop()
    }
  },

  toggleRecording() {
    if (this.data.isRecording) {
      this.stopRecording()
    } else {
      this.startRecording()
    }
  },

  async sendAudioToAsr(tempFilePath) {
    this.setLoading(true)
    try {
      // 读取音频文件并转为 Base64
      const fileData = await new Promise((resolve, reject) => {
        wx.getFileSystemManager().readFile({
          filePath: tempFilePath,
          encoding: 'base64',
          success: (res) => resolve(res.data),
          fail: (err) => reject(err),
        })
      })

      // 调用后端 ASR 接口
      const data = await api.asr({
        audio_base64: fileData,
        format: 'mp3',
      })

      const text = data.text || ''
      this.setData({ asrText: text })

      if (text && text.trim()) {
        // ASR 成功，自动填入消息并发送
        this.setData({ message: text.trim() })
        await this.sendChat()
      } else {
        this.setData({ errorText: '未能识别到语音内容，请重试' })
      }
    } catch (err) {
      console.error('ASR error:', err)
      this.setData({ errorText: '语音识别失败: ' + (err.message || '未知错误') })
    } finally {
      this.setLoading(false)
    }
  },

  // ========== 语音播报（TTS） ==========
  async getTts() {
    this.syncBaseUrl()
    this.setLoading(true)
    this.setData({ errorText: '' })

    try {
      // 获取最后一条 AI 回答
      const latestAssistant = [...this.data.messages].reverse().find(item => item.role === 'assistant')
      if (!latestAssistant) {
        this.setData({ errorText: '暂无内容可以播报' })
        this.setLoading(false)
        return
      }

      const data = await api.tts({
        text: latestAssistant.content,
        voice_id: 'longxiaochun_v3',
        speed: 1,
        emotion: 'calm',
      })

      if (data.audio_base64) {
        // Base64 音频，写入临时文件并播放
        this.setData({ ttsResult: data, isPlaying: true })
        const filePath = `${wx.env.USER_DATA_PATH}/tts_${Date.now()}.mp3`

        wx.getFileSystemManager().writeFile({
          filePath,
          data: data.audio_base64,
          encoding: 'base64',
          success: () => {
            innerAudioContext.src = filePath
            innerAudioContext.play()
            innerAudioContext.onEnded(() => {
              this.setData({ isPlaying: false })
            })
          },
          fail: (err) => {
            console.error('音频写入失败:', err)
            this.setData({ errorText: '音频播放失败', isPlaying: false })
          },
        })
      } else if (data.audio_url) {
        // 音频 URL，直接播放
        this.setData({ ttsResult: data, isPlaying: true })
        innerAudioContext.src = data.audio_url
        innerAudioContext.play()
        innerAudioContext.onEnded(() => {
          this.setData({ isPlaying: false })
        })
      } else {
        this.setData({ errorText: '语音合成未返回音频，请检查 API Key 配置' })
      }
    } catch (err) {
      console.error('TTS error:', err)
      this.setData({ errorText: '语音合成失败: ' + (err.message || '未知错误') })
    } finally {
      this.setLoading(false)
    }
  },

  stopTts() {
    innerAudioContext.stop()
    this.setData({ isPlaying: false })
  },

  // ========== 路线推荐 ==========
  async getRoute() {
    this.syncBaseUrl()
    try {
      const interests = util.splitInterests(this.data.interestsText)
      const data = await api.recommendRoute({
        user_id: this.data.userId,
        interests: interests,
        duration_minutes: this.data.durationMinutes,
      })
      this.setData({
        routeResult: data.routes || [],
        interestTags: interests,
      })
    } catch (err) {
      console.error('route error:', err)
      this.setData({
        routeResult: [
          {
            name: '经典游览路线',
            stops: ['入口广场', '主景点区', '观景平台', '文创商店'],
            estimated_time: 240,
            highlights: ['主景点区', '观景平台']
          }
        ],
      })
    }
  },

  openDashboard() {
    wx.navigateTo({
      url: '/pages/dashboard/index',
    })
  },
})
