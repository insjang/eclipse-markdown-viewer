// Select All + Copy: formulas as TeX, diagrams as their source, no button labels; the same text as Copy HTML's plain text
setTimeout(() => { try {
	getSelection().selectAllChildren(document.getElementById('c'));
	const t = plainText(false), w = htmlFragment()[1];
	document.title = encodeURIComponent(JSON.stringify({ sel: t, same: t === w }));
} catch (e) { document.title = 'ERR ' + e; } }, 3000);
