var i18n = require('../../../utils/i18n')

function buildAgeOptions() {
  var arr = []
  for (var i = 1; i <= 100; i++) { arr.push(i) }
  return arr
}

Page({
  data: {
    t: {},
    currentLang: 'zh',
    userProfile: { age: 0, gender: '', location: '' },
    ageOptions: buildAgeOptions(),
    genderOptions: [
      { value: 'male', label: '男' },
      { value: 'female', label: '女' },
      { value: 'other', label: '其他' },
    ],
  },

  onLoad() {
    this.applyTranslations()
    this.loadUserProfile()
  },

  onShow() { this.applyTranslations() },

  applyTranslations() {
    var lang = i18n.getLang()
    var t = i18n.getMessages(lang)
    t.scenicName = (wx.getStorageSync('guide_user_profile') || {}).nickName || i18n.t('scenicName', lang)

    var genderOptions = [
      { value: 'male', label: t.genderMale },
      { value: 'female', label: t.genderFemale },
      { value: 'other', label: t.genderOther },
    ]

    this.setData({ t: t, currentLang: lang, genderOptions: genderOptions })
  },

  loadUserProfile() {
    var saved = wx.getStorageSync('guide_user_profile')
    if (saved) this.setData({ userProfile: saved })
  },

  saveUserProfile(profile) {
    wx.setStorageSync('guide_user_profile', profile)
    this.setData({ userProfile: profile })
  },

  onAgeChange(e) {
    var age = this.data.ageOptions[parseInt(e.detail.value)]
    var profile = Object.assign({}, this.data.userProfile)
    profile.age = age
    this.saveUserProfile(profile)
  },

  onGenderChange(e) {
    var profile = Object.assign({}, this.data.userProfile)
    profile.gender = e.currentTarget.dataset.gender
    this.saveUserProfile(profile)
  },

  onLocationInput(e) {
    var profile = Object.assign({}, this.data.userProfile)
    profile.location = e.detail.value
    this.saveUserProfile(profile)
  },
})
