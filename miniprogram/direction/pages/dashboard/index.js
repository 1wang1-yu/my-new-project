const api = require('../../utils/api')

Page({
  data: {
    dateRange: 'today',
    startDate: '2026-04-01',
    endDate: '2026-04-15',
    dashboard: null,
    sentiment: null,
    uploadResult: null,
    uploadTitle: '景区常见问题文档',
    uploadCategory: 'faq',
    selectedFileName: '',
    selectedFilePath: '',
    systemConfig: null,
    loading: false,
    errorText: '',
  },

  onLoad() {
    this.fetchDashboard()
    this.fetchSentiment()
    this.fetchSystemConfig()
  },

  onFieldChange(e) {
    const { field } = e.currentTarget.dataset
    const value = e.detail && typeof e.detail.value !== 'undefined'
      ? e.detail.value
      : e.currentTarget.dataset.value
    this.setData({ [field]: value })
  },

  setLoading(loading) {
    this.setData({ loading })
    wx.showNavigationBarLoading()
    if (!loading) {
      wx.hideNavigationBarLoading()
    }
  },

  async fetchDashboard() {
    this.setLoading(true)
    this.setData({ errorText: '' })
    try {
      const data = await api.dashboard(this.data.dateRange)
      this.setData({ dashboard: data })
    } catch (err) {
      this.setData({ errorText: err.message || '获取大屏数据失败' })
    } finally {
      this.setLoading(false)
    }
  },

  async fetchSentiment() {
    this.setLoading(true)
    this.setData({ errorText: '' })
    try {
      const data = await api.sentimentReport(this.data.startDate, this.data.endDate)
      this.setData({ sentiment: data })
    } catch (err) {
      this.setData({ errorText: err.message || '获取情绪报告失败' })
    } finally {
      this.setLoading(false)
    }
  },

  async fetchSystemConfig() {
    try {
      const data = await api.systemConfig()
      this.setData({ systemConfig: data })
    } catch (err) {
      this.setData({ errorText: err.message || '获取系统配置失败' })
    }
  },

  chooseKnowledgeFile() {
    wx.chooseMessageFile({
      count: 1,
      type: 'file',
      extension: ['txt', 'md'],
      success: ({ tempFiles }) => {
        const target = tempFiles && tempFiles[0]
        if (!target) {
          return
        }
        this.setData({
          selectedFileName: target.name,
          selectedFilePath: target.path,
        })
      },
      fail: () => {
        this.setData({ errorText: '未选择知识库文件' })
      },
    })
  },

  async uploadKnowledge() {
    if (!this.data.selectedFilePath) {
      this.setData({ errorText: '请先选择知识库文件' })
      return
    }

    this.setLoading(true)
    this.setData({ errorText: '' })
    try {
      const data = await api.uploadKnowledge(this.data.selectedFilePath, {
        title: this.data.uploadTitle,
        category: this.data.uploadCategory,
      })
      this.setData({ uploadResult: data })
      this.fetchDashboard()
    } catch (err) {
      this.setData({ errorText: err.message || '上传知识库失败' })
    } finally {
      this.setLoading(false)
    }
  },
})
