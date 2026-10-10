"""YouTube search + stream resolution + downloads, bundled with yt-dlp via Chaquopy.

Every entry point takes and returns a JSON string so the Kotlin side never has to
navigate Chaquopy's dynamic object graph. Failures are returned as ``{"error": "..."}``
instead of raising, because an exception crossing the bridge loses the useful part of
yt-dlp's message.

Two traffic strategies are used:
  * Search always uses one stable desktop User-Agent - the search page parses embedded
    player config that rejects rotated/odd UAs, which is how "no results" usually creeps in.
  * Format resolution rotates a random device identity per call so the server sees
    different clients, and falls back through more permissive format strings, because a
    single client sometimes reports "Requested format is not available".
"""

import json
import os
import random
import time

# Search keeps one well-known desktop Chrome UA. Rotating it makes YouTube's search page
# return an empty result list far more often than rotating it helps.
SEARCH_UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/122.0.0.0 Safari/537.36"
)

# Client identities rotate per request for format resolution/downloads.
_DEVICES = [
    {
        "client": "android",
        "ua": "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
    },
    {
        "client": "ios",
        "ua": "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) "
        "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1",
    },
    {
        "client": "web",
        "ua": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    },
]

# Downloaded bytes are reported through a dict instead of yt-dlp's stdout so a second
# Kotlin thread can poll a single number instead of scraping logcat.
_PROGRESS = {}

_OPTS = {
    "quiet": True,
    "no_warnings": True,
    "noplaylist": True,
    "cachedir": False,
    "socket_timeout": 12,
    "retries": 1,
    "extractor_retries": 2,
    "sleep_interval_requests": 0.5,
    "geo_bypass": True,
    "format": "bestaudio/best",
}

# YouTube blocks some clients for some videos ("Requested format is not available" is the
# usual symptom). Trying the whole ladder underneath keeps playback alive in practice.
# mp4-capable clients first, because Android's decoder is happiest with them.
_CLIENTS = ["android", "ios", "web", "android_vr", "tv", "mweb", "web_music"]

# Stream URLs stay valid for a while; cache them so replaying a song is instant instead
# of another slow round trip to the extractor.
_RESOLVE_CACHE = {}
_RESOLVE_TTL = 900  # 15 minutes


def _cap_format(height):
    """The most specific height-capped muxed format selector for the given resolution.
    Prefers mp4 (Android decodes it everywhere), then any muxed stream."""
    base = "best[height<=%d]" % max(240, int(height))
    if height >= 2160:
        base = "best[height<=2160]"
    return "%s[ext=mp4]/%s/best" % (base, base)


def _device_opts(base_format=None):
    """Options with a randomly picked device UA; used for streams, not search."""
    device = random.choice(_DEVICES)
    opts = dict(_OPTS)
    if base_format:
        opts["format"] = base_format
    opts["http_headers"] = {"User-Agent": device["ua"]}
    return opts


def _run(func):
    try:
        return json.dumps(func())
    except Exception as exc:  # noqa: BLE001 - bridge wants the message, not a traceback
        return json.dumps({"error": str(exc) or type(exc).__name__})


def search(query, limit=25):
    """Flat search. A throttled endpoint answers with an empty playlist, so retry and,
    as a last resort, re-run without extract_flat (full extraction is heavier but often
    still passes through)."""

    def job():
        import yt_dlp

        url = "ytsearch%d:%s" % (int(limit), str(query))
        results = []
        last_flat = None
        for attempt in range(2):
            opts = dict(
                dict(_OPTS, extract_flat=True),
                http_headers={"User-Agent": SEARCH_UA},
            )
            try:
                with yt_dlp.YoutubeDL(opts) as ydl:
                    info = ydl.extract_info(url, download=False)
                entries = [e for e in (info.get("entries") or []) if e]
                if entries:
                    results = entries
                    break
            except Exception as exc:  # noqa: BLE001
                last_flat = exc
            time.sleep(1.2 * (attempt + 1))

        if not results:
            # Last resort: full extraction. Slower, but it refuses to give up quietly.
            try:
                opts = dict(
                    dict(_OPTS, extract_flat=False),
                    http_headers={"User-Agent": SEARCH_UA},
                )
                with yt_dlp.YoutubeDL(opts) as ydl:
                    info = ydl.extract_info(url, download=False)
                results = [e for e in (info.get("entries") or []) if e]
            except Exception as exc:  # noqa: BLE001
                last_flat = exc

        out = []
        for entry in results:
            if not entry:
                continue
            out.append(
                {
                    "id": entry.get("id"),
                    "title": entry.get("title") or "",
                    "channel": entry.get("channel") or entry.get("uploader") or "",
                    "duration": entry.get("duration"),
                    "thumbnail": entry.get("thumbnail")
                    or "https://i.ytimg.com/vi/%s/hqdefault.jpg" % entry.get("id", ""),
                    "views": entry.get("view_count"),
                }
            )
        if not out:
            return {"results": [], "note": "YouTube rate-limited this network" if last_flat is not None else ""}
        return {"results": out}

    return _run(job)


def _stream(target, base_format):
    """Pull a playable stream URL + headers. Falls back through more permissive format
    selectors AND the whole player-client ladder, so a single client's "Requested format
    is not available" never kills playback."""

    def job():
        import yt_dlp

        def attempt(opts, target_url):
            with yt_dlp.YoutubeDL(opts) as ydl:
                return ydl.extract_info(target_url, download=False)

        target_url = str(target)
        if not target_url.startswith(("http://", "https://")):
            target_url = "https://www.youtube.com/watch?v=%s" % target_url

        info = None
        last_error = None
        for fmt in (base_format, "best", "18"):
            for client in _CLIENTS:
                opts = dict(_device_opts(fmt), extract_flat=False)
                opts["extractor_args"] = {"youtube": {"player_client": [client]}}
                try:
                    info = attempt(opts, target_url)
                except Exception as exc:  # noqa: BLE001 - try the next client/format
                    last_error = exc
                    continue
                break
            if info is not None:
                break

        if info is None:
            raise RuntimeError("No playable stream (%s)" % (last_error or "unknown"))

        fmt = info.get("requested_formats") or []
        pick = next(
            (f for f in fmt if f.get("acodec") not in (None, "none")),
            None,
        )
        if pick is None:
            url = info.get("url")
            headers = info.get("http_headers") or {}
        else:
            url = pick.get("url")
            headers = pick.get("http_headers") or info.get("http_headers") or {}

        if not url:
            raise RuntimeError("No playable stream")

        return {
            "url": url,
            "headers": {str(k): str(v) for k, v in (headers or {}).items()},
            "id": info.get("id"),
            "title": info.get("title") or "",
            "channel": info.get("channel") or info.get("uploader") or "",
            "duration": info.get("duration"),
            "thumbnail": info.get("thumbnail") or "",
        }

    return _run(job)


def resolve(video_id, max_height=720, video_url=None):
    """Return a playable muxed (audio+video) stream up to [max_height], plus the headers
    Media3 needs to fetch it. Music gets its audio from the muxed stream, and the in-app
    player can attach a surface to the same stream to render the video when it has one.
    Answers from the short-lived cache make replays instant."""
    key = "v:%d:%s" % (int(max_height), str(video_id))
    hit = _RESOLVE_CACHE.get(key)
    if hit is not None and time.time() - hit[0] < _RESOLVE_TTL:
        return json.dumps(hit[1])

    target = video_url or "https://www.youtube.com/watch?v=%s" % video_id
    payload = json.loads(_stream(target, _cap_format(int(max_height))))
    if payload.get("url"):
        _RESOLVE_CACHE[key] = [time.time(), payload]
        if len(_RESOLVE_CACHE) > 32:
            _RESOLVE_CACHE.pop(next(iter(_RESOLVE_CACHE)))
    return json.dumps(payload)


def resolve_video(video_id, max_height=1080, video_url=None):
    """A muxed stream for the full-screen player, honouring the chosen resolution."""
    target = video_url or "https://www.youtube.com/watch?v=%s" % video_id
    return _stream(target, _cap_format(int(max_height)))


def download_progress(token):
    """Small JSON for the progress poll; the download thread keeps this updated."""
    return json.dumps(_PROGRESS.get(str(token)) or {"status": "unknown"})


def download(video_id, kind, token, outdir, max_height=720):
    """Download a song (kind=audio) or a video (kind=video) into outdir, reporting
    byte-level progress on _PROGRESS. The calling (Kotlin) side moves the result into
    public storage afterwards.

    Video uses single-file mp4 formats only, because there is no ffmpeg on the device to
    merge separate video+audio tracks; it falls back across the whole client ladder."""

    def job():
        import yt_dlp

        if kind == "video":
            cap = int(max_height)
            formats = (
                "best[height<=%d][ext=mp4]" % cap,
                "best[ext=mp4]",
                "18",
                "best",
            )
        else:
            formats = ("bestaudio/best", "bestaudio", "best")

        url = "https://www.youtube.com/watch?v=%s" % video_id
        info = None
        last_error = None
        for fmt in formats:
            for client in _CLIENTS:
                opts = dict(
                    _device_opts(fmt),
                    skip_download=False,
                    outtmpl=os.path.join(str(outdir), "%(title).120s-%(id)s.%(ext)s"),
                    progress_hooks=[_progress_hook(str(token))],
                )
                opts["extractor_args"] = {"youtube": {"player_client": [client]}}
                try:
                    with yt_dlp.YoutubeDL(opts) as ydl:
                        info = ydl.extract_info(url, download=True)
                except Exception as exc:  # noqa: BLE001 - try the next client/format
                    _PROGRESS[str(token)] = {"status": "error", "error": str(exc)}
                    last_error = exc
                    continue
                break
            if info is not None:
                break

        if info is None:
            raise RuntimeError("Download failed (%s)" % (last_error or "unknown"))

        requested = info.get("requested_downloads") or []
        path = requested[0].get("filepath") if requested else None
        if not path or not os.path.exists(path):
            _PROGRESS[str(token)] = {"status": "error", "error": "Download produced no file"}
            raise RuntimeError("Download produced no file")

        _PROGRESS[str(token)] = {
            "status": "finished",
            "path": path,
            "title": info.get("title") or "",
        }
        return {
            "path": path,
            "title": info.get("title") or "",
            "ext": os.path.splitext(path)[1].lstrip(".") or "m4a",
        }

    return _run(job)


def _progress_hook(token):
    def hook(data):
        status = data.get("status")
        total = data.get("total_bytes") or data.get("total_bytes_estimate")
        entry = {
            "status": status,
            "downloaded": data.get("downloaded_bytes", 0),
            "total": total or 0,
            "path": (data.get("filename") or "") if status == "finished" else "",
        }
        if status == "error":
            entry["error"] = str(data.get("error") or "download failed")
        _PROGRESS[token] = entry

    return hook


def version():
    try:
        import yt_dlp

        return yt_dlp.version.__version__
    except Exception as exc:  # noqa: BLE001
        return "error: %s" % exc