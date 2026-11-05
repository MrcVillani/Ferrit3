#!/usr/bin/env sh
# Rigenera web/index.html a partire da src/ferrite-player.html (la stessa pagina dell'anteprima).
set -e
cd "$(dirname "$0")"
{
  printf '<!doctype html>\n<html lang="it">\n<head>\n<meta charset="utf-8">\n'
  printf '<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">\n'
  printf '<link rel="manifest" href="manifest.webmanifest">\n<meta name="theme-color" content="#14202b">\n'
  printf '<link rel="icon" href="icons/icon-192.png">\n<link rel="apple-touch-icon" href="icons/icon-192.png">\n'
  cat src/ferrite-player.html
  printf '\n<script>if ("serviceWorker" in navigator && location.protocol === "https:") navigator.serviceWorker.register("sw.js").catch(function(){});</script>\n</html>\n'
} > web/index.html
echo "web/index.html aggiornato"
