var util = require('../../../utils/util')
var i18n = require('../../../utils/i18n')

Page({
  data: {
    t: {},
    chatHistory: [],
    expandedIndex: null,
  },

  onLoad() {
    this.applyTranslations()
    this.loadHistory()
  },

  onShow() {
    this.applyTranslations()
    this.loadHistory()
  },

  applyTranslations() {
    var lang = i18n.getLang()
    this.setData({ t: i18n.getMessages(lang) })
  },

  loadHistory() {
    var t = this.data.t
    var stored = wx.getStorageSync(util.getHistoryKey()) || []
    var reversed = stored.slice().reverse()
    var emptyPreview = t.emptyChat || '(空对话)'

    var history = reversed.map(function (item) {
      var firstUserMsg = ''
      if (item.messages) {
        for (var j = 0; j < item.messages.length; j++) {
          if (item.messages[j].role === 'user') {
            firstUserMsg = item.messages[j].content; break
          }
        }
      }
      return {
        id: item.id, date: item.date, time: item.time,
        timestamp: item.timestamp,
        preview: firstUserMsg || emptyPreview,
        messageCount: item.messages ? item.messages.length : 0,
        messages: item.messages || [],
      }
    })

    this.setData({ chatHistory: history })
  },

  toggleDetail(e) {
    var index = e.currentTarget.dataset.index
    this.setData({
      expandedIndex: this.data.expandedIndex === index ? null : index,
    })
  },

  clearHistory() {
    var self = this
    var t = this.data.t
    wx.showModal({
      title: t.clearTitle,
      content: t.clearContent,
      confirmText: t.clearOk,
      confirmColor: '#D94A3A',
      success: function (res) {
        if (res.confirm) {
          wx.removeStorageSync(util.getHistoryKey())
          self.setData({ chatHistory: [], expandedIndex: null })
          wx.showToast({ title: t.cleared, icon: 'success' })
        }
      },
    })
  },
})
