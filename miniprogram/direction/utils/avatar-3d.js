// ========== Three.js GLB 数字人渲染器 ==========
var glbLoader = require('./glb-loader.js');

var THREE = null;

function ensureThree(canvas) {
  if (THREE) return THREE;
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
}

Avatar3D.prototype.init = function (canvas, width, height, modelUrl) {
  this.canvas = canvas;
  var T = ensureThree(canvas);

  this.renderer = new T.WebGLRenderer({ canvas: canvas, alpha: true, antialias: true });
  this.renderer.setSize(width, height);
  this.renderer.setPixelRatio(Math.min(2, wx.getSystemInfoSync().pixelRatio || 2));
  this.renderer.setClearColor(0x000000, 0);

  this.scene = new T.Scene();

  this.camera = new T.PerspectiveCamera(40, width / Math.max(height, 1), 0.1, 100);
  this.camera.position.set(0, 0.9, 3.8);
  this.camera.lookAt(0, 0.9, 0);

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

            var category = null;
            if (/_a$/i.test(lower) || /^(a|aa|mouth.*open|jaw.*open|vowel.*a)/i.test(lower)) category = 'A';
            else if (/_i$/i.test(lower) || /^(i|ih|ee|mouth.*smile|vowel.*i)/i.test(lower)) category = 'I';
            else if (/_u$/i.test(lower) || /^(u|oo|mouth.*round|vowel.*u)/i.test(lower)) category = 'U';
            else if (/_e$/i.test(lower) || /^(e|eh|mouth.*wide|vowel.*e)/i.test(lower)) category = 'E';
            else if (/_o$/i.test(lower) || /^(o|oh|mouth.*close|vowel.*o)/i.test(lower)) category = 'O';

            if (category) {
              if (!self.morphMap[category]) self.morphMap[category] = [];
              self.morphMap[category].push({ mesh: child, index: idx });
            }
          }
        }
      }
    });
  }
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
  var dist = Math.max(newSize.y * 0.35, 0.6);
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

  // 重置
  var allKeys = Object.keys(this.morphMap);
  for (var k = 0; k < allKeys.length; k++) {
    var entries = this.morphMap[allKeys[k]];
    for (var e = 0; e < entries.length; e++) {
      entries[e].mesh.morphTargetInfluences[entries[e].index] = 0;
    }
  }

  if (open < 0.02 || viseme === 'rest') return;

  // 主视素权重
  this._setMorph(viseme, open * 1.0);

  // 辅助视素：根据主视素混入相邻口型，使过渡更自然
  if (open > 0.4) {
    // 张嘴时微混 A 型确保嘴巴张开
    if (viseme !== 'A') this._setMorph('A', open * 0.25);
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

  // 口型平滑
  var spd = this.isSpeaking ? 14 : 8;
  this.mouthOpen += (this.targetMouthOpen - this.mouthOpen) * spd * dt;

  // 视素直接切换（不插值，切换速度由开度平滑过渡掩盖）
  if (this.isSpeaking) {
    this.currentViseme = this.targetViseme;
  } else if (this.mouthOpen < 0.02) {
    this.currentViseme = 'rest';
  }

  if (this.isModelReady) {
    this.applyMouthBlends();
  }

  // 眨眼
  this.blinkTimer -= dt;
  if (this.blinkTimer <= 0) {
    this.blinkTimer = 2.2 + Math.random() * 4.5;
  }

  // 待机浮动
  this.floatPhase += dt * 1.2;
  if (this.modelRoot && this.modelBaseY !== undefined) {
    this.modelRoot.position.y = this.modelBaseY + Math.sin(this.floatPhase) * 0.008;
  }
  if (this.placeholder) {
    this.placeholder.rotation.y += dt * 0.5;
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
