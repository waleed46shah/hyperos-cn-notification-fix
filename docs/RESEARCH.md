# Why notifications die on HyperOS China ROMs

This is how we found out why Google push notifications (FCM) stop working on a
Xiaomi phone running a HyperOS **China** ROM with Google services installed, and
why the usual fixes don't stick.

**Device:** Xiaomi 14 Ultra (`aurora`), HyperOS 3, `OS3.0.306.0.WNACNXM` and
`OS3.0.308.0.WNACNXM`, `ro.miui.region=CN`.

**Method:** Plain ADB only (no root): `dumpsys`, `logcat`, `settings`, plus
decompiling `miui-services.jar`, PowerKeeper and Security Center pulled from the
device with `adb pull`, read with jadx. This document cites class and method
names only. No Xiaomi code is reproduced here.

## Symptoms

- WhatsApp, Slack, Instagram, GitHub and Gmail notifications arrive late, or all at
  once when the phone is unlocked or the app is opened.
- Google's "Tap Yes, it's me" sign-in prompt never shows up.
- `*#*#426#*#*` (FCM diagnostics) shows **Not connected** after the phone has been
  locked for a while.
- The usual fixes (autostart, "No restrictions", locking apps in recents, Doze
  whitelist, appops) help a bit but don't solve it.

## Evidence

From one day of normal use, before the fix:

- `dumpsys greezer` showed **195 freezes of Play services' UID** in a single
  day, each about 5 seconds after screen-off, with the reason
  `screen off`. Its freeze record showed it **spent more time frozen than
  active**.
- The FCM service dump showed `Not connected`, with its reconnect alarm
  **more than 6 hours overdue**. A frozen process can't run its own reconnect.
- WhatsApp, Slack and Instagram themselves had a **frozen time of 0**: they were
  already exempt. Only the thing that delivers their messages was being frozen.
- `IM mGmsLimitEnabled : true` and `mCurrentCNPolicy: 1` in the Greeze dump.

## Root cause

### 1. Greeze freezes Play services on CN builds

Xiaomi's freezer lives in `miui-services.jar` (`com.miui.server.greeze`).

- `PolicyManager.CN_MODEL` is `true` when `ro.miui.region` is `CN`. That's a
  read-only property, so it can't be changed without root.
- `power.ListAppsManager` keeps a system blacklist that contains
  `com.google.android.gms`. The code **removes it only when `CN_MODEL` is false**.
  On global builds Play services is left alone; on CN builds it's fair game.
- On top of that, `GreezeManagerService.triggerGMSLimitAction()` (CN only,
  controlled by `mGmsLimitEnabled`) quick-freezes Play services, and broadcasts to
  Play services are deferred while the screen is off.

### 2. The one exemption a normal app can reach

`power.PolicyMaker.isAllowFreeze()` runs every candidate through
`AurogonFilterManager.filter()`. One of its checks is
`AurogonImmobulusMode.isNoRestrictApp(pkg)`, which reads the comma-separated
`Settings.System` key **`MILLET_NO_RESTRICT_APP`** (it has a `ContentObserver`, so
changes apply immediately). If a package is on that list, it isn't frozen.

When we added `com.google.android.gms` to that key over ADB, Greeze froze other
apps **443 times over 16 hours and never froze Play services once**.

### 3. PowerKeeper keeps removing it

The exemption disappeared again the next morning. In PowerKeeper,
`controller.ActiveStateController.dealNoRestrictApp()` **overwrites**
`MILLET_NO_RESTRICT_APP` with the packages whose row in PowerKeeper's `userTable`
has `bgControl = noRestrict`, which is what "No restrictions" in Battery
settings writes. It runs whenever that table changes, and in practice after app
installs, updates and removals (we reproduced it by uninstalling an app).

Play services never has such a row:

- The Battery settings screen for Play services shows usage only, with no restriction options.
  Security Center's `PowerDetailActivity` (it's exported, so it can be opened over
  ADB with a `package_name` extra) only shows the options when PowerKeeper returns
  a configuration for the package. For `com.google.android.gms`, and for
  `com.google.android.gsf` (they share a UID, and PowerKeeper's `PreSetGroup` treats
  `gsf` as that group's head), it returns none.
- `userTable` is behind PowerKeeper's `PowerKeeperConfigureProvider`, which
  requires `miui.permission.powerkeeper.HIDDEN_MODE_PROVIDER`, a permission only
  Xiaomi's own apps hold. The ADB shell gets `Permission Denial`.
- PowerKeeper can't be disabled: `pm disable-user` returns `Cannot disable
  system packages`.

So any manual fix lasts only until the next Play Store update.

## Things that don't work

| Attempt | Result |
|---|---|
| Doze whitelist, appops, standby bucket for Play services | Play services is already system-whitelisted; Greeze ignores these |
| `settings put global cached_apps_freezer disabled` | That's Android's own freezer, not Greeze |
| `settings put system cloud_greezer_enable false` | Greeze stays enabled (`persist.sys.powmillet.enable=true`, read-only) |
| `dumpsys greezer force_cn_global ...` | Only works when `Build.IS_DEBUGGABLE` (Xiaomi's internal builds) |
| Writing PowerKeeper's `userTable` | Needs Xiaomi's signature permission |
| Disabling PowerKeeper | Blocked for system packages |
| Opening the hidden battery screen for gms/gsf | No row, so no options |

## The fix

Since the list can't be made to stick, **PushGuard keeps putting Play services back**:

- It registers a `ContentObserver` on `MILLET_NO_RESTRICT_APP` and re-adds
  `com.google.android.gms`, `com.google.android.gsf` and itself within about a second of any
  rewrite.
- It **targets API 22**. Android's settings provider only lets non-privileged apps
  write non-public `Settings.System` keys if they target API 22 or lower.
  `WRITE_SECURE_SETTINGS` doesn't help here. Installing needs
  `adb install --bypass-low-target-sdk-block` on Android 14+.
- It restarts on boot and on package changes, and it's a regular app, so it can
  be set to "No restrictions" in Battery settings. That puts PushGuard itself on
  PowerKeeper's list.

### Results

- After installing: **zero** Play services freezes, including with the screen off.
- After the OTA from 306 to 308: PushGuard restarted on boot, and the list was still correct.
- Overnight test: FCM still **Connected** in the morning, and messages arrived on time.

## Open questions

- Other CN-only behavior (deferred broadcasts to Play services while the screen is off)
  could still delay some things. We haven't seen it matter in practice yet.
- Other devices and HyperOS versions: please report with `scripts/check.sh`.
