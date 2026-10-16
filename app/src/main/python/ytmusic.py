"""YouTube search + stream resolution + downloads, bundled with yt-dlp via Chaquopy.

Every entry point takes and returns a JSON string so the Kotlin side never has to
navigate Chaquopy's dynamic object graph. Failures are returned as ``{"error": "..."}``
instead of raising, because an exception crossing the bridge loses the useful part of
yt-dlp's message.

Traffic strategies:
  * Search always uses one stable desktop User-Agent - the search page parses embedded
    player config that rejects rotated/odd UAs, which is how "no results" usually creeps in.
  * Stream/download extraction never forces a YouTube ``player_client`` up front: the
    default set yt-dlp tunes for this JS-less environment is tried first, then a ladder of
    explicit clients. Forcing a client that now needs a PO Token is what causes the classic
    "Requested format is not available" (or a silent no-playback) on newer yt-dlp builds.
"""

import json
import os
import time

# Search keeps one well-known desktop Chrome UA. Rotating it makes YouTube's search page
# return an empty result list far more often than rotating it helps.
SEARCH_UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/122.0.0.0 Safari/537.36"
)

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

# Player clients are only pinned as a *fallback*. The default set yt-dlp picks for the
# running environment (JS-less here, so it leans on Android/iOS clients that do not need a
# PO Token) is always tried first - forcing a client that now requires a PO Token, or one
# that yt-dlp has since removed, is exactly what turns "Every song fails" into "Requested
# format is not available". These names are validated by yt-dlp itself, so an unknown one
# merely wastes a single attempt instead of breaking the loop.
_CLIENTS = [
    "default",
    "tv",
    "android_vr",
    "ios",
    "ios_downgraded",
    "mweb",
    "tv_simply",
    "web_safari",
    "android",
    "web",
]

# Stream URLs stay valid for a while; cache them so replaying a song is instant instead
# of another slow round trip to the extractor.
_RESOLVE_CACHE = {}
_RESOLVE_TTL = 900  # 15 minutes

# Autocomplete answers for typed prefixes barely change, so keep them around briefly to make
# backspacing and re-typing instant instead of another round trip.
_SUGGEST_CACHE = {}
_SUGGEST_TTL = 600  # 10 minutes


def _cap_format(height):
    """The most specific height-capped muxed format selector for the given resolution.
    Prefers mp4 (Android decodes it everywhere), then any muxed stream."""
    base = "best[height<=%d]" % max(240, int(height))
    if height >= 2160:
        base = "best[height<=2160]"
    return "%s[ext=mp4]/%s/best" % (base, base)


def _device_opts(base_format=None):
    """Options shared by stream + download extraction.

    No User-Agent is forced here: yt-dlp picks the right UA per player client, and the
    extractor returns the exact ``http_headers`` it signed the URL with, which the player
    replays verbatim. Forcing our own UA is what makes the CDN answer the later media
    request with 403."""
    opts = dict(_OPTS)
    if base_format:
        opts["format"] = base_format
    return opts


def _apply_client(opts, client):
    """Clone [opts] configured to extract with a single YouTube player client."""
    attempt = dict(opts)
    if client and client != "default":
        attempt["extractor_args"] = {"youtube": {"player_client": [client]}}
    return attempt


def _run(func):
    try:
        return json.dumps(func())
    except Exception as exc:  # noqa: BLE001 - bridge wants the message, not a traceback
        return json.dumps({"error": str(exc) or type(exc).__name__})


def _pick_audio(info):
    """Best playable (url, headers) from an extracted info dict. Prefers the requested
    format that actually carries audio; falls back to the single muxed file."""
    formats = info.get("requested_formats") or []
    pick = next((f for f in formats if f.get("acodec") not in (None, "none")), None)
    if pick is None:
        pick = info
    url = pick.get("url") or info.get("url")
    if not url:
        return None, {}
    headers = pick.get("http_headers") or info.get("http_headers") or {}
    return url, headers


def _pick_streams(info):
    """Split an extraction into (video, audio) format dicts. For a site that muxes audio
    and video into one file, only [video] is returned and [audio] is None."""
    formats = info.get("requested_formats") or []
    video = next((f for f in formats if f.get("vcodec") not in (None, "none")), None)
    audio = next((f for f in formats if f.get("acodec") not in (None, "none")), None)
    if video is None and audio is None and info.get("url"):
        return info, None
    return video, audio


def _dash_payload(info):
    """Shape an extraction into the video_url/video_headers[/audio_url] payload Media3
    merges on the fly. Falls back to a single (possibly muxed) file when a site has no
    separate audio track, which is the norm for Instagram."""
    if info is None:
        return {}
    video, audio = _pick_streams(info)
    if video is not None and audio is not None:
        return {
            "video_url": video.get("url"),
            "video_headers": {str(k): str(v) for k, v in (video.get("http_headers") or {}).items()},
            "audio_url": audio.get("url"),
            "audio_headers": {str(k): str(v) for k, v in (audio.get("http_headers") or {}).items()},
            "id": info.get("id"),
            "title": info.get("title") or "",
            "channel": info.get("channel") or info.get("uploader") or "",
            "duration": info.get("duration"),
            "thumbnail": info.get("thumbnail") or "",
        }
    # One single file (or only one of the two). Play it as-is.
    url, headers = _pick_audio(info)
    if not url:
        return {}
    return {
        "video_url": url,
        "video_headers": {str(k): str(v) for k, v in (headers or {}).items()},
        "title": info.get("title") or "",
        "thumbnail": info.get("thumbnail") or "",
        "merged": False,
    }


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


def suggest(query, limit=10):
    """YouTube's own search autocomplete (the dropdown suggestions), via the public
    complete/search endpoint. Cheap and fast compared to a full search.

    The endpoint only answers for certain ``client`` values: ``firefox`` now returns
    HTTP 400, and ``youtube`` wraps its JSON in ``window.google.ac.h(...)`` with each
    entry shaped like ``[text, score, ...]``. Several clients are tried so a single
    one being blocked upstream never means "no suggestions"."""

    def parse(raw):
        text = raw.strip()
        # Strip a JSONP callback wrapper like window.google.ac.h( ... ).
        if "(" in text and text.endswith(")"):
            text = text[text.find("(") + 1:text.rfind(")")]
        data = json.loads(text)
        items = data[1] if isinstance(data, list) and len(data) > 1 else []
        out = []
        for x in items:
            if isinstance(x, str):
                out.append(x)
            elif isinstance(x, list) and x and isinstance(x[0], str):
                out.append(x[0])
        return out

    def job():
        import urllib.parse
        import urllib.request

        q = str(query or "").strip()
        if not q:
            return {"suggestions": []}

        # Every prefix the user types is requested as they type, so a tiny TTL cache turns
        # backspacing and repeated prefixes into an instant response.
        key = "%s\n%s" % (q.lower(), int(limit))
        hit = _SUGGEST_CACHE.get(key)
        if hit and (time.time() - hit[0]) < _SUGGEST_TTL:
            return {"suggestions": hit[1]}

        quoted = urllib.parse.quote(q)
        for client in ("youtube", "youtube-reduced", "firefox"):
            url = (
                "https://suggestqueries-clients6.youtube.com/complete/search"
                "?ds=yt&hl=en&client=%s&q=%s" % (client, quoted)
            )
            try:
                req = urllib.request.Request(url, headers={"User-Agent": SEARCH_UA})
                with urllib.request.urlopen(req, timeout=4) as resp:
                    out = parse(resp.read().decode("utf-8", "replace"))
                if out:
                    items = [str(x) for x in out][: int(limit)]
                    _SUGGEST_CACHE[key] = (time.time(), items)
                    return {"suggestions": items}
            except Exception:  # noqa: BLE001 - try the next client
                continue
        return {"suggestions": []}

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
        url = None
        headers = {}
        last_error = None
        for fmt in (base_format, "best", "18"):
            for client in _CLIENTS:
                opts = dict(_device_opts(fmt), extract_flat=False)
                try:
                    candidate = attempt(_apply_client(opts, client), target_url)
                except Exception as exc:  # noqa: BLE001 - try the next client/format
                    last_error = exc
                    continue
                found_url, found_headers = _pick_audio(candidate)
                if found_url:
                    info, url, headers = candidate, found_url, found_headers
                    break
            if url:
                break

        if not url:
            raise RuntimeError("No playable stream (%s)" % (last_error or "unknown"))

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


def resolve_video_dash(video_id, max_height=2160):
    """Separate video-only and audio-only streams so Media3 can merge them on the fly.

    YouTube only offers muxed (single-file) video up to 720p; everything above that -
    1080p, 1440p, 4K - exists only as a video-only track. Returning both lets the player
    combine them (MergingMediaSource) and actually show the chosen resolution.

    Falls back to a plain muxed stream when a separate pair is unavailable."""

    def job():
        target = "https://www.youtube.com/watch?v=%s" % video_id
        h = max(240, int(max_height))
        vfmt = "bestvideo[height<=%d][ext=mp4]/bestvideo[height<=%d]/best[height<=%d]" % (h, h, h)
        afmt = "bestaudio[ext=m4a]/bestaudio/best"

        try:
            info = _extract_streams(target, "%s+%s" % (vfmt, afmt))
        except Exception:  # noqa: BLE001 - fall back to a single file below
            info = None
        payload = _dash_payload(info) if info is not None else {}
        if payload.get("video_url"):
            return payload
        # Last resort: whatever single file we can get.
        return json.loads(_stream(target, _cap_format(h)))

    return _run(job)


def resolve_url(url, max_height=1080):
    """Playable stream(s) for any pasted link (YouTube, Instagram, ...). Returns a
    video-only + audio-only pair when the site separates them, otherwise a single muxed
    URL. Used by the Downloader tab's play button."""

    def job():
        link = str(url or "").strip()
        h = max(240, int(max_height))
        vfmt = "bestvideo[height<=%d][ext=mp4]/bestvideo[height<=%d]/best[height<=%d]" % (h, h, h)
        afmt = "bestaudio[ext=m4a]/bestaudio/best"
        for fmt in ("%s+%s" % (vfmt, afmt), _cap_format(h), "best", "18"):
            try:
                info = _extract_streams(link, fmt)
            except Exception:  # noqa: BLE001 - try the next format
                info = None
            if info is None:
                continue
            payload = _dash_payload(info)
            if payload.get("video_url"):
                return payload
        return json.loads(_stream(link, _cap_format(h)))

    return _run(job)


def fetch(url, max_height=1080):
    """Metadata for a pasted link so the Downloader can show a preview and play button
    without downloading anything: title, thumbnail, duration, uploader, whether it has a
    video track, and the distinct video heights on offer."""

    def job():
        try:
            info = _extract_streams(str(url), "best")
        except Exception as exc:  # noqa: BLE001
            raise RuntimeError(str(exc) or "Could not read that link")

        heights = set()
        for f in info.get("formats") or []:
            height = f.get("height")
            if height and f.get("vcodec") not in (None, "none"):
                heights.add(int(height))

        return {
            "url": info.get("webpage_url") or str(url),
            "title": info.get("title") or "",
            "uploader": info.get("uploader") or info.get("channel") or "",
            "thumbnail": info.get("thumbnail") or "",
            "duration": info.get("duration") or 0,
            "is_video": any(
                f.get("vcodec") not in (None, "none") for f in (info.get("formats") or [])
            ) or info.get("vcodec") not in (None, "none"),
            "heights": sorted(heights),
        }

    return _run(job)


def _extract_streams(url, base_format):
    """Extract and split video/audio for any URL using the default client first."""
    import yt_dlp

    info = None
    last_error = None
    opts = dict(_device_opts(base_format), extract_flat=False)
    for client in _CLIENTS:
        try:
            with yt_dlp.YoutubeDL(_apply_client(opts, client)) as ydl:
                info = ydl.extract_info(str(url), download=False)
            break
        except Exception as exc:  # noqa: BLE001 - try the next client
            info = None
            last_error = exc
            continue
    if info is None:
        raise RuntimeError(str(last_error) or "Extraction failed")

    entries = [e for e in (info.get("entries") or []) if e]
    if entries:
        info = entries[0]
    return info



def _is_instagram(url):
    return "instagram.com" in str(url).lower()


def download_url(url, kind, token, outdir, max_height=1080):
    """Download any supported link (YouTube, Instagram, ...) into outdir.

    kind: "audio" grabs the best audio track, "video"/"auto" grabs the best single file
    up to [max_height]. Instagram photos have no video track, so an image URL is pulled
    from the page as a last resort. Progress goes to _PROGRESS under [token]."""

    def job():
        import yt_dlp

        link = str(url or "").strip()
        if not link.startswith(("http://", "https://")):
            raise RuntimeError("Enter a full http(s) link")

        cap = max(240, int(max_height))
        if kind == "audio":
            formats = ("bestaudio/best", "bestaudio", "best")
        else:
            formats = (
                "best[height<=%d][ext=mp4]" % cap,
                "best[height<=%d]" % cap,
                "best[ext=mp4]",
                "best",
            )

        info = None
        last_error = None
        for fmt in formats:
            for client in _CLIENTS:
                opts = _apply_client(
                    dict(
                        _device_opts(fmt),
                        skip_download=False,
                        noplaylist=True,
                        outtmpl=os.path.join(str(outdir), "%(title).120s-%(id)s.%(ext)s"),
                        progress_hooks=[_progress_hook(str(token))],
                    ),
                    client,
                )
                try:
                    with yt_dlp.YoutubeDL(opts) as ydl:
                        info = ydl.extract_info(link, download=True)
                except Exception as exc:  # noqa: BLE001 - try the next client/format
                    _PROGRESS[str(token)] = {"status": "error", "error": str(exc)}
                    last_error = exc
                    info = None
                    continue
                break
            if info is not None:
                break

        if info is None:
            if _is_instagram(link):
                image = _download_instagram_image(link, str(outdir), str(token))
                if image:
                    return image
            raise RuntimeError("Download failed (%s)" % (last_error or "unknown"))

        requested = info.get("requested_downloads") or []
        path = requested[0].get("filepath") if requested else info.get("filepath")
        if not path or not os.path.exists(path):
            raise RuntimeError("Download produced no file")

        _PROGRESS[str(token)] = {"status": "finished", "path": path, "title": info.get("title") or ""}
        return {
            "path": path,
            "title": info.get("title") or "",
            "ext": os.path.splitext(path)[1].lstrip(".") or "mp4",
        }

    return _run(job)


def _download_instagram_image(link, outdir, token):
    """Instagram photos: read the page's og:image and save it. Best effort - returns None
    when the page cannot be parsed."""
    try:
        import re
        import urllib.request

        req = urllib.request.Request(link, headers={"User-Agent": SEARCH_UA})
        with urllib.request.urlopen(req, timeout=10) as resp:
            html = resp.read().decode("utf-8", "replace")
        match = re.search(r'<meta[^>]+property="og:image"[^>]+content="([^"]+)"', html)
        if not match:
            return None
        image_url = match.group(1).replace("&amp;", "&")
        os.makedirs(outdir, exist_ok=True)
        dest = os.path.join(outdir, "instagram-%d.jpg" % int(time.time()))
        req2 = urllib.request.Request(image_url, headers={"User-Agent": SEARCH_UA})
        with urllib.request.urlopen(req2, timeout=15) as img, open(dest, "wb") as out:
            out.write(img.read())
        _PROGRESS[token] = {"status": "finished", "path": dest, "title": "Instagram photo"}
        return {"path": dest, "title": "Instagram photo", "ext": "jpg"}
    except Exception:  # noqa: BLE001 - best effort
        return None


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
                opts = _apply_client(
                    dict(
                        _device_opts(fmt),
                        skip_download=False,
                        outtmpl=os.path.join(str(outdir), "%(title).120s-%(id)s.%(ext)s"),
                        progress_hooks=[_progress_hook(str(token))],
                    ),
                    client,
                )
                try:
                    with yt_dlp.YoutubeDL(opts) as ydl:
                        info = ydl.extract_info(url, download=True)
                except Exception as exc:  # noqa: BLE001 - try the next client/format
                    _PROGRESS[str(token)] = {"status": "error", "error": str(exc)}
                    last_error = exc
                    info = None
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