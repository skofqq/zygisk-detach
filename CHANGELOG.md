# Changelog

Each `## <version>` section becomes the notes of that GitHub release, and the in-app updater shows them before installing. Write them for users, newest version on top.

## 1.8

- Экспорт и импорт списка отвязанных приложений в настройках: список сохраняется в текстовый файл, при импорте приложения из файла добавляются к текущему списку
- Проверка модуля zygisk-detach при запуске: приложение подскажет, если модуль не установлен, выключен, помечен на удаление, ждёт перезагрузки или вышла его новая версия, и предложит включить его, перезагрузить устройство или открыть релизы
- Перед обновлением приложение показывает, что нового в версии, включая пропущенные версии
- Для каждого pull request CI собирает отладочный APK

## 1.7

- Work profile and private space apps are listed with a badge, a label and their own filter
- "Both profiles" label for apps installed in both profiles
- Filter chips are grouped and mutually exclusive
- Predictive back gesture in settings

## 1.6.1

- The updater keeps working when the GitHub API rate limit is used up

## 1.6

- Renamed to Zygisk Detach, new split Play icon and animated splash screen
- Settings: light / dark / system theme and a Material You colors toggle
- Built-in updater: checks GitHub releases, downloads the APK and installs it through root
- One-tap Add to Obtainium
- Releases are signed with a permanent key

## 1.5

- Material 3 Expressive redesign with filters and a bottom bar
- Status bar no longer covers the search field
- Detached apps listed first; Detach button shown only for unsaved changes
- Translations: ru, uk, be, kk, de, es, it
