const http = require('http');
const fs = require('fs');
const path = require('path');
const puppeteer = require('puppeteer-core');
const chromium = require('@sparticuz/chromium').default;

const DIST = process.env.DIST || `${__dirname}/build/dist/wasmJs/productionExecutable`;
const VARIANT = process.argv[2] || 'close';
const RUN_MS = parseInt(process.argv[3] || '15000', 10);

const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript', '.wasm': 'application/wasm', '.map': 'application/json' };

const server = http.createServer((req, res) => {
  const file = path.join(DIST, req.url.split('?')[0].split('#')[0] === '/' ? 'index.html' : req.url.split('?')[0]);
  fs.readFile(file, (err, data) => {
    if (err) { res.writeHead(404); res.end('nope'); return; }
    res.writeHead(200, { 'Content-Type': MIME[path.extname(file)] || 'application/octet-stream' });
    res.end(data);
  });
});

(async () => {
  await new Promise(r => server.listen(8137, r));
  const args = chromium.args.filter(a => !a.startsWith('--single-process'));
  args.push('--js-flags=--expose-gc');
  const browser = await puppeteer.launch({
    args,
    executablePath: await chromium.executablePath(),
    headless: 'shell' === 'never' ? false : chromium.headless,
  });
  const page = await browser.newPage();
  const messages = [];
  page.on('console', msg => {
    const text = msg.text();
    messages.push(`[console.${msg.type()}] ${text}`);
    console.log(`[console.${msg.type()}] ${text}`);
  });
  page.on('pageerror', e => console.log('[pageerror]', e.message));

  await page.goto(`http://127.0.0.1:8137/index.html#${VARIANT}`, { waitUntil: 'load' });
  await new Promise(r => setTimeout(r, RUN_MS));

  await page.screenshot({ path: `/tmp/repro-${VARIANT}.png` });
  await browser.close();
  server.close();

  const hits = messages.filter(m => /no valid shader program|INVALID_OPERATION/i.test(m));
  console.log('\n=== RESULT ===');
  console.log(`variant=${VARIANT} consoleMessages=${messages.length} glErrors=${hits.length}`);
  console.log(hits.length > 0 ? 'REPRODUCED: ' + hits[0] : 'NOT reproduced');
  process.exit(0);
})().catch(e => { console.error('FATAL', e); process.exit(1); });
