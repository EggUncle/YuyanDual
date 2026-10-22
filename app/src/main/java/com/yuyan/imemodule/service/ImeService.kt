package com.yuyan.imemodule.service

import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Display
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.yuyan.imemodule.R
import com.yuyan.imemodule.data.emojicon.YuyanEmojiCompat
import com.yuyan.imemodule.data.theme.ThemeManager
import com.yuyan.imemodule.data.theme.ThemeManager.OnThemeChangeListener
import com.yuyan.imemodule.keyboard.InputView
import com.yuyan.imemodule.keyboard.KeyboardManager
import com.yuyan.imemodule.keyboard.container.ClipBoardContainer
import com.yuyan.imemodule.prefs.AppPrefs.Companion.getInstance
import com.yuyan.imemodule.prefs.behavior.SkbMenuMode
import com.yuyan.imemodule.singleton.EnvironmentSingleton
import com.yuyan.imemodule.utils.StringUtils
import com.yuyan.imemodule.view.preference.ManagedPreference
import splitties.bitflags.hasFlag

class ImeService : InputMethodService() {
    private var isWindowShown = false
    private var inputViewStarted = false
    private var normalInputView: InputView? = null
    private var secondaryPresentation: KeyboardPresentation? = null
    private var secondaryInputView: InputView? = null
    private var displayManager: DisplayManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private val logTag = "YuyanImeDual"

    // Include dimensions and rotation so an existing view is only reused on the same layout.
    private data class RouteKey(
        val hostDisplayId: Int,
        val keyboardDisplayId: Int,
        val width: Int,
        val height: Int,
        val density: Int,
        val rotation: Int
    )
    private var routeKey: RouteKey? = null
    private val refreshDisplay = Runnable {
        if (inputViewStarted && isWindowShown) {
            currentInputEditorInfo?.let { bindKeyboard(it, restarting = true, onlyIfChanged = true) }
        }
    }

    private fun scheduleDisplayRefresh() {
        handler.removeCallbacks(refreshDisplay)
        handler.postDelayed(refreshDisplay, 100L)
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = scheduleDisplayRefresh()
        override fun onDisplayRemoved(displayId: Int) = scheduleDisplayRefresh()
        override fun onDisplayChanged(displayId: Int) = scheduleDisplayRefresh()
    }
    private val onThemeChangeListener = OnThemeChangeListener { getActiveInputView()?.updateTheme() }
    private val clipboardUpdateContent = getInstance().internal.clipboardUpdateContent
    private val clipboardUpdateContentListener = ManagedPreference.OnChangeListener<String> { _, value ->
        val view = getActiveInputView()
        if (getInstance().clipboard.clipboardSuggestion.getValue() && value.isNotBlank() && view?.isShown == true) {
            val container = KeyboardManager.instance.currentContainer
            if (container is ClipBoardContainer && container.getMenuMode() == SkbMenuMode.ClipBoard) {
                container.showClipBoardView(SkbMenuMode.ClipBoard)
            } else {
                view.showSymbols(arrayOf(value))
            }
        }
    }

    override fun onCreateInputView(): View {
        routeKey = null
        normalInputView = null
        return emptyInputView()
    }

    private fun emptyInputView(): View = object : View(this) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), 0)
        }
    }

    private fun getActiveInputView(): InputView? = secondaryInputView ?: normalInputView

    private fun imeHostDisplay(): Display? =
        window?.window?.decorView?.display ?: displayManager?.getDisplay(Display.DEFAULT_DISPLAY)

    private fun secondaryDisplay(editor: Display): Display? {
        // Retroid's "fixed keyboard" system option explicitly requests a normal IME.
        if (Settings.System.getInt(contentResolver, "ime_fixed", 0) == 1 ||
            editor.displayId != Display.DEFAULT_DISPLAY) return null
        return displayManager?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            ?.filter {
                it.displayId != Display.DEFAULT_DISPLAY && it.isValid &&
                    it.state != Display.STATE_OFF && it.state != Display.STATE_DOZE_SUSPEND
            }
            ?.minByOrNull { it.displayId }
    }

    private fun bindKeyboard(editorInfo: EditorInfo, restarting: Boolean, onlyIfChanged: Boolean = false) {
        val editor = imeHostDisplay() ?: return
        val target = secondaryDisplay(editor)
        val keyboardDisplay = target ?: editor
        val metrics = createDisplayContext(keyboardDisplay).resources.displayMetrics
        val newKey = RouteKey(editor.displayId, keyboardDisplay.displayId,
            metrics.widthPixels, metrics.heightPixels, metrics.densityDpi, keyboardDisplay.rotation)
        if (newKey == routeKey && getActiveInputView()?.isAttachedToWindow == true) {
            if (!onlyIfChanged) getActiveInputView()?.onStartInputView(editorInfo, restarting)
            return
        }

        dismissSecondary()
        normalInputView = null
        super.setInputView(emptyInputView())
        val env = EnvironmentSingleton.instance
        env.systemNavbarWindowsBottom = 0
        env.forceFullOnSecondary = target != null
        env.currentDisplayState = when {
            target != null -> EnvironmentSingleton.DisplayState.SecondaryFullscreen
            metrics.widthPixels > metrics.heightPixels -> EnvironmentSingleton.DisplayState.SecondaryLandscape
            editor.displayId == Display.DEFAULT_DISPLAY -> EnvironmentSingleton.DisplayState.PrimaryPortrait
            else -> EnvironmentSingleton.DisplayState.SecondaryPortrait
        }
        env.initDataWithDisplayMetrics(metrics)
        if (target != null && showSecondary(target, editorInfo, restarting)) {
            routeKey = newKey
            Log.i(logTag, "route host=${editor.displayId} keyboard=${target.displayId} mode=secondary ${metrics.widthPixels}x${metrics.heightPixels}")
        } else {
            val editorMetrics = createDisplayContext(editor).resources.displayMetrics
            env.forceFullOnSecondary = false
            env.currentDisplayState = when {
                editorMetrics.widthPixels > editorMetrics.heightPixels -> EnvironmentSingleton.DisplayState.SecondaryLandscape
                editor.displayId == Display.DEFAULT_DISPLAY -> EnvironmentSingleton.DisplayState.PrimaryPortrait
                else -> EnvironmentSingleton.DisplayState.SecondaryPortrait
            }
            env.initDataWithDisplayMetrics(editorMetrics)
            val view = InputView(ContextThemeWrapper(this, R.style.Theme_AppTheme), this)
            super.setInputView(view)
            view.onStartInputView(editorInfo, restarting)
            if (isWindowShown) view.onWindowShown()
            normalInputView = view
            routeKey = newKey.copy(keyboardDisplayId = editor.displayId,
                width = editorMetrics.widthPixels, height = editorMetrics.heightPixels,
                density = editorMetrics.densityDpi, rotation = editor.rotation)
            Log.i(logTag, "route host=${editor.displayId} keyboard=${editor.displayId} mode=normal ${editorMetrics.widthPixels}x${editorMetrics.heightPixels}")
        }
        window?.window?.decorView?.requestLayout()
    }

    private fun showSecondary(display: Display, info: EditorInfo, restarting: Boolean): Boolean {
        var presentation: KeyboardPresentation? = null
        return try {
            val created = KeyboardPresentation(createDisplayContext(display), display, this)
            presentation = created
            secondaryPresentation = created
            created.setOnDismissListener {
                if (secondaryPresentation === created) {
                    secondaryPresentation = null
                    secondaryInputView = null
                    routeKey = null
                    scheduleDisplayRefresh()
                }
            }
            created.show()
            secondaryInputView = created.inputView
            created.inputView.onStartInputView(info, restarting)
            if (isWindowShown) created.inputView.onWindowShown()
            true
        } catch (e: Exception) {
            Log.e(logTag, "Cannot show keyboard on display ${display.displayId}; using normal IME", e)
            secondaryPresentation = null
            secondaryInputView = null
            try { presentation?.dismiss() } catch (_: Exception) {}
            false
        }
    }

    private fun dismissSecondary() {
        val old = secondaryPresentation
        secondaryPresentation = null
        secondaryInputView = null
        try { old?.dismiss() } catch (e: Exception) { Log.w(logTag, "Dismiss secondary keyboard", e) }
    }

    override fun onStartInputView(editorInfo: EditorInfo, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        inputViewStarted = true
        YuyanEmojiCompat.setEditorInfo(editorInfo)
        bindKeyboard(editorInfo, restarting)
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onEvaluateInputViewShown(): Boolean =
        resources.configuration.keyboard != Configuration.KEYBOARD_QWERTY || super.onEvaluateInputViewShown()

    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean =
        if (resources.configuration.keyboard == Configuration.KEYBOARD_12KEY) true
        else super.onShowInputRequested(flags, configChange)

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        scheduleDisplayRefresh()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        isWindowShown = true
        getActiveInputView()?.onWindowShown()
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        getActiveInputView()?.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesEnd)
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        isWindowShown = false
        getActiveInputView()?.onWindowHidden()
        dismissSecondary()
        routeKey = null
    }

    private fun finishKeyboard() {
        inputViewStarted = false
        handler.removeCallbacks(refreshDisplay)
        getActiveInputView()?.resetToIdleState()
        dismissSecondary()
        routeKey = null
    }

    override fun onFinishInput() {
        super.onFinishInput()
        finishKeyboard()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        finishKeyboard()
    }

    override fun onUnbindInput() {
        super.onUnbindInput()
        finishKeyboard()
    }

    fun sendDeleteKeyEvent()
    {
        val eventTime = SystemClock.uptimeMillis()
        sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_DEL)
        sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_DEL)
    }

    private fun sendKeyUpEvent(keyEventCode: Int, eventTime: Long){
        val event = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyEventCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0)
        val ic = currentInputConnection
        if(ic!=null) ic.sendKeyEvent(event)
    }

    fun sendEnterKeyEvent(){
        val ic = currentInputConnection
        val info = currentInputEditorInfo
        if (info == null || ic == null) {
            val t = SystemClock.uptimeMillis()
            sendDownKeyEvent(t, KeyEvent.KEYCODE_ENTER)
            sendUpKeyEvent(t, KeyEvent.KEYCODE_ENTER)
            return
        }
        if ((info.inputType and android.text.InputType.TYPE_MASK_CLASS) == android.text.InputType.TYPE_NULL
            || info.imeOptions.hasFlag(EditorInfo.IME_FLAG_NO_ENTER_ACTION)) {
            val t = SystemClock.uptimeMillis()
            sendDownKeyEvent(t, KeyEvent.KEYCODE_ENTER)
            sendUpKeyEvent(t, KeyEvent.KEYCODE_ENTER)
        } else if (!info.actionLabel.isNullOrEmpty() && info.actionId != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(info.actionId)
        } else {
            when (val action = info.imeOptions and EditorInfo.IME_MASK_ACTION) {
                EditorInfo.IME_ACTION_UNSPECIFIED, EditorInfo.IME_ACTION_NONE -> {
                    val t = SystemClock.uptimeMillis()
                    sendDownKeyEvent(t, KeyEvent.KEYCODE_ENTER)
                    sendUpKeyEvent(t, KeyEvent.KEYCODE_ENTER)
                }
                else -> ic.performEditorAction(action)
            }
        }
    }

    private fun sendDownKeyEvent(eventTime: Long, keyEventCode: Int, metaState: Int = 0){
        val event = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyEventCode, 0, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0)
        val ic = currentInputConnection
        if(ic!=null) ic.sendKeyEvent(event)
    }

    private fun sendUpKeyEvent(eventTime: Long, keyEventCode: Int, metaState: Int = 0){
        val event = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyEventCode, 0, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0)
        val ic = currentInputConnection
        if(ic!=null) ic.sendKeyEvent(event)
    }

    fun sendCombinationKeyEvents(keyCode: Int, shift: Boolean = false) {
        val eventTime = SystemClock.uptimeMillis()
        if (shift) sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_SHIFT_LEFT)
        sendDownKeyEvent(eventTime, keyCode)
        sendUpKeyEvent(eventTime, keyCode)
        if (shift) sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_SHIFT_LEFT)
    }

    fun commitText(text: String) {
        val ic: InputConnection? = currentInputConnection
        ic?.commitText(text, 1)
    }

    fun setComposingText(text: CharSequence) {
        currentInputConnection?.setComposingText(text, 1)
    }

    fun finishComposingText() {
        currentInputConnection?.finishComposingText()
    }

    fun forceHideKeyboard() {
        try { requestHideSelf(0) } catch (_: Throwable) {}
        inputViewStarted = false
        isWindowShown = false
        dismissSecondary()
        routeKey = null
    }

    fun getTextBeforeCursor(length:Int) : String{
        val ic: InputConnection? = currentInputConnection
        return StringUtils.fromCSDString(ic?.getTextBeforeCursor(length, 0)?.toString())
    }

    fun commitTextEditMenu(id:Int){
        val ic: InputConnection? = currentInputConnection
        ic?.performContextMenuAction(id)
    }

    fun performEditorAction(editorAction:Int){
        val ic: InputConnection? = currentInputConnection
        ic?.performEditorAction(editorAction)
    }

    fun deleteSurroundingText(length:Int){
        val ic: InputConnection? = currentInputConnection
        ic?.deleteSurroundingText(length, 0)
    }

    fun setSelection(start: Int, end: Int){
        val ic: InputConnection? = currentInputConnection
        ic?.setSelection(start, end)
    }

    override fun onComputeInsets(outInsets: Insets) {
        val hostHeight = window?.window?.decorView?.height ?: 0
        if (secondaryInputView != null) {
            outInsets.contentTopInsets = hostHeight
            outInsets.visibleTopInsets = hostHeight
            outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
            outInsets.touchableRegion.setEmpty()
            return
        }
        val activeView = normalInputView
        if (activeView == null) {
            super.onComputeInsets(outInsets)
            return
        }
        val location = IntArray(2)
        (if (activeView.isAddPhrases) activeView.mAddPhrasesLayout else activeView.mSkbRoot)
            .getLocationInWindow(location)
        val (x, y) = location
        if (EnvironmentSingleton.instance.keyboardModeFloat) {
            outInsets.contentTopInsets = hostHeight
            outInsets.visibleTopInsets = hostHeight
            outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
            outInsets.touchableRegion.set(x, y, x + activeView.mSkbRoot.width, y + activeView.mSkbRoot.height)
        } else {
            outInsets.contentTopInsets = y
            outInsets.visibleTopInsets = y
            outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_CONTENT
            outInsets.touchableRegion.setEmpty()
        }
    }

    override fun onCreate() {
        super.onCreate()
        displayManager = getSystemService(DisplayManager::class.java)
        displayManager?.registerDisplayListener(displayListener, handler)
        ThemeManager.addOnChangedListener(onThemeChangeListener)
        clipboardUpdateContent.registerOnChangeListener(clipboardUpdateContentListener)
    }

    override fun onDestroy() {
        inputViewStarted = false
        handler.removeCallbacksAndMessages(null)
        dismissSecondary()
        normalInputView = null
        displayManager?.unregisterDisplayListener(displayListener)
        ThemeManager.removeOnChangedListener(onThemeChangeListener)
        clipboardUpdateContent.unregisterOnChangeListener(clipboardUpdateContentListener)
        EnvironmentSingleton.instance.clearDisplayMetricsOverride()
        super.onDestroy()
    }
}
