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

private val EXOPLAYER_PLAY_WHEN_READY_DESCRIPTORS = setOf(
    "Lcom/google/android/exoplayer2/k;",
    "Lcom/google/android/exoplayer2/w1;"
)

private val EXOPLAYER_PLAY_WHEN_READY_OPCODES = setOf(
    Opcode.INVOKE_VIRTUAL,
    Opcode.INVOKE_INTERFACE
)

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
            instruction.opcode in EXOPLAYER_PLAY_WHEN_READY_OPCODES &&
            methodReference?.definingClass in EXOPLAYER_PLAY_WHEN_READY_DESCRIPTORS &&
            methodReference.name == "m" &&
            methodReference.returnType == "V" &&
            methodReference.parameterTypes == listOf("Z")
        ) {
            index
        } else {
            null
        }
    }

    if (playIndices.isEmpty()) {
        return
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
