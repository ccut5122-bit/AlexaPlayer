"""YouTube search + stream resolution + downloads, bundled with yt-dlp via Chaquopy.

Every entry point takes and returns a JSON string so the Kotlin side never has to
navigate Chaquopy's dynamic object graph. Failures are returned as
``{"error": "..."}`` instead of raising, because an exception crossing the bridge
loses the useful part of yt-dlp's message.

Rate limits: YouTube throttles by IP but also by *client* - each request below picks
a random device/client and a fresh User-Agent, so the server sees different devices
rolling past instead of one machine crawling. That dodges client-fingerprint bans;
a true IP change needs a proxy which an on-device player can't honestly offer.
"""

import json
import os
import random
import time

# Client identities rotate per request. yt-dlp's "player_client" extractor arg plus a
# matching User-Agent make each call look like a different device.
_DEVICES = [
    {
        "client": "android",
        "ua": "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
    },
    {
        "client": "android_vr",
        "ua": "Mozilla/5.0 (Linux; Android 14; VR) AppleWebKit/537.36 "
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
    {
        "client": "tv",
        "ua": "Mozilla/5.0 (SMART-TV; Linux; Tizen 6.0) AppleWebKit/537.36 "
        "(KHTML, like Gecko) 69.1.2 TV Safari/537.36",
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
    "socket_timeout": 10,
    "retries": 1,
    "extractor_retries": 2,
    "sleep_interval_requests": 0.5,
    "geo_bypass": True,
    # Media3 only needs one clean audio stream; muxed video would just waste data.
    "format": "bestaudio[ext=m4a]/bestaudio/best",
}


def _fresh_opts(base_format=None):
    """Build a per-call option set with a randomly picked device identity."""
    device = random.choice(_DEVICES)
    opts = dict(_OPTS)
    if base_format:
        opts["format"] = base_format
    opts["http_headers"] = dict(opts.get("http_headers") or {})
    opts["http_headers"]["User-Agent"] = device["ua"]
    opts["extractor_args"] = {
        "youtube": ["player_client=%s" % device["client"]],
    }
    return opts


def _run(func):
    try:
        return json.dumps(func())
    except Exception as exc:  # noqa: BLE001 - bridge wants the message, not a traceback
        return json.dumps({"error": str(exc) or type(exc).__name__})


def search(query, limit=25):
    """Flat search: ids, titles, thumbnails and durations, without video streams."""

    def job():
        import yt_dlp

        opts = dict(_fresh_opts(), extract_flat=True, force_generic_extractor=False)
        url = "ytsearch%d:%s" % (int(limit), str(query))
        results = []
        # YouTube answers a throttled search with an empty playlist rather than an error,
        # so treat "nothing came back" as a retryable condition in its own right.
        for attempt in range(3):
            with yt_dlp.YoutubeDL(opts) as ydl:
                info = ydl.extract_info(url, download=False)
            results = [e for e in (info.get("entries") or []) if e]
            if results:
                break
            time.sleep(1.5 * (attempt + 1))
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
        return {"results": out}

    return _run(job)


def _stream(target, base_format):
    """Pull the chosen stream URL + headers for a video."""

    def job():
        import yt_dlp

        # Must not rebind `target` here - Python would treat it as local and this raises
        # "cannot access local variable 'target'" (UnboundLocalError). Read into a fresh name.
        target_url = str(target)
        if not target_url.startswith(("http://", "https://")):
            target_url = "https://www.youtube.com/watch?v=%s" % target_url

        opts = dict(_fresh_opts(base_format), extract_flat=False)
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(target_url, download=False)

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


def resolve(video_id, video_url=None):
    """Return the playable audio URL plus the headers Media3 needs to fetch it."""
    target = video_url or "https://www.youtube.com/watch?v=%s" % video_id
    return _stream(target, "bestaudio[ext=m4a]/bestaudio/best")

def resolve_video(video_id, video_url=None):
    """Return a playable muxed (audio+video) URL for full-screen playback."""
    target = video_url or "https://www.youtube.com/watch?v=%s" % video_id
    return _stream(target, "best[height<=1080][ext=mp4]/best[ext=mp4]/best")


def download_progress(token):
    """Small JSON for the progress poll; the download thread keeps this updated."""
    return json.dumps(_PROGRESS.get(str(token)) or {"status": "unknown"})


def download(video_id, kind, token, outdir):
    """Download a song (kind=audio -> best audio) or a video (kind=video -> muxed mp4)."""

    def job():
        import yt_dlp

        if kind == "video":
            base_format = "best[height<=1080][ext=mp4]/best[ext=mp4]/best"
            ext_hint = "mp4"
        else:
            base_format = "bestaudio[ext=m4a]/bestaudio/best"
            ext_hint = "m4a"
        opts = dict(
            _fresh_opts(base_format),
            skip_download=False,
            outtmpl=os.path.join(str(outdir), "%(title).120s-%(id)s.%(ext)s"),
            progress_hooks=[_progress_hook(str(token))],
        )
        url = "https://www.youtube.com/watch?v=%s" % video_id
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=True)

        requested = info.get("requested_downloads") or []
        path = requested[0].get("filepath") if requested else None
        if not path or not os.path.exists(path):
            _PROGRESS[str(token)] = {"status": "error", "error": "Download produced no file"}
            raise RuntimeError("Download produced no file")

        _PROGRESS[str(token)] = {"status": "finished", "path": path, "title": info.get("title") or ""}
        return {
            "path": path,
            "title": info.get("title") or "",
            "ext": os.path.splitext(path)[1].lstrip(".") or ext_hint,
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