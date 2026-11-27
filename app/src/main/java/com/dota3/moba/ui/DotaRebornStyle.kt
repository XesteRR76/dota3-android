package com.dota3.moba.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import com.dota3.moba.render.HudCustomization

object DotaRebornStyle {
    const val BG_OBSIDIAN = 0xFF090C12.toInt()
    const val PANEL_DARK = 0xEA0E131D.toInt()
    const val PANEL_CARD = 0xE0141B29.toInt()
    const val BRONZE_GOLD = 0xFFC89B3C.toInt()
    const val BRONZE_DARK = 0xFF6B5220.toInt()
    const val GOLD_BRIGHT = 0xFFFDE047.toInt()
    const val CRIMSON_TOP = 0xFF7F1D1D.toInt()
    const val CRIMSON_BOT = 0xFF450A0A.toInt()
    const val EMERALD_TOP = 0xFF065F46.toInt()
    const val EMERALD_BOT = 0xFF022C22.toInt()
    const val SLATE_TOP = 0xFF1E293B.toInt()
    const val SLATE_BOT = 0xFF0F172A.toInt()

    /**
     * Beveled Dota 2 Reborn obsidian stone panel with metallic gold/bronze border & inner highlight.
     */
    fun beveledPanel(
        topColor: Int = 0xF0151C2B.toInt(),
        botColor: Int = 0xF00B0F18.toInt(),
        borderColor: Int = BRONZE_GOLD,
        radiusPx: Float = 14f,
        strokePx: Int = 2
    ): Drawable {
        val outerDark = GradientDrawable().apply {
            setColor(0xCC04060A.toInt())
            setStroke(strokePx + 2, 0xFF05070B.toInt())
            cornerRadius = radiusPx + 2f
        }
        val body = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(topColor, botColor)).apply {
            setStroke(strokePx, borderColor)
            cornerRadius = radiusPx
        }
        val innerHighlight = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(1, 0x33FDE68A)
            cornerRadius = (radiusPx - 2f).coerceAtLeast(2f)
        }
        return LayerDrawable(arrayOf(outerDark, body, innerHighlight)).apply {
            setLayerInset(1, 2, 2, 2, 2)
            setLayerInset(2, 4, 4, 4, 4)
        }
    }

    private fun dp(context: Context, v: Int): Int =
        (v * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)

    fun rebornButton(
        context: Context,
        label: String,
        topColor: Int = SLATE_TOP,
        botColor: Int = SLATE_BOT,
        borderColor: Int = BRONZE_GOLD,
        textSizeSp: Float = 11.5f,
        onClick: (Button) -> Unit
    ): Button = Button(context).apply {
        text = label
        textSize = textSizeSp
        setTextColor(Color.WHITE)
        setTypeface(null, Typeface.BOLD)
        background = beveledPanel(topColor, botColor, borderColor, radiusPx = dp(context, 6).toFloat(), strokePx = dp(context, 1).coerceAtLeast(2))
        setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6))
        minHeight = 0
        minimumHeight = 0
        minWidth = 0
        minimumWidth = 0
        gravity = Gravity.CENTER
        isAllCaps = false
        setShadowLayer(2f, 0f, 1f, Color.BLACK)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(dp(context, 3), dp(context, 2), dp(context, 3), dp(context, 2))
        }
        setOnClickListener { onClick(this) }
    }
}

object HudPrefs {
    private const val PREF_NAME = "dota3_hud_prefs"

    fun load(context: Context): HudCustomization {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return HudCustomization(
            joystickScale = sp.getFloat("joy_scale", 1.0f),
            skillScale = sp.getFloat("skill_scale", 1.0f),
            consoleScale = sp.getFloat("console_scale", 1.0f),
            minimapScale = sp.getFloat("mm_scale", 1.0f),
            joystickOffsetX = sp.getFloat("joy_ox", 0f),
            joystickOffsetY = sp.getFloat("joy_oy", 0f),
            skillOffsetX = sp.getFloat("skill_ox", 0f),
            skillOffsetY = sp.getFloat("skill_oy", 0f)
        ).clamped()
    }

    fun save(context: Context, custom: HudCustomization) {
        val c = custom.clamped()
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putFloat("joy_scale", c.joystickScale)
            .putFloat("skill_scale", c.skillScale)
            .putFloat("console_scale", c.consoleScale)
            .putFloat("mm_scale", c.minimapScale)
            .putFloat("joy_ox", c.joystickOffsetX)
            .putFloat("joy_oy", c.joystickOffsetY)
            .putFloat("skill_ox", c.skillOffsetX)
            .putFloat("skill_oy", c.skillOffsetY)
            .apply()
    }

    fun reset(context: Context): HudCustomization {
        val def = HudCustomization()
        save(context, def)
        return def
    }
}
