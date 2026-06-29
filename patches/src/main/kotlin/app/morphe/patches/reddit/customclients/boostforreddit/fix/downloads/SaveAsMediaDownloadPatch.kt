/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.downloads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val SAVE_AS_DOWNLOAD_EXTENSION_DESCRIPTOR =
    "Lapp/morphe/extension/boostforreddit/BoostSaveAsDownload;"

private val ACTIVITY_RESULT_PARAMETER_TYPES = listOf(
    "I",
    "I",
    "Landroid/content/Intent;"
)
private val ACTIVITY_RESULT_PARAMETERS = ACTIVITY_RESULT_PARAMETER_TYPES.map { type ->
    ImmutableMethodParameter(type, null, null)
}

private const val EXO_ACTIVITY_SUPERCLASS =
    "Lcom/rubenmayayo/reddit/ui/activities/d;"
private const val MEDIA_VIDEO_ACTIVITY_SUPERCLASS =
    "Lcom/rubenmayayo/reddit/ui/activities/MediaActivity;"
private const val BASE_IMAGE_ACTIVITY_SUPERCLASS =
    "Lcom/rubenmayayo/reddit/ui/activities/c;"
private const val DOWNLOAD_MEDIA_ACTIVITY_SUPERCLASS =
    "Lcom/rubenmayayo/reddit/ui/activities/d;"

@Suppress("unused")
val saveAsMediaDownloadPatch = bytecodePatch(
    name = "Save Boost media as",
    description = "Long-presses Boost's media viewer download icon to save through Android's system picker.",
    default = true
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith(*BoostCompatible)

    execute {
        val activityFingerprints = listOf(
            exoActivityOnCreateFingerprint to EXO_ACTIVITY_SUPERCLASS,
            mediaVideoActivityOnCreateFingerprint to MEDIA_VIDEO_ACTIVITY_SUPERCLASS
        )
        val menuFingerprints = listOf(
            exoActivityOnCreateOptionsMenuFingerprint,
            mediaVideoActivityOnCreateOptionsMenuFingerprint,
            mediaImageActivityOnCreateOptionsMenuFingerprint,
            imageActivityOnCreateOptionsMenuFingerprint,
            imageActivity2OnCreateOptionsMenuFingerprint,
            gifActivityOnCreateOptionsMenuFingerprint,
            galleryActivityOnCreateOptionsMenuFingerprint
        )
        val activityResultTargets = listOf(
            exoActivityOnCreateFingerprint.classDef to EXO_ACTIVITY_SUPERCLASS,
            mediaVideoActivityOnCreateFingerprint.classDef to MEDIA_VIDEO_ACTIVITY_SUPERCLASS,
            mediaImageActivityOnCreateOptionsMenuFingerprint.classDef to MEDIA_VIDEO_ACTIVITY_SUPERCLASS,
            imageActivityOnCreateOptionsMenuFingerprint.classDef to BASE_IMAGE_ACTIVITY_SUPERCLASS,
            imageActivity2OnCreateOptionsMenuFingerprint.classDef to BASE_IMAGE_ACTIVITY_SUPERCLASS,
            gifActivityOnCreateOptionsMenuFingerprint.classDef to DOWNLOAD_MEDIA_ACTIVITY_SUPERCLASS,
            galleryActivityOnCreateOptionsMenuFingerprint.classDef to BASE_IMAGE_ACTIVITY_SUPERCLASS
        )

        activityFingerprints.forEach { (fingerprint, _) ->
            fingerprint.method.injectSaveAsInstallBeforeReturns()
        }
        menuFingerprints.forEach { fingerprint ->
            fingerprint.method.injectSaveAsInstallAfterMenuInflation()
        }

        activityResultTargets
            .distinctBy { (activityClass, _) -> activityClass.type }
            .forEach { (activityClass, superclass) ->
                activityClass.injectSaveAsActivityResultHook(superclass)
            }
    }
}

private fun MutableMethod.injectSaveAsInstallBeforeReturns() {
    val returnIndices = implementation!!.instructions.withIndex()
        .filter { (_, instruction) -> instruction.opcode == Opcode.RETURN_VOID }
        .map { (index, _) -> index }

    check(returnIndices.isNotEmpty()) {
        "Expected at least one return-void in $definingClass->$name"
    }

    returnIndices.asReversed().forEach { index ->
        addInstructions(
            index,
            """
                invoke-static {p0}, $SAVE_AS_DOWNLOAD_EXTENSION_DESCRIPTOR->install(Ljava/lang/Object;)V
            """
        )
    }
}

private fun MutableMethod.injectSaveAsInstallAfterMenuInflation() {
    val returnIndices = implementation!!.instructions.withIndex()
        .filter { (_, instruction) -> instruction.opcode == Opcode.RETURN }
        .map { (index, _) -> index }

    check(returnIndices.isNotEmpty()) {
        "Expected at least one return in $definingClass->$name"
    }

    returnIndices.asReversed().forEach { index ->
        addInstructions(
            index,
            """
                invoke-static {p0}, $SAVE_AS_DOWNLOAD_EXTENSION_DESCRIPTOR->install(Ljava/lang/Object;)V
            """
        )
    }
}

private fun MutableClass.injectSaveAsActivityResultHook(superclass: String) {
    val existing = methods.firstOrNull { method ->
        method.name == "onActivityResult" &&
            method.returnType == "V" &&
            method.parameters.map { it.type } == ACTIVITY_RESULT_PARAMETER_TYPES
    }

    if (existing != null) {
        existing.injectSaveAsActivityResultCall()
        return
    }

    methods.add(
        ImmutableMethod(
            type,
            "onActivityResult",
            ACTIVITY_RESULT_PARAMETERS,
            "V",
            AccessFlags.PROTECTED.value,
            null,
            null,
            ImmutableMethodImplementation.of(MutableMethodImplementation(4))
        ).toMutable().apply {
            addInstructions(
                0,
                """
                    invoke-static {p0, p1, p2, p3}, $SAVE_AS_DOWNLOAD_EXTENSION_DESCRIPTOR->handleActivityResult(Ljava/lang/Object;IILandroid/content/Intent;)V
                    invoke-super {p0, p1, p2, p3}, $superclass->onActivityResult(IILandroid/content/Intent;)V
                    return-void
                """
            )
        }
    )
}

private fun MutableMethod.injectSaveAsActivityResultCall() {
    addInstructions(
        0,
        """
            invoke-static {p0, p1, p2, p3}, $SAVE_AS_DOWNLOAD_EXTENSION_DESCRIPTOR->handleActivityResult(Ljava/lang/Object;IILandroid/content/Intent;)V
        """
    )
}
