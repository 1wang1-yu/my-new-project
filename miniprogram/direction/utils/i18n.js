var LANG_KEY = 'guide_language'

var messages = {
  zh: {
    // 通用
    scenicName: '灵山胜境',
    // 首页 - 问候
    morning: '上午好', 
    afternoon: '下午好',
    evening: '晚上好',
    // 首页 - 操作栏
    actionChat: '智能问答',
    actionRoute: '路线规划',
    actionTts: '语音播报',
    actionStop: '停止',
    actionProfile: '我的',
    // 首页 - 对话区
    chatTitle: '智能导览对话',
    chatPlaceholder: '想问什么尽管说...',
    sendBtn: '发送',
    voiceBtn: '🎤',
    recordingBtn: '⏺',
    suggestLabel: '猜你想问',
    // 首页 - 路线
    routeTitle: '为你定制的路线',
    routeBasedOn: '基于你对',
    routeInterest: '的兴趣',
    routeAdjust: '调整兴趣偏好',
    routeRedo: '重新规划路线',
    routeMin: '分钟',
    // 首页 - 数字人
    avatarSubtitle: '您好，请问有什么可以帮您？',
    // 首页 - 新对话
    newChat: '＋ 新对话',
    newChatStarted: '新对话已开始',
    // 个人中心
    profileTitle: '个人中心',
    userName: '游客',
    userDesc: '智能导览用户',
    statChats: '次对话',
    statMsgs: '条消息',
    statDays: '天使用',
    avatarTitle: '选择形象',
    avatarDefault: '默认形象',
    avatarDefault2: '自然向导',
    avatarChanged: '形象已切换',
    historyTitle: '聊天记录',
    msgCount: '条消息',
    viewDetail: '查看详情',
    collapse: '收起',
    emptyHistory: '暂无聊天记录',
    emptyHint: '去首页开始一段导览对话吧',
    clearBtn: '清空聊天记录',
    clearHint: '聊天记录仅保存在本地，清空后无法恢复',
    settingsTitle: '设置',
    clearTitle: '确认清空',
    clearContent: '聊天记录仅保存在本地，清空后无法恢复。确定要继续吗？',
    clearOk: '清空',
    cleared: '已清空',
    emptyChat: '(空对话)',
    languageTitle: '语言 / Language',
    languageChanged: '已切换语言',
    // 个人信息
    profileTitle: '个人信息',
    profileAge: '年龄',
    profileGender: '性别',
    profileLocation: '居住地',
    profileAgePlaceholder: '请选择年龄',
    profileLocationPlaceholder: '请输入城市名',
    profileSaved: '个人信息已保存',
    genderMale: '男',
    genderFemale: '女',
    genderOther: '其他',
    // 打卡
    checkinTitle: '景区打卡',
    checkinGpsLoading: '正在获取位置...',
    checkinGpsError: '无法获取位置，请检查GPS权限',
    checkinRange: '在范围内',
    checkinChecked: '已打卡',
    checkinNotChecked: '未打卡',
    checkinTotal: '打卡进度',
    checkinRefresh: '刷新位置',
    checkinDetail: '查看打卡记录',
    // 对话角色
    roleYou: '你',
    roleGuide: '小导',
  },
  en: {
    scenicName: 'Lingshan',
    morning: 'Good Morning',
    afternoon: 'Good Afternoon',
    evening: 'Good Evening',
    actionChat: 'Chat',
    actionRoute: 'Route',
    actionTts: 'Audio',
    actionStop: 'Stop',
    actionProfile: 'Me',
    chatTitle: 'AI Tour Guide',
    chatPlaceholder: 'Ask me anything...',
    sendBtn: 'Send',
    voiceBtn: '🎤',
    recordingBtn: '⏺',
    suggestLabel: 'Suggested',
    routeTitle: 'Your Custom Route',
    routeBasedOn: 'Based on your interest in',
    routeInterest: '',
    routeAdjust: 'Adjust Interests',
    routeRedo: 'Re-plan Route',
    routeMin: 'min',
    avatarSubtitle: 'Hello, how can I help you?',
    newChat: 'New Chat',
    newChatStarted: 'New chat started',
    profileTitle: 'Profile',
    userName: 'Visitor',
    userDesc: 'AI Tour Guide User',
    statChats: 'Chats',
    statMsgs: 'Messages',
    statDays: 'Days',
    avatarTitle: 'Choose Avatar',
    avatarDefault: 'Default',
    avatarDefault2: 'Nature Guide',
    avatarChanged: 'Avatar changed',
    historyTitle: 'Chat History',
    msgCount: 'messages',
    viewDetail: 'Details',
    collapse: 'Collapse',
    emptyHistory: 'No chat history',
    emptyHint: 'Start a conversation on the home page',
    clearBtn: 'Clear History',
    clearHint: 'Chat history is stored locally only. Cannot be recovered after clearing.',
    settingsTitle: 'Settings',
    clearTitle: 'Confirm',
    clearContent: 'Chat history is stored locally only. Cannot be recovered after clearing. Continue?',
    clearOk: 'Clear',
    cleared: 'Cleared',
    emptyChat: '(empty)',
    languageTitle: 'Language',
    languageChanged: 'Language changed',
    profileTitle: 'Personal Info',
    profileAge: 'Age',
    profileGender: 'Gender',
    profileLocation: 'Location',
    profileAgePlaceholder: 'Select age',
    profileLocationPlaceholder: 'Enter city name',
    profileSaved: 'Profile saved',
    genderMale: 'Male',
    genderFemale: 'Female',
    genderOther: 'Other',
    checkinTitle: 'Check-in',
    checkinGpsLoading: 'Getting location...',
    checkinGpsError: 'Cannot get location, check GPS permission',
    checkinRange: 'In range',
    checkinChecked: 'Checked in',
    checkinNotChecked: 'Not checked',
    checkinTotal: 'Progress',
    checkinRefresh: 'Refresh',
    checkinDetail: 'Check-in History',
    roleYou: 'You',
    roleGuide: 'Guide',
  },
}

function getLang() {
  try {
    var lang = wx.getStorageSync(LANG_KEY)
    if (lang && messages[lang]) return lang
  } catch (e) { /* ignore */ }
  return 'zh'
}

function setLang(lang) {
  try {
    wx.setStorageSync(LANG_KEY, lang)
  } catch (e) { /* ignore */ }
}

/**
 * 根据当前语言取全量翻译对象
 */
function getMessages(lang) {
  lang = lang || getLang()
  return messages[lang] || messages['zh']
}

/**
 * 单 key 翻译
 */
function t(key, lang) {
  var m = getMessages(lang)
  return m[key] !== undefined ? m[key] : (messages['zh'][key] || key)
}

/**
 * 获取问候语（根据时间自动选择）
 */
function greeting(lang) {
  var m = getMessages(lang)
  var h = new Date().getHours()
  if (h < 12) return m.morning
  if (h < 18) return m.afternoon
  return m.evening
}

module.exports = {
  getLang: getLang,
  setLang: setLang,
  getMessages: getMessages,
  t: t,
  greeting: greeting,
  LANG_KEY: LANG_KEY
}
