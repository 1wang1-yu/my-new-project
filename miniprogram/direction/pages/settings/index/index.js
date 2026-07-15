var api = require('../../../utils/api')
var i18n = require('../../../utils/i18n')

function buildAgeOptions() {
  var arr = []
  for (var i = 1; i <= 100; i++) { arr.push(i + '岁') }
  return arr
}

Page({
  data: {
    t: {},
    langList: [
      { code: 'zh', label: '中文' },
      { code: 'en', label: 'English' },
    ],
    currentLang: 'zh',
    // 个人信息
    userId: 0,
    nickName: '',
    age: 0,
    ageIndex: -1,
    ageText: '',
    gender: '',
    avatarUrl: '',
    ageOptions: buildAgeOptions(),
    saving: false,
  },

  onLoad() {
    this.applyTranslations()
    this.loadProfile()
  },

  onShow() {
    this.applyTranslations()
    this.loadProfile()
  },

  applyTranslations() {
    var lang = i18n.getLang()
    var t = i18n.getMessages(lang)
    this.setData({ t: t, currentLang: lang })
  },

  loadProfile() {
    var profile = wx.getStorageSync('guide_user_profile') || {}
    var userId = wx.getStorageSync('guide_user_id') || 0
    var age = profile.age || 0
    var ageIndex = age > 0 ? Math.min(age - 1, 99) : -1
    this.setData({
      userId: userId,
      nickName: profile.nickName || '',
      age: age,
      ageIndex: ageIndex,
      ageText: age > 0 ? age + '岁' : '',
      gender: profile.gender || '',
      avatarUrl: profile.avatarUrl || '',
    })
  },

  onNickInput(e) { this.setData({ nickName: e.detail.value }) },

  onAgeChange(e) {
    var idx = parseInt(e.detail.value)
    this.setData({ ageIndex: idx, age: idx + 1, ageText: (idx + 1) + '岁' })
  },

  setGender(e) {
    this.setData({ gender: e.currentTarget.dataset.gender })
  },

  /** 选择本地头像 */
  chooseAvatar() {
    var self = this
    wx.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: function (res) {
        var tempPath = res.tempFilePaths[0]
        // 转 base64 存本地预览
        wx.getFileSystemManager().readFile({
          filePath: tempPath,
          encoding: 'base64',
          success: function (fr) {
            var b64 = 'data:image/jpeg;base64,' + fr.data
            self.setData({ avatarUrl: b64 })
            // 也保存到本地临时头像
            wx.setStorageSync('guide_temp_avatar', b64)
          }
        })
      }
    })
  },

  /** 保存到后端 */
  saveProfile() {
    var self = this
    if (!this.data.userId) { wx.showToast({ title: '请先登录', icon: 'none' }); return }
    this.setData({ saving: true })

    var baseUrl = wx.getStorageSync('guide_base_url') || 'http://192.168.3.42:8081'
    var payload = {
      user_id: this.data.userId,
      nick_name: this.data.nickName || '',
    }
    if (this.data.age > 0) payload.age = this.data.age
    if (this.data.gender) payload.gender = this.data.gender
    // 头像 base64 只存本地，不上传到后端（数据库字段不够长）

    console.log('[PROFILE] saving to', baseUrl + '/api/v1/user/update', payload)
    wx.request({
      url: baseUrl + '/api/v1/user/update',
      method: 'POST',
      data: payload,
      success: function (res) {
        console.log('[PROFILE] response', res.data)
        if (res.data && res.data.code === 0) {
          var r = res.data.data
          var profile = wx.getStorageSync('guide_user_profile') || {}
          profile.nickName = r.nick_name || self.data.nickName
          profile.age = r.age || self.data.age
          profile.gender = r.gender || self.data.gender
          // 头像 base64 保留本地
          if (self.data.avatarUrl && self.data.avatarUrl.indexOf('base64') >= 0) {
            profile.avatarUrl = self.data.avatarUrl
          }
          wx.setStorageSync('guide_user_profile', profile)
          wx.showToast({ title: '保存成功', icon: 'success' })
        } else {
          wx.showToast({ title: res.data.message || '保存失败', icon: 'none' })
        }
      },
      fail: function (err) {
        console.error('[PROFILE] network error', err)
        wx.showToast({ title: '网络错误，已保存到本地', icon: 'none' })
        var profile = wx.getStorageSync('guide_user_profile') || {}
        profile.nickName = self.data.nickName
        profile.age = self.data.age
        profile.gender = self.data.gender
        if (self.data.avatarUrl) profile.avatarUrl = self.data.avatarUrl
        wx.setStorageSync('guide_user_profile', profile)
      },
      complete: function () { self.setData({ saving: false }) }
    })
  },

  selectLang(e) {
    var lang = e.currentTarget.dataset.lang
    if (!lang || lang === this.data.currentLang) return
    i18n.setLang(lang)
    this.applyTranslations()
    wx.showToast({ title: i18n.t('languageChanged', lang), icon: 'success' })
  },

  onLogout() {
    var self = this
    wx.showModal({
      title: '退出登录',
      content: '确定要退出当前账号吗？',
      success: function (res) {
        if (res.confirm) {
          wx.removeStorageSync('guide_user_id')
          wx.removeStorageSync('guide_username')
          wx.removeStorageSync('guide_open_id')
          wx.removeStorageSync('guide_user_profile')
          wx.showToast({ title: '已退出', icon: 'success' })
          setTimeout(function () { wx.reLaunch({ url: '/pages/login/index' }) }, 1000)
        }
      }
    })
  },
})
