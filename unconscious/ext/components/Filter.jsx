import {preserveState, isReactive} from "unconscious";
import './filter.css';

/**
 * @typedef {Object} Config
 * @property {string} id
 * @property {string} name
 * @property {string} type
 */

/**
 * @param {Config[]} props.config 配置列表
 * @param {Object[]} props.choices={} 默认选项值
 * @param {function(string, any, Object[]): void|string} props.onChange=null 回调
 * @return JSX.Element
 */
export default function Filter(props) {
	const initialState = isReactive(props.choices) ? props.choices : Object.assign({}, props.choices);
	props.config.forEach(item => {
		switch (item.type) {
			case 'input':
			case 'textbox': initialState[item.id] = initialState[item.id] ?? ''; break;
			case 'multiple': if (item.id) initialState[item.id] = initialState[item.id] ?? []; break;
			case 'range': {
				const min = item.min, max = item.max;
				const clamp = (v) => Math.max(min, Math.min(max, v));

				const init = initialState[item.id];
				initialState[item.id] = init
					? [
						clamp(Number(init[0] ?? min)),
						clamp(Number(init[1] ?? max))
					].sort((a, b) => a - b)
					: [min, max];
			}
			break;
		}
	});

	const state = preserveState(initialState);
	const emit = (name, newValue) => {
		let result;
		try {
			result = props.onChange?.(name, newValue, state);
		} catch (e) {
			result = e;
		}
		return result;
	}

	const root = <div className="filter"></div>;

	// 构建每一项
	props.config.forEach(item => {
		let row, warning;

		function showWarning(text) {
			if (warning) warning.innerText = text;
			else {
				warning = <div className='input-warning' aria-live='polite'>{text}</div>;
				row.append(warning);
			}
		}

		switch (item.type) {
			case 'element': {
				row = item.element;
			}
			break;
			case 'radio': {
				const required = !!item.required;
				const handler = e => {
					const btn = e.target;
					let value = item.choices[btn.title];

					value = state[item.id] === value ? null : value;
					if (value == null && required) return;

					let err = emit(item.id, value);
					if (err) {
						showWarning(err);
						return;
					}
					warning?.remove();

					const cur = choiceScroll.querySelector('.active');
					if (cur) {
						cur.classList.remove('active');
						cur.setAttribute('aria-checked', 'false');
					}

					if (value != null) {
						// 更新同组样式
						btn.classList.add('active');
						btn.setAttribute('aria-checked', 'true');
					}

					state[item.id] = value;
				};

				const choiceScroll = <div className='choice-scroll' role='radiogroup' aria-label={item.name} onClick.children('button')={handler}></div>;

				Object.entries(item.choices).forEach(([label, value]) => {
					const active = state[item.id] === value;
					choiceScroll.appendChild(<button className={active ? 'chip active' : 'chip'} type='button'
													 role='radio' aria-checked={active}
													 title={label}>{label}</button>);
				});

				row = choiceScroll;
			}
			break;
			case 'multiple': {
				const handler = e => {
					const btn = e.target;
					const value = item.choices[btn.title];

					let selectedNow;
					if (item.id) {
						const arr = [...state[item.id]];

						const idx = arr.indexOf(value);

						selectedNow = idx < 0;
						if (selectedNow) arr.push(value);
						else arr.splice(idx, 1);

						let err = emit(item.id, arr);
						if (err) {
							showWarning(err);
							return;
						}
						warning?.remove();

						state[item.id] = arr;
					} else {
						let err = emit(value, !state[value]);
						if (err) {
							showWarning(err);
							return;
						}
						warning?.remove();

						selectedNow = state[value] ^= true;
					}

					btn.classList.toggle('active', selectedNow);
					btn.setAttribute('aria-checked', selectedNow ? 'true' : 'false');
				};

				const choiceScroll = <div className='choice-scroll' role='group' aria-label={item.name} onClick.children('button')={handler}></div>;

				Object.entries(item.choices).forEach(([label, value]) => {
					const selected = item.id ? state[item.id].includes(value) : !!state[value];
					choiceScroll.appendChild(<button className={selected ? 'chip active' : 'chip'} type='button'
													 role='checkbox' aria-checked={selected}
													 title={label}>{label}</button>);
				});

				row = choiceScroll;
			}
			break;
			case 'secret':
			case 'input': {
				const pattern =
					item.pattern instanceof RegExp
						? item.pattern
						: typeof item.pattern === 'string'
							? new RegExp(item.pattern)
							: null;

				const handler = function (e) {
					const val = this.value.trim();
					let invalid = pattern && val && !pattern.test(val);
					let warnMessage = item.warning || '输入不符合要求';
					if (invalid) {
						if (e.type === 'change') {
							this.value = state[item.id];
							invalid = false;
						}
					} else {
						warnMessage = emit(item.id, val);
						if (warnMessage) invalid = true;
						else state[item.id] = val;
					}
					this.classList.toggle('invalid', invalid);

					if (invalid) showWarning(warnMessage);
					else warning?.remove();
				};

				if (item.type === 'secret') {
					row = <div className='input-warp'>
						<input className='text-input' type='password' placeholder={item.placeholder || ''} value={state[item.id]}
							   onblur={function(){this.type="password"}} onfocus={function(){this.type="text"}}
							   onInput={handler} onChange={handler}/>
					</div>;
				} else {
					row = <div className='input-warp'>
						<input className='text-input' type='text' placeholder={item.placeholder || ''} value={state[item.id]}
							   onInput={handler} onChange={handler}/>
					</div>;
				}
			}
			break;
			case 'textbox': {
				const pattern =
					item.pattern instanceof RegExp
						? item.pattern
						: typeof item.pattern === 'string'
							? new RegExp(item.pattern)
							: null;

				const handler = function (e) {
					const val = this.value.trim();
					let invalid = pattern && val && !pattern.test(val);
					let warnMessage = item.warning || '输入不符合要求';
					if (invalid) {
						if (e.type === 'change') {
							this.value = state[item.id];
							invalid = false;
						}
					} else {
						warnMessage = emit(item.id, val);
						if (warnMessage) invalid = true;
						else state[item.id] = val;
					}
					this.classList.toggle('invalid', invalid);

					if (invalid) showWarning(warnMessage);
					else warning?.remove();
				};

				row = <div className='input-warp'>
					<textarea className='text-input' type='text' placeholder={item.placeholder || ''} onInput={handler} onChange={handler}>{state[item.id]}</textarea>
				</div>;
			}
				break;
			case 'number': {
				const min = item.min, max = item.max, step = item.step || 1;

				const clamp = (v) => Math.max(min, Math.min(max, v));
				const pct = (v) => ((v - min) / (max - min)) * 100;

				const initialValue = state[item.id];

				const trackFill = <div className='range-track-fill'></div>;

				const slider = <input type='range' min={min} max={max} step={step} value={initialValue}/>;
				const input = <input type='number' min={min} max={max} step={step} value={initialValue}/>;

				const updateUI = () => {
					const myMin = slider.valueAsNumber;
					input.valueAsNumber = myMin;
					trackFill.style.width = `${pct(myMin)}%`;
				};
				const syncState = () => {
					const newValue = slider.valueAsNumber;
					emit(item.id, newValue);
					state[item.id] = newValue;
				};

				const limitMax = e => {
					slider.valueAsNumber = clamp(Number(e.target.value));
					updateUI();
				};

				slider.addEventListener('input', limitMax);
				input.addEventListener('input', limitMax);

				slider.addEventListener('change', syncState);
				input.addEventListener('change', syncState);

				row = <div className='range-wrap'>
					<div className='range-slider'>
						<div className='range-track-bg'></div>
						{trackFill}
						{slider}
					</div>
					<div className='range-values'>
						<span>值</span>{input}
					</div>
				</div>;

				// 初始绘制
				updateUI();
			}
			break;
			case 'range': {
				const min = item.min, max = item.max, step = item.step || 1;

				const clamp = (v) => Math.max(min, Math.min(max, v));
				const pct = (v) => ((v - min) / (max - min)) * 100;

				const initialValue = state[item.id];

				const trackFill = <div className='range-track-fill'></div>;

				const r1 = <input type='range' min={min} max={max} step={step} value={initialValue[0]}/>;
				const r2 = <input type='range' min={min} max={max} step={step} value={initialValue[1]}/>;

				const nMin = <input type='number' min={min} max={max} value={initialValue[0]}/>;
				const nMax = <input type='number' min={min} max={max} value={initialValue[1]}/>;

				const updateUI = () => {
					const myMin = r1.valueAsNumber;
					const myMax = r2.valueAsNumber;

					nMin.valueAsNumber = myMin;
					nMax.valueAsNumber = myMax;

					const left = pct(myMin), right = pct(myMax);
					trackFill.style.left = `${left}%`;
					trackFill.style.width = `${right - left}%`;
				};
				const syncState = () => {
					const newValue = [r1.valueAsNumber, r2.valueAsNumber];
					emit(item.id, newValue);
					state[item.id] = newValue;
				};

				const limitMax = e => {
					let v = clamp(Number(e.target.value));
					r1.valueAsNumber = Math.min(v, r2.valueAsNumber);
					updateUI();
				};
				const limitMin = e => {
					let v = clamp(Number(e.target.value));
					r2.valueAsNumber = Math.max(v, r1.valueAsNumber);
					updateUI();
				};

				r1.addEventListener('input', limitMax);
				r2.addEventListener('input', limitMin);
				nMin.addEventListener('input', limitMax);
				nMax.addEventListener('input', limitMin);

				r1.addEventListener('change', syncState);
				r2.addEventListener('change', syncState);
				nMin.addEventListener('change', syncState);
				nMax.addEventListener('change', syncState);

				row = <div className='range-wrap'>
					<div className='range-slider'>
						<div className='range-track-bg'></div>
						{trackFill}
						{r1}
						{r2}
					</div>
					<div className='range-values'>
						<span>最小</span>{nMin}
						<span>最大</span>{nMax}
					</div>
				</div>;

				// 初始绘制
				updateUI();
			}
			break;
		}

		root.appendChild(<div className="filter-row" data-id={item.id}>
			<div className="filter-label">{item.name}</div>
			{row}
		</div>);
	});

	return root;
};
