package org.videolan.vlc.gui.dialogs

import android.app.Dialog
import android.content.DialogInterface
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.BaseInputConnection
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.videolan.resources.AndroidDevices
import org.videolan.tools.Settings
import org.videolan.vlc.R
import org.videolan.vlc.gui.video.VideoPlayerActivity.Companion.videoRemoteFlow
import org.videolan.vlc.util.isTalkbackIsEnabled

abstract class VLCBottomSheetDialogFragment : BottomSheetDialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Settings.showTvUi) {
            requireActivity().setTheme(R.style.Theme_VLC_Black_BottomSheet)
        }
        super.onCreate(savedInstanceState)
    }

    private var videoRemoteJob: Job? = null
    var onDismissListener: DialogInterface.OnDismissListener? = null

    fun inflate(inflater: LayoutInflater, container: ViewGroup?, @LayoutRes layout: Int): View? {
        return inflater.inflate(layout, container, false)

    }

    override fun onStart() {
        if (allowRemote()) {
            shouldInterceptRemote.postValue(true)
            videoRemoteJob = lifecycleScope.launch {
                videoRemoteFlow.collect { action ->
                    when (action) {
                        "up" -> KeyEvent.KEYCODE_DPAD_UP
                        "down" -> KeyEvent.KEYCODE_DPAD_DOWN
                        "right" -> KeyEvent.KEYCODE_DPAD_RIGHT
                        "left" -> KeyEvent.KEYCODE_DPAD_LEFT
                        "center" -> KeyEvent.KEYCODE_DPAD_CENTER
                        "back" -> KeyEvent.KEYCODE_BACK
                        else -> null
                    }?.let { keyCode ->
                        simulateKeyPress(keyCode)
                        videoRemoteFlow.emit(null)
                    }
                }
            }
        }
        super.onStart()
    }

    override fun onStop() {
        if (allowRemote()) shouldInterceptRemote.postValue(false)
        videoRemoteJob?.cancel()
        super.onStop()
    }

    /**
     * Apply bottom margin for Android TV
     */
    private fun applyOverscanMargin(view: View) {
        val vm = requireActivity().resources.getDimensionPixelSize(org.videolan.resources.R.dimen.tv_overscan_vertical)
        view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom + vm)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launchWhenStarted {
            dialog?.window?.setLayout(resources.getDimensionPixelSize(R.dimen.default_context_width), ViewGroup.LayoutParams.MATCH_PARENT)
            dialog?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let {
                val bsb = BottomSheetBehavior.from(it)
                if (bsb.state == BottomSheetBehavior.STATE_COLLAPSED) bsb.state = getDefaultState()
            }
            dialog?.findViewById<View>(R.id.touch_outside)?.isFocusable = false
            dialog?.findViewById<View>(R.id.touch_outside)?.isFocusableInTouchMode = false
            if (AndroidDevices.isTv) applyOverscanMargin(view)
        }


    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val bottomSheetDialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        if (AndroidDevices.isChromeBook) bottomSheetDialog.setOnShowListener {
            bottomSheetDialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                ?.let { bottomSheet ->
                    BottomSheetBehavior.from(bottomSheet).isDraggable = false
                }
        }
        return bottomSheetDialog
    }

    private fun simulateKeyPress(key: Int) {
        view?.let {
            val inputConnection = BaseInputConnection(
                it,
                true
            )
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, key)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, key)
            inputConnection.sendKeyEvent(downEvent)
            inputConnection.sendKeyEvent(upEvent)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissListener?.onDismiss(dialog)
    }

    override fun onResume() {
        super.onResume()

        initialFocusedView().isFocusable = true
        initialFocusedView().isFocusableInTouchMode = true

        initialFocusedView().requestFocus()
        if (requireActivity().isTalkbackIsEnabled()) initialFocusedView().sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
    }


    override fun onConfigurationChanged(newConfig: Configuration) {
        if (!needToManageOrientation()) {
            super.onConfigurationChanged(newConfig)
            return
        }
        if (isAdded) {
            dismiss()
        }
        super.onConfigurationChanged(newConfig)
        if (isAdded) {
            show(parentFragmentManager.beginTransaction().setReorderingAllowed(false), tag)
        }
    }

    /**
     * Default state for the [BottomSheetBehavior]
     * Should be one of [BottomSheetBehavior.STATE_EXPANDED],[BottomSheetBehavior.STATE_COLLAPSED],[BottomSheetBehavior.STATE_HALF_EXPANDED]
     */
    abstract fun getDefaultState(): Int

    /**
     * Sends true if the fragments needs to be re-created when Activity is not recreated onConfigurationChanged
     */
    abstract fun needToManageOrientation(): Boolean

    /**
     * The initial view to be focused to avoid BottomSheetDialogFragment to steal it
     * Both fields [isFocusable] and [isFocusableInTouchMode] will be set to true
     */
    abstract fun initialFocusedView(): View

    open fun allowRemote() = false

    companion object {
        val shouldInterceptRemote = MutableLiveData(false)
    }


}