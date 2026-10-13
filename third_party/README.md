# Сторонние зависимости

## llama.cpp (LLM runtime)

Собирается автоматически через GitHub Actions (`.github/scripts/build-llama-server.sh`).

Локальная сборка:

```bash
git clone https://github.com/ggerganov/llama.cpp third_party/llama.cpp
cd third_party/llama.cpp
cmake -B build-android \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-28 \
  -DLLAMA_BUILD_SERVER=ON \
  -DBUILD_SHARED_LIBS=ON
cmake --build build-android --config Release -j
```

Файлы `llama-server` и `*.so` попадут в `app/src/main/assets/`.

## Vosk (offline ASR)

Модель `vosk-model-small-ru-0.22` (~45 МБ) скачивается автоматически через
`.github/scripts/download-vosk-model.sh`.

Локально:

```bash
mkdir -p app/src/main/assets
cd /tmp
curl -LO https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip
unzip vosk-model-small-ru-0.22.zip
mv vosk-model-small-ru-0.22 $OLDPWD/app/src/main/assets/
```

## Picovoice Porcupine (offline wake-word)

Для русского wake-word:

1. Зарегистрируйтесь на https://console.picovoice.ai/
2. Создайте Custom Keyword на русском языке
3. Скачайте `.ppn`-файл и русскую модель `porcupine_params_ru.pv`
4. Положите в `app/src/main/assets/porcupine/`
5. В настройках приложения укажите AccessKey и пути к файлам

## Модели LLM (GGUF)

Формат **GGUF**. Разместите в:

- `/sdcard/Models/`
- `/sdcard/Download/`
- `Android/data/com.example.llmcar/files/models/`
- Любая папка, добавленная через SAF в настройках

### Рекомендуемые модели

| Модель | Размер | RAM | Профиль |
|---|---|---|---|
| Qwen 2.5 0.5B Q4_K_M | 350 MB | ~600 MB | low |
| Qwen 2.5 0.5B Q4_K_M | 350 MB | ~800 MB | balanced |
| Qwen 2.5 1.5B Q4_K_M | 1 GB | ~1.5 GB | balanced |
| Qwen 2.5 1.5B Q5_K_M | 1.2 GB | ~2 GB | performance |