# Network Speed Monitor - Technical Specification

Цей документ описує технічні деталі та архітектуру для Android застосунку, який відображає швидкість інтернет-з'єднання в панелі статусу.

## 1. Загальна інформація та Технічний Стек
- **Мова:** Kotlin.
- **UI Фреймворк:** Jetpack Compose (Material 3).
- **Асинхронність:** Kotlin Coroutines & `StateFlow`.
- **Dependency Injection:** Dagger Hilt.
- **Зберігання налаштувань:** Jetpack DataStore (Preferences) — для збереження конфігурації (наприклад, стан `isEnabled: Boolean`, щоб знати, чи запускатися після перезавантаження пристрою).
- **SDK:** `minSdk = 26` (Android 8.0), `targetSdk = 35`, `compileSdk = 35`.
- **Локалізація:** `uk` (за замовчуванням), `en` (fallback).
- **Аналітика та Crash Reporting:** З метою збереження максимальної енергоефективності та приватності — свідомо **не** використовуються жодні сторонні трекери.

## 2. Функціональні вимоги та Алгоритми
- **Відображення в статус-барі:** Постійне сповіщення (Foreground Service). Іконка генерується динамічно як `Bitmap` із текстом швидкості `Upload` та `Download`.
- **Інтервал оновлення:** Цільовий — 1000 мс (один тік на секунду) реалізовано через `delay(1000)` в рамках Coroutine, яка відстежує стан.
- **Обчислення швидкості (Формула з усуненням дрейфу таймера):**
  - Дельта розраховується з урахуванням реального часу, що минув між тіками, для уникнення похибок таймера (наприклад, 1050 мс замість 1000 мс):
  ```kotlin
  bitsPerSecond = (currentBytes - previousBytes) * 8 * 1000 / elapsedMs
  ```
- **Масштабування (Formatter) та Пороги:** 
  Округлення **до цілих чисел**, окрім діапазону Gbps — там **дозволяється 1 знак після коми** для збереження точності та читабельності (наприклад `1.2G`). Діапазони:
  - `< 1 000 bps` → показувати у **bps**
  - `1 000 – 999 999 bps` → показувати у **Kbps**
  - `1 000 000 – 999 999 999 bps` → показувати у **Mbps**
  - `>= 1 000 000 000 bps` → показувати у **Gbps** (або `G`)
- **Розширене сповіщення (Шторка):**
  - Заголовок `Швидкість мережі`
  - Текст: `↓ 14 Mbps | ↑ 2 Mbps` (динамічно оновлюється).
  - **Дія при натисканні (Tap):** Відкриття Dashboard за допомогою `PendingIntent.getActivity`. Обов'язково використовувати флаг `PendingIntent.FLAG_IMMUTABLE` (вимога Android 12+).

## 3. UI/UX та Accessibility
- **Лейаут іконки Status Bar:** 
  Текст розташовано вертикально: зверху Upload, знизу Download. 
  `↑ 2M`
  `↓ 14M`
  *Скорочення:* значення понад 999 обрізати до макс. 4 символів (з урахуванням крапки для діапазону Gbps).
- **Головний екран (Dashboard):**
  - **Switch** для увімкнення/вимкнення моніторингу.
  - **Статус сервісу:** Текстовий індикатор: "Активний / Очікує мережу / Зупинений".
  - **Жива швидкість:** Відображається той самий потік швидкості, що й у сервісі (зв'язок відбувається через спільний Hilt Singleton `StateFlow`). Коли Switch вимкнено (сервіс зупинено) — показувати прочерки (`— / —`).
  - **Дозволи:** Блок керування дозволами (`POST_NOTIFICATIONS` та Battery Optimization). 
- **Accessibility:** Для всіх активних елементів (Switch, кнопки) прописати коректні `contentDescription` для підтримки TalkBack.
- **Dark/Light Theme:** Динамічна підтримка системної теми (Compose Material 3 Theme).

## 4. Стратегія тестування
- **Unit-тести:** Обов'язкове покриття для класу-калькулятора та форматера (SpeedFormatter), перевірка обробки нульових дельт, overflow (від'ємних значень) та коректного округлення порогів до цілих/сотень мегабітів.
- **Instrumented/UI тести:** Тестування Compose Dashboard, відображення станів StateFlow.

## 5. Нефункціональні вимоги та Memory Management
- **Зчитування трафіку:** Використання `TrafficStats.getTotalRxBytes()` / `getTotalTxBytes()`. Дані включають весь системний трафік, що гарантує високу продуктивність.
- **Bitmap Management та Монохромність (CRITICAL):**
  - `smallIcon` у Status Bar є монохромним. Обов'язково створюється `Bitmap` конфігурації `Bitmap.Config.ALPHA_8` з розміром `48x48 px`. Це зменшує споживання пам'яті в 4 рази. 
  - `Paint.color` встановлюється рівним `Color.WHITE` (система бере виключно альфа-значення для малювання).
  - На старті створюється **тільки один** екземпляр `Bitmap` і `Canvas`. Для кожного нового тіку об'єкт перевикористовується: `Canvas` очищається викликом `drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)`, наноситься оновлений текст, і передається в `Icon.createWithBitmap()`. 

## 6. Граничні випадки (Edge Cases)
- **Мережа відключена (Airplane Mode):** Моніторинг призупиняється, іконка повністю приховується, а `ConnectivityManager.NetworkCallback` чекає на мережу для поновлення.
- **Відмова у POST_NOTIFICATIONS (Android 13+):** Заборонено стартувати `Foreground Service`. На Dashboard виводиться червоний Alert з кнопкою переходу в налаштування. Коли дозвіл отримано через callback `ActivityResultLauncher.registerForActivityResult(RequestPermission())` — сервіс запускається.
- **Константи Notifications:** 
  - `const val CHANNEL_ID = "speed_monitor_channel"`
  - `const val NOTIFICATION_ID = 1`
  Якщо користувач вимикає сповіщення каналу у налаштуваннях ОС, Dashboard інформує про це запитом на увімкнення перемикача.
- **Doze / Оптимізація Батареї:** Broadcast повідомлення `Intent.ACTION_SCREEN_OFF` зупиняє розрахунки таймера, а `ACTION_SCREEN_ON` відновлює сервіс та оновлює Bitmap одразу.
- **Аномалії розрахунку:** 
  - *Перший тік:* delta = 0, попередні байти зберігаються, виводиться `0 bps`.
  - *Overflow / Від'ємна дельта:* При рестарті інтерфейсу або переповненні лічильника ОС, `currentBytes < previousBytes`. Це розцінюється як "Перший тік", delta = 0.
- **Автозапуск після ребуту:** 
  `BroadcastReceiver` слухає виключно `android.intent.action.BOOT_COMPLETED`. (Без `directBootAware`). Сервіс запуститься після того, як пристрій буде вперше розблоковано. Це критично важливо, оскільки сховище налаштувань DataStore/SharedPreferences у Credential Protected Storage недоступне до першого розблокування. Читання стану раніше призведе до IOException (crash).

## 7. Android 14/15 FGS та Всі Необхідні Дозволи
Для повноцінної стабільної роботи має бути прописано увесь наступний список `<uses-permission>`:
- `FOREGROUND_SERVICE`: базова вимога для FGS в старих та нових API.
- `FOREGROUND_SERVICE_SPECIAL_USE`: тип сервісу для Android 14+. Для нього **обов'язкова** наявність властивості: `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="Continuous network speed status bar overlay" />` у тегу Service. Також, він передається під час ініціалізації: 
  ```kotlin
  startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
  ```
- `ACCESS_NETWORK_STATE`: для відстежування стану через `NetworkCallback`.
- `POST_NOTIFICATIONS`: запит на показ `smallIcon` у Status Bar.
- `RECEIVE_BOOT_COMPLETED`: читається `BroadcastReceiver`.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: запитується з налаштувань Dashboard.

**Налаштування самого каналу сповіщення (Notification Setup):**
- **Priority/Importance:** `NotificationManager.IMPORTANCE_LOW` (без звуку, вібрації, але видиме значення у Status Bar).
- **Visibility:** `NotificationCompat.VISIBILITY_PUBLIC` (показ швидкості прямо на Lock Screen).
