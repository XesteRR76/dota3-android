package com.dota3.moba.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.dota3.moba.core.DotaAssetLoader
import com.dota3.moba.core.SoundEngine
import com.dota3.moba.model.GameMode

class MainMenuActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DotaAssetLoader.init(this)
        SoundEngine.init()
        hideSystemBars()

        val frame = FrameLayout(this)

        DotaAssetLoader.getSplashBanner()?.let { banner ->
            frame.addView(ImageView(this).apply {
                setImageBitmap(banner)
                scaleType = ImageView.ScaleType.CENTER_CROP
                alpha = 0.36f
            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }

        val dp = { v: Int -> (v * resources.displayMetrics.density).toInt().coerceAtLeast(1) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xA607090F.toInt())
            setPadding(dp(22), dp(14), dp(22), dp(14))
        }

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
            setShadowLayer(6f, 0f, 2f, Color.BLACK)
            letterSpacing = 0.08f
        })
        titleCol.addView(TextView(this).apply {
            text = "ANCIENT REBIRTH  •  LIBGDX 3D MOBILE MOBA"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(DotaRebornStyle.BRONZE_GOLD)
            letterSpacing = 0.14f
        })
        header.addView(titleCol)

        header.addView(DotaRebornStyle.rebornButton(
            this,
            if (SoundEngine.enabled) "SOUND: ON" else "SOUND: OFF",
            DotaRebornStyle.SLATE_TOP, DotaRebornStyle.SLATE_BOT, DotaRebornStyle.BRONZE_DARK, 12f
        ) { btn ->
            SoundEngine.enabled = !SoundEngine.enabled
            btn.text = if (SoundEngine.enabled) "SOUND: ON" else "SOUND: OFF"
        })

        header.addView(DotaRebornStyle.rebornButton(
            this, "HEROES & ARMORY",
            DotaRebornStyle.SLATE_TOP, DotaRebornStyle.SLATE_BOT, DotaRebornStyle.BRONZE_GOLD, 12f
        ) {
            startActivity(Intent(this, EncyclopediaActivity::class.java))
        }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = 12
            }
        })
        root.addView(header)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = 18
            }
        }

        val modesScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.32f)
        }
        val modesList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (mode in GameMode.entries) {
            modesList.addView(buildModeCard(mode))
        }
        modesScroll.addView(modesList)
        content.addView(modesScroll)

        val infoScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                leftMargin = 22
            }
        }
        val infoPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = DotaRebornStyle.beveledPanel(0xDF121723.toInt(), 0xDF090C14.toInt(), DotaRebornStyle.BRONZE_GOLD, 14f, 2)
            setPadding(22, 18, 22, 18)
        }
        infoPanel.addView(TextView(this).apply {
            text = "DOTA 2 REBORN 3D ENGINE & CONTROLS"
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(DotaRebornStyle.GOLD_BRIGHT)
        })
        infoPanel.addView(TextView(this).apply {
            text = """
                • LibGDX 3D Dark-Fantasy Движок: гладкие .obj модели героев, крипов, вышек, казарм, Тронов, деревьев, Рошана и 3D-снарядов с UV-текстурами, реальными тенями и Туманом Войны
                • Классическая камера Dota (~60°) со смещением над консолью, плавным опережением движения, сдвигом к дальним скиллшотам (Hook / Sun Strike) и отдачей на критах/ультах
                • 0ms отклик атаки в стиле MLBB + 3-ударное комбо оружия с 3D-шлейфами и искрами
                • Портреты врагов возле ATK для мгновенного таргет-лока + Тактические команды союзным ботам (PUSH / ROSH / DEF / BACK)
                • Крафт-дерево предметов в магазине (покупка по частям в Quick-Buy) + кастомизация размера и позиции HUD
            """.trimIndent()
            textSize = 12f
            setTextColor(0xFFE2E8F0.toInt())
            setPadding(0, 10, 0, 0)
            setLineSpacing(4f, 1.05f)
        })
        infoScroll.addView(infoPanel)
        content.addView(infoScroll)

        root.addView(content)
        frame.addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(frame)
    }

    private fun buildModeCard(mode: GameMode): View {
        val isFeatured = mode == GameMode.ALL_PICK_5V5
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = DotaRebornStyle.beveledPanel(
                if (isFeatured) 0xE61C1822.toInt() else 0xE0131926.toInt(),
                0xE00A0D15.toInt(),
                if (isFeatured) DotaRebornStyle.GOLD_BRIGHT else DotaRebornStyle.BRONZE_DARK,
                14f, if (isFeatured) 3 else 2
            )
            setPadding(20, 14, 20, 14)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 12
            }
        }
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textCol.addView(TextView(this).apply {
            text = mode.title.uppercase()
            textSize = 16.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (isFeatured) DotaRebornStyle.GOLD_BRIGHT else Color.WHITE)
        })
        textCol.addView(TextView(this).apply {
            text = mode.subtitle
            textSize = 11.5f
            setTextColor(0xFF94A3B8.toInt())
            setPadding(0, 4, 0, 0)
        })
        card.addView(textCol)

        val playBtn = DotaRebornStyle.rebornButton(
            this, "PLAY",
            DotaRebornStyle.CRIMSON_TOP, DotaRebornStyle.CRIMSON_BOT, DotaRebornStyle.GOLD_BRIGHT, 13f
        ) {
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

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
