const { DEFAULT_BASE_URL } = require('./utils/request')

App({
  onLaunch() {
    const logs = wx.getStorageSync('logs') || []
    logs.unshift(Date.now())
    wx.setStorageSync('logs', logs)

    // 清除旧的 baseUrl 缓存，强制使用最新配置
    wx.removeStorageSync('guide_base_url')
  },
  globalData: {
    userInfo: null,
    baseUrl: DEFAULT_BASE_URL,
    scenicName: '灵山胜境',
    avatarName: '小导',
  }
})
