// Castly relay: a meeting point only. It hands the TV browser the phone's home-network address, then steps aside.
// Video and sound never pass through here; they go phone -> TV directly over the home Wi-Fi.
const http = require('http'), fs = require('fs'), path = require('path'), crypto = require('crypto');
const { WebSocketServer } = require('ws'); const QR = require('qrcode');
const PORT = process.env.PORT || 3000;
const sessions = new Map(), hits = new Map();                       // code -> TV socket ; ip -> {n, t}
const LAN = /^http:\/\/(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)[\d.]+:(8080|8082)\/\?code=\d{6}$/;   // phones on home networks only
const ip = r => (r.headers['x-forwarded-for'] || r.socket.remoteAddress || '').split(',')[0].trim();
function limited(r) { const k = ip(r), n = Date.now(), h = hits.get(k);                         // 10 tries a minute per address
  if (!h || n - h.t > 60000) { hits.set(k, { n: 1, t: n }); return false } return ++h.n > 10 }
setInterval(() => { const n = Date.now(); for (const [k, h] of hits) if (n - h.t > 60000) hits.delete(k) }, 60000);
const page = fs.readFileSync(path.join(__dirname, 'public', 'index.html'));
const getPage = fs.readFileSync(path.join(__dirname, 'public', 'get.html'));          // /get = download page for the app

const server = http.createServer(async (req, res) => {
  const u = new URL(req.url, 'http://x');
  if (req.method === 'GET' && u.pathname === '/') { res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' }); return res.end(page) }
  if (req.method === 'GET' && u.pathname === '/get') { res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' }); return res.end(getPage) }
  if (req.method === 'GET' && u.pathname === '/qr.svg') {
    const c = u.searchParams.get('c');
    if (!sessions.has(c)) { res.writeHead(404); return res.end() }
    res.writeHead(200, { 'Content-Type': 'image/svg+xml' });
    return res.end(await QR.toString(`castly://join?c=${c}`, { type: 'svg', margin: 2 }));
  }
  if (req.method === 'POST' && u.pathname === '/join') {                // the phone says: "code 123456 is me, find me here"
    if (limited(req)) { res.writeHead(429); return res.end() }
    let body = ''; req.on('data', d => { body += d; if (body.length > 1000) req.destroy() });
    req.on('end', () => {
      try {
        const { code, url } = JSON.parse(body), tv = sessions.get(code);
        if (!tv || !LAN.test(url)) { res.writeHead(404); return res.end() }
        tv.send(JSON.stringify({ go: url })); sessions.delete(code);       // one use only
        res.writeHead(200); res.end('ok');
      } catch (e) { res.writeHead(400); res.end() }
    });
    return;
  }
  res.writeHead(404); res.end();
});

new WebSocketServer({ server, path: '/tv' }).on('connection', ws => {   // each TV page gets its own code
  let c; do { c = String(crypto.randomInt(100000, 1000000)) } while (sessions.has(c));
  sessions.set(c, ws); ws.send(JSON.stringify({ code: c }));
  const t = setTimeout(() => ws.close(), 10 * 60 * 1000);                // codes last 10 minutes
  ws.on('close', () => { clearTimeout(t); sessions.delete(c) });
});
server.listen(PORT, () => console.log('Castly relay on :' + PORT));
