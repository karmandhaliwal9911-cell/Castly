# Castly for LG TV (webOS web app)
A thin app: it opens your relay (castly.tv), which shows the code and QR. The phone connects, then the TV jumps to the stream (phone to TV directly over Wi-Fi).
Needs an LG TV with webOS 23 or newer (2023+) for screen mirroring. Older TVs can still use "Share video, music, photos" and "Send files" through the TV browser.
1. Edit RELAY in index.html to your relay address.
2. Install the webOS CLI (npm i -g @webos-tools/cli).
3. On the TV install "Developer Mode" from the LG Content Store, sign in with a free LG developer account, turn Dev Mode on, note the passphrase.
4. ares-setup-device --add tv --info "{'host':'TV_IP','port':'9922','username':'prisoner'}"  then ares-novacom --device tv --getkey
5. ares-package .   then   ares-install --device tv tv.castly.app_1.0.0_all.ipk   then   ares-launch --device tv tv.castly.app
To publish for everyone: submit the .ipk in LG Seller Lounge.
