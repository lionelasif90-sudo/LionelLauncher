# Lionel Launcher
An all-in-one Android launcher built on top of [YagniLauncher](https://github.com/JackEblan/YagniLauncher) (GPL-3.0).

## What's new on top of Yagni
All new code lives in the `:lionel` module plus one hook in `framework/launcher-apps` (`startMainActivity`).

| Feature | How it works |
|---|---|
| **Smart suggestions** | Every launch is counted per hour of day (on device). The Hub shows the apps you usually open around now. |
| **AI command bar** | Type or speak. Simple English commands (open app, alarm, timer, call, search, settings) run offline. Add an Anthropic API key in *Lock & AI* for other languages (Bengali too) and questions. |
| **Focus mode** | Pick distracting apps, start a 25 min / 1 h / 2 h / until-stopped session. Those apps won't open from anywhere in the launcher. |
| **App lock** | Locked apps ask for your screen PIN / pattern / fingerprint (60 s grace). Needs a screen lock on the phone. |
| **Auto-organize + smart search** | Apps are grouped by category; search tolerates typos and matches letters in order. |

Open **Lionel Hub** from the app drawer. To reach it with a gesture: Lionel settings > Gestures > choose *Open app* > Lionel Hub.

## Build (GitHub Actions)
1. Push this folder to a GitHub repo, open *Actions > Build Lionel APK > Run workflow*.
2. Download the `LionelLauncher-release` artifact. It is a **release** build (R8 shrunk), much smoother than a debug APK.
3. To keep updates installing over each other, create a keystore once with `scripts/make-keystore.sh` and add the three secrets it prints.
   Without secrets a key is generated once and cached by GitHub; if the cache is evicted the key changes and you must reinstall.

## Notes
- Not compiled by the author's tooling yet: first CI run may need small fixes; paste the log.
- Focus mode and app lock apply to launches that go through Lionel. They are not a security boundary against a determined user.
- The API key is stored in the app's private storage, unencrypted.
