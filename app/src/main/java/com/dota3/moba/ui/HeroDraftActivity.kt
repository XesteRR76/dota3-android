package com.dota3.moba.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.GridLayout
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
import com.dota3.moba.model.*

class HeroDraftActivity : AppCompatActivity() {

    private var mode: GameMode = GameMode.ALL_PICK_5V5
    private var selectedHero: HeroDef = HeroCatalog.allHeroes.first()
    private var selectedFacetIdx: Int = 0
    private var selectedTeam: Team = Team.RADIANT
    private var selectedLane: Int = 1
    private var selectedDiff: BotDifficulty = BotDifficulty.NORMAL
    private var filterAttr: PrimaryAttribute? = null

    private lateinit var heroGrid: GridLayout
    private lateinit var detailPanel: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DotaAssetLoader.init(this)
        hideSystemBars()

        mode = intent.getStringExtra("mode")?.let { runCatching { GameMode.valueOf(it) }.getOrNull() }
            ?: GameMode.ALL_PICK_5V5

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0A0D14.toInt())
            setPadding(24, 16, 24, 16)
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topBar.addView(TextView(this).apply {
            text = "HERO DRAFT  •  ${mode.title.uppercase()}"
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFF5B041.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        fun addFilterBtn(label: String, attr: PrimaryAttribute?) {
            topBar.addView(Button(this).apply {
                text = label
                textSize = 11.5f
                setTextColor(Color.WHITE)
                background = boxBg(0xFF1E293B.toInt(), attr?.colorHex ?: 0xFF64748B.toInt())
                setPadding(14, 6, 14, 6)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    leftMargin = 8
                }
                setOnClickListener {
                    filterAttr = attr
                    populateGrid()
                }
            })
        }
        addFilterBtn("ALL", null)
        PrimaryAttribute.entries.forEach { addFilterBtn(it.shortLabel, it) }

        topBar.addView(Button(this).apply {
            text = "BACK"
            textSize = 11.5f
            setTextColor(Color.WHITE)
            background = boxBg(0xFF334155.toInt(), 0xFF64748B.toInt())
            setPadding(16, 6, 16, 6)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = 14
            }
            setOnClickListener { finish() }
        })
        root.addView(topBar)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = 12
            }
        }

        val gridScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f)
        }
        heroGrid = GridLayout(this).apply { columnCount = 3 }
        gridScroll.addView(heroGrid)
        body.addView(gridScroll)

        val detailScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.25f).apply {
                leftMargin = 18
            }
        }
        detailPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = boxBg(0xFF121722.toInt(), 0xFF2D3748.toInt())
            setPadding(18, 14, 18, 14)
        }
        detailScroll.addView(detailPanel)
        body.addView(detailScroll)

        root.addView(body)
        setContentView(root)

        populateGrid()
        renderDetail()
    }

    private fun populateGrid() {
        heroGrid.removeAllViews()
        val list = HeroCatalog.allHeroes.filter { filterAttr == null || it.attribute == filterAttr }
        for (h in list) {
            val isSel = h.id == selectedHero.id
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = boxBg(
                    if (isSel) 0xFF232D42.toInt() else 0xFF141A26.toInt(),
                    if (isSel) 0xFFF5B041.toInt() else h.primaryColor
                )
                setPadding(6, 6, 6, 8)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(5, 5, 5, 5)
                }
                setOnClickListener {
                    selectedHero = h
                    selectedFacetIdx = 0
                    populateGrid()
                    renderDetail()
                }
            }

            DotaAssetLoader.getHeroPortrait(h.id)?.let { bmp ->
                card.addView(ImageView(this).apply {
                    setImageBitmap(bmp)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 96)
                })
            }

            card.addView(TextView(this).apply {
                text = "${h.attribute.shortLabel}  ${h.name}"
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, 4, 0, 0)
            })
            card.addView(TextView(this).apply {
                text = h.roles.first().label
                textSize = 10f
                setTextColor(h.attribute.colorHex)
            })
            heroGrid.addView(card)
        }
    }

    private fun renderDetail() {
        detailPanel.removeAllViews()
        val h = selectedHero

        val heroTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        DotaAssetLoader.getHeroPortrait(h.id)?.let { bmp ->
            heroTop.addView(ImageView(this).apply {
                setImageBitmap(bmp)
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(160, 92).apply { rightMargin = 14 }
            })
        }
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleCol.addView(TextView(this).apply {
            text = "${h.name.uppercase()} — ${h.title}"
            textSize = 16.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        titleCol.addView(TextView(this).apply {
            text = "[${h.attribute.shortLabel}] • ${if (h.isRanged) "Ranged (${h.attackRange.toInt()})" else "Melee"} • MS ${h.baseMoveSpeed.toInt()}\nInnate: ${h.innateName} — ${h.innateDesc}"
            textSize = 11.5f
            setTextColor(0xFF93C5FD.toInt())
        })
        heroTop.addView(titleCol)
        detailPanel.addView(heroTop)

        detailPanel.addView(TextView(this).apply {
            text = "CHOOSE DOTA 3 FACET:"
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFF5B041.toInt())
            setPadding(0, 8, 0, 0)
        })
        val facetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 4; bottomMargin = 8
            }
        }
        h.facets.forEachIndexed { idx, facet ->
            val sel = idx == selectedFacetIdx
            val fCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = boxBg(if (sel) 0xFF1E3A5F.toInt() else 0xFF0F172A.toInt(), if (sel) 0xFF38BDF8.toInt() else 0xFF334155.toInt())
                setPadding(10, 6, 10, 6)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (idx == 0) rightMargin = 8
                }
                setOnClickListener {
                    selectedFacetIdx = idx
                    renderDetail()
                }
            }
            fCard.addView(TextView(this).apply {
                text = facet.name
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
            })
            fCard.addView(TextView(this).apply {
                text = facet.description
                textSize = 10.5f
                setTextColor(0xFFCBD5E1.toInt())
            })
            facetRow.addView(fCard)
        }
        detailPanel.addView(facetRow)

        // Abilities with real Dota 2 ability icons!
        for (ab in h.abilities) {
            val abRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = 5
                }
            }
            DotaAssetLoader.getAbilityIcon(ab.id)?.let { bmp ->
                abRow.addView(ImageView(this).apply {
                    setImageBitmap(bmp)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = LinearLayout.LayoutParams(44, 44).apply { rightMargin = 10 }
                })
            }
            abRow.addView(TextView(this).apply {
                text = "[${ab.keyLabel}] ${ab.name}: ${ab.description}"
                textSize = 11f
                setTextColor(0xFFE2E8F0.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            detailPanel.addView(abRow)
        }

        val optsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 6; bottomMargin = 10
            }
        }
        val laneNames = listOf("TOP", "MID", "BOT")
        optsRow.addView(optBtn("TEAM: ${selectedTeam.name}", if (selectedTeam == Team.RADIANT) 0xFF14532D.toInt() else 0xFF7F1D1D.toInt()) {
            selectedTeam = selectedTeam.enemy(); renderDetail()
        })
        optsRow.addView(optBtn("LANE: ${laneNames[selectedLane]}", 0xFF1E293B.toInt()) {
            selectedLane = (selectedLane + 1) % 3; renderDetail()
        })
        optsRow.addView(optBtn("AI: ${selectedDiff.label}", 0xFF1E293B.toInt()) {
            val all = BotDifficulty.entries
            selectedDiff = all[(all.indexOf(selectedDiff) + 1) % all.size]; renderDetail()
        })
        detailPanel.addView(optsRow)

        detailPanel.addView(Button(this).apply {
            text = "ENTER 3D BATTLE AS ${h.name.uppercase()}"
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = boxBg(0xFFB71C1C.toInt(), 0xFFF5B041.toInt())
            setPadding(24, 12, 24, 12)
            setOnClickListener {
                val intent = Intent(this@HeroDraftActivity, GameActivity::class.java).apply {
                    putExtra("heroId", h.id)
                    putExtra("facetIdx", selectedFacetIdx)
                    putExtra("team", selectedTeam.name)
                    putExtra("lane", if (mode == GameMode.MID_1V1) 1 else selectedLane)
                    putExtra("mode", mode.name)
                    putExtra("diff", selectedDiff.name)
                }
                startActivity(intent)
            }
        })
    }

    private fun optBtn(label: String, bg: Int, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 11f
        setTextColor(Color.WHITE)
        background = boxBg(bg, 0xFF64748B.toInt())
        setPadding(10, 6, 10, 6)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = 8
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
