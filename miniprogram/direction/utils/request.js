const DEFAULT_BASE_URL = 'http://192.168.3.42:8081'

function normalizeError(message) {
  if (!message) {
    return '请求失败，请检查后端服务是否已启动'
  }
  if (message.indexOf('request:fail') >= 0) {
    return '无法连接后端，请确认 Spring Boot 已启动且手机/模拟器可访问该地址'
  }
  return message
}

function request({ url, method = 'GET', data, header = {} }) {
  const app = getApp()
  const baseUrl = (app && app.globalData && app.globalData.baseUrl) || DEFAULT_BASE_URL

  var requestTask = null
  var promise = new Promise((resolve, reject) => {
    requestTask = wx.request({
      url: `${baseUrl}${url}`,
      method,
      data,
      timeout: 200000,
      header: {
        'content-type': 'application/json',
        'Accept': 'application/json, text/plain, */*',
        ...header,
      },
      success(res) {
        const body = res.data || {}
        if (res.statusCode >= 200 && res.statusCode < 300 && body.code === 0) {
          resolve(body.data)
          return
        }
        reject(new Error(body.message || `请求失败(${res.statusCode})`))
      },
      fail(err) {
        reject(new Error(normalizeError(err.errMsg)))
      },
    })
  })

  // 返回同时支持 then/catch 和 abort 的对象
  return {
    then: promise.then.bind(promise),
    catch: promise.catch.bind(promise),
    finally: promise.finally.bind(promise),
    abort: function () {
      if (requestTask) requestTask.abort()
    },
  }
}

function upload({ url, filePath, name = 'file', formData = {} }) {
  const app = getApp()
  const baseUrl = (app && app.globalData && app.globalData.baseUrl) || DEFAULT_BASE_URL

  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: `${baseUrl}${url}`,
      filePath,
      name,
      timeout: 300000,
      formData,
      success(res) {
        let body = {}
        try {
          body = JSON.parse(res.data)
        } catch (e) {
          reject(new Error('上传响应解析失败'))
          return
        }

        if (res.statusCode >= 200 && res.statusCode < 300 && body.code === 0) {
          resolve(body.data)
          return
        }
        reject(new Error(body.message || `上传失败(${res.statusCode})`))
      },
      fail(err) {
        reject(new Error(normalizeError(err.errMsg)))
      },
    })
  })
}

module.exports = {
  request,
  upload,
  DEFAULT_BASE_URL,
}
