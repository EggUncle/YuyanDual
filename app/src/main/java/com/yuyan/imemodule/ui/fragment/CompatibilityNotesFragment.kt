package com.yuyan.imemodule.ui.fragment

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.yuyan.imemodule.R

class CompatibilityNotesFragment : Fragment(R.layout.fragment_compatibility_notes) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val document = requireContext().assets.open("docs/rp-duo-compatibility.html")
            .bufferedReader().use { it.readText() }
        view.findViewById<TextView>(R.id.compatibility_notes_text).text = HtmlCompat.fromHtml(
            document.substringAfter("<body>").substringBefore("</body>"),
            HtmlCompat.FROM_HTML_MODE_COMPACT
        )
        val bottomPadding = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            view.setPadding(
                view.paddingLeft, view.paddingTop, view.paddingRight,
                bottomPadding + insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }
}
