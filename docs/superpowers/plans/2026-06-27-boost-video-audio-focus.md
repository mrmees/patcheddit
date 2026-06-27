# Boost Video Audio Focus Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Boost for Reddit patch that requests Android audio focus whenever Boost starts video playback, so phone-side Boost video audio can play over the active car or Android Auto audio route.

**Architecture:** Runtime audio-focus behavior lives in one Java extension helper that accepts any patched Boost object and resolves a `Context`. Kotlin bytecode patches inject calls before Boost ExoPlayer `setPlayWhenReady(true)` calls and at the start of legacy `UniversalVideoView` start/pause/release methods. The patch does not add Android Auto UI, media browser services, or manifest category changes.

**Tech Stack:** Kotlin Morphe bytecode patches, dexlib2 instruction scanning, Java Android extension code, Android `AudioManager`, `AudioFocusRequest`, `AudioAttributes`, jadx for target inspection, GitHub Actions for bundle builds.

---

## File Structure

- Create: `extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/BoostAudioFocus.java`
  - Owns context resolution, `requestAudioFocus`, `abandonAudioFocus`, and logcat diagnostics.
- Create: `patches/src/main/kotlin/app/morphe/patches/reddit/customclients/boostforreddit/fix/audiofocus/Fingerprints.kt`
  - Declares Boost playback method fingerprints for feed video cards, profile video cards, full-screen Exo activities, detail Exo fragments, and `UniversalVideoView`.
- Create: `patches/src/main/kotlin/app/morphe/patches/reddit/customclients/boostforreddit/fix/audiofocus/RequestVideoAudioFocusPatch.kt`
  - Injects `BoostAudioFocus.requestVideoFocus(p0)` before ExoPlayer starts and `BoostAudioFocus.abandonVideoFocus()` when playback pauses/releases.
- Modify after verification: `gradle.properties`
  - Bump `version = 1.4.3` to `version = 1.4.4`.
- Modify after verification: `patches-list.json`
  - Use the generated list from the successful GitHub Actions artifact so the new patch appears in Morphe.
- Modify after release: `patches-bundle.json`
  - Point Morphe at `https://github.com/mrmees/patcheddit/releases/download/v1.4.4/patches-1.4.4.mpp`.

## Target Map

Reconfirm these targets during Task 1 before editing code:

| Area | Class Descriptor | Request-Focus Methods | Abandon-Focus Methods |
| --- | --- | --- | --- |
| Feed video cards | `Lcom/rubenmayayo/reddit/ui/fragments/a;` | `F`, `D`, `E` | `A`, `G` |
| Profile video cards | `Lcom/rubenmayayo/reddit/ui/profile/b;` | `F`, `D`, `E` | `A`, `G` |
| Full-screen Exo viewer | `Lcom/rubenmayayo/reddit/ui/activities/ExoActivity;` | `z2`, `k2` | `A2`, `p2` |
| Full-screen media viewer | `Lcom/rubenmayayo/reddit/ui/activities/MediaVideoActivity;` | `V2`, `H2` | `W2`, `L2` |
| Detail Exo fragment | `Lcom/rubenmayayo/reddit/ui/fragments/type/ExoFragment;` | `k3`, `a3` | `l3`, `d3` |
| Legacy MediaPlayer view | `Lcom/rubenmayayo/reddit/ui/customviews/UniversalVideoView;` | `start` | `pause`, `O`, `R` |

The request hooks for ExoPlayer methods should be inserted immediately before `Lcom/google/android/exoplayer2/k;->m(Z)V` inside the request-focus methods. This avoids requesting audio focus for silent GIF branches that call `pl.droidsonroids.gif.b.start()`.

---

### Task 1: Reconfirm Boost Playback Targets

**Files:**
- Read: installed Boost APK from device `ZY22LBDRM9`
- Create temporary: `.tmp/boost-audiofocus/boost-base.apk`
- Create temporary: `.tmp/boost-audiofocus/jadx`

- [ ] **Step 1: Check the worktree**

Run:

```bash
git status --short --branch
```

Expected: either clean except the plan document, or only changes from the current task. Stop if unrelated user edits overlap with the audio-focus files listed in this plan.

- [ ] **Step 2: Pull the installed Boost APK**

Run:

```bash
mkdir -p .tmp/boost-audiofocus
APK_PATH=$("/mnt/e/Android/Sdk/platform-tools/adb.exe" -s ZY22LBDRM9 shell pm path com.rubenmayayo.reddit | tr -d '\r' | sed -n 's/^package://p' | head -1)
printf '%s\n' "$APK_PATH"
"/mnt/e/Android/Sdk/platform-tools/adb.exe" -s ZY22LBDRM9 pull "$APK_PATH" .tmp/boost-audiofocus/boost-base.apk
```

Expected: prints a `/data/app/.../base.apk` path and pulls one APK.

- [ ] **Step 3: Decompile code with jadx**

Run:

```bash
rm -rf .tmp/boost-audiofocus/jadx
jadx --no-res -d .tmp/boost-audiofocus/jadx .tmp/boost-audiofocus/boost-base.apk
```

Expected: jadx may finish with non-fatal decompiler errors, but `.tmp/boost-audiofocus/jadx/sources/com/rubenmayayo/reddit` exists.

- [ ] **Step 4: Confirm ExoPlayer and MediaPlayer targets**

Run:

```bash
rg -n "\.m\(true\)|\.m\(false\)|\.start\(\)|\.pause\(\)|\.release\(\)" .tmp/boost-audiofocus/jadx/sources/com/rubenmayayo/reddit/ui -g '*.java' | sed -n '1,220p'
```

Expected hits include:

```text
com/rubenmayayo/reddit/ui/fragments/a.java: this.f36359a.m(true)
com/rubenmayayo/reddit/ui/profile/b.java: this.f37126a.m(true)
com/rubenmayayo/reddit/ui/activities/ExoActivity.java: this.f34584l.m(true)
com/rubenmayayo/reddit/ui/activities/MediaVideoActivity.java: this.f34779r.m(true)
com/rubenmayayo/reddit/ui/fragments/type/ExoFragment.java: this.f36530o.m(true)
com/rubenmayayo/reddit/ui/customviews/UniversalVideoView.java: this.f35747g.start()
```

- [ ] **Step 5: Confirm method names from the target map**

Run:

```bash
rg -n "void F\(|void D\(|void E\(|void A\(|void G\(|void z2\(|void k2\(|void A2\(|void p2\(|void V2\(|void H2\(|void W2\(|void L2\(|void k3\(|void a3\(|void l3\(|void d3\(|void start\(|void pause\(|void O\(|void R\(" \
  .tmp/boost-audiofocus/jadx/sources/com/rubenmayayo/reddit/ui/fragments/a.java \
  .tmp/boost-audiofocus/jadx/sources/com/rubenmayayo/reddit/ui/profile/b.java \
  .tmp/boost-audiofocus/jadx/sources/com/rubenmayayo/reddit/ui/activities/ExoActivity.java \
  .tmp/boost-audiofocus/jadx/sources/com/rubenmayayo/reddit/ui/activities/MediaVideoActivity.java \
  .tmp/boost-audiofocus/jadx/sources/com/rubenmayayo/reddit/ui/fragments/type/ExoFragment.java \
  .tmp/boost-audiofocus/jadx/sources/com/rubenmayayo/reddit/ui/customviews/UniversalVideoView.java
```

Expected: every method in the Target Map is present. Stop if a target is missing, because the fingerprints in Task 3 rely on these names.

---

### Task 2: Add the Runtime Audio-Focus Helper

**Files:**
- Create: `extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/BoostAudioFocus.java`
- Test: standalone `javac` compilation against Android SDK

- [ ] **Step 1: Create `BoostAudioFocus.java`**

Create `extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/BoostAudioFocus.java` with:

```java
package app.morphe.extension.boostforreddit;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Method;

public final class BoostAudioFocus {
    private static final String TAG = "BoostAudioFocus";

    private static final AudioManager.OnAudioFocusChangeListener FOCUS_CHANGE_LISTENER =
            new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {
                    Log.d(TAG, "audio focus changed: " + focusChange);
                }
            };

    private static AudioManager audioManager;
    private static AudioFocusRequest focusRequest;
    private static boolean hasFocus;

    private BoostAudioFocus() {
    }

    public static synchronized void requestVideoFocus(Object source) {
        try {
            Context context = resolveContext(source);
            if (context == null) {
                Log.d(TAG, "request skipped: no context for " + className(source));
                return;
            }

            AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (manager == null) {
                Log.d(TAG, "request skipped: no audio manager");
                return;
            }

            if (hasFocus && manager == audioManager) {
                Log.d(TAG, "request skipped: focus already held");
                return;
            }

            if (hasFocus) {
                abandonVideoFocus();
            }

            int result;
            AudioFocusRequest request = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioAttributes attributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build();

                request = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(attributes)
                        .setOnAudioFocusChangeListener(FOCUS_CHANGE_LISTENER)
                        .build();
                result = manager.requestAudioFocus(request);
            } else {
                result = manager.requestAudioFocus(
                        FOCUS_CHANGE_LISTENER,
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN
                );
            }

            audioManager = manager;
            focusRequest = request;
            hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            Log.d(TAG, "request result=" + result + " granted=" + hasFocus);
        } catch (Throwable throwable) {
            Log.e(TAG, "request failed", throwable);
        }
    }

    public static synchronized void abandonVideoFocus() {
        try {
            if (audioManager == null) {
                hasFocus = false;
                focusRequest = null;
                return;
            }

            int result;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                result = audioManager.abandonAudioFocusRequest(focusRequest);
            } else {
                result = audioManager.abandonAudioFocus(FOCUS_CHANGE_LISTENER);
            }

            Log.d(TAG, "abandon result=" + result);
        } catch (Throwable throwable) {
            Log.e(TAG, "abandon failed", throwable);
        } finally {
            hasFocus = false;
            focusRequest = null;
            audioManager = null;
        }
    }

    private static Context resolveContext(Object source) {
        if (source == null) {
            return null;
        }

        if (source instanceof Context) {
            return ((Context) source).getApplicationContext();
        }

        if (source instanceof View) {
            Context context = ((View) source).getContext();
            return context == null ? null : context.getApplicationContext();
        }

        Context context = invokeContextMethod(source, "getContext");
        if (context != null) {
            return context.getApplicationContext();
        }

        context = invokeContextMethod(source, "getActivity");
        return context == null ? null : context.getApplicationContext();
    }

    private static Context invokeContextMethod(Object source, String methodName) {
        try {
            Method method = source.getClass().getMethod(methodName);
            Object value = method.invoke(source);
            return value instanceof Context ? (Context) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String className(Object source) {
        return source == null ? "null" : source.getClass().getName();
    }
}
```

- [ ] **Step 2: Compile the helper by itself**

Run:

```bash
rm -rf .tmp/boost-audiofocus-javac
mkdir -p .tmp/boost-audiofocus-javac
javac -cp /mnt/e/Android/Sdk/platforms/android-36/android.jar \
  -d .tmp/boost-audiofocus-javac \
  extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/BoostAudioFocus.java
```

Expected: no compiler output and `.tmp/boost-audiofocus-javac/app/morphe/extension/boostforreddit/BoostAudioFocus.class` exists.

- [ ] **Step 3: Commit the helper**

Run:

```bash
git add extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/BoostAudioFocus.java
git commit -m "feat: add Boost video audio focus helper"
```

Expected: one commit containing only `BoostAudioFocus.java`.

---

### Task 3: Add Bytecode Fingerprints

**Files:**
- Create: `patches/src/main/kotlin/app/morphe/patches/reddit/customclients/boostforreddit/fix/audiofocus/Fingerprints.kt`

- [ ] **Step 1: Create the audiofocus package**

Run:

```bash
mkdir -p patches/src/main/kotlin/app/morphe/patches/reddit/customclients/boostforreddit/fix/audiofocus
```

Expected: directory exists.

- [ ] **Step 2: Create `Fingerprints.kt`**

Create `patches/src/main/kotlin/app/morphe/patches/reddit/customclients/boostforreddit/fix/audiofocus/Fingerprints.kt` with:

```kotlin
/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.audiofocus

import app.morphe.patcher.Fingerprint

private const val FEED_VIDEO_HOLDER_DESCRIPTOR =
    "Lcom/rubenmayayo/reddit/ui/fragments/SubmissionRecyclerViewLinearCardsVideoFragment\$VideoAdapter\$VideoSubmissionViewHolder;"

private const val PROFILE_VIDEO_HOLDER_DESCRIPTOR =
    "Lcom/rubenmayayo/reddit/ui/profile/UserContributionListVideoFragment\$VideoAdapter\$VideoSubmissionViewHolder;"

internal val feedBoostPlayerPlayVideoFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/fragments/a;",
    name = "F",
    returnType = "V",
    parameters = listOf("I", "Ljava/lang/String;", FEED_VIDEO_HOLDER_DESCRIPTOR)
)

internal val feedBoostPlayerPlayResolvedVideoFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/fragments/a;",
    name = "D",
    returnType = "V",
    parameters = listOf("I", "Ljava/lang/String;", FEED_VIDEO_HOLDER_DESCRIPTOR)
)

internal val feedBoostPlayerPlayHolderFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/fragments/a;",
    name = "E",
    returnType = "V",
    parameters = listOf(FEED_VIDEO_HOLDER_DESCRIPTOR)
)

internal val feedBoostPlayerPauseFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/fragments/a;",
    name = "A",
    returnType = "V",
    parameters = emptyList()
)

internal val feedBoostPlayerReleaseFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/fragments/a;",
    name = "G",
    returnType = "V",
    parameters = emptyList()
)

internal val profileBoostPlayerPlayVideoFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/profile/b;",
    name = "F",
    returnType = "V",
    parameters = listOf("I", "Ljava/lang/String;", PROFILE_VIDEO_HOLDER_DESCRIPTOR)
)

internal val profileBoostPlayerPlayResolvedVideoFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/profile/b;",
    name = "D",
    returnType = "V",
    parameters = listOf("I", "Ljava/lang/String;", PROFILE_VIDEO_HOLDER_DESCRIPTOR)
)

internal val profileBoostPlayerPlayHolderFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/profile/b;",
    name = "E",
    returnType = "V",
    parameters = listOf(PROFILE_VIDEO_HOLDER_DESCRIPTOR)
)

internal val profileBoostPlayerPauseFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/profile/b;",
    name = "A",
    returnType = "V",
    parameters = emptyList()
)

internal val profileBoostPlayerReleaseFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/profile/b;",
    name = "G",
    returnType = "V",
    parameters = emptyList()
)

internal val exoActivityResumeVideoFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/activities/ExoActivity;",
    name = "z2",
    returnType = "V",
    parameters = emptyList()
)

internal val exoActivityStartVideoFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/activities/ExoActivity;",
    name = "k2",
    returnType = "V",
    parameters = emptyList()
)

internal val exoActivityPauseVideoFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/activities/ExoActivity;",
    name = "A2",
    returnType = "V",
    parameters = emptyList()
)

internal val exoActivityReleasePlayerFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/activities/ExoActivity;",
    name = "p2",
    returnType = "V",
    parameters = emptyList()
)

internal val mediaVideoActivityResumeVideoFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/activities/MediaVideoActivity;",
    name = "V2",
    returnType = "V",
    parameters = emptyList()
)

internal val mediaVideoActivityStartVideoFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/activities/MediaVideoActivity;",
    name = "H2",
    returnType = "V",
    parameters = emptyList()
)

internal val mediaVideoActivityPauseVideoFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/activities/MediaVideoActivity;",
    name = "W2",
    returnType = "V",
    parameters = emptyList()
)

internal val mediaVideoActivityReleasePlayerFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/activities/MediaVideoActivity;",
    name = "L2",
    returnType = "V",
    parameters = emptyList()
)

internal val exoFragmentResumeVideoFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/fragments/type/ExoFragment;",
    name = "k3",
    returnType = "V",
    parameters = emptyList()
)

internal val exoFragmentStartVideoFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/fragments/type/ExoFragment;",
    name = "a3",
    returnType = "V",
    parameters = emptyList()
)

internal val exoFragmentPauseVideoFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/fragments/type/ExoFragment;",
    name = "l3",
    returnType = "V",
    parameters = emptyList()
)

internal val exoFragmentReleasePlayerFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/fragments/type/ExoFragment;",
    name = "d3",
    returnType = "V",
    parameters = emptyList()
)

internal val universalVideoViewStartFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/customviews/UniversalVideoView;",
    name = "start",
    returnType = "V",
    parameters = emptyList()
)

internal val universalVideoViewPauseFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/customviews/UniversalVideoView;",
    name = "pause",
    returnType = "V",
    parameters = emptyList()
)

internal val universalVideoViewInternalReleaseFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/customviews/UniversalVideoView;",
    name = "O",
    returnType = "V",
    parameters = listOf("Z")
)

internal val universalVideoViewStopReleaseFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/customviews/UniversalVideoView;",
    name = "R",
    returnType = "V",
    parameters = emptyList()
)
```

- [ ] **Step 3: Sanity-check Kotlin syntax**

Run:

```bash
./gradlew :patches:compileKotlin --dry-run
```

Expected: Gradle may fail early if the private Morphe plugin cannot resolve locally. If it reaches task planning, verify no syntax error is reported for `Fingerprints.kt`. Continue to Task 4 even if dependency resolution blocks local Gradle.

---

### Task 4: Add the Bytecode Patch

**Files:**
- Create: `patches/src/main/kotlin/app/morphe/patches/reddit/customclients/boostforreddit/fix/audiofocus/RequestVideoAudioFocusPatch.kt`

- [ ] **Step 1: Create `RequestVideoAudioFocusPatch.kt`**

Create `patches/src/main/kotlin/app/morphe/patches/reddit/customclients/boostforreddit/fix/audiofocus/RequestVideoAudioFocusPatch.kt` with:

```kotlin
/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.audiofocus

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val AUDIO_FOCUS_EXTENSION_DESCRIPTOR =
    "Lapp/morphe/extension/boostforreddit/BoostAudioFocus;"

private const val EXOPLAYER_DESCRIPTOR =
    "Lcom/google/android/exoplayer2/k;"

@Suppress("unused")
val requestVideoAudioFocusPatch = bytecodePatch(
    name = "Request audio focus for Boost videos",
    description = "Requests Android audio focus whenever Boost starts video playback.",
    default = true
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith(*BoostCompatible)

    execute {
        listOf(
            feedBoostPlayerPlayVideoFingerprint,
            feedBoostPlayerPlayResolvedVideoFingerprint,
            feedBoostPlayerPlayHolderFingerprint,
            profileBoostPlayerPlayVideoFingerprint,
            profileBoostPlayerPlayResolvedVideoFingerprint,
            profileBoostPlayerPlayHolderFingerprint,
            exoActivityResumeVideoFingerprint,
            exoActivityStartVideoFingerprint,
            mediaVideoActivityResumeVideoFingerprint,
            mediaVideoActivityStartVideoFingerprint,
            exoFragmentResumeVideoFingerprint,
            exoFragmentStartVideoFingerprint
        ).forEach { fingerprint ->
            fingerprint.method.injectRequestBeforeExoPlayWhenReady()
        }

        universalVideoViewStartFingerprint.method.injectRequestAtEntry()

        listOf(
            feedBoostPlayerPauseFingerprint,
            feedBoostPlayerReleaseFingerprint,
            profileBoostPlayerPauseFingerprint,
            profileBoostPlayerReleaseFingerprint,
            exoActivityPauseVideoFingerprint,
            exoActivityReleasePlayerFingerprint,
            mediaVideoActivityPauseVideoFingerprint,
            mediaVideoActivityReleasePlayerFingerprint,
            exoFragmentPauseVideoFingerprint,
            exoFragmentReleasePlayerFingerprint,
            universalVideoViewPauseFingerprint,
            universalVideoViewInternalReleaseFingerprint,
            universalVideoViewStopReleaseFingerprint
        ).forEach { fingerprint ->
            fingerprint.method.injectAbandonAtEntry()
        }
    }
}

private fun MutableMethod.injectRequestBeforeExoPlayWhenReady() {
    val playIndices = implementation!!.instructions.withIndex().mapNotNull { (index, instruction) ->
        val methodReference = instruction.getReference<MethodReference>()
        if (
            instruction.opcode == Opcode.INVOKE_VIRTUAL &&
            methodReference?.definingClass == EXOPLAYER_DESCRIPTOR &&
            methodReference.name == "m" &&
            methodReference.returnType == "V" &&
            methodReference.parameterTypes == listOf("Z")
        ) {
            index
        } else {
            null
        }
    }

    check(playIndices.isNotEmpty()) {
        "Could not find ExoPlayer setPlayWhenReady call in ${definingClass}->${name}"
    }

    playIndices.asReversed().forEach { index ->
        addInstructions(
            index,
            """
                invoke-static {p0}, $AUDIO_FOCUS_EXTENSION_DESCRIPTOR->requestVideoFocus(Ljava/lang/Object;)V
                """
        )
    }
}

private fun MutableMethod.injectRequestAtEntry() {
    addInstructions(
        0,
        """
            invoke-static {p0}, $AUDIO_FOCUS_EXTENSION_DESCRIPTOR->requestVideoFocus(Ljava/lang/Object;)V
            """
    )
}

private fun MutableMethod.injectAbandonAtEntry() {
    addInstructions(
        0,
        """
            invoke-static {}, $AUDIO_FOCUS_EXTENSION_DESCRIPTOR->abandonVideoFocus()V
            """
    )
}
```

- [ ] **Step 2: Check for exact import names**

Run:

```bash
rg -n "class MutableMethod|interface MutableMethod|typealias MutableMethod|mutableTypes.MutableMethod" ~/.gradle/caches/modules-2/files-2.1 2>/dev/null | sed -n '1,20p'
```

Expected: if Gradle cache exists, `app.morphe.patcher.util.proxy.mutableTypes.MutableMethod` is present. If the cache is unavailable locally, let GitHub Actions validate the import in Task 7.

- [ ] **Step 3: Commit the patch files**

Run:

```bash
git add patches/src/main/kotlin/app/morphe/patches/reddit/customclients/boostforreddit/fix/audiofocus/Fingerprints.kt \
  patches/src/main/kotlin/app/morphe/patches/reddit/customclients/boostforreddit/fix/audiofocus/RequestVideoAudioFocusPatch.kt
git commit -m "feat: request audio focus for Boost video playback"
```

Expected: one commit containing only the new audiofocus Kotlin patch files.

---

### Task 5: Local Static Verification

**Files:**
- Read: files created in Tasks 2-4

- [ ] **Step 1: Re-run Java helper compilation**

Run:

```bash
rm -rf .tmp/boost-audiofocus-javac
mkdir -p .tmp/boost-audiofocus-javac
javac -cp /mnt/e/Android/Sdk/platforms/android-36/android.jar \
  -d .tmp/boost-audiofocus-javac \
  extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/BoostAudioFocus.java
```

Expected: no compiler output.

- [ ] **Step 2: Search for patch discoverability**

Run:

```bash
rg -n "requestVideoAudioFocusPatch|Request audio focus for Boost videos|BoostAudioFocus" \
  patches/src/main/kotlin/app/morphe/patches/reddit/customclients/boostforreddit \
  extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit
```

Expected: one top-level `requestVideoAudioFocusPatch` val, one patch name string, and extension descriptor usage.

- [ ] **Step 3: Check formatting and whitespace**

Run:

```bash
git diff --check HEAD~2..HEAD
```

Expected: no output.

---

### Task 6: Version the Bundle Metadata

**Files:**
- Modify: `gradle.properties`
- Modify after artifact download: `patches-list.json`
- Modify after release creation: `patches-bundle.json`

- [ ] **Step 1: Bump `gradle.properties`**

Change:

```properties
version = 1.4.3
```

to:

```properties
version = 1.4.4
```

- [ ] **Step 2: Commit the version bump**

Run:

```bash
git add gradle.properties
git commit -m "chore: bump bundle version to 1.4.4"
```

Expected: one commit containing only `gradle.properties`.

---

### Task 7: Build the Patch Bundle with GitHub Actions

**Files:**
- Read: GitHub Actions artifact
- Modify: `patches-list.json`

- [ ] **Step 1: Push the implementation commits**

Run:

```bash
git push origin main
```

Expected: push succeeds and starts the repository workflow.

- [ ] **Step 2: Find the latest run**

Run:

```bash
RUN_ID=$(gh run list --repo mrmees/patcheddit --branch main --workflow build_pull_request.yml --limit 1 --json databaseId --jq '.[0].databaseId')
printf '%s\n' "$RUN_ID"
test -n "$RUN_ID"
```

Expected: prints one numeric workflow run id for the pushed commit.

- [ ] **Step 3: Watch the run**

Run:

```bash
gh run watch "$RUN_ID" --repo mrmees/patcheddit --exit-status
```

Expected: workflow exits successfully. If it fails on Kotlin imports or fingerprints, use the job log to fix the exact compile error, then commit and repeat Task 7.

- [ ] **Step 4: Download the artifact**

Run:

```bash
rm -rf ".tmp/artifact-$RUN_ID"
gh run download "$RUN_ID" --repo mrmees/patcheddit --name patcheddit -D ".tmp/artifact-$RUN_ID"
find ".tmp/artifact-$RUN_ID" -maxdepth 2 -type f | sort
```

Expected: artifact contains `patches-1.4.4.mpp` and generated metadata files.

- [ ] **Step 5: Update `patches-list.json` from the artifact**

Run:

```bash
cp ".tmp/artifact-$RUN_ID/patches-list.json" patches-list.json
rg -n '"version": "1.4.4"|"Request audio focus for Boost videos"' patches-list.json
```

Expected: `patches-list.json` has version `1.4.4` and includes `"Request audio focus for Boost videos"`.

- [ ] **Step 6: Commit `patches-list.json`**

Run:

```bash
git add patches-list.json
git commit -m "chore: update patch list for Boost audio focus"
git push origin main
```

Expected: one commit updates `patches-list.json` and push succeeds.

---

### Task 8: Release Bundle v1.4.4

**Files:**
- Read: `.tmp/artifact-$RUN_ID/patches-1.4.4.mpp`
- Modify: `patches-bundle.json`

- [ ] **Step 1: Create the GitHub release**

Run:

```bash
FULL_SHA=$(git rev-parse HEAD)
gh release create v1.4.4 ".tmp/artifact-$RUN_ID/patches-1.4.4.mpp" \
  --repo mrmees/patcheddit \
  --target "$FULL_SHA" \
  --title "v1.4.4" \
  --notes "Adds a Boost for Reddit patch that requests Android audio focus whenever Boost starts video playback."
```

Expected: release `v1.4.4` exists and has asset `patches-1.4.4.mpp`.

- [ ] **Step 2: Update `patches-bundle.json`**

Set `patches-bundle.json` to:

```json
{
  "created_at": "2026-06-27T00:00:00",
  "description": "Patcheddit fork release with Boost video playback audio focus requests.",
  "download_url": "https://github.com/mrmees/patcheddit/releases/download/v1.4.4/patches-1.4.4.mpp",
  "signature_download_url": "",
  "version": "1.4.4"
}
```

- [ ] **Step 3: Commit and push bundle metadata**

Run:

```bash
git add patches-bundle.json
git commit -m "chore: publish bundle metadata for v1.4.4"
git push origin main
```

Expected: one commit updates `patches-bundle.json`.

- [ ] **Step 4: Confirm the raw bundle URL**

Run:

```bash
curl -fsSL https://raw.githubusercontent.com/mrmees/patcheddit/main/patches-bundle.json
```

Expected: JSON points to `v1.4.4` and `patches-1.4.4.mpp`.

---

### Task 9: Device Verification on the Moto

**Files:**
- Read: device logs
- Device: `ZY22LBDRM9`

- [ ] **Step 1: Clear old logcat lines**

Run:

```bash
"/mnt/e/Android/Sdk/platform-tools/adb.exe" -s ZY22LBDRM9 logcat -c
```

Expected: no output.

- [ ] **Step 2: Apply the new patch bundle in Morphe**

Use Morphe on the Moto with source URL:

```text
https://github.com/mrmees/patcheddit
```

Expected: Morphe downloads bundle `1.4.4` and includes patch `Request audio focus for Boost videos`.

- [ ] **Step 3: Watch audio-focus logs**

Run:

```bash
"/mnt/e/Android/Sdk/platform-tools/adb.exe" -s ZY22LBDRM9 logcat -v time | rg "BoostAudioFocus|AudioManager|audio focus"
```

Expected while opening Boost videos:

```text
BoostAudioFocus: request result=1 granted=true
```

Expected while pausing, closing, or leaving the video:

```text
BoostAudioFocus: abandon result=1
```

- [ ] **Step 4: Verify feed video cards**

In Boost on the Moto:

1. Open a feed with Reddit-hosted video cards.
2. Start a video from the feed.
3. Confirm logcat shows `request result=1 granted=true`.
4. Scroll away or pause playback.
5. Confirm logcat shows `abandon result=1`.

- [ ] **Step 5: Verify full-screen video**

In Boost on the Moto:

1. Open a Reddit-hosted video in the full-screen viewer.
2. Confirm logcat shows `request result=1 granted=true`.
3. Press Back.
4. Confirm logcat shows `abandon result=1`.

- [ ] **Step 6: Verify legacy GIFV/MP4 path**

In Boost on the Moto:

1. Open a Gfycat/GIFV style post that uses `UniversalVideoView`.
2. Confirm logcat shows `request result=1 granted=true`.
3. Pause or leave the view.
4. Confirm logcat shows `abandon result=1`.

- [ ] **Step 7: Verify car route behavior**

With the phone connected to the car or Android Auto route:

1. Start other car audio.
2. Open Boost on the phone.
3. Start a Boost video.
4. Confirm Boost video audio plays over the car route.
5. Pause or close the video.
6. Confirm the prior audio app can resume normally.

---

### Task 10: Final Cleanup

**Files:**
- Read: repository status
- Remove: temporary `.tmp/boost-audiofocus*` folders if no longer needed

- [ ] **Step 1: Check final status**

Run:

```bash
git status --short --branch
```

Expected: clean worktree on `main`, with `main` even with `origin/main`.

- [ ] **Step 2: Remove temporary local artifacts**

Run:

```bash
rm -rf .tmp/boost-audiofocus .tmp/boost-audiofocus-javac
```

Expected: temp APK/decompile files removed.

- [ ] **Step 3: Summarize results**

Report:

```text
Implemented Boost video audio focus patch.
Published bundle version: 1.4.4
Device verification:
- Feed video focus request: pass/fail
- Full-screen video focus request: pass/fail
- UniversalVideoView focus request: pass/fail
- Car route playback: pass/fail
```
