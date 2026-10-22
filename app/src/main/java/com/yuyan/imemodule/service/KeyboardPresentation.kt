package com.yuyan.imemodule.service

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.Display
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.view.WindowCompat
import com.yuyan.imemodule.R
import com.yuyan.imemodule.keyboard.InputView

/** A touchable lower-screen keyboard that leaves the editor's input focus intact. */
class KeyboardPresentation(
    ctx: Context,
    display: Display,
    private val service: ImeService,
) : Presentation(ContextThemeWrapper(ctx, R.style.Theme_AppTheme), display) {
    lateinit var inputView: InputView
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setBackgroundDrawable(ColorDrawable(Color.BLACK))
            setGravity(Gravity.BOTTOM)
            setTitle("Yuyan RP Duo secondary keyboard")
            WindowCompat.setDecorFitsSystemWindows(this, false)
        }
        val root = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        inputView = InputView(context, service)
        root.addView(inputView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM,
        ))
        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
    }
}
