# Boost Save As Download Design

## Goal

Add a Boost for Reddit patch that lets users long-press the existing media viewer download icon to choose a filename and destination with Android's system picker.

Normal tap behavior on the download icon must remain unchanged.

## Context

Patcheddit's Boost support is organized as Kotlin bytecode patches under `patches/src/main/kotlin/app/morphe/patches/reddit/customclients/boostforreddit` and Java extension helpers under `extensions/boostforreddit`.

Recent Boost patches use this pattern:

- Kotlin patches find obfuscated Boost classes and inject calls into app bytecode.
- Java extension helpers contain Android runtime logic that is easier to write and maintain outside smali.
- Source-level Python regression tests protect important patch behavior where a full APK fixture is not available.

The repository does not currently contain a Boost APK or decompiled Boost sources. Exact obfuscated target methods and fields for the media download icon must be confirmed during implementation by inspecting a local APK, a connected device install, or a decompiled artifact.

## Selected Approach

Attach a long-click handler to Boost's existing media viewer download control and launch Android's `Intent.ACTION_CREATE_DOCUMENT` flow.

The injected patch should call a helper such as `BoostSaveAsDownload.install(downloadView, owner)` after Boost wires the normal download icon. The helper will set only an `OnLongClickListener`; it must not replace the existing `OnClickListener` or change Boost's one-tap download path.

On long-press, the helper will:

1. Resolve the media URL and a default filename from the active viewer object.
2. Infer a MIME type from the media URL, known media metadata, or the file extension.
3. Start `ACTION_CREATE_DOCUMENT` with `CATEGORY_OPENABLE`, `EXTRA_TITLE`, and the inferred MIME type.
4. Remember enough pending state to complete the save after the system picker returns.
5. Stream the selected media to the returned `content://` URI.

The picker is the intended "Save as" UI. The patch should not implement a custom filename dialog.

## Alternatives Considered

### In-app filename prompt

An `AlertDialog` with an `EditText` would be easier to control and would avoid `onActivityResult` integration, but it would still save through Boost's normal download location. That is not a true Android "Save as" workflow.

### Replace the normal download action

Replacing the existing click behavior with the picker would be simpler than preserving two behaviors, but it would remove Boost's current quick download flow. Users should keep one-tap downloads and get save-as only when they long-press.

### Hook Boost's download pipeline only

Intercepting deeper download methods might allow filename changes with less UI work, but it is more likely to affect every download path and less likely to provide destination selection.

## Runtime Behavior

Short tap:

- Boost's existing download icon behavior runs exactly as it does today.

Long-press:

- The system file picker opens with a suggested filename.
- The user can rename the file and choose a destination supported by Android's document provider UI.
- If the user confirms a destination, the helper downloads or streams the current media into that URI.
- If the user cancels, no file is saved and no fallback download is started.

The helper should use application networking already available to the extension where practical. If it needs its own request, it should perform work off the main thread and write to `ContentResolver.openOutputStream(uri)`.

## Error Handling

The patch must be fail-closed around the new behavior:

- If the download view cannot be found, patching should fail during build rather than silently shipping a non-working patch.
- If the current media URL cannot be resolved at runtime, long-press should return without crashing and may show a short toast.
- If the filename cannot be resolved, the helper should use a safe generated fallback name with the best known extension.
- If the picker cannot be launched, the helper may show a toast and leave normal tap behavior untouched.
- If writing fails, the helper should close streams, avoid leaving an active pending request, and notify the user with a short toast.

Runtime exceptions in the helper must not crash Boost's media viewer.

## Patch Scope

The first implementation should target Boost's full-screen media viewing flow where the existing download icon is shown.

It should support image and video media when the active viewer exposes a direct media URL. It does not need to add save-as behavior to feed cards, comment previews, galleries without an obvious current item, or external browser flows in the first version.

The patch does not add a new toolbar icon, new settings, background download manager UI, overwrite prompts outside the system picker, or custom file manager behavior.

## Expected Files

Likely implementation files:

- `extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/BoostSaveAsDownload.java`
- A new Kotlin patch under `patches/src/main/kotlin/app/morphe/patches/reddit/customclients/boostforreddit/fix/downloads`
- Updates to `patches/api/patches.api` if the generated API surface changes
- A focused regression test under `test/`
- Patch list and bundle metadata updates when publishing

## Verification

Implementation should be verified in layers:

1. Add a failing source-level regression test proving the patch installs a long-click listener and does not replace normal click behavior.
2. Implement the bytecode patch and helper until that test passes.
3. Compile the Java extension helper against the local Android SDK or Gradle build.
4. Build the patch bundle with `./gradlew buildAndroid`.
5. Apply the patch to Boost and inspect logs or smali to confirm the long-click hook is injected into the media viewer download control.
6. On device, open media in Boost, short-tap the icon to confirm the existing download path still works, then long-press it to confirm the system picker opens.
7. Save a renamed image and video through the picker and verify the output files are readable.

## Risks

Boost's media viewer may use different classes or fields for images, videos, galleries, and GIF-like media. Implementation may need a narrow first target or multiple hooks.

`ACTION_CREATE_DOCUMENT` is asynchronous, so the helper must integrate with the viewer's activity result path or an equivalent Activity Result API path available in Boost's old Android stack.

Some media URLs may require headers, cookies, redirects, or transformed URLs. The first implementation should prefer reusing Boost-visible final media URLs and should avoid changing existing download behavior when save-as cannot resolve a reliable source.
