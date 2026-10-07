## zygisk-detach-app

Material 3 Expressive app for the [zygisk-detach](https://github.com/j-hc/zygisk-detach) Magisk module: pick the apps that Play Store should stop updating.

Based on [j-hc/zygisk-detach-app](https://github.com/j-hc/zygisk-detach-app) by j-hc (Apache-2.0); modified by skofqq.

### Changes from the original

- Fixed system bar insets (status bar no longer covers the search field, list clears the gesture bar)
- Material 3 Expressive redesign: flexible top bar, pill search, filter chips, Google Photos style bottom bar with All / User / System sections, segmented list with switches
- Detached apps listed first as their own group; Detach button shown only for unsaved changes
- App list loads off the main thread; snackbars instead of toasts
- New vector adaptive launcher icon with themed icon support
- Translations: ru, uk, be, kk, de, es, it
- GitHub Actions builds and releases

### License

Apache License 2.0, see [LICENSE](LICENSE). Original work © j-hc.

### Download

Get the APK from [Releases](../../releases). Requires root and the zygisk-detach module.

### Signing

Release builds are signed with the key from the `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD` repository secrets. Without them CI falls back to a throwaway debug key, so updates between such builds need a reinstall.
