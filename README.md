# Ferrite Player

Player retrò a finestre che comanda Spotify. Una sola pagina (`src/ferrite-player.html`) diventa:

- **sito installabile** su GitHub Pages: https://mrcvillani.github.io/Ferrit3/
- **app desktop** Windows / Linux / macOS (cartella `desktop/`, Electron)
- **APK Android** (cartella `android/`, Trusted Web Activity che apre il sito a schermo intero)

L'audio esce sempre dall'app Spotify ufficiale: Ferrite la comanda via Spotify Connect (serve Premium).

## Scaricare

Ogni push su `main` compila tutto e pubblica una **Release**: https://github.com/MrcVillani/Ferrit3/releases/latest

- Android: `Ferrite-N.apk` (al primo avvio Android chiede di consentire l'installazione da fonti sconosciute)
- Windows: `Ferrite Setup ….exe` oppure la versione `portable`
- Linux: `.AppImage` · macOS: `.dmg` non firmato (primo avvio: tasto destro → Apri)

## Collegare Spotify

Su https://developer.spotify.com/dashboard crea un'app (API: *Web API*) e aggiungi due Redirect URI:

- `https://mrcvillani.github.io/Ferrit3/` (sito e app Android)
- `http://127.0.0.1:43219/` (app desktop)

Poi incolla il Client ID nella finestra **Collegamento** di Ferrite.

## Modificare

Cambia `src/ferrite-player.html` e fai push: il workflow rigenera `web/index.html`, il sito e gli eseguibili.
App desktop in locale: `./build-web.sh && cd desktop && npm install && npm start`.

## Firma dell'APK

La prima build genera una chiave e la tiene nella cache di GitHub Actions, così gli aggiornamenti si installano sopra la versione precedente. La cache scade se il repo resta fermo per 7 giorni: in quel caso disinstalla e reinstalla l'app. Per una chiave permanente aggiungi i secret `FERRITE_KEYSTORE_B64` (keystore in base64) e `FERRITE_KS_PASS`.

Il sito sta in una sottocartella di github.io, quindi l'app Android mostra una sottile barra con l'indirizzo. Con un dominio tuo (variabile `SITE_URL` + `.well-known/assetlinks.json`) sparisce.
