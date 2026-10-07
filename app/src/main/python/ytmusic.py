"""YouTube search + stream resolution, bundled with yt-dlp via Chaquopy.

Every entry point takes and returns a JSON string so the Kotlin side never has to
navigate Chaquopy's dynamic object graph. Failures are returned as
``{"error": "..."}`` instead of raising, because an exception crossing the bridge
loses the useful part of yt-dlp's message.
"""

import json
import time

# Keeping yt-dlp quiet matters: Chaquopy captures stdout/stderr into logcat and a
# wall of download progress just hides the line that explains a real failure.
_OPTS = {
    "quiet": True,
    "no_warnings": True,
    "noplaylist": True,
    "skip_download": True,
    "cachedir": False,
    "socket_timeout": 15,
    "retries": 2,
    "extractor_retries": 3,
    "sleep_interval_requests": 1,
    "geo_bypass": True,
    # Media3 only needs one clean audio stream; muxed video would just waste data.
    "format": "bestaudio[ext=m4a]/bestaudio/best",
    "http_headers": {
        "User-Agent": "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36",
    },
}


def _run(func):
    try:
        return json.dumps(func())
    except Exception as exc:  # noqa: BLE001 - the bridge wants the message, not the traceback
        return json.dumps({"error": str(exc) or type(exc).__name__})


def search(query, limit=25):
    """Flat search: ids, titles, thumbnails and durations, without video streams."""

    def job():
        import yt_dlp

        opts = dict(_OPTS, extract_flat=True, force_generic_extractor=False)
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


def resolve(video_id, video_url=None):
    """Return the playable audio URL plus the headers Media3 needs to fetch it."""

    def job():
        import yt_dlp

        target = video_url or video_id
        if not str(target).startswith(("http://", "https://")):
            target = "https://www.youtube.com/watch?v=%s" % target

        opts = dict(_OPTS, extract_flat=False, format_sort=None)
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(target, download=False)

        fmt = info.get("requested_formats") or []
        audio = next(
            (f for f in fmt if f.get("acodec") not in (None, "none")),
            None,
        )
        if audio is None:
            url = info.get("url")
            headers = info.get("http_headers") or {}
        else:
            url = audio.get("url")
            headers = audio.get("http_headers") or info.get("http_headers") or {}

        if not url:
            raise RuntimeError("No playable audio stream")

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


def version():
    try:
        import yt_dlp

        return yt_dlp.version.__version__
    except Exception as exc:  # noqa: BLE001
        return "error: %s" % exc
