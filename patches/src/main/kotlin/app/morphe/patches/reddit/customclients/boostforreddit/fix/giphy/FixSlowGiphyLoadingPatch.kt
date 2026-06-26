/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.giphy

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible

@Suppress("unused")
val fixSlowGiphyLoadingPatch = bytecodePatch(
    name = "Fix slow Giphy loading",
    description = "Bypasses Boost's slow Giphy API resolver and uses Boost's direct media.giphy.com MP4 fallback for Giphy posts.",
    default = true
) {
    compatibleWith(*BoostCompatible)

    execute {
        resolveGiphyApiFingerprint.method.addInstructions(
            0,
            """
                invoke-direct { p0 }, Lhe/r;->x()V
                return-void
                """
        )
    }
}
