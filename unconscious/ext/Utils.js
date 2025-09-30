/*! FunEx next (beta)
 *  Author: Roj234 @ 2024/7/15 - 2025/4/28, All rights reserved
 */


//元素选择器
/**
 *
 * @param {string} selector
 * @param {HTMLElement=document} element
 * @returns {HTMLElement | null}
 */
function G(selector, element){return (element??document).querySelector(selector);}
/**
 *
 * @param {string} selector
 * @param {HTMLElement=document} element
 * @returns {HTMLElement[]}
 */
function A(selector, element){return Array.from((element??document).querySelectorAll(selector));}

export {G, A}

//region 通知
function addNotification(message, duration) {
	const notification = E(`<div class="notification"><button class="close" onclick="closeNotification(this.parentElement)">&times;</button>${message}</div>`);

	G('#notifications').appendChild(notification);

	requestAnimationFrame(() => notification.classList.add('show'));

	const close = () => closeNotification(notification);
	if (duration) setTimeout(close, duration);
	return {close}
}
function closeNotification(el) {
	el.classList.remove('show');
	setTimeout(() => el.remove(), 300);
}
export {addNotification, closeNotification}
//endregion
//region 日期与时间
const parseSubRx = {
	"Y": ["(\\d{4})", "setFullYear"],
	"y": "(\\d{2})",

	"m": "(\\d{2})",
	"n": "(\\d{1,2})",

	"d": ["(\\d{2})", "setDate"],
	"j": ["(\\d{1,2})", "setDate"],

	"H": ["(\\d{2})", "setHours"],
	"i": ["(\\d{2})", "setMinutes"],
	"s": ["(\\d{2})", "setSeconds"],

	"c": "(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[+-]\\d{2}:\\d{2})",
	"P": "([+-]\\d{2}:\\d{2})",
	"O": "([+-]\\d{4})",
	"U": "(\\d{0,20})"
};

const parseDate = function(pattern, date) {
	var _times = [];
	var id = 1;
	pattern = pattern.replace(/[\\]?([a-zA-Z])/g, function(t, s, i) {
		var px = parseSubRx[t];
		if (px) {
			_times.push({
				i: id++,
				type: t,
				func: px.sort && px[1]
			});
			return px.sort ? px[0] : px;
		}
		return s;
	});

	var rx = new RegExp("^" + pattern + "$").exec(date);

	if (rx == null) {
		throw new Error("Format error, expecting " + pattern);
	}

	date = new Date(0);

	var o = {};
	for (var j = 0; j < _times.length; j++) {
		var t = rx[_times[j].i];
		if (_times[j].func)
			date[_times[j].func](parseInt(t, 10));
		else
			o[_times[j].type] = t;
	}

	if (o.c !== undefined) {
		return strToTimeV2("Y-m-dTH:i:sP", o.c);
	} else if (o.U !== undefined) {
		date.setTime(parseInt(o.U, 10) * 1000);
	} else if (o.m !== undefined) {
		date.setMonth(parseInt(o.m, 10) - 1);
	} else if (o.n !== undefined) {
		date.setMonth(parseInt(o.n, 10) - 1);
	}

	return date;
}

var txt_weekdays = ["天", "一", "二", "三", "四", "五", "六"];

function pad(n, c) {
	return String(n).padStart(c, "0");
}

const formatDate = function(format, stamp) {
	if (stamp === 0) return "-";

	var date = (null != stamp ? new Date(stamp) : new Date());
	var f = {
		// Year
		L: function() { // 闰年
			var y = f.Y();
			return (0 === (y & 3) && (y % 1e2 || 0 === (y % 4e2))) ? 1 : 0;
		},
		Y: function() { // 2012
			return date.getFullYear();
		},
		y: function() { // 12
			return String(date.getFullYear()).slice(2);
		},
		// Day
		d: function() { // 01
			return pad(f.j(), 2);
		},
		j: function() { // 1
			return date.getDate();
		},
		// Week
		l: function() { // 星期一
			return "星期" + txt_weekdays[f.w()];
		},
		w: function() { // 0-6星期
			return date.getDay();
		},
		N: function() { // 1-7
			return f.w() + 1;
		},
		// Month
		m: function() { // 01
			return pad(f.n(), 2);
		},
		n: function() { // 1
			return date.getMonth() + 1;
		},
		t: function() { // 本月有几天
			var n;
			if ((n = date.getMonth() + 1) === 2) {
				return 28 + f.L();
			} else {
				n = 0 !== (n & 1);
				if (n && n < 8 || !n && n > 7) {
					return 31;
				} else {
					return 30;
				}
			}
		},
		// Time
		a: function() { // 小写
			return date.getHours() > 11 ? "pm" : "am";
		},
		A: function() { // 大写
			return f.a().toUpperCase();
		},
		g: function() { // am/pm时间
			return date.getHours() % 12 || 12;
		},
		G: function() {
			return date.getHours();
		},
		h: function() { // 01
			return pad(f.g(), 2);
		},
		H: function() { // 01
			return pad(date.getHours(), 2);
		},
		i: function() {
			return pad(date.getMinutes(), 2);
		},
		s: function() {
			return pad(date.getSeconds(), 2);
		},
		O: function() { // timezone offset 2
			var t = pad(Math.abs(date.getTimezoneOffset() / 60 * 100), 4);
			if (date.getTimezoneOffset() > 0) t = "-" + t;
			else t = "+" + t;
			return t;
		},
		P: function() { // timezone offset
			var O = f.O();
			return (O.substr(0, 3) + ":" + O.substr(3, 2));
		},
		c: function() { // UTC
			return f.Y() + "-" + f.m() + "-" + f.d() + "T" + f.H() + ":" + f.i() + ":" + f.s() + f.P();
		},
		U: function() { // Unix
			return Math.round(date.getTime() / 1000);
		}
	};

	return format.replace(/[\\]?([a-zA-Z])/g, function(t, s) {
		if (t === s && f[s]) {
			return f[s]();
		}
		return s;
	});
};

const tms = {
	60: " 秒前",
	1800: " 分前",
	3600: "半小时前",
	86400: " 小时前",
	604800: " 天前"
};
const factor = {
	60: 60,
	1800: 0,
	3600: 60,
	86400: 24,
	604800: 7
};

function prettyTime(timestamp) {
	if (timestamp === 0) return "-";

	var timeNow = Date.now();
	var diff = Math.abs((timeNow - timestamp) / 1000);
	if (diff < 1) return "现在";
	var val = diff;
	var flag = false;
	for (var i in tms) {
		if (diff < i) {
			var str = flag ? tms[i] : (Math.round(val) + tms[i]);
			if (timeNow < timestamp) str = str.replace("前", "后");
			return str;
		}
		if (factor[i] !== 0) {
			val /= factor[i];
			flag = false;
		} else {
			flag = true;
		}
	}
	return formatDate("Y-m-d H:i:s", timestamp);
}

export {formatDate, prettyTime, parseDate};
//endregion

const SCALE = ["B", "KB", "MB", "GB", "TB", "PB", "EB"];
function formatSize(size) {
	size = parseInt(size);
	if (isNaN(size)) return "NaN";
	var cap = 1n;
	var i = 0;
	for (;i < SCALE.length;) {
		var next = cap << 10n;
		if (next > size) break;

		cap = next;
		i++;
	}

	return (size / parseFloat(cap)).toFixed(i ? 2 : 0) + SCALE[i];
}

export {formatSize};