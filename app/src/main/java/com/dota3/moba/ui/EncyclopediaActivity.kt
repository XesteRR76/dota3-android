package com.dota3.moba.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.dota3.moba.data.HeroCatalog
import com.dota3.moba.data.ItemCatalog

class EncyclopediaActivity : AppCompatActivity() {

    private lateinit var contentContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0A0D14.toInt())
            setPadding(36, 22, 36, 22)
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topBar.addView(TextView(this).apply {
            text = "DOTA 3 ARMORY & COMPENDIUM"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFF5B041.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        topBar.addView(tabBtn("HEROES (${HeroCatalog.allHeroes.size})") { showHeroes() })
        topBar.addView(tabBtn("ITEMS (${ItemCatalog.allItems.size})") { showItems() })
        topBar.addView(tabBtn("NEUTRALS (${ItemCatalog.neutralItems.size})") { showNeutrals() })
        topBar.addView(tabBtn("BACK") { finish() })
        root.addView(topBar)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = 14
            }
        }
        contentContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(contentContainer)
        root.addView(scroll)

        setContentView(root)
        showHeroes()
    }

    private fun showHeroes() {
        contentContainer.removeAllViews()
        for (h in HeroCatalog.allHeroes) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = boxBg(0xFF131824.toInt(), h.primaryColor)
                setPadding(20, 14, 20, 14)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = 12
                }
            }
            card.addView(TextView(this).apply {
                text = "${h.name.uppercase()} — ${h.title}  [${h.attribute.shortLabel}]  (${h.roles.joinToString { it.label }})"
                textSize = 15.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
            })
            card.addView(TextView(this).apply {
                text = "Innate: ${h.innateName} — ${h.innateDesc}\nFacets: ${h.facets.joinToString(" | ") { "${it.name}: ${it.description}" }}"
                textSize = 12f
                setTextColor(0xFFF5B041.toInt())
                setPadding(0, 4, 0, 6)
            })
            val abText = h.abilities.joinToString("\n") { ab ->
                val extra = buildString {
                    if (ab.scepterUpgrade.isNotEmpty()) append(" [Scepter: ${ab.scepterUpgrade}]")
                    if (ab.shardUpgrade.isNotEmpty()) append(" [Shard: ${ab.shardUpgrade}]")
                }
                "  [${ab.keyLabel}] ${ab.name}: ${ab.description}$extra"
            }
            card.addView(TextView(this).apply {
                text = abText
                textSize = 12f
                setTextColor(0xFFCBD5E1.toInt())
            })
            contentContainer.addView(card)
        }
    }

    private fun showItems() {
        contentContainer.removeAllViews()
        for (item in ItemCatalog.allItems) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = boxBg(0xFF131824.toInt(), item.color)
                setPadding(18, 12, 18, 12)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = 8
                }
            }
            card.addView(TextView(this).apply {
                text = "${item.name} [${item.shortCode}] — ${item.cost}g  (${item.category.title})"
                textSize = 14.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(0xFFF5B041.toInt())
            })
            card.addView(TextView(this).apply {
                text = item.description
                textSize = 12f
                setTextColor(0xFFCBD5E1.toInt())
            })
            contentContainer.addView(card)
        }
    }

    private fun showNeutrals() {
        contentContainer.removeAllViews()
        for (n in ItemCatalog.neutralItems) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = boxBg(0xFF131824.toInt(), 0xFFA855F7.toInt())
                setPadding(18, 12, 18, 12)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = 8
                }
            }
            card.addView(TextView(this).apply {
                text = "Tier ${n.tier}: ${n.name}"
                textSize = 14.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(0xFFD8B4FE.toInt())
            })
            card.addView(TextView(this).apply {
                text = n.description
                textSize = 12f
                setTextColor(0xFFCBD5E1.toInt())
            })
            contentContainer.addView(card)
        }
    }

    private fun tabBtn(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 12f
        setTextColor(Color.WHITE)
        background = boxBg(0xFF1E293B.toInt(), 0xFF475569.toInt())
        setPadding(18, 8, 18, 8)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = 10
        }
        setOnClickListener { onClick() }
    }

    private fun boxBg(fill: Int, stroke: Int) = GradientDrawable().apply {
        setColor(fill)
        setStroke(2, stroke)
        cornerRadius = 12f
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
