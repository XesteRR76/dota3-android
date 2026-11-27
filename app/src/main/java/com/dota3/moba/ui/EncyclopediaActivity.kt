package com.dota3.moba.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.dota3.moba.core.DotaAssetLoader
import com.dota3.moba.data.HeroCatalog
import com.dota3.moba.data.ItemCatalog

class EncyclopediaActivity : AppCompatActivity() {

    private lateinit var contentContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DotaAssetLoader.init(this)
        hideSystemBars()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(DotaRebornStyle.BG_OBSIDIAN)
            setPadding(36, 22, 36, 22)
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topBar.addView(TextView(this).apply {
            text = "DOTA 3 ARMORY & COMPENDIUM"
            textSize = 21f
            setTypeface(null, Typeface.BOLD)
            setTextColor(DotaRebornStyle.GOLD_BRIGHT)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        topBar.addView(tabBtn("HEROES (${HeroCatalog.allHeroes.size})") { showHeroes() })
        topBar.addView(tabBtn("ITEMS (${ItemCatalog.allItems.size})") { showItems() })
        topBar.addView(tabBtn("NEUTRALS (${ItemCatalog.neutralItems.size})") { showNeutrals() })
        topBar.addView(DotaRebornStyle.rebornButton(this, "BACK", DotaRebornStyle.CRIMSON_TOP, DotaRebornStyle.CRIMSON_BOT, 0xFFF87171.toInt()) { finish() })
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
                background = DotaRebornStyle.beveledPanel(0xEE131824.toInt(), 0xEE090D15.toInt(), h.primaryColor, 12f, 2)
                setPadding(18, 14, 18, 14)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = 12
                }
            }
            val top = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            DotaAssetLoader.getHeroPortrait(h.id)?.let { bmp ->
                top.addView(ImageView(this).apply {
                    setImageBitmap(bmp)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = LinearLayout.LayoutParams(150, 86).apply { rightMargin = 14 }
                })
            }
            val headCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            headCol.addView(TextView(this).apply {
                text = "${h.name.uppercase()} — ${h.title}  [${h.attribute.shortLabel}]"
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
            })
            headCol.addView(TextView(this).apply {
                text = "Innate: ${h.innateName} — ${h.innateDesc}\nFacets: ${h.facets.joinToString(" | ") { "${it.name}: ${it.description}" }}"
                textSize = 11.5f
                setTextColor(DotaRebornStyle.BRONZE_GOLD)
            })
            top.addView(headCol)
            card.addView(top)

            for (ab in h.abilities) {
                val abRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = 6
                    }
                }
                DotaAssetLoader.getAbilityIcon(ab.id)?.let { bmp ->
                    abRow.addView(ImageView(this).apply {
                        setImageBitmap(bmp)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        layoutParams = LinearLayout.LayoutParams(40, 40).apply { rightMargin = 10 }
                    })
                }
                val extra = buildString {
                    if (ab.scepterUpgrade.isNotEmpty()) append(" [Scepter: ${ab.scepterUpgrade}]")
                    if (ab.shardUpgrade.isNotEmpty()) append(" [Shard: ${ab.shardUpgrade}]")
                }
                abRow.addView(TextView(this).apply {
                    text = "[${ab.keyLabel}] ${ab.name}: ${ab.description}$extra"
                    textSize = 11.5f
                    setTextColor(0xFFCBD5E1.toInt())
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                card.addView(abRow)
            }
            contentContainer.addView(card)
        }
    }

    private fun showItems() {
        contentContainer.removeAllViews()
        for (item in ItemCatalog.allItems) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = DotaRebornStyle.beveledPanel(0xEE131824.toInt(), 0xEE090D15.toInt(), DotaRebornStyle.BRONZE_DARK, 10f, 2)
                setPadding(14, 10, 14, 10)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = 8
                }
            }
            DotaAssetLoader.getItemIcon(item.id)?.let { bmp ->
                card.addView(ImageView(this).apply {
                    setImageBitmap(bmp)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = LinearLayout.LayoutParams(62, 46).apply { rightMargin = 12 }
                })
            }
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val compStr = if (item.components.isNotEmpty()) {
                "  •  Craft: " + item.components.joinToString(" + ") { ItemCatalog.get(it)?.name ?: it }
            } else ""
            col.addView(TextView(this).apply {
                text = "${item.name} — ${item.cost}g  (${item.category.title})$compStr"
                textSize = 13.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(DotaRebornStyle.GOLD_BRIGHT)
            })
            col.addView(TextView(this).apply {
                text = item.description
                textSize = 11.5f
                setTextColor(0xFFCBD5E1.toInt())
            })
            card.addView(col)
            contentContainer.addView(card)
        }
    }

    private fun showNeutrals() {
        contentContainer.removeAllViews()
        for (n in ItemCatalog.neutralItems) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = DotaRebornStyle.beveledPanel(0xEE131824.toInt(), 0xEE090D15.toInt(), 0xFFA855F7.toInt(), 10f, 2)
                setPadding(14, 10, 14, 10)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = 8
                }
            }
            DotaAssetLoader.getItemIcon(n.id)?.let { bmp ->
                card.addView(ImageView(this).apply {
                    setImageBitmap(bmp)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = LinearLayout.LayoutParams(58, 44).apply { rightMargin = 12 }
                })
            }
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            col.addView(TextView(this).apply {
                text = "Tier ${n.tier}: ${n.name}"
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(0xFFD8B4FE.toInt())
            })
            col.addView(TextView(this).apply {
                text = n.description
                textSize = 11.5f
                setTextColor(0xFFCBD5E1.toInt())
            })
            card.addView(col)
            contentContainer.addView(card)
        }
    }

    private fun tabBtn(label: String, onClick: () -> Unit): Button =
        DotaRebornStyle.rebornButton(this, label, DotaRebornStyle.SLATE_TOP, DotaRebornStyle.SLATE_BOT, DotaRebornStyle.BRONZE_GOLD, 11.5f) {
            onClick()
        }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
