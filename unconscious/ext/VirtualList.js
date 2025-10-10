import {debugSymbol, PASSIVE_EVENT} from "../runtime_shared.js";

/**
 * 项目渲染出元素的索引，因为实际有它所以{@link ITEM_KEY}才可以重复
 * @type {symbol}
 */
const INDEX = debugSymbol("VL.Index");
/**
 * 该项目的键，在数组中可以重复，如果和上次结果不同就重新渲染项目
 * @type {symbol}
 */
const ITEM_KEY = debugSymbol("VL.Key");
/**
 * 该项目的高度
 * @type {symbol}
 */
const ITEM_HEIGHT = debugSymbol("VL.Height");

/**
 * @typedef VirtualList<T>
 * @template {Object} T 数据类型
 * @property {HTMLDivElement} dom - 内部垫高容器
 * @property {number} itemHeight - 每项的预期高度 (实际高度可以不同)
 * @property {number} height - 外部容器高度
 * @property {Array<T>} items - 数据源
 * @property {function(data: T, index: number, recycle: Array<HTMLElement>): HTMLElement} renderer - 渲染函数
 */
class VirtualList {
	_start = 0;
	_end = 0;

	_startHeight = 0;
	_totalHeight = 0;

	_updatePending = false;

	_recycle = [];

	/**
	 * 虚拟列表
	 * @template {Object} T 数据类型
	 * @param {Object} config - 配置选项
	 * @param {HTMLElement} config.element - 父元素
	 * @param {Array<T>} config.data - 数据源
	 * @param {number} config.itemHeight - 每项的高度
	 * @param {function(item: T): any} [config.keyFunc=item => item] - 生成唯一索引的函数
	 * @param {function(data: T, index: number, recycle: Array<HTMLElement>): HTMLElement} config.renderer - 渲染函数
	 * @param {number} [config.height=element.offsetHeight] - 总高度
	 * @param {boolean} [config.visible=auto] - 当前是否可见
	 * @param {boolean} [config.fixed=false] - 元素高度是否固定
	 * @returns {VirtualList<T>}
	 */
	constructor(config) {
		const wrapper = this._wrapper = config.element;
		const container = this.dom = <div className="_vl"></div>;
		wrapper.appendChild(container);

		if (!(this.itemHeight = config.itemHeight)) throw "默认高度不能为0";
		this.renderer = config.renderer;
		this.height = config.height ?? wrapper.offsetHeight;

		this._visible = config.visible ?? window.getComputedStyle(wrapper).display !== "none";
		(this._observer = new IntersectionObserver(entries => {
			if((this._visible = entries[entries.length-1].isIntersecting) && this._updatePending) {
				this._updatePending = false;
				this.repaint();
			}
		})).observe(wrapper);

		this.render = (config.fixed ? this._updateFixed : this._update).bind(this);
		this.keyFunc = config.keyFunc ?? (item => item[ITEM_KEY] ?? item);

		// 头疼医头，脚疼医脚，解决丢失滚动目标的问题；完美的解决方案是对列表项使用绝对定位，使得浏览器基于父元素定位，但是这会引入每次滚动更新offset的开销
		wrapper.addEventListener('wheel', this._handleMouseWheel, PASSIVE_EVENT);
		wrapper.addEventListener('mousedown', this._handleMiddleClick);
		wrapper.addEventListener('scroll', this.render);

		if ((this.items = config.data)) {
			if (this._visible && this.height) {
				this.render();
			} else {
				this._updatePending = true;
			}
		}
	}

	// 鼠标事件处理
	_handleMouseWheel = (e) => {
		let el = e.target;
		while(el) {
			if (el[INDEX]) {
				this._hoveringElement = el;
				break;
			}
			el = el.parentElement;
		}
	}

	_handleMiddleClick = (e) => {
		if (e.button === 1) { // 鼠标中键
			this._handleMouseWheel(e);
		}
	}

	/**
	 * 子元素或列表高度修改后重新计算需要渲染的项目
	 * 传入true仅更新列表高度
	 * @param {boolean=false} itemHeightUnchanged 子元素高度未变化
	 */
	repaint(itemHeightUnchanged) {
		if (!this._visible) {this._updatePending = true;return;}
		if (!itemHeightUnchanged) {
			this._start = 0;
			this._startHeight = 0;
			this._totalHeight = 0;
		}

		this.dom.style.height = "99999px";
		this.height = this._wrapper.offsetHeight;
		this.render();
	}

	/**
	 * 更新数组某项并(立即)更新虚拟列表，如果这项正在渲染
	 * @param {number} i 数组索引
	 * @param {T} item 新的值
	 * @param {boolean=false} heightUnchanged
	 */
	setItem(i, item, heightUnchanged) {
		if (!item) item = this.items[i];
		else this.items[i] = item;

		if (!heightUnchanged) {
			delete item[ITEM_HEIGHT];
			if (i < this._start) {
				this._start = 0;
				this._startHeight = 0;
			}
			this._totalHeight = 0;
		}
		if (i < this._start || i >= this._end) return;

		this.getValue(i)[INDEX] = -1;
		if (!this._visible) {this._updatePending = true;return;}
		this.render();
	}

	/**
	 * 更新数组并(立即)更新虚拟列表
	 * @param {T[]} items 新的值
	 */
	setItems(items) {
		this.items = items;
		this.repaint();
	}

	/**
	 * 获取DOM低N项
	 * @param i
	 * @returns {Element}
	 */
	getValue(i) {return this.dom.children[i - this._start];}

	/**
	 * 查找当前可见的项目
	 * @param {Object} item
	 * @returns {number}
	 */
	findIndex(item) {
		for (let i = this._start; i < this._end; i++) {
			if (this.items[i] === item) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * 查找项目
	 * @param {function(T): boolean} predicate
	 * @returns {number}
	 */
	indexMatch(predicate) {
		for (let i = this._start; i < this._end; i++) {
			if (predicate(this.items[i])) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * 回收利用现有元素，有必要吗？
	 * @param {string} match pattern
	 * @returns {HTMLElement|null}
	 * @deprecated 都用框架了还关心这个？
	 */
	findElement(match) {
		const r = this._recycle;
		for (let i = r.length-1; i >= 0; i--) {
			if (r[i].matches(match)) return r.splice(i, 1)[0];
		}
		return null;
	}

	/**
	 * 清理资源
	 */
	destroy() {
		this._observer.disconnect();
		this._wrapper.removeEventListener('scroll', this.render);
		this._wrapper.removeEventListener('wheel', this._handleMouseWheel);
		this._wrapper.removeEventListener('mousedown', this._handleMiddleClick);
	}

	_updateFixed() {
		const items = this.items;
		const itemHeight = this.itemHeight;

		const startIndex = Math.floor(this._wrapper.scrollTop / itemHeight);
		const endIndex = Math.min(items.length, startIndex + Math.ceil(this.height / itemHeight) + 1);

		const container = this.dom;
		container.style = `padding-top: ${startIndex * itemHeight}px; padding-bottom: ${(items.length - endIndex) * itemHeight}px`;

		this._renderList(container, startIndex, endIndex, items);
	}

	_update() {
		if (!this._visible) {console.warn("渲染不可见的列表");return}
		const items = this.items;

		let i = this._start;
		let height = this._startHeight;

		//进入视口
		const viewStart = this._wrapper.scrollTop;
		if (height < viewStart) {
			while(i < items.length) {
				const h = items[i][ITEM_HEIGHT] ?? this.itemHeight;
				if ((height + h) >= viewStart) break;
				i++;
				height += h;
			}
		} else if (height !== viewStart) {
			while (i > 0) {
				const h = items[--i][ITEM_HEIGHT] ?? this.itemHeight;
				if ((height -= h) < viewStart) break;
			}
		}
		const startIndex = this._start = i;
		const startHeight = this._startHeight = height;

		//离开视口
		const viewEnd = viewStart + this.height;
		while(i < items.length) {
			const h = items[i++][ITEM_HEIGHT] ?? this.itemHeight;
			if ((height += h) > viewEnd) break;
		}

		//总高度
		let totalHeight = this._totalHeight;
		if (!totalHeight) {
			totalHeight = height;
			let j = i;
			while(j < items.length) totalHeight += items[j++][ITEM_HEIGHT] ?? this.itemHeight;
			this._totalHeight = totalHeight;
		}

		// 未渲染的元素的高度由padding-top和padding-bottom代替，保证滚动条位置正确
		// 这里如果把设置padding的操作放在渲染元素之后，部分浏览器滚动到最后一个元素时会有问题
		const container = this.dom;
		container.style = `padding-top: ${startHeight}px; padding-bottom: ${totalHeight-height}px`;
		this._renderList(container, startIndex, Math.min(i+1, items.length)/*多渲染一个，防止滚动太快跟不上*/, items);

		i = startIndex;
		// 缓存已知的元素高度，减少滚动条不跟手的问题
		for(const el of container.children) {
			if (!el[ITEM_KEY]) continue;

			const h = el.offsetHeight;
			let item;
			if (h !== this.itemHeight && h !== (item=items[i])[ITEM_HEIGHT]) {
				item[ITEM_HEIGHT] = h;
				this._totalHeight += (h - this.itemHeight);
			}
			i++;
		}
	}

	_renderList(container, startIndex, endIndex, items) {
		const recycle = this._recycle;

		const reuse = {};
		// 遍历现有元素，回收不可见或key变化的元素
		for (const item of Array.from(container.children)) {
			const i = item[INDEX];
			if (i < startIndex || i >= endIndex || item[ITEM_KEY] !== this.keyFunc(items[i], i)) {
				if (item === this._hoveringElement) {
					item.style.visibility = 'hidden';
					item.style.position = 'fixed';
					delete item[ITEM_KEY];
				} else {
					recycle.push(item);
					item.remove();
				}
			} else {
				reuse[i] = item;
			}
		}

		// 插入新元素
		let anchorNode = null;
		for (let i = startIndex; i < endIndex; i++) {
			if (reuse[i]) {
				anchorNode = reuse[i];
				continue; // 未变化则跳过
			}

			// 创建或复用元素
			const item = this.renderer(items[i], i, recycle);
			item[INDEX] = i;
			item[ITEM_KEY] = this.keyFunc(items[i], i);

			if (!anchorNode) {
				// 情况1：向上滚动
				container.prepend(item);
			} else {
				// 情况2：向下滚动
				anchorNode.after(item);
			}
			anchorNode = item; // 更新锚点为新元素
		}

		recycle.length = 0;
		this._start = startIndex;
		this._end = endIndex;
	}
}

export {VirtualList, INDEX, ITEM_KEY, ITEM_HEIGHT};