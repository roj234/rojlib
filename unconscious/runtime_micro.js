import {
	ID_CLASSLIST,
	ID_DANGEROUSLY_SET_INNERHTML,
	ID_EVENTHANDLER,
	ID_NAMESPACE,
	ID_STYLELIST,
	isPureObject
} from "./constant.js";

/**
 * 创建 DOM 元素
 * @param {string} type - 元素标签类型
 * @param {Object} [props] - 元素属性对象（可包含特殊属性 ns 用于命名空间）
 * @param {...*} children - 子元素列表（支持字符串、节点、响应式变量）
 * @returns {HTMLElement} 创建的 DOM 元素
 */
export function createElement(type, props, ...children) {
	// 创建元素
	const element = document.createElementNS(props?.[ID_NAMESPACE] || "http://www.w3.org/1999/xhtml", type);

	// 设置属性
	if (props) {
		for (const [key, value] of Object.entries(props)) {
			if (key === ID_NAMESPACE) continue;
			setAttribute(element, key, value);
		}
	}

	appendChildren(element, children);
	return element;
}

/**
 * 创建文档片段
 * @param {...*} children - 子元素列表（支持字符串、节点、响应式变量）
 * @returns {DocumentFragment} 文档片段对象
 */
export function createFragment(...children) {
	const fragment = document.createDocumentFragment();
	appendChildren(fragment, children);
	return fragment;
}

/**
 * 往父元素中插入子元素
 * @param {Element} parent - 父元素
 * @param {Array} children - 子元素列表（支持字符串、节点、响应式变量）
 */
export function appendChildren(parent, children) {
	for (const child of children) {
		if (child == null) continue;
		parent.appendChild(createChildNode(child));
	}
}

/**
 * 转换为节点
 * @param {any} child
 * @returns {Node}
 */
function createChildNode(child) {
	return child instanceof Node ? child : new Text(child);
}

/**
 * 设置属性
 * @param {HTMLElement} element
 * @param {string} key
 * @param {string|Reactive<string>} value
 */
function setAttribute(element, key, value) {
	switch (key[0]) {
		case "_":
			element[key] = value;
		return;
		case ID_DANGEROUSLY_SET_INNERHTML:
			element.innerHTML = value;
		return;
		case ID_STYLELIST:
			element.style[key.substring(ID_STYLELIST.length)] = value;
		return;
		case ID_CLASSLIST:
			element.classList.toggle(key.substring(ID_CLASSLIST.length), value);
		return;
		case ID_EVENTHANDLER:
			let attrib; // once, capture, passive等
			if (Array.isArray(value))
				[value, attrib] = value;

			element.addEventListener(key.substring(ID_EVENTHANDLER.length), value, attrib);
		return;
		case "s":
			if (key === "style" && isPureObject(value)) {
				Object.assign(element.style, value);
				return;
			}
		break;
	}
	element.setAttribute(key, value);
}

export {_left, _right, _middle, _button, _children, _prevent, _stop, _delegate, debugSymbol} from './runtime_shared.js';
