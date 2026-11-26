#!/usr/bin/env python3
"""
Генерирует озвучку упражнений через ElevenLabs и кладёт mp3 в app/src/main/assets/voice/.
Ключ НЕ хранится в репозитории и не попадает в APK — только эти mp3.

  export ELEVENLABS_API_KEY=...        # ключ из кабинета ElevenLabs
  python3 tools/generate_voice.py      # только недостающие файлы
  python3 tools/generate_voice.py --force

Голос можно поменять: ELEVENLABS_VOICE_ID=<id> (по умолчанию — Adam, мультиязычный).
"""
import json, os, sys, time, urllib.request, urllib.error, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app" / "src" / "main" / "assets"
OUT = ASSETS / "voice"
KEY = os.environ.get("ELEVENLABS_API_KEY")
VOICE = os.environ.get("ELEVENLABS_VOICE_ID", "pNInz6obpgDQGcFmaJgB")
MODEL = os.environ.get("ELEVENLABS_MODEL", "eleven_multilingual_v2")

def tts(text: str) -> bytes:
    url = f"https://api.elevenlabs.io/v1/text-to-speech/{VOICE}?output_format=mp3_22050_32"
    body = json.dumps({
        "text": text,
        "model_id": MODEL,
        "voice_settings": {"stability": 0.5, "similarity_boost": 0.75, "style": 0.3, "use_speaker_boost": True},
    }).encode()
    req = urllib.request.Request(url, data=body, method="POST", headers={
        "xi-api-key": KEY, "Content-Type": "application/json", "Accept": "audio/mpeg",
    })
    with urllib.request.urlopen(req, timeout=120) as r:
        return r.read()

def main():
    if not KEY:
        sys.exit("Нет ELEVENLABS_API_KEY")
    force = "--force" in sys.argv
    data = json.loads((ASSETS / "exercises.json").read_text(encoding="utf-8"))
    jobs = [(e["id"], e["voice"]) for e in data["exercises"]] + list(data["phrases"].items())
    OUT.mkdir(parents=True, exist_ok=True)
    for key, text in jobs:
        f = OUT / f"{key}.mp3"
        if f.exists() and not force:
            continue
        for attempt in range(4):
            try:
                f.write_bytes(tts(text))
                print("ok ", key, f.stat().st_size // 1024, "KB")
                break
            except urllib.error.HTTPError as e:
                print("err", key, e.code, e.read()[:200])
                if e.code in (401, 403):
                    sys.exit(1)
                time.sleep(2 ** attempt)

if __name__ == "__main__":
    main()
