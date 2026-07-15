var DEFAULT_BASE_URL = 'http://192.168.3.42:8081'
function getBaseUrl() {
  var app = getApp()
  var globalUrl = app && app.globalData && app.globalData.baseUrl
  // 优先用 storage 中保存的地址（用户在设置页配置的）
  var savedUrl = wx.getStorageSync('guide_base_url')
  return savedUrl || globalUrl || DEFAULT_BASE_URL
}

Page({
  data: {
    isLogin: true,
    username: '',
    password: '',
    nickName: '',
    ageStr: '',
    genderIndex: 0,
    loading: false,
    genderList: ['男', '女'],
    genderText: '选择性别',
  },

  onLoad: function () {
    // 已登录则直接跳首页
    if (wx.getStorageSync('guide_user_id')) {
      wx.reLaunch({ url: '/pages/index/index' })
    }
  },

  onUsernameInput: function (e) { this.setData({ username: e.detail.value }) },
  onPasswordInput: function (e) { this.setData({ password: e.detail.value }) },
  onNickInput: function (e) { this.setData({ nickName: e.detail.value }) },
  onAgeInput: function (e) { this.setData({ ageStr: e.detail.value }) },
  onGenderChange: function (e) {
    var idx = parseInt(e.detail.value)
    this.setData({ genderIndex: idx, genderText: this.data.genderList[idx] })
  },
  toggleMode: function () { this.setData({ isLogin: !this.data.isLogin }) },

  onSubmit: function () {
    var username = this.data.username.trim()
    var password = this.data.password.trim()
    if (!username || !password) {
      wx.showToast({ title: '请输入用户名和密码', icon: 'none' })
      return
    }

    var self = this
    this.setData({ loading: true })

    var url = getBaseUrl() + '/api/v1/user/' + (this.data.isLogin ? 'login' : 'register')
    var data = { username: username, password: password }

    if (!this.data.isLogin) {
      data.nickName = this.data.nickName.trim() || username
      data.age = this.data.ageStr || '0'
      data.gender = this.data.genderIndex === 0 ? 'male' : 'female'
    }

    wx.request({
      url: url, method: 'POST', data: data,
      success: function (res) {
        if (res.data && res.data.code === 0) {
          var r = res.data.data
          wx.setStorageSync('guide_user_id', r.user_id)
          wx.setStorageSync('guide_username', r.username)
          wx.setStorageSync('guide_user_profile', {
            age: r.age || 0, gender: r.gender || '', location: '',
            nickName: r.nick_name || username, username: r.username,
          })
          wx.showToast({ title: self.data.isLogin ? '登录成功' : '注册成功', icon: 'success' })
          setTimeout(function () { wx.reLaunch({ url: '/pages/index/index' }) }, 1200)
        } else {
          wx.showToast({ title: res.data.message || '操作失败', icon: 'none' })
        }
      },
      fail: function () {
        wx.showToast({ title: '网络错误，请检查后端', icon: 'none' })
      },
      complete: function () { self.setData({ loading: false }) }
    })
  },
})
