# HyperOS CN Notification Fix (PushGuard)

> **Status: beta, still under test.** It works on the device listed below and
> passed an overnight test, but it hasn't been tried widely yet. Reports from
> other phones and builds are very welcome.

Delayed or missing notifications on a **Xiaomi / Redmi / POCO phone running a
HyperOS China ROM with Google services installed**? WhatsApp, Slack, Instagram
or Gmail messages only arriving when you unlock the phone or open the app? The
"Tap Yes, it's me" Google sign-in prompt never showing up?

The cause isn't your app settings. **HyperOS CN freezes Google Play services
itself**, and Play services carries the push connection for almost every app.
PushGuard is a tiny app that stops that, **without root and without Shizuku**.
You need ADB once to install it, and nothing after that.

## What's going on

Full write-up with evidence: **[docs/RESEARCH.md](docs/RESEARCH.md)**. In short:

1. Xiaomi's process freezer (**Greeze**) freezes background apps about 5 seconds
   after the screen turns off. On CN builds, `com.google.android.gms` starts out on
   its system blacklist; the code only removes it when the ROM region isn't `CN`.
2. When Play services is frozen, the Firebase Cloud Messaging (FCM) connection drops and
   can't reconnect, so no app receives pushes until you wake the phone.
3. The freezer skips packages listed in the `Settings.System` key
   `MILLET_NO_RESTRICT_APP`. That's the only exemption it checks that a normal
   app can affect.
4. **PowerKeeper** rebuilds that list from its battery-settings table on every app
   install, update or removal. Play services has no row in that table, the Settings app
   won't let you create one, and only Xiaomi's own apps can write to it. So
   even if you add Play services yourself (for example over ADB), it's gone
   again after the next Play Store update.

## What PushGuard does

It watches `MILLET_NO_RESTRICT_APP` and puts Google Play services back **within
a second** whenever PowerKeeper rewrites the list. That's it.

- Starts on boot, runs as a minimal foreground service, and restarts after app installs.
- Also protects itself, so the freezer leaves it alone.
- Has **no internet permission**. The only thing it changes is that one setting.
- Shows its status when you open it: protected packages, last check, and how many repairs it has made.

## Requirements

- Xiaomi phone on a **HyperOS China ROM** (`ro.miui.region` = `CN`) with Google
  Play services installed
- A computer with **ADB** (`brew install android-platform-tools` on macOS)
- USB debugging turned on **for the install only**. You can switch Developer
  options off afterwards, for example for banking apps.

## Install

### Option A: prebuilt APK (easiest)

1. Download `PushGuard.apk` from the [latest release](../../releases).
2. Connect the phone with USB debugging on, then run:

   ```bash
   git clone https://github.com/waleed46shah/hyperos-cn-notification-fix
   cd hyperos-cn-notification-fix
   scripts/install.sh ~/Downloads/PushGuard.apk
   ```

   If you'd rather not clone the repo, the essential command is
   `adb install --bypass-low-target-sdk-block PushGuard.apk`, then open the app once.

3. On the phone: **Settings → Battery → PushGuard → No restrictions**.

On first launch Android may warn that the app was built for an older version of
Android. That's expected (see [Why targetSdk 22?](#why-targetsdk-22)), so tap **OK**.

### Option B: build from source (macOS, no Android Studio)

```bash
scripts/setup.sh     # once: Google's command-line SDK tools via Homebrew (~150 MB)
scripts/build.sh     # builds PushGuard.apk with aapt2/javac/d8/apksigner, no Gradle
scripts/install.sh   # installs it and grants background permissions
```

## Check that it works

- Open PushGuard. Every package should show **[on list]**.
- Dial `*#*#426#*#*` (Google's FCM diagnostics). It should say **Connected**.
- Or run `scripts/check.sh` with the phone connected. It shows Play services
  freezes (should be empty), the push connection state, and PushGuard's log.

## Protect more apps

Normal apps don't need PushGuard: set them to **Settings → Battery → No restrictions**,
and PowerKeeper keeps them on the list permanently. PushGuard is for packages
whose battery options the Settings app hides (Google apps, for example). To add
one:

```bash
adb shell am start -n dev.pushguard/.MainActivity --es add com.google.android.gm
adb shell am start -n dev.pushguard/.MainActivity --es remove com.google.android.gm
```

## Tested on

| Device | ROM | Result |
|---|---|---|
| Xiaomi 14 Ultra (`aurora`) | HyperOS 3, OS3.0.306.0.WNACNXM | Play services no longer frozen |
| Xiaomi 14 Ultra (`aurora`) | HyperOS 3, OS3.0.308.0.WNACNXM | Survived the OTA; overnight test passed (FCM connected, messages on time) |

If you test another device or build, please open an issue with the output of
`scripts/check.sh`.

## Why targetSdk 22?

Android only lets normal apps write non-public `Settings.System` keys if they
target API level 22 or lower. `MILLET_NO_RESTRICT_APP` is a Xiaomi key, so it's
non-public. That's also why Android 14+ needs `--bypass-low-target-sdk-block` to
install it, and why the "older version of Android" warning appears.

## Limitations

- **Beta.** Tested on one device so far.
- Normal-priority pushes (some Instagram DMs, for example) can still wait for Android's
  Doze maintenance window. Pixels do the same, so that's standard Android behavior, not this bug.
- A future HyperOS update could rename the setting, change how the freezer
  decides, or stop old-target apps from writing settings. If PushGuard ever shows
  `write FAILED` or a package as `[MISSING]`, please open an issue.
- After a factory reset you need ADB again to reinstall it.

## Uninstall

```bash
adb uninstall dev.pushguard
```

## Roadmap

- Notify the user if a repair ever fails
- Optional mode: automatically protect every installed app that uses Google push
- Test on more devices and HyperOS versions

## Disclaimer

Not affiliated with Xiaomi or Google. It changes one system setting and is
provided as-is under the [MIT License](LICENSE). Use at your own risk.
