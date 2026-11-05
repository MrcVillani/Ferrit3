const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('ferriteDesktop', {
  setMini: (on, height) => ipcRenderer.send('mini', !!on, height)
});
