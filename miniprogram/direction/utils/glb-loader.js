// ========== 小程序 GLB 下载 + GLTFLoader 解析 ==========
var createGLTFLoader = require('./GLTFLoader.js');

/**
 * 从小程序下载 GLB 并以 Three.js 场景加载
 * @param {object} THREE - createScopedThreejs 返回的 THREE 命名空间
 * @param {string} url - GLB 文件 URL
 * @returns {Promise<{scene: THREE.Scene, animations: Array, meshes: Array}>}
 */
function loadGLB(THREE, url) {
  return new Promise(function (resolve, reject) {
    wx.request({
      url: url,
      responseType: 'arraybuffer',
      success: function (res) {
        if (res.statusCode !== 200) {
          reject(new Error('下载 GLB 失败: HTTP ' + res.statusCode));
          return;
        }

        try {
          var GLTFLoader = createGLTFLoader(THREE);
          var loader = new GLTFLoader();

          loader.parse(
            res.data,
            '',
            function (gltf) {
              resolve(gltf);
            },
            function (err) {
              reject(new Error('GLB 解析失败: ' + (err.message || err)));
            }
          );
        } catch (e) {
          reject(new Error('GLB 初始化异常: ' + e.message));
        }
      },
      fail: function (err) {
        reject(new Error('网络请求失败: ' + (err.errMsg || '未知错误')));
      }
    });
  });
}

module.exports = { loadGLB: loadGLB };
