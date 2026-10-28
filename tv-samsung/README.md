# Castly for Samsung TV (Tizen web app)
A thin app: it opens your relay (castly.tv), which shows the code and QR. The phone connects, then the TV jumps to the stream (phone to TV directly over Wi-Fi).
Needs a Samsung TV from 2022 or newer (Tizen 7+, for screen mirroring). Older TVs can still use "Share video, music, photos" and "Send files" through the TV browser.
1. Edit RELAY in index.html to your relay address.
2. Install Tizen Studio (with the TV extension). File > Import > Tizen Project > this folder.
3. Make a Samsung certificate (Tools > Certificate Manager > Samsung). A free developer certificate works for your own TVs.
4. On the TV: Apps, type 12345, turn Developer mode on, enter your computer's IP. Restart the TV.
5. Tizen Studio > Device Manager > connect to the TV > right-click project > Run As > Tizen Web Application.
To publish for everyone: package a signed .wgt and submit it in Samsung Seller Office.
