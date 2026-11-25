# Changelog

Each `## <version>` section becomes the notes of that GitHub release, and the in-app updater shows them before installing. Write them for users, newest version on top.

## 1.9

### English

- Search button in the bottom bar: the bar turns into a search field right above the keyboard; back closes it. Search covers all apps
- Presets: ready-made lists of apps to detach, opened from the bookmarks icon
  - Patched and modded: music and video, social and messengers, Google apps, photo and tools, sport and news. Built from ReVanced and the Morphe patch bundles (morphe-patches, piko, De-Vanced)
  - By country: Russia (banks, Yandex, VK, Ozon, Avito, Wildberries) and China
  - "Patched by root modules": apps that your installed ReVanced/Morphe-style Magisk modules replace are found automatically
  - A preset only marks installed apps; nothing changes until you press ✓
  - App names are shown even for apps that are not installed
- Your own presets: create from scratch, from the detached apps or from a built-in preset; edit, delete with undo; export and import as JSON (a .txt detach list can be imported too)
- Settings redesigned: app card with version and module status, Material 3 Expressive theme picker, grouped sections, links to the module and the original app
- Fixed clipped labels in the bottom bar and settings

### Русский

- Кнопка поиска в нижнем меню: панель превращается в поле поиска прямо над клавиатурой, «назад» закрывает его. Поиск идёт по всем приложениям
- Пресеты: готовые списки приложений для отвязки, открываются по значку закладок
  - «Патчи и моды»: музыка и видео, соцсети и мессенджеры, приложения Google, фото и инструменты, спорт и новости. Собраны по ReVanced и наборам патчей Morphe (morphe-patches, piko, De-Vanced)
  - По странам: Россия (банки, Яндекс, VK, Ozon, Авито, Wildberries) и Китай
  - «Пропатчены root-модулями»: приложения, которые подменяют установленные Magisk-модули вроде ReVanced/Morphe, находятся автоматически
  - Пресет только отмечает установленные приложения, ничего не меняется, пока не нажата ✓
  - Названия приложений видны, даже если приложение не установлено
- Свои пресеты: создание с нуля, из отвязанных или из встроенного пресета, изменение, удаление с отменой, экспорт и импорт в JSON (можно импортировать и .txt-список)
- Новые настройки: карточка с версией и статусом модуля, выбор темы в стиле Material 3 Expressive, сгруппированные разделы, ссылки на модуль и оригинальное приложение
- Исправлен обрезанный текст в нижнем меню и настройках

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
