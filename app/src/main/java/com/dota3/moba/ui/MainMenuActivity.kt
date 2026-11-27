package com.dota3.moba.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.dota3.moba.core.SoundEngine
import com.dota3.moba.model.GameMode

class MainMenuActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SoundEngine.init()
        hideSystemBars()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0A0D14.toInt())
            setPadding(44, 28, 44, 28)
        }

        // Top Title Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleCol.addView(TextView(this).apply {
            text = "DOTA 3"
            textSize = 36f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFE53935.toInt())
            letterSpacing = 0.08f
        })
        titleCol.addView(TextView(this).apply {
            text = "ANCIENT REBIRTH  •  MOBILE MOBA"
            textSize = 13f
            setTextColor(0xFFF5B041.toInt())
            letterSpacing = 0.14f
        })
        header.addView(titleCol)

        val soundBtn = styledButton(if (SoundEngine.enabled) "SOUND: ON" else "SOUND: OFF", 0xFF1E293B.toInt(), 0xFF64748B.toInt()) { btn ->
            SoundEngine.enabled = !SoundEngine.enabled
            btn.text = if (SoundEngine.enabled) "SOUND: ON" else "SOUND: OFF"
        }
        header.addView(soundBtn)

        val guideBtn = styledButton("HEROES & ITEMS", 0xFF1E293B.toInt(), 0xFFF5B041.toInt()) {
            startActivity(Intent(this, EncyclopediaActivity::class.java))
        }.apply {
            (layoutParams as? LinearLayout.LayoutParams)?.leftMargin = 16
        }
        val guideLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = 16
        }
        header.addView(guideBtn, guideLp)
        root.addView(header)

        // Main Split: Play Modes on Left, Dota 3 Features on Right
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = 20
            }
        }

        // Left: Play Mode Cards
        val modesScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.35f)
        }
        val modesList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        for (mode in GameMode.entries) {
            modesList.addView(buildModeCard(mode))
        }
        modesScroll.addView(modesList)
        content.addView(modesScroll)

        // Right: Dota 3 Feature Showcase & Controls
        val infoScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                leftMargin = 24
            }
        }
        val infoPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg(0xFF121722.toInt(), 0xFF2B3548.toInt())
            setPadding(24, 20, 24, 20)
        }
        infoPanel.addView(TextView(this).apply {
            text = "WHAT'S IN DOTA 3"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFF5B041.toInt())
        })
        infoPanel.addView(TextView(this).apply {
            text = """
                • 12 Heroes (STR / AGI / INT / UNIVERSAL) incl. Dota 3's Chronos Prime
                • 24 Dota 3 Facets + Lv 10/15/20/25 Talent Trees
                • Aghanim's Scepter & Aghanim's Shard Upgrades
                • 44+ Items w/ Auto-Combine Recipes + 12 Neutral Tiers
                • Full 3-Lane Map: River, High-Ground Miss (25%), Destructible Trees
                • Last-Hitting, Denying (!), Tower Backdoor, Glyph & Buyback
                • Courier Delivery, TP Scrolls, Observer & Sentry Wards, Smoke & Dust
                • Roshan (Aegis + Cheese + Refresher), Tormentors, Twin Gates, Lotus & Runes
                • Day/Night Cycle & Real Grid Fog of War
                
                CONTROLS:
                • Left Thumb: Virtual Joystick (or Tap Map to Move)
                • Right Thumb: ATK (Hero), LH (Creep), ! (Deny)
                • Drag Q/W/E/R to Aim Skillshots or Tap for Auto-Aim
                • Top-Left Minimap: Tap/Drag to Pan Camera
            """.trimIndent()
            textSize = 12.5f
            setTextColor(0xFFCBD5E1.toInt())
            setPadding(0, 10, 0, 0)
            setLineSpacing(4f, 1.05f)
        })
        infoScroll.addView(infoPanel)
        content.addView(infoScroll)

        root.addView(content)
        setContentView(root)
    }

    private fun buildModeCard(mode: GameMode): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cardBg(0xFF151B28.toInt(), if (mode == GameMode.ALL_PICK_5V5) 0xFFE53935.toInt() else 0xFF334155.toInt())
            setPadding(22, 16, 22, 16)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 14
            }
        }
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textCol.addView(TextView(this).apply {
            text = mode.title.uppercase()
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        textCol.addView(TextView(this).apply {
            text = mode.subtitle
            textSize = 12f
            setTextColor(0xFF94A3B8.toInt())
            setPadding(0, 4, 0, 0)
        })
        card.addView(textCol)

        val playBtn = styledButton("PLAY", 0xFFB71C1C.toInt(), 0xFFF5B041.toInt()) {
            val intent = Intent(this, HeroDraftActivity::class.java)
            intent.putExtra("mode", mode.name)
            startActivity(intent)
        }
        card.addView(playBtn)
        card.setOnClickListener {
            val intent = Intent(this, HeroDraftActivity::class.java)
            intent.putExtra("mode", mode.name)
            startActivity(intent)
        }
        return card
    }

    private fun styledButton(label: String, bg: Int, border: Int, onClick: (Button) -> Unit): Button {
        return Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            background = cardBg(bg, border)
            setPadding(24, 12, 24, 12)
            setOnClickListener { onClick(this) }
        }
    }

    private fun cardBg(fill: Int, stroke: Int): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        setStroke(3, stroke)
        cornerRadius = 16f
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
