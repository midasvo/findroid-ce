package dev.jdtech.jellyfin.presentation.player

import android.app.Dialog
import android.os.Bundle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.jdtech.jellyfin.player.local.R
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.utils.VideoQualities
import java.lang.IllegalStateException

class QualitySelectionDialogFragment : DialogFragment() {
    private val viewModel: PlayerViewModel by activityViewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val qualityBitrates = VideoQualities.bitrates
        val qualityTexts = qualityBitrates.map { bitrate ->
            VideoQualities.label(bitrate) ?: getString(R.string.video_quality_original)
        }

        return activity?.let { activity ->
            val builder = MaterialAlertDialogBuilder(activity)
            builder.setTitle(getString(R.string.select_video_quality)).setSingleChoiceItems(
                qualityTexts.toTypedArray(),
                qualityBitrates.indexOf(viewModel.maxBitrate),
            ) { dialog, which ->
                viewModel.selectMaxBitrate(qualityBitrates[which])
                dialog.dismiss()
            }
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Fix for hiding the system bars on API < 30
        activity?.window?.let {
            WindowCompat.getInsetsController(it, it.decorView).apply {
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}
