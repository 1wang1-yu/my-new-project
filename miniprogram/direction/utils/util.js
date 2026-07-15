function formatTime(date) {
  const year = date.getFullYear()
  const month = date.getMonth() + 1
  const day = date.getDate()
  const hour = date.getHours()
  const minute = date.getMinutes()
  const second = date.getSeconds()

  return `${[year, month, day].map(formatNumber).join('/')} ${[hour, minute, second].map(formatNumber).join(':')}`
}

function formatNumber(n) {
  const s = n.toString()
  return s[1] ? s : `0${s}`
}

function saveBaseUrl(baseUrl) {
  wx.setStorageSync('guide_base_url', baseUrl)
}

function loadBaseUrl(defaultValue) {
  return wx.getStorageSync('guide_base_url') || defaultValue
}

function splitInterests(text) {
  return (text || '')
    .split(/[，,、\s]+/)
    .map(item => item.trim())
    .filter(Boolean)
}

function getHistoryKey() {
  var userId = '0'
  try { userId = wx.getStorageSync('guide_user_id') || '0' } catch (e) {}
  return 'guide_chat_history_' + userId
}

module.exports = {
  formatTime,
  saveBaseUrl,
  loadBaseUrl,
  splitInterests,
  getHistoryKey,
}
