// ========== 拼音 + 英文 口型同步模块（视素驱动）==========

var pinyinData = require('./pinyin-data.js');

var charFinalMap = null;

function loadMap() {
  if (charFinalMap) return charFinalMap;
  charFinalMap = new Map();
  var entries = pinyinData.split('|');
  for (var i = 0; i < entries.length; i++) {
    var entry = entries[i];
    var ci = entry.indexOf(':');
    if (ci > 0) {
      charFinalMap.set(entry.charAt(0), entry.slice(ci + 1));
    }
  }
  return charFinalMap;
}

// 韵母 → { viseme, open }
var FINAL_MAP = {
  'a':  { viseme: 'A', open: 0.95 },
  'ai': { viseme: 'A', open: 0.85 },
  'ao': { viseme: 'A', open: 0.92 },
  'an': { viseme: 'A', open: 0.80 },
  'ang':{ viseme: 'A', open: 0.90 },
  'o':  { viseme: 'O', open: 0.75 },
  'ou': { viseme: 'O', open: 0.60 },
  'ong':{ viseme: 'O', open: 0.70 },
  'e':  { viseme: 'E', open: 0.65 },
  'ei': { viseme: 'E', open: 0.50 },
  'en': { viseme: 'E', open: 0.45 },
  'eng':{ viseme: 'E', open: 0.60 },
  'er': { viseme: 'E', open: 0.50 },
  'i':  { viseme: 'I', open: 0.28 },
  'ia': { viseme: 'I', open: 0.72 },
  'ie': { viseme: 'I', open: 0.58 },
  'iu': { viseme: 'I', open: 0.22 },
  'ian':{ viseme: 'I', open: 0.62 },
  'in': { viseme: 'I', open: 0.22 },
  'ing':{ viseme: 'I', open: 0.22 },
  'iang':{ viseme: 'I', open: 0.72 },
  'iong':{ viseme: 'I', open: 0.55 },
  'u':  { viseme: 'U', open: 0.18 },
  'ua': { viseme: 'U', open: 0.72 },
  'uo': { viseme: 'U', open: 0.62 },
  'ui': { viseme: 'U', open: 0.18 },
  'uai':{ viseme: 'U', open: 0.68 },
  'uan':{ viseme: 'U', open: 0.58 },
  'un': { viseme: 'U', open: 0.18 },
  'uang':{ viseme: 'U', open: 0.72 },
  'v':  { viseme: 'U', open: 0.15 },
  've': { viseme: 'U', open: 0.42 },
  'vn': { viseme: 'U', open: 0.15 },
  'ih': { viseme: 'I', open: 0.20 }
};

// 英文字母 → { viseme, open }
var EN_MAP = {
  'a': { viseme: 'A', open: 0.80 },
  'e': { viseme: 'E', open: 0.55 },
  'i': { viseme: 'I', open: 0.25 },
  'o': { viseme: 'O', open: 0.65 },
  'u': { viseme: 'U', open: 0.15 },
  'y': { viseme: 'I', open: 0.20 },
  'w': { viseme: 'U', open: 0.15 },
  'b': { viseme: 'rest', open: 0.18 },
  'm': { viseme: 'rest', open: 0.18 },
  'p': { viseme: 'rest', open: 0.18 },
  'f': { viseme: 'rest', open: 0.12 },
  'v': { viseme: 'rest', open: 0.12 },
  'l': { viseme: 'E', open: 0.30 },
  'r': { viseme: 'E', open: 0.30 },
  's': { viseme: 'I', open: 0.12 },
  'z': { viseme: 'I', open: 0.12 },
  'c': { viseme: 'I', open: 0.15 },
  'h': { viseme: 'rest', open: 0.50 },
  'j': { viseme: 'I', open: 0.18 },
  't': { viseme: 'rest', open: 0.10 },
  'd': { viseme: 'rest', open: 0.10 },
  'n': { viseme: 'rest', open: 0.10 },
  'k': { viseme: 'rest', open: 0.12 },
  'g': { viseme: 'rest', open: 0.12 },
  'q': { viseme: 'U', open: 0.20 },
  'x': { viseme: 'I', open: 0.15 }
};

function mapFinal(f) {
  return FINAL_MAP[f] || { viseme: 'rest', open: 0.20 };
}

function mapEn(ch) {
  return EN_MAP[ch.toLowerCase()] || { viseme: 'rest', open: 0.10 };
}

function hasCJK(text) {
  for (var i = 0; i < text.length; i++) {
    var code = text.charCodeAt(i);
    if (code >= 0x4E00 && code <= 0x9FFF) return true;
    if (code >= 0x3400 && code <= 0x4DBF) return true;
  }
  return false;
}

/**
 * 构建口型序列
 * 优化：更精确的时间分配，更自然的口型过渡
 * @returns {Array<{time: number, viseme: string, open: number}>}
 */
function buildLipSequence(text, durationMs) {
  if (!text || text.length === 0) return [];

  var chars = text.split('');
  // 至少留 300ms 的 rest 在结尾
  var duration = Math.max((durationMs || Math.max(chars.length * 200, 1000)) - 200, 200);
  var seq = [];
  var visemeOpenValues = [];

  seq.push({ time: 0, viseme: 'rest', open: 0.02 });

  var isChinese = hasCJK(text);
  var totalWeight = 0;

  if (isChinese) {
    var map = loadMap();
    // 开口音权重高，闭口音权重低，标点权重低
    var VOWEL_WEIGHT = { 'A': 2.2, 'E': 1.6, 'O': 1.6, 'I': 1.0, 'U': 0.8, 'rest': 0.5 };
    for (var i = 0; i < chars.length; i++) {
      var ch = chars[i];
      var viseme, open, weight;
      if (ch >= '一' && ch <= '鿿') {
        var final = map.get(ch);
        var m = mapFinal(final);
        viseme = m.viseme;
        open = m.open;
        weight = VOWEL_WEIGHT[viseme] || 1.0;
      } else if (/[，。！？、；：\s]/.test(ch)) {
        viseme = 'rest';
        open = 0.02;
        weight = 0.6;
      } else {
        viseme = 'rest';
        open = 0.15;
        weight = 0.5;
      }
      visemeOpenValues.push({ viseme: viseme, open: open, weight: weight });
      totalWeight += weight;
    }
  } else {
    var EN_WEIGHT = { 'A': 2.0, 'E': 1.4, 'O': 1.4, 'I': 0.9, 'U': 0.8, 'rest': 0.5 };
    for (var j = 0; j < chars.length; j++) {
      var ech = chars[j];
      var enM, w;
      if (ech === ' ' || ech === '\n' || ech === '\t') {
        enM = { viseme: 'rest', open: 0.02 }; w = 0.4;
      } else if (/[.,!?;:\-—\"\'()]/.test(ech)) {
        enM = { viseme: 'rest', open: 0.05 }; w = 0.5;
      } else {
        enM = mapEn(ech);
        w = EN_WEIGHT[enM.viseme] || 0.7;
      }
      visemeOpenValues.push({ viseme: enM.viseme, open: enM.open, weight: w });
      totalWeight += w;
    }
  }

  // 按权重分配时间（使用小数避免累积取整误差）
  var perWeight = totalWeight > 0 ? duration / totalWeight : (chars.length > 0 ? duration / chars.length : 10);
  var accumulated = 0;

  for (var k = 0; k < visemeOpenValues.length; k++) {
    var item = visemeOpenValues[k];
    accumulated += item.weight * perWeight;
    // 不取整，保留毫秒精度
    var t = Math.min(accumulated, duration);
    // 增强开口/闭口对比度
    var finalOpen = item.open;
    if (item.viseme === 'A' || item.viseme === 'O') {
      finalOpen = Math.min(1.0, item.open * 1.15);
    } else if (item.viseme === 'I' || item.viseme === 'U') {
      finalOpen = item.open * 0.85;
    }
    seq.push({ time: t, viseme: item.viseme, open: finalOpen });
  }

  // 结尾 rest
  if (seq.length > 0 && seq[seq.length - 1].time < duration) {
    seq.push({ time: duration + 100, viseme: 'rest', open: 0.02 });
  }
  // 再加一个保底
  seq.push({ time: duration + 200, viseme: 'rest', open: 0.02 });

  return seq;
}

module.exports = { buildLipSequence: buildLipSequence };
