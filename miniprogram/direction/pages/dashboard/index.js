Page({
  data: {
    userName: '游客',
    scenicName: '灵山胜境',
    totalChats: 0,
    totalMessages: 0,
    totalDays: 0,
    chatHistory: [],
    expandedIndex: null,
  },

  onLoad() {
    this.loadProfile()
  },

  onShow() {
    this.loadProfile()
  },

  loadProfile() {
    try {
      var stored = wx.getStorageSync('guide_chat_history') || []
      var reversed = stored.slice().reverse()
      var totalMsgs = 0
      for (var i = 0; i < stored.length; i++) {
        totalMsgs += stored[i].messages ? stored[i].messages.length : 0
      }

      var history = reversed.map(function (item) {
        var firstUserMsg = ''
        if (item.messages) {
          for (var j = 0; j < item.messages.length; j++) {
            if (item.messages[j].role === 'user') {
              firstUserMsg = item.messages[j].content
              break
            }
          }
        }
        return {
          id: item.id,
          date: item.date,
          time: item.time,
          timestamp: item.timestamp,
          preview: firstUserMsg || '(空对话)',
          messageCount: item.messages ? item.messages.length : 0,
          messages: item.messages || [],
          scenicName: item.scenicName || '灵山胜境',
        }
      })

      var days = 0
      if (stored.length > 0) {
        var dates = {}
        for (var k = 0; k < stored.length; k++) {
          dates[stored[k].date] = true
        }
        days = Object.keys(dates).length
      }

      this.setData({
        chatHistory: history,
        totalChats: stored.length,
        totalMessages: totalMsgs,
        totalDays: days || 1,
      })
    } catch (e) {
      console.warn('加载个人中心数据失败', e)
    }
  },

  toggleDetail(e) {
    var index = e.currentTarget.dataset.index
    if (this.data.expandedIndex === index) {
      this.setData({ expandedIndex: null })
    } else {
      this.setData({ expandedIndex: index })
    }
  },

  clearHistory() {
    var self = this
    wx.showModal({
      title: '确认清空',
      content: '聊天记录仅保存在本地，清空后无法恢复。确定要继续吗？',
      confirmText: '清空',
      confirmColor: '#D94A3A',
      success: function (res) {
        if (res.confirm) {
          try {
            wx.removeStorageSync('guide_chat_history')
          } catch (e) {
            // ignore
          }
          self.setData({
            chatHistory: [],
            totalChats: 0,
            totalMessages: 0,
            totalDays: 0,
            expandedIndex: null,
          })
          wx.showToast({ title: '已清空', icon: 'success' })
        }
      },
    })
  },
})
