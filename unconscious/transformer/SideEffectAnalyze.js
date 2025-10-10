import t from '@babel/types';

export default function() {
	function checkSideEffect(p) {
		if (p.scope === this.programScope) {
			if (!isPureFunction(p)) {
				this.sideEffects.push(p.node);
				return;
			}

			// 盲猜以后会出bug
			p.skip();
		}
	}

	return {
		name: 'SideEffectAnalyze',
		pre() {
			this.sideEffects = [];
		},
		visitor: {
			CallExpression: checkSideEffect,
			OptionalCallExpression: checkSideEffect,
			NewExpression: checkSideEffect,

			Program: {
				enter(path) {
					this.programScope = path.scope;
				},
				exit(path) {
					const hasCleanup = findTopLevelFunction(path.scope, '$$cleanup');
					path.hub.file.metadata.allowHMR = hasCleanup || !this.sideEffects.length;

					if (hasCleanup) {
						const ast = t.ifStatement(t.identifier("import.meta.hot"), t.expressionStatement(
							t.callExpression(t.identifier("import.meta.hot.dispose"), [t.identifier("$$cleanup")])
						));
						path.pushContainer('body', ast);
					}
				}
			},
		},
	};
};

function findTopLevelFunction(scope, name) {
	const binding = scope.getOwnBinding(name);
	return binding && binding.path.isFunctionDeclaration();
}

function isPureFunction(path) {
	const comments = path.node.leadingComments;
	if (!comments) return false;

	for (const c of comments) {
		//if (c.type !== "CommentBlock") continue;

		const value = c.value.trim();
		if ("#__PURE__" === value) return true;
		if (/@sideeffects\s*false/i.test(value)) return true;
	}

	return false;
}

