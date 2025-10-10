// 组件化开发示例
// 这个示例展示了框架中几乎所有功能，配合注释和live demo，看一遍基本上就掌握得差不多了
// 需要注意的只是本框架的少数实现细节
// 毕竟这个框架可以理解为 “Svelte煮的Vue风味的React汤”
// 此外，每个框架函数我都有写JSDoc

import {
	$animate,
	$asyncComponent,
	$computed,
	$forElse,
	$state,
	$store, $update,
	appendChildren,
	preserveState
} from "unconscious";

const title = $state("计数器 ");
setInterval(() => {
	title.value = title.value === "计数器 " ? "Counter " : "计数器 ";
}, 100);

// 这是一个异步组件，不过同样支持热重载，现在就试试吧
// 在F12中对比源代码和转换后的代码，可以让你理解$asyncWithCleanup语法糖和$disposable函数是如何工作的
const AsyncCounter = $asyncComponent(
	() => new Promise((resolve, failure) => setTimeout(() => {
		if (Math.random() < 0.5) failure("哎呀。网络错误了！");
		import('./Counter.jsx').then(n => resolve(n.Counter));
	}, Math.random() * 1000)),
	() => <div>加载中喵</div>,
	err => <div>加载失败喵: {err}</div>
);

const items = $state([1, 2, 3]);

// 我们始终建议您使用appendChildren添加响应式元素到真实DOM节点上，尽管添加单个元素时可能显得多余，否则原生append可能在边界情况出现问题，例如整体消失。
appendChildren(document.body, [
	<AsyncCounter title={title} value={42} />,
// 两次调用，理所当然会创建两个不同的组件
	<AsyncCounter title={title} value={42} />
]);

// 同样可以用Fragment元素，它和手动定义数组是等效的
// forElse和foreach差不多，除了在没有元素时显示'空元素'，'空元素'可以是响应式的
// 这解决了我老是记不住v-if和v-for优先级的问题 (虽然vue有template)
appendChildren(document.body, <>
	<div onClick={() => {
		items.push(items.length+1);
		if (items.length === 10) {
			items.length = 0;
		}
	}}>
		{$forElse(items, item => <button>我是{item}号按钮</button>, <span>怎么一个按钮都没有</span>)}
	</div>
</>);

// Unconscious的组件支持子元素，不过这需要显式声明
//
// 如果你将children从参数列表中删除并提供了子元素，那么将会在开发时抛出异常
// 你可以尝试一下这么做
// 同样的，如果你删除props参数并提供了props，也会抛出异常
// 值得注意的是，限制不是通过参数名称，而是声明的数量；第一个参数是props，第二个参数是children
function ChildrenWrapper(props, children) {
	return children;
}
appendChildren(document.body, <>
	<ChildrenWrapper title={title}>
		<h1>喵喵喵</h1>
		<h2>汪汪汪</h2>
		<h3>嗷嗷嗷</h3>
	</ChildrenWrapper>
</>);

/**
 * 异步加载HTML.
 *
 * 另外一件事是，所有名称第一个字符大写，并且名称长度超过1的函数，都被假定为组件函数，所以它们必须返回值，并且会注入热重载代码
 *
 * @param props
 * @property {string} props.url
 * @return {JSX.Element}
 * @constructor
 */
function AsyncFetch(props) {
	// 创建一个响应式变量
	// 在这里，我使用preserveState包装了一层，这是给热重载的提示：这个参数需要在模块热重载后传递
	// 之所以还需要套$state，是因为非响应式变量也可以在模块热重载时传递
	// 除此之外，传递规则也和其它工具不同，其基于固定key，为表达式的字符串加序号，例如下面的是
	// "$state([])_0"
	// 当然，你也可以通过第二个参数手动指定id
	// 该函数不会影响构件大小
	const html = preserveState($state([]));

	// 很不幸，因为main.js是根(直接被html引用)，所以它不支持热重载
	// 你可以尝试在前面的Counter.jsx中测试preserveState

	function fetchMore() {
		// 可以直接使用html.pop()，这也会移除元素，但是需要注意两点
		// 1. 所有响应式移除的元素，都会删除其上所有响应式监听器(直接或间接的$watch)
		// 2. 在当前上下文中，.remove不会过于不直观，性能也更好
		this.remove();
		const loadMoreBtn = html.value.pop();

		setTimeout(() => {

			if (Math.random() < 0.5) {
				html.value.length = 0;
				html.value.push("加载失败, 全部木大！");
			} else {
				html.value.push("一些数据"+Math.random());
			}

			html.value.push(loadMoreBtn);
			// 直接使用html.push也能触发更新，二者等效
			// Unconscious基于名称匹配Array、Map、Set的所有mutator方法
			// 但是对于自定义对象的mutator方法，你很可能需要手动更新
			$update(html);
		}, 100);
	}

	function fetchUrl() {
		fetch(props.url)
			.then(response => {
				if (!response.ok) {
					throw new Error('Network response was not ok');
				}
				return response.text();
			})
			.then(text => {
				setTimeout(() => {
					// 记住，在本框架中，JSX Fragment就是数组，JSX Element就是原生DOM元素
					html.value = <>{text}<button onClick={fetchMore}>加载更多</button></>;
				}, 1000);
			})
			.catch(e => {
				html.value = "加载失败！"+e.message;
			});
	}

	// 这里的 ? : 表达式是一个语法糖，会自动将内容转换为计算属性 ($computed)
	// 如果你确实不需要计算，你可以使用{AS_IS(... ? ... : ...)}包装
	// 该函数不会影响构件大小
	return <div>
		{html.value.length ? html.value :
			<button onClick={() => {
				fetchUrl();
				html.push("加载中");
			}}>加载内容</button>
		}
	</div>;
}
appendChildren(document.body, [<AsyncFetch url={"text.txt"} />]);

/**
 * 通过"行为"实现的简单表单-值的响应式绑定
 * @param {HTMLInputElement|HTMLSelectElement|HTMLTextAreaElement} formElement
 * @param {Reactive<string>} variable
 */
function bind(formElement, variable) {
	formElement.addEventListener("input", e => {
		variable.value = formElement.value;
	});
	// 因为queueMicrotask在空闲时统一处理事件监听器，所以这不会和上面的input发生递归
	// 除此以外，开发环境本身也有递归检查
	$computed(() => {
		formElement.value = variable.value;
	});

	return formElement;
}

// 把最喜欢的数字存入localStorage，它包含自动同步机制，你可以尝试打开两个example，然后发现对其中一个的修改会影响到第二个
const currentValue = $store("my_favorite_number", "42", {
	persist: true
});

// 行为: style:XXX是内置行为，响应式更新style中的某项，而不是更新整个style字符串
// 你可以使用 @styles={{color: $computed(() => currentValue.value === "114514" ? "red" : "")}} 来一次更新多个属性
// @bind是上面这个函数，第一个参数是元素自身，第二个是参数，相当于包装器，按定义顺序一层层包装
appendChildren(document.body, <>
	输入你<span style:color={$computed(() => currentValue.value === "114514" ? "red" : "")}>最喜欢</span>的数字：{currentValue} <br/>
	<input @bind={currentValue} />
	<button onClick={() => currentValue.value = "114514"}>仙贝最喜欢的数字</button>
</>);




const buttonIsVisible = $state(true);
let targetButtonVisible;
// 对于“隐藏并消失”模式来说，复用元素并不是必须的
// 然而由于本框架的事件监听会延迟派发，如果你设置buttonIsVisible后，从响应式变量读取值不是null，需要等待事件派发结束 (见toggleButton2)
const animationTest = <div className="loading">我要消失了</div>;
appendChildren(document.body, <>
	<br />
	隐藏并消失 with 元素复用 (might inside component)
	{buttonIsVisible.value ? animationTest : null}
	<button onClick={toggleButton}>{buttonIsVisible.value ? '隐藏' : "显示"}</button>
</>);

function toggleButton() {
	buttonIsVisible.value = true;

	const targetVisible = targetButtonVisible;
	const targetOpacity = targetButtonVisible ? 1 : 0;
	targetButtonVisible ^= true;

	// 自动处理动画打断
	$animate(animationTest, {
		duration: 1000,
		easing: "linear",
		from: {
			opacity: 1-targetOpacity,
		},
		to: {
			opacity: targetOpacity
		}
	}).then(() => {
		// 在动画正常结束时执行
		buttonIsVisible.value = targetVisible;
		targetButtonVisible = !targetVisible;
	});
}

const buttonIsVisible2 = $state(true);
let targetButtonVisible2;
const animationTest2 = $computed(() => {
	return buttonIsVisible2.value ? <div className="loading">我要消失了</div> : null;
});
appendChildren(document.body, <>
	<br />
	隐藏并消失 without 元素复用
	{animationTest2}
	<button onClick={toggleButton2}>{buttonIsVisible2.value ? '隐藏' : "显示"}</button>
</>);

function toggleButton2() {
	buttonIsVisible2.value = true;

	const targetVisible = targetButtonVisible2;
	const targetOpacity = targetButtonVisible2 ? 1 : 0;
	targetButtonVisible2 ^= true;

	// 然而，你必须延迟到事件派发结束后，才能正常调用
	queueMicrotask(() => {
		$animate(animationTest2, {
			duration: 1000,
			easing: "linear",
			from: {
				opacity: 1-targetOpacity,
			},
			to: {
				opacity: targetOpacity
			}
		}).then(() => {
			buttonIsVisible2.value = targetVisible;
			targetButtonVisible2 = !targetVisible;
		});
	});
}

// 其它函数
// isReactive 判断参数是否响应式
// unconscious 如果参数是响应式的，递归返回它的原始值，否则返回参数自身

// assertReactive 要求某个值必须是响应式的，否则抛出异常 只在开发时有效
// checkProps 开发时验证props的类型

// 这些用的比较少，主要是框架内部使用
// $dispose(element, keepListener) 递归删除元素和绑定的响应式监听器
// $watchOn(reactive, listener, element) 注册自动删除的监听器


