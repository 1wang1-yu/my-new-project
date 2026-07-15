var i18n = require('../../../utils/i18n')
var api = require('../../../utils/api')

// 每个景点的视觉配置：真实照片 / 表情 + 渐变背景
var SPOT_STYLES = [
  { emoji: '🗿', gradient: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)', image: '/images/p1.png' },
  { emoji: '🏛️', gradient: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)', image: '' },
  { emoji: '🎭', gradient: 'linear-gradient(135deg, #fa8231 0%, #f7b731 100%)', image: '' },
  { emoji: '🕌', gradient: 'linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%)', image: '' },
  { emoji: '⛩️', gradient: 'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)', image: '' },
  { emoji: '🧘', gradient: 'linear-gradient(135deg, #0ba360 0%, #3cba92 100%)', image: '' },
  { emoji: '🍜', gradient: 'linear-gradient(135deg, #f7971e 0%, #ffd200 100%)', image: '' },
  { emoji: '🌅', gradient: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)', image: '' },
  { emoji: '🛍️', gradient: 'linear-gradient(135deg, #e0c3fc 0%, #8ec5fc 100%)', image: '' },
  { emoji: '🏘️', gradient: 'linear-gradient(135deg, #d4a373 0%, #e6ccb2 100%)', image: '' },
]

Page({
  data: {
    t: {},
    spotList: [],
    checkinSummary: { totalSpots: 0, checkedIn: 0 },
    checkinPercent: 0,
    gpsLat: 0, gpsLng: 0,
    gpsLoading: false, gpsError: '',
  },

  onLoad() {
    this.applyTranslations()
    this.getLocation()
  },

  onShow() { this.applyTranslations() },

  applyTranslations() {
    var lang = i18n.getLang()
    this.setData({ t: i18n.getMessages(lang) })
  },

  getLocation() {
    var self = this
    this.setData({ gpsLoading: true, gpsError: '' })
    wx.getLocation({
      type: 'gcj02',
      success: function (res) {
        self.setData({ gpsLat: res.latitude, gpsLng: res.longitude })
        self.loadCheckInData(res.latitude, res.longitude)
      },
      fail: function () {
        self.setData({ gpsLoading: false, gpsError: self.data.t.checkinGpsError || '无法获取位置' })
        self.loadCheckInData(31.4245, 120.0918)
      },
    })
  },

  refreshCheckIn() { this.getLocation() },

  loadCheckInData(lat, lng) {
    var self = this
    api.fetchSpots(10001, lat, lng).then(function (data) {
      var spots = data.spots || []
      var summary = data.summary || { totalSpots: 0, checkedIn: 0 }
      var percent = summary.totalSpots > 0 ? Math.round(summary.checkedIn / summary.totalSpots * 100) : 0

      // 合并视觉样式
      for (var i = 0; i < spots.length; i++) {
        var style = SPOT_STYLES[i] || SPOT_STYLES[SPOT_STYLES.length - 1]
        spots[i].emoji = style.emoji
        spots[i].gradient = style.gradient
        spots[i].image = style.image || ''
      }

      wx.setStorageSync('guide_checkin_summary', summary)
      self.setData({
        spotList: spots, checkinSummary: summary,
        checkinPercent: percent, gpsLoading: false,
        gpsLat: lat, gpsLng: lng,
      })
    }).catch(function (err) {
      console.error('加载打卡数据失败:', err)
      self.setData({ gpsLoading: false })
    })
  },
})
