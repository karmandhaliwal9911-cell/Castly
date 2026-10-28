# Castly website and relay
- `/`      TV page: shows a code and QR. The phone connects, then the TV jumps to the stream.
- `/get`   Download page for the app (edit APK_URL in public/get.html).
Run locally: `npm install && npm start` (Node 18+). Host free on Render (see the guide): Root Directory `relay`, Build `npm install`, Start `npm start`.
Codes last 10 minutes and work once; 10 tries a minute per address. The relay only sees the phone's home-network address, never video or sound.
