package com.dota3.moba.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
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

        // Epic Dota 3 Splash Banner backdrop
        DotaAssetLoader.getSplashBanner()?.let { banner ->
            frame.addView(ImageView(this).apply {
                setImageBitmap(banner)
                scaleType = ImageView.ScaleType.CENTER_CROP
                alpha = 0.34f
            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0x99080A10.toInt())
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
            text = "ANCIENT REBIRTH  •  3D MOBILE MOBA"
            textSize = 13f
            setTextColor(0xFFF5B041.toInt())
            letterSpacing = 0.14f
        })
        header.addView(titleCol)

        val soundBtn = styledButton(if (SoundEngine.enabled) "SOUND: ON" else "SOUND: OFF", 0xDD1E293B.toInt(), 0xFF64748B.toInt()) { btn ->
            SoundEngine.enabled = !SoundEngine.enabled
            btn.text = if (SoundEngine.enabled) "SOUND: ON" else "SOUND: OFF"
        }
        header.addView(soundBtn)

        val guideBtn = styledButton("HEROES & ITEMS", 0xDD1E293B.toInt(), 0xFFF5B041.toInt()) {
            startActivity(Intent(this, EncyclopediaActivity::class.java))
        }
        val guideLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = 16
        }
        header.addView(guideBtn, guideLp)
        root.addView(header)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = 20
            }
        }

        val modesScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.35f)
        }
        val modesList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (mode in GameMode.entries) {
            modesList.addView(buildModeCard(mode))
        }
        modesScroll.addView(modesList)
        content.addView(modesScroll)

        val infoScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                leftMargin = 24
            }
        }
        val infoPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg(0xCC111622.toInt(), 0xFF334155.toInt())
            setPadding(24, 20, 24, 20)
        }
        infoPanel.addView(TextView(this).apply {
            text = "DOTA 3 — DOTA HUD & MLBB CONTROLS"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFF5B041.toInt())
        })
        infoPanel.addView(TextView(this).apply {
            text = """
                • 3D OpenGL ES движок: детальные 3D-герои со своим оружием, крипы с щитами, катапульты, вышки-шпили, Троны Древних, Рошан, Торменторы, плавная 3D-река и брусчатка линий
                • Оригинальная нижняя консоль как в Dota 2:
                  - Портрет героя, кружок уровня, шкала XP и тап-центрирование камеры
                  - Атрибуты STR/AGI/INT, Урон/Броня/Скорость, Древо Талантов, Scepter/Shard, баффы
                  - Сетка предметов 3x2 + слоты TP и Нейтрального предмета (предметы вроде Blink/Ward можно тянуть для прицела!)
                • Эргономика пальцами в стиле MLBB:
                  - Левый палец: плавный джойстик | Правый: веер Q/W/E/R и удержание ATK / CREEP / TWR/!
                  - Отмена каста на CANCEL X прямо над дугой скиллов + зум двумя пальцами или кнопкой ZOOM
            """.trimIndent()
            textSize = 12.5f
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
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cardBg(0xDD151B28.toInt(), if (mode == GameMode.ALL_PICK_5V5) 0xFFE53935.toInt() else 0xFF334155.toInt())
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
