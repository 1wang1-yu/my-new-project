// ========== 拼音口型同步模块（视素驱动）==========

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
// viseme: A(大张) I(咧嘴) U(嘟嘴) E(中开) O(圆唇)
var FINAL_MAP = {
  'a':  { viseme: 'A', open: 0.90 },
  'ai': { viseme: 'A', open: 0.80 },
  'ao': { viseme: 'A', open: 0.90 },
  'an': { viseme: 'A', open: 0.75 },
  'ang':{ viseme: 'A', open: 0.90 },
  'o':  { viseme: 'O', open: 0.70 },
  'ou': { viseme: 'O', open: 0.55 },
  'ong':{ viseme: 'O', open: 0.65 },
  'e':  { viseme: 'E', open: 0.60 },
  'ei': { viseme: 'E', open: 0.45 },
  'en': { viseme: 'E', open: 0.40 },
  'eng':{ viseme: 'E', open: 0.55 },
  'er': { viseme: 'E', open: 0.45 },
  'i':  { viseme: 'I', open: 0.25 },
  'ia': { viseme: 'I', open: 0.70 },
  'ie': { viseme: 'I', open: 0.55 },
  'iu': { viseme: 'I', open: 0.20 },
  'ian':{ viseme: 'I', open: 0.60 },
  'in': { viseme: 'I', open: 0.20 },
  'ing':{ viseme: 'I', open: 0.20 },
  'iang':{ viseme: 'I', open: 0.70 },
  'iong':{ viseme: 'I', open: 0.50 },
  'u':  { viseme: 'U', open: 0.15 },
  'ua': { viseme: 'U', open: 0.70 },
  'uo': { viseme: 'U', open: 0.60 },
  'ui': { viseme: 'U', open: 0.15 },
  'uai':{ viseme: 'U', open: 0.65 },
  'uan':{ viseme: 'U', open: 0.55 },
  'un': { viseme: 'U', open: 0.15 },
  'uang':{ viseme: 'U', open: 0.70 },
  'v':  { viseme: 'U', open: 0.12 },
  've': { viseme: 'U', open: 0.40 },
  'vn': { viseme: 'U', open: 0.12 },
  'ih': { viseme: 'I', open: 0.20 }
};

function mapFinal(f) {
  return FINAL_MAP[f] || { viseme: 'rest', open: 0.20 };
}

/**
 * 构建口型时间轴
 * @returns {Array<{time: number, viseme: string, open: number}>}
 */
function buildLipSequence(text, durationMs) {
  if (!text || text.length === 0) return [];

  var map = loadMap();
  var chars = text.split('');
  var duration = durationMs || Math.max(chars.length * 250, 1000);
  var msPerChar = duration / chars.length;
  var seq = [];

  seq.push({ time: 0, viseme: 'rest', open: 0.02 });

  for (var i = 0; i < chars.length; i++) {
    var ch = chars[i];
    var viseme, open;

    if (ch >= '一' && ch <= '鿿') {
      var final = map.get(ch);
      var m = mapFinal(final);
      viseme = m.viseme;
      open = m.open;
    } else if (/[，。！？、；：\s]/.test(ch)) {
      viseme = 'rest';
      open = 0.02;
    } else {
      viseme = 'rest';
      open = 0.15;
    }

    seq.push({ time: Math.round((i + 1) * msPerChar), viseme: viseme, open: open });
  }

  seq.push({ time: duration, viseme: 'rest', open: 0.02 });

  return seq;
}

module.exports = { buildLipSequence: buildLipSequence };
