package cn.jlu.schedule.ui.theme

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import com.google.android.material.snackbar.Snackbar

object UiFeedback {
    fun showMessage(anchor: View?, message: String, palette: ThemePalette) {
        if (anchor == null) return
        Snackbar.make(anchor, message, Snackbar.LENGTH_SHORT)
            .setBackgroundTint(palette.panelBackground)
            .setTextColor(palette.textPrimary)
            .setActionTextColor(palette.iconTint)
            .setAction("知道了") { }
            .show()
    }

    fun stylePrimaryButton(button: Button, palette: ThemePalette) {
        button.backgroundTintList = null
        button.background = roundedDrawable(
            fillColor = palette.buttonBackground,
            strokeColor = ColorUtils.blendARGB(palette.buttonBackground, palette.iconTint, 0.25f)
        )
        button.setTextColor(palette.buttonText)
        button.isAllCaps = false
        button.minHeight = 0
        button.minimumHeight = 0
        button.setPadding(30, 18, 30, 18)
    }

    fun styleSecondaryButton(button: Button, palette: ThemePalette) {
        button.backgroundTintList = null
        button.background = roundedDrawable(
            fillColor = palette.panelAltBackground,
            strokeColor = ColorUtils.blendARGB(palette.panelAltBackground, palette.iconTint, 0.35f)
        )
        button.setTextColor(palette.textSecondary)
        button.isAllCaps = false
        button.minHeight = 0
        button.minimumHeight = 0
        button.setPadding(28, 16, 28, 16)
    }

    fun styleDangerButton(button: Button, palette: ThemePalette) {
        button.backgroundTintList = null
        val danger = if (palette.isDark) {
            ColorUtils.blendARGB(0xFF8B2525.toInt(), palette.panelBackground, 0.45f)
        } else {
            ColorUtils.blendARGB(0xFFD35454.toInt(), palette.buttonBackground, 0.45f)
        }
        val textColor = if (palette.isDark) 0xFFEF9A9A.toInt() else 0xFFFFFFFF.toInt()
        button.background = roundedDrawable(
            fillColor = danger,
            strokeColor = ColorUtils.blendARGB(danger, palette.textPrimary, 0.25f)
        )
        button.setTextColor(textColor)
        button.isAllCaps = false
        button.minHeight = 0
        button.minimumHeight = 0
        button.setPadding(28, 16, 28, 16)
    }

    fun styleInput(input: android.widget.EditText, palette: ThemePalette) {
        input.background = roundedDrawable(
            fillColor = palette.panelBackground,
            strokeColor = ColorUtils.blendARGB(palette.panelBackground, palette.iconTint, 0.30f)
        )
        input.setTextColor(palette.textPrimary)
        input.setHintTextColor(palette.textSecondary)
    }

    fun styleDialogSurface(dialog: AlertDialog, palette: ThemePalette) {
        dialog.window?.setBackgroundDrawable(
            roundedDrawable(
                fillColor = palette.panelAltBackground,
                strokeColor = ColorUtils.blendARGB(palette.panelAltBackground, palette.iconTint, 0.22f)
            )
        )
    }

    private fun roundedDrawable(fillColor: Int, strokeColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 18f
            setColor(fillColor)
            setStroke(2, strokeColor)
        }
    }
}
