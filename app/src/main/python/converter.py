"""YouTube link -> MP3, running inside the app via Chaquopy.

yt-dlp downloads the best audio-only stream; PyAV (FFmpeg bundled in the wheel)
re-encodes it to MP3 with libmp3lame. `cb` is a Kotlin object with
onProgress(percent: Double, status: String).
"""
import os

import av
import yt_dlp


def _encode_mp3(src, dst, kbps, title, artist, cb):
    try:
        av.codec.Codec("libmp3lame", "w")
    except Exception as e:  # noqa: BLE001
        raise RuntimeError("This build's FFmpeg has no MP3 encoder (libmp3lame): %s" % e)

    inp = av.open(src)
    out = av.open(dst, "w", format="mp3")
    try:
        s_in = inp.streams.audio[0]
        s_out = out.add_stream("libmp3lame", rate=44100)
        s_out.bit_rate = kbps * 1000
        if title:
            out.metadata["title"] = title
        if artist:
            out.metadata["artist"] = artist

        resampler = av.AudioResampler(format="s16p", layout="stereo", rate=44100)
        total = (inp.duration or 0) / 1_000_000.0

        for frame in inp.decode(s_in):
            for rf in resampler.resample(frame):
                for pkt in s_out.encode(rf):
                    out.mux(pkt)
            if total and frame.time is not None:
                cb.onProgress(82.0 + 17.0 * min(frame.time / total, 1.0), "Converting to MP3…")

        try:
            for rf in resampler.resample(None):
                for pkt in s_out.encode(rf):
                    out.mux(pkt)
        except Exception:  # older PyAV: no flush support
            pass
        for pkt in s_out.encode(None):
            out.mux(pkt)
    finally:
        out.close()
        inp.close()


def convert(url, out_dir, kbps, cb):
    os.makedirs(out_dir, exist_ok=True)

    def hook(d):
        if d.get("status") == "downloading":
            total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
            done = d.get("downloaded_bytes") or 0
            pct = (done / total * 80.0) if total else 0.0
            cb.onProgress(pct, "Downloading audio…")
        elif d.get("status") == "finished":
            cb.onProgress(80.0, "Download finished")

    opts = {
        "format": "bestaudio[ext=m4a]/bestaudio/best",
        "outtmpl": os.path.join(out_dir, "%(title).120B.%(ext)s"),
        "noplaylist": True,
        "quiet": True,
        "no_warnings": True,
        "windowsfilenames": True,
        "progress_hooks": [hook],
    }
    with yt_dlp.YoutubeDL(opts) as ydl:
        info = ydl.extract_info(url, download=True)
        src = ydl.prepare_filename(info)

    title = info.get("track") or info.get("title") or ""
    artist = (info.get("artist") or info.get("uploader") or "").replace(" - Topic", "")

    cb.onProgress(82.0, "Converting to MP3…")
    dst = os.path.splitext(src)[0] + ".mp3"
    try:
        _encode_mp3(src, dst, int(kbps), title, artist, cb)
    finally:
        if os.path.exists(src) and src != dst:
            os.remove(src)
    cb.onProgress(100.0, "Done")
    return dst
