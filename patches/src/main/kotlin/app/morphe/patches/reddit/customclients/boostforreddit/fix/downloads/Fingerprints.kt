/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.downloads

import app.morphe.patcher.Fingerprint

internal val downloadAudioFingerprint = Fingerprint(
    strings = listOf("/DASH_audio.mp4", "/audio")
)

internal val exoActivityOnCreateFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/activities/ExoActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;")
)

internal val exoActivityOnCreateOptionsMenuFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/activities/ExoActivity;",
    name = "onCreateOptionsMenu",
    returnType = "Z",
    parameters = listOf("Landroid/view/Menu;")
)

internal val mediaVideoActivityOnCreateFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/activities/MediaVideoActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;")
)

internal val mediaVideoActivityOnCreateOptionsMenuFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/activities/MediaVideoActivity;",
    name = "onCreateOptionsMenu",
    returnType = "Z",
    parameters = listOf("Landroid/view/Menu;")
)

internal val mediaImageActivityOnCreateOptionsMenuFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/activities/MediaImageActivity;",
    name = "onCreateOptionsMenu",
    returnType = "Z",
    parameters = listOf("Landroid/view/Menu;")
)

internal val imageActivityOnCreateOptionsMenuFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/activities/ImageActivity;",
    name = "onCreateOptionsMenu",
    returnType = "Z",
    parameters = listOf("Landroid/view/Menu;")
)

internal val imageActivity2OnCreateOptionsMenuFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/activities/ImageActivity2;",
    name = "onCreateOptionsMenu",
    returnType = "Z",
    parameters = listOf("Landroid/view/Menu;")
)

internal val gifActivityOnCreateOptionsMenuFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/activities/GifActivity;",
    name = "onCreateOptionsMenu",
    returnType = "Z",
    parameters = listOf("Landroid/view/Menu;")
)

internal val galleryActivityOnCreateOptionsMenuFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/activities/GalleryActivity;",
    name = "onCreateOptionsMenu",
    returnType = "Z",
    parameters = listOf("Landroid/view/Menu;")
)
