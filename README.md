## zygisk-detach-app

Material 3 Expressive app for the [zygisk-detach](https://github.com/j-hc/zygisk-detach) Magisk module: pick the apps that Play Store should stop updating.

Based on [j-hc/zygisk-detach-app](https://github.com/j-hc/zygisk-detach-app) (Apache-2.0).

### Download

Get the APK from [Releases](../../releases). Requires root and the zygisk-detach module.

### Signing

Release builds are signed with the key from the `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD` repository secrets. Without them CI falls back to a throwaway debug key, so updates between such builds need a reinstall.
