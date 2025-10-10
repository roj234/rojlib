import t from '@babel/types';

export default function() {
	return {
		name: 'RemoveSpecialImport',
		pre: function() {
			this.processed = new Set;
		},
		visitor: {
			ImportDeclaration(path, state) {
				const opts = state.opts.removeSpecialImport || {};

				const sourceValue = path.node.source?.value;
				if (!Object.hasOwn(opts, sourceValue)) return;

				const toRemoveNames = new Set(opts[sourceValue]);

				const kept = [];
				for (const spec of path.node.specifiers) {
					if (t.isImportSpecifier(spec) && toRemoveNames.has(spec.imported.name)) {
						//path.scope.removeBinding(spec.local.name);
					} else {
						kept.push(spec);
					}
				}

				this.processed.add(sourceValue);
				path.node.specifiers = kept;
				if (kept.length === 0 && this.processed.has(sourceValue)) {
					path.remove();
				}
			}
		},
	};
};
