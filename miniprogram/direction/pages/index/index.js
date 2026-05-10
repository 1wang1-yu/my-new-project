const api = require('../../utils/api')
const util = require('../../utils/util')

function createSessionId() {
  return `session_${Date.now()}`
}

Page({
  data: {
    baseUrl: '',
    sessionId: createSessionId(),
    userId: 10001,
    scenicName: '西湖景区',
    avatarName: '小导·西湖景区数字导游',
    subtitle: '正在为您讲解断桥残雪…',
    message: '断桥残雪是什么景观？每个季节各有什么特色？',
    interestsText: '历史文化,自然风光,摄影打卡',
    interestTags: ['历史文化', '自然风光', '摄影打卡'],
    durationMinutes: 180,
    messages: [
      {
        role: 'assistant',
        content: '断桥残雪是西湖十景之一，冬日初晴时桥面若隐若现，特别适合拍照与故事讲解。',
      },
    ],
    suggestedQuestions: ['推荐路线', '历史故事', '美食推荐'],
    routeResult: [],
    loading: false,
    errorText: '',
    asrText: '',
    ttsResult: null,
  },

  onLoad() {
    const app = getApp()
    const baseUrl = util.loadBaseUrl((app && app.globalData && app.globalData.baseUrl) || 'http://127.0.0.1:8081')
    this.setData({
      baseUrl,
      scenicName: app.globalData.scenicName,
      avatarName: app.globalData.avatarName,
      interestTags: util.splitInterests(this.data.interestsText),
    })
    this.syncBaseUrl(baseUrl)
    this.getRoute()
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

  useSuggestedQuestion(e) {
    this.setData({
      message: e.currentTarget.dataset.question,
    })
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

  async sendChat() {
    this.syncBaseUrl()
    this.setLoading(true)
    this.setData({ errorText: '' })

    const nextMessages = this.data.messages.concat({
      role: 'user',
      content: this.data.message,
    })
    this.setData({ messages: nextMessages })

    try {
      // 直接使用本地模拟智能回答，避免后端调用超时
      const data = await api.directChat(this.data.message)
      this.setData({
        sessionId: data.session_id || this.data.sessionId,
        subtitle: '已完成本轮智能讲解',
        messages: nextMessages.concat({
          role: 'assistant',
          content: data.answer,
        }),
        suggestedQuestions: data.suggested_questions || this.data.suggestedQuestions,
        ttsResult: {
          audio_url: data.tts_url,
          emotion: data.emotion,
        },
      })
    } catch (err) {
      this.setData({ errorText: err.message || '智能问答调用失败' })
    } finally {
      this.setLoading(false)
    }
  },

  async getRoute() {
    this.syncBaseUrl()
    try {
      // 直接使用本地模拟路线推荐，避免后端调用超时
      const interests = util.splitInterests(this.data.interestsText)
      const mockRoutes = [
        {
          name: '经典西湖游览路线',
          stops: ['断桥残雪', '白堤', '孤山', '三潭印月', '雷峰塔', '苏堤'],
          estimated_time: 240,
          highlights: ['断桥残雪', '三潭印月', '雷峰塔']
        },
        {
          name: '历史文化之旅',
          stops: ['岳王庙', '灵隐寺', '飞来峰', '龙井村', '九溪烟树'],
          estimated_time: 300,
          highlights: ['灵隐寺', '飞来峰', '龙井村']
        }
      ]
      this.setData({
        routeResult: mockRoutes,
        interestTags: interests,
      })
    } catch (err) {
      this.setData({ errorText: err.message || '路线推荐调用失败' })
    }
  },

  chooseAndUploadAudio() {
    wx.chooseMessageFile({
      count: 1,
      type: 'file',
      extension: ['mp3', 'wav'],
      success: ({ tempFiles }) => {
        const target = tempFiles && tempFiles[0]
        if (!target) {
          return
        }
        this.readAudioAsBase64(target.path)
      },
      fail: () => {
        this.setData({ errorText: '未选择音频文件' })
      },
    })
  },

  readAudioAsBase64(filePath) {
    this.syncBaseUrl()
    this.setLoading(true)
    this.setData({ errorText: '' })

    wx.getFileSystemManager().readFile({
      filePath,
      encoding: 'base64',
      success: async (res) => {
        try {
          const format = filePath.toLowerCase().endsWith('.mp3') ? 'mp3' : 'wav'
          const data = await api.asr({
            audio_base64: res.data,
            format,
          })
          this.setData({
            asrText: data.text || '',
            message: data.text || this.data.message,
          })
          if (data.text) {
            await this.sendChat()
          }
        } catch (err) {
          this.setData({ errorText: err.message || 'ASR 调用失败' })
        } finally {
          this.setLoading(false)
        }
      },
      fail: () => {
        this.setLoading(false)
        this.setData({ errorText: '音频读取失败' })
      },
    })
  },

  async getTts() {
    this.syncBaseUrl()
    this.setLoading(true)
    this.setData({ errorText: '' })

    try {
      const latestAssistant = [...this.data.messages].reverse().find(item => item.role === 'assistant')
      const data = await api.tts({
        text: latestAssistant ? latestAssistant.content : this.data.message,
        voice_id: 'guide-female-1',
        speed: 1,
        emotion: 'calm',
      })
      this.setData({ ttsResult: data })
    } catch (err) {
      this.setData({ errorText: err.message || 'TTS 调用失败' })
    } finally {
      this.setLoading(false)
    }
  },

  openDashboard() {
    wx.navigateTo({
      url: '/pages/dashboard/index',
    })
  },
})
