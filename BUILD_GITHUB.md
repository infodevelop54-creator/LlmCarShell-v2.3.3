# Сборка LLM Car Shell через GitHub Actions

Полностью без Android Studio. Всё делается push'ем в репозиторий.

## 🚀 Быстрый старт

### 1. Загрузить проект на GitHub

```bash
git init
git add .
git commit -m "Initial commit v2.2.0"
git branch -M main
git remote add origin https://github.com/<your-user>/LlmCarShell.git
git push -u origin main
```

### 2. Дождаться первой сборки

Откройте вкладку **Actions** в вашем репозитории. Workflow **Build Debug APK** запустится автоматически.

Время первой сборки: **~30–45 минут** (компиляция llama.cpp + Gradle).
Последующие: **~5–10 минут** (кэш Gradle и llama.cpp).

### 3. Скачать APK

1. **Actions** → выберите завершённый workflow
2. Прокрутите вниз до **Artifacts**
3. Скачайте `llmcar-debug-<sha>.zip`
4. Внутри — `app-debug.apk`
5. Установите на магнитолу: `adb install app-debug.apk`

## 🏷️ Создание релиза (подписанный APK)

### 1. Создать keystore (один раз, локально)

```bash
keytool -genkey -v \
  -keystore release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias llmcar
```

### 2. Загрузить keystore в GitHub Secrets

**Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Значение |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 release.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | пароль keystore |
| `ANDROID_KEY_ALIAS` | `llmcar` |
| `ANDROID_KEY_PASSWORD` | пароль ключа |

### 3. Создать релиз

```bash
git tag v2.2.0
git push origin v2.2.0
```

Через ~10 минут в разделе **Releases** появится подписанный APK.

## 📊 Что делает каждый workflow

### `build-debug.yml`
- Запускается на каждый push в `main` или `develop`
- Собирает **debug** APK (без подписи, но устанавливаемый)
- Артефакт доступен 30 дней
- Не требует секретов

### `release.yml`
- Запускается на тег `v*`
- Собирает **release** APK с ProGuard + подписью
- Публикует в GitHub Releases
- Требует 4 секрета для подписи

## 🐛 Отладка

### APK не собирается

1. **Actions** → **Build Debug APK** → последний run
2. Откройте job → шаг с ошибкой → скачайте лог
3. Частые причины:
   - **NDK не установился** — проверьте версию r26b в `build-debug.yml`
   - **llama.cpp не собрался** — очистите кэш GitHub Actions
   - **Gradle не может скачать зависимости** — проверьте `settings.gradle.kts`

### Ошибка "Unsupported class file major version"

Убедитесь, что в workflow `java-version: 17` и в `app/build.gradle.kts` `jvmTarget = "17"`.

## 📱 Установка APK на магнитолу

### Способ 1: ADB по USB

```bash
adb connect <IP-магнитолы>:5555
adb install -r llmcar-2.2.0-arm64.apk
```

### Способ 2: Через флешку

1. Скачайте APK из GitHub Releases
2. Скопируйте на USB-флешку
3. Вставьте в магнитолу
4. Через файловый менеджер установите APK

### Способ 3: Прямая ссылка

```
https://github.com/<user>/LlmCarShell/releases/latest/download/llmcar-2.2.0-arm64.apk
```