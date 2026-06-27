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
