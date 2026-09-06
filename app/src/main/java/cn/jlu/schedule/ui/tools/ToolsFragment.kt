package cn.jlu.schedule.ui.tools

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import cn.jlu.schedule.R
import cn.jlu.schedule.ui.theme.ThemePaletteProvider

/** 工具页：汇聚绩点计算器等教务小工具（底部导航「工具」Tab） */
class ToolsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_tools, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val palette = ThemePaletteProvider.fromContext(requireContext())
        view.findViewById<View>(R.id.toolsCardStudy).background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 18f
            setColor(palette.panelAltBackground)
            setStroke(1, ColorUtils.blendARGB(palette.panelAltBackground, palette.iconTint, 0.16f))
        }
        view.findViewById<View>(R.id.iconToolStudy).backgroundTintList =
            android.content.res.ColorStateList.valueOf(palette.iconTint)
        view.findViewById<View>(R.id.rowGpaCalculator).setOnClickListener {
            startActivity(Intent(requireContext(), GpaCalculatorActivity::class.java))
        }
    }
}
