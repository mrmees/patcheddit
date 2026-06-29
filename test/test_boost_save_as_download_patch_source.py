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
    test_patch_installs_helper_without_click_replacement()
    test_patch_targets_boost_media_activity_on_create_methods()
    test_patch_injects_install_before_every_return_in_reverse_order()
    test_patch_generates_activity_result_bridge_with_expected_superclasses()
    test_commit_does_not_track_temporary_or_apk_artifacts()
