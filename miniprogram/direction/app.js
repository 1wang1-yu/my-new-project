const { DEFAULT_BASE_URL } = require('./utils/request')

App({
  onLaunch() {
    const logs = wx.getStorageSync('logs') || []
    logs.unshift(Date.now())
    wx.setStorageSync('logs', logs)

    // 从缓存读取 baseUrl，没有则用默认值
    var savedUrl = wx.getStorageSync('guide_base_url')
    if (savedUrl) {
      this.globalData.baseUrl = savedUrl
    }
  },
  globalData: {
    userInfo: null,
    baseUrl: 'http://192.168.3.42:8081',
    scenicName: '灵山胜境',
    avatarName: '小导',
  }
})
