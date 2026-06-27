# Boost Video Audio Focus Design

## Goal

Add a Boost for Reddit patch that requests Android audio focus whenever Boost starts video playback, so video audio has a better chance of playing over the active car or Android Auto audio route while the app is being used on the phone.

The target behavior is phone-side playback. This design does not try to make Boost appear as an Android Auto app on the car screen.

## Context

The existing patcheddit Boost work is organized as Kotlin patch definitions under `patches/src/main/kotlin/app/morphe/patches/reddit/customclients/boostforreddit` and Java extension code under `extensions/boostforreddit`.

Recent Boost patches already use this pattern:

- Kotlin patches locate and modify Boost bytecode.
- Java extension helpers contain app-facing runtime logic.
- Release validation depends on GitHub Actions because the local Gradle build cannot resolve the private Morphe patch plugin.

Android's car media surface is separate from audio focus. Android Auto media apps are service/session-backed integrations, while `requestAudioFocus()` is the normal playback mechanism for coordinating audio priority with other apps and routes.

Relevant Android documentation:

- https://developer.android.com/media/optimize/audio-focus
- https://developer.android.com/training/cars/media

## Selected Approach

Implement a focused audio-focus shim for Boost video playback.

The patch will add a small extension helper, tentatively named `BoostAudioFocus`, and inject calls into Boost's video playback start and stop paths. When a Boost video starts playing, the injected code calls `requestVideoFocus(context)`. When playback pauses, stops, finishes, or the media viewer is destroyed, the injected code calls `abandonVideoFocus()`.

Audio focus should be requested any time a video starts playing. It should not wait for the user to unmute first.

## Alternatives Considered

### ExoPlayer-native handling

If Boost's video paths are mostly ExoPlayer, the patch could set ExoPlayer audio attributes and enable built-in focus handling. This is clean where it applies, but it may miss older `MediaPlayer` or custom video-view paths. It is also more dependent on the exact internal player objects Boost uses.

### Android Auto media/news integration

Boost could theoretically be patched toward a `MediaBrowserService`/`MediaSession` style integration. That is not appropriate for the first version because it changes the app surface rather than just playback behavior, requires a browse/playback model Android Auto can render, and is much more invasive than the requested phone-side playback behavior.

## Runtime Behavior

On Android O and newer, `BoostAudioFocus` will use `AudioFocusRequest` with media-style `AudioAttributes`:

- `USAGE_MEDIA`
- `CONTENT_TYPE_MOVIE`
- `AUDIOFOCUS_GAIN`

On older Android versions, it will use the legacy `AudioManager.requestAudioFocus` API.

The helper will keep enough state to abandon the same active focus request safely. Repeated video-start calls should be idempotent and should not stack multiple active requests.

If the focus request fails or throws, Boost should keep its existing behavior. The helper may log the failure for debugging, but it must not crash playback or the app.

## Patch Scope

The first implementation should target Boost's native video playback paths, especially Reddit-hosted video playback and full-screen media viewers.

Silent animated GIF display does not need audio focus. Giphy or GIF viewer paths should only receive focus hooks if the underlying Boost path is actually playing video with possible audio.

The patch does not add car-screen controls, app category spoofing, background playback, notification controls, playlist browsing, or Android Auto browse UI.

## Expected Files

Likely implementation files:

- `extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/BoostAudioFocus.java`
- A new Kotlin patch under `patches/src/main/kotlin/app/morphe/patches/reddit/customclients/boostforreddit/fix/audiofocus`
- Updates to the Boost custom-client patch registration if the codebase requires explicit registration
- Patch list and bundle metadata updates when publishing

Exact bytecode targets must be confirmed by decompiling or inspecting the installed Boost APK during implementation.

## Verification

Implementation should be verified in layers:

1. Compile the new Java extension helper against the local Android SDK.
2. Inspect the patched bytecode or smali output to confirm calls were injected into video start and stop paths.
3. Build the patch bundle through the repository's GitHub Actions workflow.
4. Install/apply the patch on the connected Moto device.
5. Use logcat to confirm focus request and abandon events while opening and closing Boost videos.
6. Practical check: with Android Auto or the car audio route active, starting a Boost video from the phone should request audio focus and allow the video's audio to play through the car route.

## Risks

Boost may have multiple playback implementations. The first implementation may need more than one injection target to cover inline, full-screen, and external media viewers.

Requesting focus on every video start is intentionally aggressive. It may interrupt other audio sooner than a mute-aware implementation would, but this matches the requested behavior.

Android Auto may still apply route, projection, or vehicle-state behavior outside Boost's control. This patch can request audio focus for phone-side playback; it cannot guarantee that Android Auto exposes Boost as a car-screen media app.
