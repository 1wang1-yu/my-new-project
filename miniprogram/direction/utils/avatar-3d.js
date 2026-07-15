// ========== Three.js GLB 数字人渲染器 ==========
var glbLoader = require('./glb-loader.js');

var THREE = null;
var THREE_CANVAS = null;

function ensureThree(canvas) {
  // 如果 canvas 变了（页面重建后），必须重新创建 THREE 实例
  if (THREE && THREE_CANVAS === canvas) return THREE;
  THREE_CANVAS = canvas;
  var createScopedThreejs = require('threejs-miniprogram').createScopedThreejs;
  THREE = createScopedThreejs(canvas);
  return THREE;
}

function Avatar3D() {
  this.renderer = null;
  this.scene = null;
  this.camera = null;
  this.canvas = null;
  this.modelRoot = null;
  this.morphMeshes = [];
  this.morphMap = {};
  this.animId = null;
  this.lastTime = 0;

  this.mouthOpen = 0;
  this.targetMouthOpen = 0;
  this.currentViseme = 'rest';
  this.targetViseme = 'rest';
  this.isSpeaking = false;
  this.isModelReady = false;
  this.blinkTimer = 3.0;
  this.floatPhase = 0;
  this.modelBaseY = 0;

  // 眨眼 morph 列表
  this.blinkMorphs = [];
}

Avatar3D.prototype.init = function (canvas, width, height, modelUrl) {
  this.canvas = canvas;
  var T = ensureThree(canvas);

  try {
    this.renderer = new T.WebGLRenderer({ canvas: canvas, alpha: true, antialias: true });
    this.renderer.setSize(width, height);
    this.renderer.setPixelRatio(Math.min(2, wx.getSystemInfoSync().pixelRatio || 2));
    this.renderer.setClearColor(0x000000, 0);
  } catch (e) {
    console.error('WebGL 渲染器初始化失败:', e);
    THREE = null;  // 重置 THREE，允许后续重试
    THREE_CANVAS = null;
    return;
  }

  this.scene = new T.Scene();

  this.camera = new T.PerspectiveCamera(40, width / Math.max(height, 1), 0.1, 100);
  this.camera.position.set(0, 0.6, 2.5);
  this.camera.lookAt(0, 0.6, 0);

  this.scene.add(new T.AmbientLight(0xffffff, 0.7));
  var key = new T.DirectionalLight(0xffffff, 0.8);
  key.position.set(1, 2, 3);
  this.scene.add(key);
  var fill = new T.DirectionalLight(0x8899cc, 0.3);
  fill.position.set(-2, 0, -1);
  this.scene.add(fill);

  this.loadModel(T, modelUrl);
};

Avatar3D.prototype.loadModel = function (T, url) {
  var self = this;

  var placeGeom = new T.SphereGeometry(0.5, 16, 16);
  var placeMat = new T.MeshToonMaterial({ color: 0x556677 });
  this.placeholder = new T.Mesh(placeGeom, placeMat);
  this.scene.add(this.placeholder);

  glbLoader.loadGLB(T, url).then(function (gltf) {
    if (self.placeholder) {
      self.scene.remove(self.placeholder);
      self.placeholder.geometry.dispose();
      self.placeholder.material.dispose();
      self.placeholder = null;
    }

    self.modelRoot = gltf.scene;
    self.scene.add(self.modelRoot);

    self.scanMorphTargets(gltf);
    self.fitModelToView();

    self.isModelReady = true;
    console.log('GLB 模型加载完成,', self.morphMeshes.length, '个带口型 Mesh');
  }).catch(function (err) {
    console.error('GLB 加载失败:', err);
  });
};

Avatar3D.prototype.scanMorphTargets = function (gltf) {
  var self = this;
  this.morphMeshes = [];
  this.morphMap = {};
  this.blinkMorphs = [];

  // 下半脸 morph 排除标记（不参与口型以外控制）
  var LOWER_FACE_KEYS = /cheek|puff|suck/i;

  if (this.modelRoot) {
    this.modelRoot.traverse(function (child) {
      if (child.isMesh && child.morphTargetInfluences && child.morphTargetInfluences.length > 0) {
        self.morphMeshes.push(child);

        var dict = child.morphTargetDictionary || {};
        var names = Object.keys(dict);
        if (names.length === 0) {
          if (child.geometry && child.geometry.morphAttributes) {
            for (var i = 0; i < child.morphTargetInfluences.length; i++) {
              var key = 'target_' + i;
              if (!self.morphMap[key]) self.morphMap[key] = [];
              self.morphMap[key].push({ mesh: child, index: i });
            }
          }
        } else {
          for (var n = 0; n < names.length; n++) {
            var name = names[n];
            var idx = dict[name];
            var lower = name.toLowerCase();

            // 1) 检测是否为口型视素（原有逻辑）
            var category = null;
            if (/_a$/i.test(lower) || /^(a|aa|mouth.*open|jaw.*open|vowel.*a)/i.test(lower)) category = 'A';
            else if (/_i$/i.test(lower) || /^(i|ih|ee|mouth.*smile|vowel.*i)/i.test(lower)) category = 'I';
            else if (/_u$/i.test(lower) || /^(u|oo|mouth.*round|vowel.*u)/i.test(lower)) category = 'U';
            else if (/_e$/i.test(lower) || /^(e|eh|mouth.*wide|vowel.*e)/i.test(lower)) category = 'E';
            else if (/_o$/i.test(lower) || /^(o|oh|mouth.*close|vowel.*o)/i.test(lower)) category = 'O';

            if (category) {
              if (!self.morphMap[category]) self.morphMap[category] = [];
              self.morphMap[category].push({ mesh: child, index: idx });
              continue;
            }

            // 跳过下半脸 morph（留给口型系统）
            if (LOWER_FACE_KEYS.test(lower)) {
              continue;
            }
            // 检测眨眼 morph
            if (/blink/i.test(lower) || /eye.*(close|blink)/i.test(lower)) {
              self.blinkMorphs.push({ mesh: child, index: idx });
            }
          }
        }
      }
    });
  }

  console.log('口型视素:', Object.keys(this.morphMap).join(','));
};

Avatar3D.prototype.fitModelToView = function () {
  if (!this.modelRoot) return;
  var T = THREE;

  var box = new T.Box3().setFromObject(this.modelRoot);
  var size = box.getSize(new T.Vector3());
  var maxDim = Math.max(size.x, size.y, size.z);

  console.log('模型原始包围盒:', size.x.toFixed(2), size.y.toFixed(2), size.z.toFixed(2));

  if (maxDim > 0) {
    var scale = 3.0 / maxDim;
    this.modelRoot.scale.setScalar(scale);

    box.setFromObject(this.modelRoot);
    var center = box.getCenter(new T.Vector3());
    this.modelRoot.position.set(-center.x, -box.min.y, 0);
    this.modelBaseY = -box.min.y;
    console.log('缩放比例:', scale.toFixed(4));
  }

  var newSize = box.getSize(new T.Vector3());
  var faceY = this.modelBaseY + newSize.y * 0.85;
  var dist = Math.max(newSize.y * 0.22, 0.35);
  this.camera.position.set(0, faceY * 1.02, dist);
  this.camera.lookAt(0, faceY, 0);
};

// ========== 口型控制（视素驱动）==========

Avatar3D.prototype.setMouthOpen = function (v) {
  this.targetMouthOpen = Math.max(0, Math.min(1, v));
};

Avatar3D.prototype.setViseme = function (viseme, open) {
  this.targetViseme = viseme || 'rest';
  this.targetMouthOpen = Math.max(0, Math.min(1, open));
};

Avatar3D.prototype.setSpeaking = function (speaking) {
  this.isSpeaking = speaking;
  if (!speaking) {
    this.targetMouthOpen = 0;
    this.targetViseme = 'rest';
  }
};

/**
 * 将视素 + 开度映射到 BlendShape 权重
 * viseme: 'A'=大张 'I'=咧嘴 'U'=嘟嘴 'E'=中开 'O'=圆唇 'rest'=静止
 */
Avatar3D.prototype.applyMouthBlends = function () {
  var open = this.mouthOpen;
  var viseme = this.currentViseme || 'rest';

  // 仅重置口型 morph（不影响表情 morph）
  var allKeys = Object.keys(this.morphMap);
  for (var k = 0; k < allKeys.length; k++) {
    var entries = this.morphMap[allKeys[k]];
    for (var e = 0; e < entries.length; e++) {
      entries[e].mesh.morphTargetInfluences[entries[e].index] = 0;
    }
  }

  if (open < 0.02 || viseme === 'rest') return;

  // 主视素
  this._setMorph(viseme, open);

  // 相邻口型混合，让过渡更平滑
  // A ↔ E ↔ I ↔ U ↔ O 的相邻关系
  switch (viseme) {
    case 'A':
      this._setMorph('E', open * 0.15);
      break;
    case 'E':
      this._setMorph('A', open * 0.2);
      this._setMorph('I', open * 0.15);
      break;
    case 'I':
      this._setMorph('E', open * 0.2);
      break;
    case 'O':
      this._setMorph('U', open * 0.2);
      this._setMorph('A', open * 0.1);
      break;
    case 'U':
      this._setMorph('O', open * 0.2);
      break;
  }
};

Avatar3D.prototype._setMorph = function (category, weight) {
  var entries = this.morphMap[category];
  if (!entries) return;
  for (var i = 0; i < entries.length; i++) {
    var entry = entries[i];
    if (entry.mesh && entry.mesh.morphTargetInfluences) {
      entry.mesh.morphTargetInfluences[entry.index] = Math.max(0, Math.min(1, weight));
    }
  }
};

// ========== 动画循环 ==========

Avatar3D.prototype.startLoop = function () {
  var self = this;
  var cv = this.canvas;
  this.lastTime = Date.now();

  function frame() {
    self.update();
    if (self.renderer && self.scene && self.camera) {
      self.renderer.render(self.scene, self.camera);
    }
    self.animId = cv.requestAnimationFrame(frame);
  }

  this.animId = cv.requestAnimationFrame(frame);
};

Avatar3D.prototype.stopLoop = function () {
  if (this.animId && this.canvas && this.canvas.cancelAnimationFrame) {
    this.canvas.cancelAnimationFrame(this.animId);
  }
  this.animId = null;
};

Avatar3D.prototype.update = function () {
  var now = Date.now();
  var dt = Math.min((now - this.lastTime) / 1000, 0.1);
  this.lastTime = now;

  if (!this.isModelReady) return;

  // 口型平滑（说话时更快响应）
  var spd = this.isSpeaking ? 16 : 8;
  this.mouthOpen += (this.targetMouthOpen - this.mouthOpen) * spd * dt;

  // 视素切换
  if (this.isSpeaking) {
    this.currentViseme = this.targetViseme;
  } else if (this.mouthOpen < 0.02) {
    this.currentViseme = 'rest';
  }

  // ====== 口型 ======
  this.applyMouthBlends();

  // 眨眼
  this.blinkTimer -= dt;
  if (this.blinkTimer <= 0) {
    this.blinkTimer = 1.8 + Math.random() * 4.0;
    if (this.blinkMorphs.length > 0) {
      for (var b = 0; b < this.blinkMorphs.length; b++) {
        var bm = this.blinkMorphs[b];
        if (bm.mesh && bm.mesh.morphTargetInfluences) {
          bm.mesh.morphTargetInfluences[bm.index] = 0.9;
        }
      }
      var self = this;
      setTimeout(function () {
        for (var b2 = 0; b2 < self.blinkMorphs.length; b2++) {
          var bm2 = self.blinkMorphs[b2];
          if (bm2.mesh && bm2.mesh.morphTargetInfluences) {
            bm2.mesh.morphTargetInfluences[bm2.index] = 0;
          }
        }
      }, 120);
    }
  }

  // 待机浮动
  this.floatPhase += dt * 1.4;
  if (this.modelRoot && this.modelBaseY !== undefined) {
    this.modelRoot.position.y = this.modelBaseY + Math.sin(this.floatPhase) * 0.012 - 0.1;
  }
  if (this.placeholder) {
    this.placeholder.rotation.y += dt * 0.3;
  }
};

// ========== 资源释放 ==========

Avatar3D.prototype.dispose = function () {
  this.stopLoop();
  if (this.scene) {
    this.scene.traverse(function (obj) {
      if (obj.geometry) obj.geometry.dispose();
      if (obj.material) {
        if (Array.isArray(obj.material)) {
          obj.material.forEach(function (m) { m.dispose(); });
        } else {
          obj.material.dispose();
        }
      }
    });
    this.scene = null;
  }
  if (this.renderer) {
    this.renderer.dispose();
    this.renderer = null;
  }
};

module.exports = { Avatar3D: Avatar3D };
