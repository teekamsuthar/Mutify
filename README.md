
# <img alt="Logo" src="https://drive.google.com/u/1/uc?id=1Y5WXSdqaS4bruYLRibYg_N5GEQbkXYBf&export=download" align="center" height="60"></a>Mutify - mute annoying ads 

A powerful, light-weight Spotify Ad silencing app built using Java for Android.

### 💖Love this app? Buy me a [Coffee.](https://paypal.me/teekamsuthar) ☕ 


[<img alt="Buy me a Coffee Button" width=200 src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png">](https://www.buymeacoffee.com/teekamsuthar)
&nbsp; [![paypal](https://www.paypalobjects.com/en_US/i/btn/btn_donateCC_LG.gif)](https://paypal.me/teekamsuthar)
</br>

<img alt="Mutify Banner" src="https://drive.google.com/uc?id=1lCmutBK5hDG9YG9H-qDdwJYE3wBG_Epr&export=download" align="center" height="360"></a>

Whenever Mutify detects an ad is playing on Spotify, it helps you turn down the volume of the ads automatically, so that you can sit back and enjoy listening to your favorite music without worrying about those annoyingly loud ads.

## 🔍How it works </br>

Mutify reads the "now playing" info Spotify publishes for its media notification and lock screen (Spotify's media session). When that info says an ad is playing, Mutify turns the music volume down, and back up shortly after the ad is over.

To see Spotify's media session, Android requires **Notification access** for Mutify. Mutify only looks at Spotify's media info and ignores every other notification.

Spotify's old *Device Broadcast Status* setting is **no longer needed** — newer Spotify versions removed it, which is why older Mutify versions stopped working.

## 📑Setup </br>

1. **Allow 'Notification access' for Mutify.** The dialog on first start takes you there ("Open settings"), or go to Settings and search for *Notification access*.
2. **Android 13+, APK installed outside the Play Store:** the notification-access switch may be greyed out as a "restricted setting". Open App info > Mutify > ⋮ (top right) > *Allow restricted settings*, then grant access. The onboarding dialog shows this hint too.
3. **Samsung phones:** *Auto Blocker* (Settings > Security and privacy) blocks installing apps from outside the Play Store and USB debugging. Turn it off to sideload the APK.
4. Please add Mutify to the battery saving exceptions list (and allow autostart on Xiaomi, Oppo, Vivo, Huawei & co.) to make sure it works uninterruptedly in the background.

Mutify does not need to stay open: once access is granted, Android keeps it listening in the background, without a permanent notification.

## ✅Features </br>

- **Adjust Ads Volume**: mute ads, or play them at 10–50 % of your current volume. The slider wins during an ad — even if you press the volume keys, the ad stays at the level you chose — and your volume is restored when the ad is over.
- **Quick Settings tile**: switch Mutify on or off with one swipe; shows Off, On, Ad muted or Setup needed (Android 7+; Android 13+ offers to add it for you)
- **Home-screen widget**: 1×1, resizable to 2×1, with the same states
- Now playing info, media controls, manual mute/unmute and an Open Spotify button
- System / Light / Dark theme
- About screen with contact, source code, disclaimer and open-source licences
- Languages: English, German, French, Spanish, Brazilian Portuguese and Vietnamese — follows the phone, or pick one in the menu (⋮ › Language)

## 🛠Troubleshooting </br>

- **Ads are not muted at all?** Check that notification access is still on — the app, the tile and the widget show "Setup needed" when it is off.
- **An ad slipped through?** Connect the phone with USB debugging and run `adb logcat -s SpotifyAdListener` while the ad plays: it shows the metadata Spotify sent and what Mutify did. Please attach that output to an [issue](https://github.com/teekamsuthar/Mutify/issues).

### ℹ Disclaimer

Mutify does NOT block ads, it just turns down the device's music volume whenever an ad is detected playing. So it does not interfere with your Spotify app. Its only permission is notification access, used solely to read what Spotify is playing.

- **_Spotify Lite is NOT supported!_**

## 🏗Build </br>

Open the project in Android Studio (JDK 17), or run `./gradlew build` (debug and release builds, unit tests and lint).

If you love Mutify, feel free to fork the repo and contribute on [github](https://github.com/teekamsuthar/Mutify). (or [sponsor](https://paypal.me/teekamsuthar) this project if you feel like being generous ;)

### ⬇ Download it on [Play Store](https://play.google.com/store/apps/details?id=live.teekamsuthar.mutify)

<!-- [![Get it on Google Play](https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png)](https://play.google.com/store/apps/details?id=live.teekamsuthar.mutify) -->

<a href="https://play.google.com/store/apps/details?id=live.teekamsuthar.mutify"> <img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" align="center" height="120"></a>
<br>

If there is an issue or feature request, please email me at teekam.suthar1@gmail.com or create an issue on [github](https://github.com/teekamsuthar/Mutify/issues).

_Please don't forget to leave your valuable reviews and suggestions. It helps me to improve this app further._

### 📜LICENSE

```MIT License

Copyright (c) 2020 Teekam Suthar

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
