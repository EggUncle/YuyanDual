package com.yuyan.imemodule.singleton

import com.yuyan.imemodule.application.Launcher
import com.yuyan.imemodule.data.theme.ThemeManager.prefs
import com.yuyan.imemodule.prefs.AppPrefs
import com.yuyan.imemodule.keyboard.KeyboardGeometry
import kotlin.math.min
import android.util.DisplayMetrics

class EnvironmentSingleton private constructor() {
    var systemNavbarWindowsBottom = 0 // 导航栏高度
    var mScreenWidth = 0 // 屏幕的宽度
    var mScreenHeight = 0 // 屏幕的高度
    var inputAreaHeight = 0 // 键盘区域高度
    var inputAreaWidth = 0 // 键盘区域宽度
    var skbWidth = 0 // 键盘按键区域、候选词区域宽度
        private set
    var skbHeight = 0 // 键盘按键区高度
        private set
    var holderWidth = 0 // 单手模式下键盘占位区域宽度
        private set
    var heightForCandidatesArea = 0 // 候选词区域的高度
    var heightForcomposing = 0 // 候选词拼音区域的高度
    var heightForCandidates = 0 // 候选词区域的高度
    var heightForFullDisplayBar = 0 // 智能导航栏高度
    var heightForKeyboardMove = 0 // 悬浮键盘移动条高度
    var keyTextSize = 0 // 正常按键中文本的大小
    var keyTextSmallSize = 0 // 正常按键中文本的大小,小值
    var candidateTextSize = 0f // 候选词字体大小
    var composingTextSize = 0f // 候选词字体大小
    var isLandscape = false //键盘是否横屏
    var keyXMargin = 0 //键盘按键水平间距
    var keyYMargin = 0 //键盘按键垂直间距
    private var keyboardHeightRatio = 0f
    var forceFullOnSecondary = false
    private var lastDisplayMetrics: DisplayMetrics? = null
    // 四种显示状态枚举与当前状态
    enum class DisplayState { PrimaryPortrait, SecondaryPortrait, SecondaryLandscape, SecondaryFullscreen }
    var currentDisplayState: DisplayState = DisplayState.PrimaryPortrait

    init {
        initData()
    }

    fun initData() {
        val resources = Launcher.instance.context.resources
        val dm = lastDisplayMetrics ?: resources.displayMetrics
        reinitWithDisplayMetrics(dm)
    }

    fun initDataWithDisplayMetrics(dm: DisplayMetrics) {
        lastDisplayMetrics = DisplayMetrics().apply { setTo(dm) }
        reinitWithDisplayMetrics(dm)
    }

    fun clearDisplayMetricsOverride() {
        lastDisplayMetrics = null
    }

    private fun reinitWithDisplayMetrics(dm: DisplayMetrics) {
        mScreenWidth = dm.widthPixels
        mScreenHeight = dm.heightPixels
        isLandscape = mScreenHeight <= mScreenWidth
        inputAreaWidth = mScreenWidth
        holderWidth = 0
        skbWidth = inputAreaWidth

        // Fullscreen uses the lower screen's actual height, including room for candidates.
        val prefsInternal = AppPrefs.getInstance().internal
        val ratio = when (currentDisplayState) {
            DisplayState.PrimaryPortrait -> prefsInternal.heightRatioPrimaryPortrait.getValue()
            DisplayState.SecondaryPortrait -> prefsInternal.heightRatioSecondaryPortrait.getValue()
            DisplayState.SecondaryLandscape -> prefsInternal.heightRatioSecondaryLandscape.getValue()
            DisplayState.SecondaryFullscreen -> {
                val pref = prefsInternal.heightRatioSecondaryFullscreen.getValue()
                if (pref <= 0f) 1f else pref
            }
        }
        keyboardHeightRatio = ratio

        val geometry = KeyboardGeometry.calculate(
            displayHeight = mScreenHeight,
            heightRatio = keyboardHeightRatio,
            candidateScale = AppPrefs.getInstance().keyboardSetting.candidateTextSize.getValue() / 100f,
            reservedBottom = keyboardBottomPaddingPx + systemNavbarWindowsBottom,
            fullscreen = currentDisplayState == DisplayState.SecondaryFullscreen,
        )
        skbHeight = geometry.keys
        heightForcomposing = geometry.composing
        heightForCandidates = geometry.candidates
        heightForCandidatesArea = geometry.candidateArea
        val scaledDensity = dm.scaledDensity.coerceAtLeast(0.1f)
        composingTextSize = heightForcomposing * 0.7f / scaledDensity
        candidateTextSize = heightForCandidates * 0.7f / scaledDensity
        heightForFullDisplayBar = 0
        heightForKeyboardMove = 0

        val keyboardFontSizeRatio = prefs.keyboardFontSize.getValue()/100f
        keyTextSize = (min(skbHeight * 0.06f, skbWidth * 0.045f) * keyboardFontSizeRatio).toInt()
        keyTextSmallSize = (min(skbHeight * 0.04f, skbWidth * 0.03f) * keyboardFontSizeRatio).toInt()
        keyXMargin = (prefs.keyXMargin.getValue() / 1000f * skbWidth).toInt()
        keyYMargin = (prefs.keyYMargin.getValue() / 1000f * skbHeight).toInt()
        inputAreaHeight = skbHeight + heightForCandidatesArea
    }

    var keyBoardHeightRatio: Float
        get() = keyboardHeightRatio
        set(keyBoardHeightRatio) {
            keyboardHeightRatio = if (keyBoardHeightRatio.isFinite()) keyBoardHeightRatio.coerceIn(0.1f, 1f) else 0.5f
            val internal = AppPrefs.getInstance().internal
            when (currentDisplayState) {
                DisplayState.PrimaryPortrait -> internal.heightRatioPrimaryPortrait.setValue(keyboardHeightRatio)
                DisplayState.SecondaryPortrait -> internal.heightRatioSecondaryPortrait.setValue(keyboardHeightRatio)
                DisplayState.SecondaryLandscape -> internal.heightRatioSecondaryLandscape.setValue(keyboardHeightRatio)
                DisplayState.SecondaryFullscreen -> internal.heightRatioSecondaryFullscreen.setValue(keyboardHeightRatio)
            }
        }

    var keyboardModeFloat:Boolean = false
        set(isFloatMode) {
            field = isFloatMode
        }

    val keyboardBottomPaddingPx: Int
        get() {
            val internal = AppPrefs.getInstance().internal
            val value = when (currentDisplayState) {
                DisplayState.PrimaryPortrait -> internal.bottomPaddingPrimaryPortrait.getValue()
                DisplayState.SecondaryPortrait -> internal.bottomPaddingSecondaryPortrait.getValue()
                DisplayState.SecondaryLandscape -> internal.bottomPaddingSecondaryLandscape.getValue()
                DisplayState.SecondaryFullscreen -> internal.bottomPaddingSecondaryFullscreen.getValue()
            }
            return value.coerceIn(0, mScreenHeight.coerceAtLeast(0) / 4)
        }

    val skbAreaHeight:Int
        get() = inputAreaHeight + keyboardBottomPaddingPx + systemNavbarWindowsBottom

    val leftMarginWidth:Int
        get() = (instance.inputAreaWidth - instance.skbWidth)/2

    companion object {
        private var mInstance: EnvironmentSingleton? = null
        @JvmStatic
        val instance: EnvironmentSingleton
            get() {
                if (null == mInstance) {
                    mInstance = EnvironmentSingleton()
                }
                return mInstance!!
            }
    }
}
