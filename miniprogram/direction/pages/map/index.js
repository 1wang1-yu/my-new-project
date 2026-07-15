var api = require('../../utils/api')
var i18n = require('../../utils/i18n')

Page({
  data: {
    t: {},
    // 地图
    latitude: 31.4245,
    longitude: 120.0918,
    scale: 15,
    markers: [],
    spotList: [],
    // 用户当前位置
    userLatitude: 31.4245,
    userLongitude: 120.0918,
    userAddress: '定位中...',
    // 选中的景点
    selectedSpot: null,
    showSpotPanel: false,
  },

  onLoad: function () {
    this.setData({ t: i18n.getMessages() })
    this.getUserLocation()
    this.loadSpots()
  },

  onShow: function () {
    this.getUserLocation()
  },

  /** 获取用户位置 */
  getUserLocation: function () {
    var self = this
    wx.getLocation({
      type: 'gcj02',
      success: function (res) {
        // 缓存到全局存储，聊天页面可直接使用
        wx.setStorageSync('guide_last_lat', res.latitude)
        wx.setStorageSync('guide_last_lng', res.longitude)
        self.setData({
          latitude: res.latitude,
          longitude: res.longitude,
          userLatitude: res.latitude,
          userLongitude: res.longitude,
          userAddress: '已定位',
        })
        self.loadSpots()
      },
      fail: function () {
        wx.showToast({ title: '定位失败', icon: 'none' })
        self.setData({ userAddress: '灵山胜境' })
      }
    })
  },

  /** 加载景点标注 */
  loadSpots: function () {
    var self = this
    var userId = wx.getStorageSync('guide_user_id') || 10001
    api.fetchSpots(userId, self.data.userLatitude, self.data.userLongitude).then(function (spots) {
      if (!spots || !Array.isArray(spots)) return
      // 转为地图标记（使用系统默认图标）
      var markers = spots.map(function (s, i) {
        return {
          id: s.id || i + 1,
          latitude: parseFloat(s.lat || s.latitude || 31.4245),
          longitude: parseFloat(s.lng || s.longitude || 120.0918),
          title: s.name || s.spot_name || '',
          width: 30,
          height: 30,
          callout: {
            content: s.name || s.spot_name || '',
            fontSize: 13,
            borderRadius: 6,
            bgColor: '#3D8B7E',
            padding: '6 10',
            color: '#ffffff',
            display: 'ALWAYS',
          }
        }
      })
      self.setData({
        spotList: spots,
        markers: markers,
      })
    }).catch(function (err) {
      console.error('load spots error:', err)
    })
  },

  /** 点击标记 */
  onMarkerTap: function (e) {
    var markerId = e.markerId
    var spot = this.data.spotList.find(function (s) {
      return (s.id || s.spot_id) === markerId
    })
    if (spot) {
      this.setData({
        selectedSpot: spot,
        showSpotPanel: true,
      })
    }
  },

  /** 关闭景点面板 */
  closeSpotPanel: function () {
    this.setData({ showSpotPanel: false, selectedSpot: null })
  },

  /** 导航到该景点 */
  navigateToSpot: function () {
    var spot = this.data.selectedSpot
    if (!spot) return
    var lat = parseFloat(spot.lat || spot.latitude || 31.4245)
    var lng = parseFloat(spot.lng || spot.longitude || 120.0918)
    var name = spot.name || spot.spot_name || ''
    wx.openLocation({
      latitude: lat,
      longitude: lng,
      name: name,
      address: '灵山胜境',
      scale: 18,
    })
  },

  /** 刷新位置 */
  refreshLocation: function () {
    wx.showLoading({ title: '定位中...' })
    var self = this
    this.getUserLocation()
    setTimeout(function () {
      self.loadSpots()
      wx.hideLoading()
      wx.showToast({ title: '位置已刷新', icon: 'success' })
    }, 600)
  },
})
