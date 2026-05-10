const { DEFAULT_BASE_URL } = require('./utils/request')

App({
  onLaunch() {
    const logs = wx.getStorageSync('logs') || []
    logs.unshift(Date.now())
    wx.setStorageSync('logs', logs)

    const savedBaseUrl = wx.getStorageSync('guide_base_url')
    if (savedBaseUrl) {
      this.globalData.baseUrl = savedBaseUrl
    }
  },
  globalData: {
    userInfo: null,
    baseUrl: DEFAULT_BASE_URL,
    scenicName: '西湖景区',
    avatarName: '小导',
  }
})
