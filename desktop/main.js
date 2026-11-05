// Ferrite desktop: serve la pagina su 127.0.0.1 (indirizzo accettato da Spotify come Redirect URI)
// e la apre in una finestra. Il mini player rimpicciolisce la finestra e la tiene in primo piano.
const { app, BrowserWindow, ipcMain, shell, dialog } = require('electron');
const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 43219;
const ROOT = path.join(__dirname, 'app');
const TYPES = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json', '.webmanifest': 'application/manifest+json',
  '.png': 'image/png', '.svg': 'image/svg+xml', '.css': 'text/css'
};

function serve() {
  return new Promise((resolve, reject) => {
    const srv = http.createServer((req, res) => {
      let p = decodeURIComponent(new URL(req.url, 'http://x').pathname);
      if (p.endsWith('/')) p += 'index.html';
      const file = path.normalize(path.join(ROOT, p));
      if (!file.startsWith(ROOT)) { res.writeHead(403); return res.end(); }
      fs.readFile(file, (err, data) => {
        if (err) { res.writeHead(404); return res.end('Non trovato'); }
        res.writeHead(200, { 'Content-Type': TYPES[path.extname(file)] || 'application/octet-stream', 'Cache-Control': 'no-store' });
        res.end(data);
      });
    });
    srv.once('error', reject);
    srv.listen(PORT, '127.0.0.1', () => resolve(srv));
  });
}

let win = null;
let normalBounds = null;

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (!win) return;
    if (win.isMinimized()) win.restore();
    win.focus();
  });

  app.whenReady().then(async () => {
    try {
      await serve();
    } catch (e) {
      dialog.showErrorBox('Ferrite', `La porta ${PORT} è occupata da un altro programma. Chiudilo e riapri Ferrite.`);
      app.quit();
      return;
    }
    win = new BrowserWindow({
      width: 1180, height: 820, minWidth: 360, minHeight: 140,
      backgroundColor: '#0b0d10', autoHideMenuBar: true, title: 'Ferrite',
      icon: path.join(__dirname, 'app', 'icons', 'icon-512.png'),
      webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, sandbox: true }
    });
    win.loadURL(`http://127.0.0.1:${PORT}/`);
    // I link "apri in una nuova scheda" vanno nel browser di sistema; il login Spotify resta nella finestra.
    win.webContents.setWindowOpenHandler(({ url }) => { shell.openExternal(url); return { action: 'deny' }; });
  });

  ipcMain.on('mini', (_e, on, height) => {
    if (!win) return;
    if (on) {
      if (!normalBounds) normalBounds = win.getBounds();
      win.setMinimumSize(300, 100);
      const h = Math.max(110, Math.min(280, Number(height) || 160));
      win.setContentSize(480, h);
      win.setAlwaysOnTop(true, 'floating');
    } else {
      win.setAlwaysOnTop(false);
      win.setMinimumSize(360, 140);
      if (normalBounds) { win.setBounds(normalBounds); normalBounds = null; }
    }
  });

  app.on('window-all-closed', () => app.quit());
}
