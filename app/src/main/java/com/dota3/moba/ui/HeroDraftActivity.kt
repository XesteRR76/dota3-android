package com.dota3.moba.ui

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.GridLayout
import android.widget.HorizontalScrollView
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

    private val draftedAllies = mutableListOf<String>()
    private val draftedEnemies = mutableListOf<String>()

    private lateinit var draftStrip: LinearLayout
    private lateinit var heroGrid: GridLayout
    private lateinit var detailPanel: LinearLayout
    private var pulseAnim: ObjectAnimator? = null

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DotaAssetLoader.init(this)
        hideSystemBars()

        mode = intent.getStringExtra("mode")?.let { runCatching { GameMode.valueOf(it) }.getOrNull() }
            ?: GameMode.ALL_PICK_5V5

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(DotaRebornStyle.BG_OBSIDIAN)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        // Top Bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topBar.addView(TextView(this).apply {
            text = "HERO DRAFT  •  ${mode.title.uppercase()}"
            textSize = 16.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(DotaRebornStyle.GOLD_BRIGHT)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        val topBtnsScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val topBtns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun addFilterBtn(label: String, attr: PrimaryAttribute?) {
            topBtns.addView(DotaRebornStyle.rebornButton(
                this, label,
                DotaRebornStyle.SLATE_TOP, DotaRebornStyle.SLATE_BOT,
                attr?.colorHex ?: DotaRebornStyle.BRONZE_DARK, 11f
            ) {
                filterAttr = attr
                populateGrid()
            })
        }
        addFilterBtn("ALL", null)
        PrimaryAttribute.entries.forEach { addFilterBtn(it.shortLabel, it) }

        topBtns.addView(DotaRebornStyle.rebornButton(
            this, "SHUFFLE BOTS",
            DotaRebornStyle.SLATE_TOP, DotaRebornStyle.SLATE_BOT, DotaRebornStyle.BRONZE_GOLD, 11f
        ) {
            rollBotRoster()
            renderDraftStrip(animateAll = true)
        })

        topBtns.addView(DotaRebornStyle.rebornButton(
            this, "BACK",
            DotaRebornStyle.CRIMSON_TOP, DotaRebornStyle.CRIMSON_BOT, 0xFFF87171.toInt(), 11f
        ) { finish() })
        topBtnsScroll.addView(topBtns)
        topBar.addView(topBtnsScroll)
        root.addView(topBar)

        // Animated 5v5 Team Draft Banner
        draftStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = DotaRebornStyle.beveledPanel(0xE8121723.toInt(), 0xE80A0D14.toInt(), DotaRebornStyle.BRONZE_GOLD, 12f, 2)
            setPadding(12, 8, 12, 8)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 8; bottomMargin = 8
            }
        }
        root.addView(draftStrip)

        // Body: Hero Grid + Spotlight Detail Panel
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        val gridScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f)
        }
        heroGrid = GridLayout(this).apply { columnCount = 3 }
        gridScroll.addView(heroGrid)
        body.addView(gridScroll)

        val detailScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.24f).apply {
                leftMargin = 16
            }
        }
        detailPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = DotaRebornStyle.beveledPanel(0xEE131926.toInt(), 0xEE0A0E17.toInt(), DotaRebornStyle.BRONZE_GOLD, 14f, 2)
            setPadding(18, 14, 18, 14)
        }
        detailScroll.addView(detailPanel)
        body.addView(detailScroll)

        root.addView(body)
        setContentView(root)

        rollBotRoster()
        renderDraftStrip(animateAll = true)
        populateGrid()
        renderDetail(animateSpotlight = true)
    }

    override fun onDestroy() {
        pulseAnim?.cancel()
        super.onDestroy()
    }

    private fun rollBotRoster() {
        val pool = HeroCatalog.allHeroes.map { it.id }.filter { it != selectedHero.id }.shuffled().toMutableList()
        draftedAllies.clear()
        draftedEnemies.clear()
        if (mode == GameMode.MID_1V1 || mode == GameMode.SANDBOX) {
            draftedEnemies.add(pool.removeAt(0))
        } else {
            repeat(4) { draftedAllies.add(pool.removeAt(0)) }
            repeat(5) { draftedEnemies.add(pool.removeAt(0)) }
        }
    }

    private fun syncPlayerInDraft() {
        // Ensure no duplicate if player selects a hero already in bot roster
        if (selectedHero.id in draftedAllies || selectedHero.id in draftedEnemies) {
            val used = (draftedAllies + draftedEnemies + selectedHero.id).toSet()
            val replacement = HeroCatalog.allHeroes.firstOrNull { it.id !in used }?.id
            if (replacement != null) {
                val ai = draftedAllies.indexOf(selectedHero.id)
                if (ai >= 0) draftedAllies[ai] = replacement
                val ei = draftedEnemies.indexOf(selectedHero.id)
                if (ei >= 0) draftedEnemies[ei] = replacement
            }
        }
    }

    private fun renderDraftStrip(animateAll: Boolean = false) {
        pulseAnim?.cancel()
        draftStrip.removeAllViews()
        syncPlayerInDraft()

        val radIds = if (selectedTeam == Team.RADIANT) listOf(selectedHero.id) + draftedAllies else draftedEnemies.toList()
        val direIds = if (selectedTeam == Team.DIRE) listOf(selectedHero.id) + draftedAllies else draftedEnemies.toList()

        fun buildSlot(heroId: String, isPlayerSlot: Boolean, isRadiant: Boolean, animIndex: Int): View {
            val h = HeroCatalog.get(heroId)
            val borderCol = when {
                isPlayerSlot -> DotaRebornStyle.GOLD_BRIGHT
                isRadiant -> 0xFF2ECC71.toInt()
                else -> 0xFFE74C3C.toInt()
            }
            val slot = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = DotaRebornStyle.beveledPanel(
                    if (isPlayerSlot) 0xFF1F2A3E.toInt() else 0xFF111622.toInt(),
                    0xFF090D15.toInt(),
                    borderCol, 8f, if (isPlayerSlot) 3 else 1
                )
                setPadding(4, 4, 4, 4)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(3, 0, 3, 0)
                }
            }
            DotaAssetLoader.getHeroPortrait(h.id)?.let { bmp ->
                slot.addView(ImageView(this).apply {
                    setImageBitmap(bmp)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(26))
                })
            }
            slot.addView(TextView(this).apply {
                text = if (isPlayerSlot) "★ ${h.name}" else h.name
                textSize = 9.5f
                maxLines = 1
                setTypeface(null, if (isPlayerSlot) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (isPlayerSlot) DotaRebornStyle.GOLD_BRIGHT else 0xFFCBD5E1.toInt())
                gravity = Gravity.CENTER
                setPadding(0, 2, 0, 0)
            })

            if (animateAll) {
                slot.alpha = 0f
                slot.scaleX = 0.72f
                slot.scaleY = 0.72f
                slot.animate()
                    .alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(260L)
                    .setStartDelay(animIndex * 45L)
                    .setInterpolator(OvershootInterpolator(1.1f))
                    .start()
            }
            if (isPlayerSlot) {
                pulseAnim = ObjectAnimator.ofPropertyValuesHolder(
                    slot,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.05f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.05f)
                ).apply {
                    duration = 850L
                    repeatMode = ObjectAnimator.REVERSE
                    repeatCount = ObjectAnimator.INFINITE
                    start()
                }
            }
            return slot
        }

        val radHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        radIds.forEachIndexed { idx, hid ->
            val isMe = (selectedTeam == Team.RADIANT && idx == 0)
            radHeader.addView(buildSlot(hid, isMe, isRadiant = true, animIndex = idx))
        }

        val vsBadge = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(10, 4, 10, 4)
        }
        vsBadge.addView(TextView(this).apply {
            text = "RADIANT  VS  DIRE"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(DotaRebornStyle.BRONZE_GOLD)
        })
        vsBadge.addView(TextView(this).apply {
            text = if (mode == GameMode.MID_1V1 || mode == GameMode.SANDBOX) "1v1" else "5v5"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })

        val direHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        direIds.forEachIndexed { idx, hid ->
            val isMe = (selectedTeam == Team.DIRE && idx == 0)
            direHeader.addView(buildSlot(hid, isMe, isRadiant = false, animIndex = idx + 5))
        }

        draftStrip.addView(radHeader)
        draftStrip.addView(vsBadge)
        draftStrip.addView(direHeader)
    }

    private fun populateGrid() {
        heroGrid.removeAllViews()
        val list = HeroCatalog.allHeroes.filter { filterAttr == null || it.attribute == filterAttr }
        for (h in list) {
            val isSel = h.id == selectedHero.id
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = DotaRebornStyle.beveledPanel(
                    if (isSel) 0xFF212C42.toInt() else 0xFF131926.toInt(),
                    0xFF0A0E17.toInt(),
                    if (isSel) DotaRebornStyle.GOLD_BRIGHT else h.primaryColor,
                    10f, if (isSel) 3 else 2
                )
                setPadding(6, 6, 6, 7)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(4, 4, 4, 4)
                }
                setOnClickListener {
                    selectedHero = h
                    selectedFacetIdx = 0
                    populateGrid()
                    renderDraftStrip(animateAll = false)
                    renderDetail(animateSpotlight = true)
                }
            }

            DotaAssetLoader.getHeroPortrait(h.id)?.let { bmp ->
                card.addView(ImageView(this).apply {
                    setImageBitmap(bmp)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52))
                })
            }

            card.addView(TextView(this).apply {
                text = "${h.attribute.shortLabel}  ${h.name}"
                textSize = 11.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (isSel) DotaRebornStyle.GOLD_BRIGHT else Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, 4, 0, 0)
            })
            card.addView(TextView(this).apply {
                text = h.roles.first().label
                textSize = 9.5f
                setTextColor(h.attribute.colorHex)
            })
            heroGrid.addView(card)
        }
    }

    private fun renderDetail(animateSpotlight: Boolean = false) {
        detailPanel.removeAllViews()
        val h = selectedHero

        val heroTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        DotaAssetLoader.getHeroPortrait(h.id)?.let { bmp ->
            val iv = ImageView(this).apply {
                setImageBitmap(bmp)
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(dp(92), dp(52)).apply { rightMargin = dp(8) }
            }
            if (animateSpotlight) {
                iv.alpha = 0f
                iv.scaleX = 0.88f
                iv.scaleY = 0.88f
                iv.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200L).start()
            }
            heroTop.addView(iv)
        }
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleCol.addView(TextView(this).apply {
            text = "${h.name.uppercase()} — ${h.title}"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(DotaRebornStyle.GOLD_BRIGHT)
        })
        titleCol.addView(TextView(this).apply {
            text = "[${h.attribute.shortLabel}] • ${if (h.isRanged) "Ranged (${h.attackRange.toInt()})" else "Melee"} • MS ${h.baseMoveSpeed.toInt()}\nInnate: ${h.innateName} — ${h.innateDesc}"
            textSize = 11f
            setTextColor(0xFF93C5FD.toInt())
        })
        heroTop.addView(titleCol)
        detailPanel.addView(heroTop)

        detailPanel.addView(TextView(this).apply {
            text = "CHOOSE DOTA 3 FACET:"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(DotaRebornStyle.BRONZE_GOLD)
            setPadding(0, 6, 0, 0)
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
                background = DotaRebornStyle.beveledPanel(
                    if (sel) 0xFF1A3352.toInt() else 0xFF0E1420.toInt(),
                    0xFF080B12.toInt(),
                    if (sel) 0xFF38BDF8.toInt() else 0xFF334155.toInt(),
                    10f, 2
                )
                setPadding(10, 6, 10, 6)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (idx == 0) rightMargin = 8
                }
                setOnClickListener {
                    selectedFacetIdx = idx
                    renderDetail(animateSpotlight = false)
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
                    layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply { rightMargin = dp(6) }
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
        optsRow.addView(optBtn("TEAM: ${selectedTeam.name}", if (selectedTeam == Team.RADIANT) DotaRebornStyle.EMERALD_TOP else DotaRebornStyle.CRIMSON_TOP) {
            selectedTeam = selectedTeam.enemy()
            renderDraftStrip(animateAll = true)
            renderDetail(animateSpotlight = false)
        })
        optsRow.addView(optBtn("LANE: ${laneNames[selectedLane]}", DotaRebornStyle.SLATE_TOP) {
            selectedLane = (selectedLane + 1) % 3; renderDetail(animateSpotlight = false)
        })
        optsRow.addView(optBtn("AI: ${selectedDiff.label}", DotaRebornStyle.SLATE_TOP) {
            val all = BotDifficulty.entries
            selectedDiff = all[(all.indexOf(selectedDiff) + 1) % all.size]; renderDetail(animateSpotlight = false)
        })
        detailPanel.addView(optsRow)

        detailPanel.addView(DotaRebornStyle.rebornButton(
            this,
            "LOCK IN & ENTER 3D BATTLE AS ${h.name.uppercase()}",
            DotaRebornStyle.CRIMSON_TOP, DotaRebornStyle.CRIMSON_BOT,
            DotaRebornStyle.GOLD_BRIGHT, 14f
        ) {
            val intent = Intent(this@HeroDraftActivity, GameActivity::class.java).apply {
                putExtra("heroId", h.id)
                putExtra("facetIdx", selectedFacetIdx)
                putExtra("team", selectedTeam.name)
                putExtra("lane", if (mode == GameMode.MID_1V1) 1 else selectedLane)
                putExtra("mode", mode.name)
                putExtra("diff", selectedDiff.name)
                putStringArrayListExtra("allyIds", ArrayList(draftedAllies))
                putStringArrayListExtra("enemyIds", ArrayList(draftedEnemies))
            }
            startActivity(intent)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        })
    }

    private fun optBtn(label: String, bg: Int, onClick: () -> Unit): Button =
        DotaRebornStyle.rebornButton(this, label, bg, DotaRebornStyle.SLATE_BOT, DotaRebornStyle.BRONZE_DARK, 11f) {
            onClick()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = 6
            }
        }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
