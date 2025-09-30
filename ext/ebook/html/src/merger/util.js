
// 初始化配置
const HeightConfig = {
	containerHeight: 600,  // 可通过此参数调节容器高度
	lineHeight: 20,        // 固定行高
};


function scheduler(callback) {
	let pending = false;
	return () => {
		if (pending) return;
		pending = true;
		requestAnimationFrame(() => {
			pending = false;
			callback();
		});
	};
}

function escapeHTML(str) {
	return str.replace(/[&<>]/gm, (str) => {
		if (str === "&") return "&amp;";
		if (str === "<") return "&lt;";
		if (str === ">") return "&gt;";
	});
}

export {HeightConfig, scheduler, escapeHTML}