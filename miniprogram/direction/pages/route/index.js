var api = require('../../utils/api')
var util = require('../../utils/util')
var i18n = require('../../utils/i18n')

Page({
  data: {
    // 兴趣标签
    allInterests: ['文化历史', '自然风光', '拍照打卡', '亲子游玩', '美食探索', '休闲放松', '古建筑', '佛教文化', '登山徒步', '网红景点'],
    selectedInterests: ['文化历史', '自然风光'],
    tagItems: [],

    // 自定义偏好输入
    customPreference: '',

    // 景点选择
    allAttractions: [],
    selectedAttractions: [],

    // 时间
    startTime: '09:00',
    endTime: '16:00',
    durationStr: '',
    // 实际用于路线的起止时间（从后端返回，可能与选择器不同）
    actualStartTime: '',
    actualEndTime: '',

    // 会话ID（从首页聊天传入）
    sessionId: '',

    // 结果
    routeResult: [],
    loading: false,
    loadingAttractions: false,
    t: {},
  },

  onLoad: function (options) {
    this.setData({ t: i18n.getMessages() })
    this.buildTagItems()
    this.calcDuration()
    this.refreshSessionId(options)
    this.loadAttractions()
  },

  /** 每次切换到此 tab 时刷新 sessionId */
  onShow: function () {
    this.refreshSessionId()
  },

  /** 从全局数据获取最新 sessionId */
  refreshSessionId: function (options) {
    if (options && options.session_id) {
      this.setData({ sessionId: options.session_id })
      return
    }
    var app = getApp()
    if (app.globalData && app.globalData.sessionId) {
      this.setData({ sessionId: app.globalData.sessionId })
    }
  },

  /** 从后端获取景点列表 */
  loadAttractions: function () {
    var self = this
    self.setData({ loadingAttractions: true })

    api.fetchAttractions().then(function (data) {
      if (data && Array.isArray(data) && data.length > 0) {
        self.setData({ allAttractions: data })
      }
    }).catch(function (err) {
      console.error('load attractions error:', err)
    }).finally(function () {
      self.setData({ loadingAttractions: false })
    })
  },

  // ===== 兴趣标签 =====
  toggleInterest: function (e) {
    var item = e.currentTarget.dataset.name
    var selected = this.data.selectedInterests
    var idx = selected.indexOf(item)
    if (idx >= 0) {
      selected.splice(idx, 1)
    } else {
      selected.push(item)
    }
    this.setData({ selectedInterests: selected })
    this.buildTagItems()
  },

  buildTagItems: function () {
    var items = this.data.allInterests.map(function (name) {
      return {
        name: name,
        active: this.data.selectedInterests.indexOf(name) >= 0
      }
    }.bind(this))
    this.setData({ tagItems: items })
  },

  // ===== 自定义偏好输入 =====
  onPreferenceInput: function (e) {
    this.setData({ customPreference: e.detail.value })
  },

  // ===== 景点选择 =====
  toggleAttraction: function (e) {
    var item = e.currentTarget.dataset.name
    var selected = this.data.selectedAttractions
    var idx = selected.indexOf(item)
    if (idx >= 0) {
      selected.splice(idx, 1)
    } else {
      selected.push(item)
    }
    this.setData({ selectedAttractions: selected })
  },

  // ===== 时间 =====
  onStartTimeChange: function (e) {
    this.setData({ startTime: e.detail.value }, this.calcDuration)
  },

  onEndTimeChange: function (e) {
    this.setData({ endTime: e.detail.value }, this.calcDuration)
  },

  calcDuration: function () {
    var start = this.data.startTime
    var end = this.data.endTime
    if (!start || !end) { this.setData({ durationStr: '' }); return }
    var s = start.split(':'), e = end.split(':')
    var minutes = (parseInt(e[0]) * 60 + parseInt(e[1])) - (parseInt(s[0]) * 60 + parseInt(s[1]))
    if (minutes <= 0) { this.setData({ durationStr: '' }); return }
    var h = Math.floor(minutes / 60), m = minutes % 60
    this.setData({ durationStr: (h > 0 ? h + '小时' : '') + (m > 0 ? m + '分钟' : '') })
  },

  // ===== 提交规划 =====
  getRoute: function () {
    if (this.data.selectedInterests.length === 0 && !this.data.customPreference) {
      wx.showToast({ title: '请选择兴趣或输入偏好', icon: 'none' })
      return
    }

    this.setData({ loading: true, routeResult: [], actualStartTime: '', actualEndTime: '' })
    var self = this

    var start = this.data.startTime
    var end = this.data.endTime
    var durationMinutes = 120
    if (start && end) {
      var s = start.split(':'), e = end.split(':')
      durationMinutes = Math.max(60, (parseInt(e[0]) * 60 + parseInt(e[1])) - (parseInt(s[0]) * 60 + parseInt(s[1])))
    }

    var payload = {
      user_id: wx.getStorageSync('guide_user_id') || 10001,
      interests: self.data.selectedInterests,
      duration_minutes: durationMinutes,
      start_time: start,
      end_time: end,
    }

    // 添加自定义偏好（如果有）
    if (self.data.customPreference) {
      payload.custom_preference = self.data.customPreference
    }

    // 添加指定景点（如果有）
    if (self.data.selectedAttractions.length > 0) {
      payload.selected_attractions = self.data.selectedAttractions
    }

    // 添加会话ID（如果有，用于从聊天历史提取偏好和时间）
    if (self.data.sessionId) {
      payload.session_id = self.data.sessionId
    }

    api.recommendRoute(payload).then(function (data) {
      var routes = data.routes || []
      var updates = { routeResult: routes }

      // 从后端返回的起止时间更新选择器
      if (data.start_time && data.end_time) {
        updates.startTime = data.start_time
        updates.endTime = data.end_time
        updates.actualStartTime = data.start_time
        updates.actualEndTime = data.end_time
        // 重算时长
        var s = data.start_time.split(':'), e = data.end_time.split(':')
        var minutes = (parseInt(e[0]) * 60 + parseInt(e[1])) - (parseInt(s[0]) * 60 + parseInt(s[1]))
        if (minutes > 0) {
          var h = Math.floor(minutes / 60), m = minutes % 60
          updates.durationStr = (h > 0 ? h + '小时' : '') + (m > 0 ? m + '分钟' : '')
        }
      }

      self.setData(updates)
      if (routes.length === 0) {
        wx.showToast({ title: '暂无路线，请调整偏好', icon: 'none' })
      } else {
        wx.showToast({ title: '路线规划成功！', icon: 'success' })
      }
    }).catch(function (err) {
      console.error('route error:', err)
      wx.showToast({ title: '路线规划失败，请检查后端是否启动', icon: 'none' })
    }).finally(function () {
      self.setData({ loading: false })
    })
  },

  // ===== 清空选择 =====
  clearAll: function () {
    this.setData({
      selectedInterests: [],
      customPreference: '',
      selectedAttractions: [],
      routeResult: [],
      actualStartTime: '',
      actualEndTime: '',
    })
    this.buildTagItems()
  },

  // ===== 导入聊天偏好 =====
  importFromChat: function () {
    if (this.data.sessionId) {
      wx.showModal({
        title: '已关联聊天记录',
        content: '你的聊天记录中的偏好和游玩时间将自动用于路线规划，请直接点击"AI 智能规划路线"按钮。',
        showCancel: false,
      })
    } else {
      wx.showModal({
        title: '未关联聊天',
        content: '你还没有在聊天中说过偏好，请先在上方输入你的需求，或者先在首页聊天中告诉我你喜欢什么~',
        showCancel: false,
      })
    }
  },
})
