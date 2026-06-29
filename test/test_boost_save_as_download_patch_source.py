#!/usr/bin/env python3
"""
Regression guard for Boost's save-as media download patch.

The patch must preserve Boost's existing tap-to-download behavior. Save-as
is installed only as a long-click action and must use Android's system
document picker.
"""

from pathlib import Path
import subprocess


PROJECT_DIR = Path(__file__).resolve().parent.parent
HELPER_FILE = (
    PROJECT_DIR
    / "extensions/boostforreddit/src/main/java/app/morphe/extension/"
    "boostforreddit/BoostSaveAsDownload.java"
)
PATCH_FILE = (
    PROJECT_DIR
    / "patches/src/main/kotlin/app/morphe/patches/reddit/customclients/"
    "boostforreddit/fix/downloads/SaveAsMediaDownloadPatch.kt"
)
FINGERPRINTS_FILE = (
    PROJECT_DIR
    / "patches/src/main/kotlin/app/morphe/patches/reddit/customclients/"
    "boostforreddit/fix/downloads/Fingerprints.kt"
)


def read_required(path: Path) -> str:
    assert path.exists(), f"{path.relative_to(PROJECT_DIR)} is missing"
    return path.read_text()


def test_helper_uses_long_click_and_system_picker() -> None:
    source = read_required(HELPER_FILE)

    assert "setOnLongClickListener" in source, (
        "save-as must be attached to long-click on the existing download view"
    )
    assert "setOnClickListener" not in source, (
        "save-as helper must not replace Boost's normal tap download listener"
    )
    assert "Intent.ACTION_CREATE_DOCUMENT" in source, (
        "long-click save-as must use Android's system create-document picker"
    )
    assert "Intent.CATEGORY_OPENABLE" in source, (
        "create-document picker should request an openable output document"
    )
    assert "Intent.EXTRA_TITLE" in source, (
        "create-document picker should provide a suggested filename"
    )
    assert "startActivityForResult" in source, (
        "helper must launch the picker in a way that can return a URI"
    )
    assert "handleActivityResult" in source, (
        "helper must expose an activity-result hook for the patch"
    )
    assert "openOutputStream" in source, (
        "selected content URI must be written through ContentResolver"
    )
    assert "HttpUtils.get" in source, (
        "media should stream through the existing Boost extension HTTP client"
    )
    assert "final boolean completed = success" in source, (
        "logging lambda should capture a final success snapshot"
    )


def test_helper_retries_toolbar_download_view_attachment() -> None:
    source = read_required(HELPER_FILE)

    assert "installRepeatedly" in source, (
        "download action views can be created or rebound after menu inflation; "
        "helper should retry attaching after install"
    )
    assert "3000L" in source, (
        "helper should keep retrying long enough to outlast toolbar menu view setup"
    )
    assert "INSTALLED_VIEWS.containsKey" not in source, (
        "helper should be able to reattach when AppCompat tooltip setup replaces "
        "the long-click listener after an earlier scan"
    )


def test_helper_derives_boost_reddit_video_download_urls() -> None:
    source = read_required(HELPER_FILE)

    assert "resolveMediaVideoDownloadUrl" in source, (
        "MediaVideoActivity should mirror Boost's own download action instead "
        "of relying only on player fields"
    )
    assert 'stringMethod(owner, "v2")' in source, (
        "MediaVideoActivity should call Boost's private DASH/HLS download URL resolver"
    )
    assert 'stringMethod(owner, "t2")' in source, (
        "MediaVideoActivity should call Boost's private GIF/MP4 alternate URL resolver"
    )
    assert "resolveBoostVideoDownloadUrl" in source, (
        "DASH/HLS media should use Boost's derived download URL, not the player manifest URL"
    )
    assert 'Class.forName("he.h0")' in source, (
        "helper should reflect Boost's existing media URL helper"
    )
    assert 'getDeclaredMethod("F"' in source, (
        "helper should call Boost's SubmissionModel/video-track URL resolver"
    )
    assert '"f34891c"' in source and '"f34585t"' in source, (
        "ExoActivity should pass its submission and video track list to Boost's resolver"
    )
    assert '"f34737g"' in source and '"f34779y"' in source, (
        "MediaVideoActivity should pass its submission and video track list to Boost's resolver"
    )
    assert '"f34572g"' in source and '"f34767m"' in source, (
        "helper should only prefer derived URLs for Boost's DASH/HLS media modes"
    )
    assert "applyRedditFallbackMp4Extension" in source, (
        "fallback DASH URLs should get Boost's .mp4 compatibility suffix"
    )
    assert ".mp4?source=fallback" in source
    assert "isDownloadableRedditVideoUrl" in source, (
        "raw v.redd.it manifests should not be treated as directly saveable media"
    )


def test_helper_resolves_boost_image_and_gallery_download_urls() -> None:
    source = read_required(HELPER_FILE)

    assert "resolveMediaImageDownloadUrl" in source, (
        "MediaImageActivity should be able to resolve the same URL used by "
        "Boost's normal image download action"
    )
    assert "resolveLegacyImageDownloadUrl" in source, (
        "older ImageActivity/ImageActivity2 viewers should also be supported"
    )
    assert "resolveGifDownloadUrl" in source, (
        "Boost's GifActivity should use the loaded gif/mp4 URL field"
    )
    assert "resolveGalleryDownloadUrl" in source, (
        "gallery viewers should save the currently visible gallery item"
    )
    assert '"R1"' in source and '"D1"' in source and '"F1"' in source, (
        "helper should call Boost's existing image URL resolver methods"
    )
    assert '"f34615h"' in source and '"viewPager"' in source and '"getDownloadUrl"' in source, (
        "gallery URL resolution should reflect the current ImageModel download URL"
    )


def test_helper_uses_boost_downloader_before_copying_to_picker_uri() -> None:
    source = read_required(HELPER_FILE)

    assert "downloadWithBoostDownloader" in source, (
        "save-as should reuse Boost's downloader so v.redd.it audio muxing still applies"
    )
    assert 'Class.forName("tb.b")' in source, (
        "helper should reflect Boost's downloader implementation"
    )
    assert 'Class.forName("tb.d")' in source, (
        "helper should provide Boost's downloader listener interface"
    )
    assert "Proxy.newProxyInstance" in source, (
        "helper should observe Boost downloader success/failure callbacks"
    )
    assert "copyFileToUri" in source, (
        "helper should copy Boost's downloaded temp file into the system picker URI"
    )
    assert "writeMediaDirectly" in source, (
        "direct HTTP streaming should remain only as a fallback"
    )


def test_patch_installs_helper_without_click_replacement() -> None:
    source = read_required(PATCH_FILE)

    assert "BoostSaveAsDownload" in source
    assert "->install(Ljava/lang/Object;)V" in source, (
        "patch should install save-as behavior into media viewer activities"
    )
    assert (
        "->handleActivityResult(Ljava/lang/Object;IILandroid/content/Intent;)V"
        in source
    ), "patch should route activity results to the helper"
    assert "setOnClickListener" not in source, (
        "bytecode patch must not replace Boost's existing download click action"
    )
    assert "injectSaveAsInstallAfterMenuInflation" in source, (
        "patch should install after Boost inflates the action menu so the "
        "download icon view exists before attaching the long-click listener"
    )


def test_patch_targets_boost_media_activity_on_create_methods() -> None:
    source = read_required(FINGERPRINTS_FILE)

    assert "exoActivityOnCreateFingerprint" in source
    assert (
        'definingClass = "Lcom/rubenmayayo/reddit/ui/activities/ExoActivity;"'
        in source
    ), "ExoActivity onCreate fingerprint must target Boost's media viewer"
    assert "mediaVideoActivityOnCreateFingerprint" in source
    assert (
        'definingClass = "Lcom/rubenmayayo/reddit/ui/activities/MediaVideoActivity;"'
        in source
    ), "MediaVideoActivity onCreate fingerprint must target Boost's media viewer"
    assert 'name = "onCreate"' in source
    assert 'returnType = "V"' in source
    assert 'parameters = listOf("Landroid/os/Bundle;")' in source


def test_patch_targets_boost_media_activity_menu_creation_methods() -> None:
    source = read_required(FINGERPRINTS_FILE)

    assert "exoActivityOnCreateOptionsMenuFingerprint" in source
    assert "mediaVideoActivityOnCreateOptionsMenuFingerprint" in source
    assert "mediaImageActivityOnCreateOptionsMenuFingerprint" in source
    assert "imageActivityOnCreateOptionsMenuFingerprint" in source
    assert "imageActivity2OnCreateOptionsMenuFingerprint" in source
    assert "gifActivityOnCreateOptionsMenuFingerprint" in source
    assert "galleryActivityOnCreateOptionsMenuFingerprint" in source
    assert 'name = "onCreateOptionsMenu"' in source
    assert 'returnType = "Z"' in source
    assert 'parameters = listOf("Landroid/view/Menu;")' in source


def test_patch_injects_install_before_every_return_in_reverse_order() -> None:
    source = read_required(PATCH_FILE)

    assert "Opcode.RETURN_VOID" in source, (
        "install injection should find every return-void in media onCreate methods"
    )
    assert "returnIndices.asReversed()" in source, (
        "return-void insertion must run in reverse order so indices stay stable"
    )
    assert "injectSaveAsInstallBeforeReturns" in source


def test_patch_generates_activity_result_bridge_with_expected_superclasses() -> None:
    source = read_required(PATCH_FILE)

    assert "MutableMethodImplementation(4)" in source, (
        "generated onActivityResult needs p0-p3 registers"
    )
    assert "ImmutableMethodParameter" in source, (
        "generated methods must pass typed immutable parameters to dexlib"
    )
    assert "ImmutableMethodImplementation.of(MutableMethodImplementation(4))" in source, (
        "generated methods must pass an immutable implementation to dexlib"
    )
    assert "ACTIVITY_RESULT_PARAMETER_TYPES" in source, (
        "existing method matching should compare parameter type descriptors"
    )
    assert "Lcom/rubenmayayo/reddit/ui/activities/d;" in source, (
        "ExoActivity should delegate onActivityResult to its actual superclass"
    )
    assert "Lcom/rubenmayayo/reddit/ui/activities/MediaActivity;" in source, (
        "MediaVideoActivity should delegate onActivityResult to its actual superclass"
    )
    assert "Lcom/rubenmayayo/reddit/ui/activities/c;" in source, (
        "legacy image and gallery activities should delegate activity results to BaseImageActivity"
    )
    assert "Lcom/rubenmayayo/reddit/ui/activities/d;" in source, (
        "legacy gif/download media activities should delegate activity results to DownloadMediaActivity"
    )
    assert "->onActivityResult(IILandroid/content/Intent;)V" in source, (
        "generated method should call the exact activity-result descriptor"
    )


def test_commit_does_not_track_temporary_or_apk_artifacts() -> None:
    result = subprocess.run(
        ["git", "ls-tree", "-r", "--name-only", "HEAD"],
        cwd=PROJECT_DIR,
        check=True,
        text=True,
        capture_output=True,
    )
    tracked_paths = result.stdout.splitlines()

    assert not any(path.startswith(".tmp/") for path in tracked_paths), (
        ".tmp artifacts must not be tracked"
    )
    assert not any(path.endswith(".apk") for path in tracked_paths), (
        "APK artifacts must not be tracked"
    )


if __name__ == "__main__":
    test_helper_uses_long_click_and_system_picker()
    test_helper_retries_toolbar_download_view_attachment()
    test_helper_derives_boost_reddit_video_download_urls()
    test_helper_resolves_boost_image_and_gallery_download_urls()
    test_helper_uses_boost_downloader_before_copying_to_picker_uri()
    test_patch_installs_helper_without_click_replacement()
    test_patch_targets_boost_media_activity_on_create_methods()
    test_patch_targets_boost_media_activity_menu_creation_methods()
    test_patch_injects_install_before_every_return_in_reverse_order()
    test_patch_generates_activity_result_bridge_with_expected_superclasses()
    test_commit_does_not_track_temporary_or_apk_artifacts()
