#!/usr/bin/env python3
"""
Regression guard for Boost's audio-focus bytecode patch.

Some Boost start methods matched by fingerprint do not contain the exact
ExoPlayer setPlayWhenReady call at patch time. The patch should skip those
methods instead of failing the whole patch bundle.
"""

from pathlib import Path


PROJECT_DIR = Path(__file__).resolve().parent.parent
PATCH_FILE = (
    PROJECT_DIR
    / "patches/src/main/kotlin/app/morphe/patches/reddit/customclients/"
    "boostforreddit/fix/audiofocus/RequestVideoAudioFocusPatch.kt"
)


def main() -> None:
    source = PATCH_FILE.read_text()

    assert "check(playIndices.isNotEmpty())" not in source, (
        "audio-focus injection must not fail when a matched method has no "
        "direct ExoPlayer setPlayWhenReady call"
    )

    empty_guard = "if (playIndices.isEmpty()) {\n        return\n    }"
    assert empty_guard in source, (
        "audio-focus injection should explicitly skip methods without "
        "setPlayWhenReady calls"
    )

    assert "Lcom/google/android/exoplayer2/w1;" in source, (
        "Boost's ExoPlayer playWhenReady calls can reference the Player "
        "interface descriptor"
    )
    assert "Opcode.INVOKE_INTERFACE" in source, (
        "Boost's ExoPlayer playWhenReady calls can use invoke-interface"
    )


if __name__ == "__main__":
    main()
