# Tv.Mods AppStore

Quest / Android sideload store. UI is **only** `app/src/main/assets/index.html`.

Replace `app/src/main/assets/img/IMG_9410.jpeg` with your real photo if you want the exact pfp.

## SPM GORILLA TAG
After install-unknown-apps permission, it downloads both APKs into **app cache only** (not Downloads) then runs PackageInstaller:

- https://github.com/Tvman4/apks/releases/download/spm/com.SPM.GorillaPatcher-Signed.apk
- https://github.com/Tvman4/apks/releases/download/spm/GorillaTag-SPM-09-04-2026-v4.1.2.apk

## Build
GitHub Actions: `.github/workflows/build.yml`

Local:

```
./gradlew assembleRelease
```

Sideload the produced APK onto Quest first. Open **Unknown Sources** → Tv.Mods AppStore.
