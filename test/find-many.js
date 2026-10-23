// 130,000 matches: capped at 10,000, no stack overflow, and a render with the find bar open still draws Mermaid
setTimeout(() => {
	findOpen();
	const i = document.querySelector('#find input');
	i.value = 'a';
	i.dispatchEvent(new Event('input'));
	render('a '.repeat(130000) + '\n\n```mermaid\ngraph TD; A-->B\n```\n', BASE);
	setTimeout(() => { document.title = 'count=' + document.querySelector('#find span').textContent + ' mermaid=' + !!document.querySelector('.mermaid svg'); }, 2500);
}, 1000);
