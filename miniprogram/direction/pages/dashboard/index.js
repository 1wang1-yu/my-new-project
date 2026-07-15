var util = require('../../utils/util')
var i18n = require('../../utils/i18n')

Page({
  data: {
    t: {},
    avatarUrl: '',
    nickName: '',
    totalChats: 0,
    totalMessages: 0,
    totalDays: 0,
    settingsSummary: '',
    historySummary: '',
  },

  onLoad() {
    this.applyTranslations()
    this.loadAllSummaries()
  },

  onShow() {
    this.applyTranslations()
    this.loadAllSummaries()
  },

  applyTranslations() {
    var lang = i18n.getLang()
    var t = i18n.getMessages(lang)
    t.scenicName = (wx.getStorageSync('guide_user_profile') || {}).nickName || i18n.t('scenicName', lang)
    this.setData({ t: t })
  },

  loadAllSummaries() {
    var t = this.data.t

    // 加载用户头像和昵称
    var profile = wx.getStorageSync('guide_user_profile') || {}
    this.setData({
      avatarUrl: profile.avatarUrl || '',
      nickName: profile.nickName || '',
    })

    // 聊天统计
    var stored = wx.getStorageSync(util.getHistoryKey()) || []
    var totalMsgs = 0
    for (var i = 0; i < stored.length; i++) {
      totalMsgs += stored[i].messages ? stored[i].messages.length : 0
    }
    var days = 1
    if (stored.length > 0) {
      var dates = {}
      for (var k = 0; k < stored.length; k++) { dates[stored[k].date] = true }
      days = Object.keys(dates).length
    }
    this.setData({
      totalChats: stored.length,
      totalMessages: totalMsgs,
      totalDays: days,
      historySummary: stored.length > 0 ? stored.length + ' ' + (t.statChats || '次对话') : (t.emptyHistory || '暂无记录'),
    })

    // 设置摘要
    var lang = i18n.getLang()
    var langLabel = lang === 'en' ? 'English' : '中文'
    this.setData({ settingsSummary: '个人信息 · ' + langLabel })

  },

  openSettings() { wx.navigateTo({ url: '/pages/settings/index/index' }) },
  openHistory() { wx.navigateTo({ url: '/pages/history/index/index' }) },

  onLogout() {
    var self = this
    wx.showModal({
      title: '退出登录',
      content: '确定要退出当前账号吗？',
      success: function (res) {
        if (res.confirm) {
          // 清除用户登录信息（聊天记录按用户隔离，不清除）
          wx.removeStorageSync('guide_user_id')
          wx.removeStorageSync('guide_username')
          wx.removeStorageSync('guide_open_id')
          wx.removeStorageSync('guide_user_profile')
          wx.showToast({ title: '已退出', icon: 'success' })
          setTimeout(function () {
            wx.reLaunch({ url: '/pages/login/index' })
          }, 1000)
        }
      }
    })
  },
})
