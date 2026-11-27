package com.dota3.moba.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.dota3.moba.core.DotaAssetLoader
import com.dota3.moba.core.SoundEngine
import com.dota3.moba.data.ItemCatalog
import com.dota3.moba.engine.GameWorld
import com.dota3.moba.model.*
import com.dota3.moba.render.HudCustomization
import com.dota3.moba.render.World3DRenderer
import kotlin.math.roundToInt

class GameActivity : AndroidApplication(), GameHudListener {

    private lateinit var world: GameWorld
    private lateinit var renderer3D: World3DRenderer
    private lateinit var hudOverlay: MlbbHudOverlayView
    private lateinit var overlayContainer: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        DotaAssetLoader.init(this)
        SoundEngine.init()

        val heroId = intent.getStringExtra("heroId") ?: "juggernaut"
        val facetIdx = intent.getIntExtra("facetIdx", 0)
        val team = intent.getStringExtra("team")?.let { runCatching { Team.valueOf(it) }.getOrNull() } ?: Team.RADIANT
        val lane = intent.getIntExtra("lane", 1)
        val mode = intent.getStringExtra("mode")?.let { runCatching { GameMode.valueOf(it) }.getOrNull() } ?: GameMode.ALL_PICK_5V5
        val diff = intent.getStringExtra("diff")?.let { runCatching { BotDifficulty.valueOf(it) }.getOrNull() } ?: BotDifficulty.NORMAL
        val allyIds = intent.getStringArrayListExtra("allyIds") ?: arrayListOf()
        val enemyIds = intent.getStringArrayListExtra("enemyIds") ?: arrayListOf()

        world = GameWorld(heroId, facetIdx, team, lane, mode, diff, draftedAllyHeroIds = allyIds, draftedEnemyHeroIds = enemyIds)
        renderer3D = World3DRenderer(world) { dt ->
            world.step(dt)
        }

        val config = AndroidApplicationConfiguration().apply {
            useAccelerometer = false
            useCompass = false
            useGyroscope = false
            useImmersiveMode = true
            r = 8; g = 8; b = 8; a = 8
            depth = 16
            numSamples = 0
        }
        val gdxView = initializeForView(renderer3D, config)

        hudOverlay = MlbbHudOverlayView(this, world, renderer3D, this)
        overlayContainer = FrameLayout(this).apply {
            visibility = View.GONE
            isClickable = true
            isFocusable = true
        }

        val rootFrame = FrameLayout(this).apply {
            addView(gdxView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(hudOverlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(overlayContainer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        setContentView(rootFrame)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (overlayContainer.visibility == View.VISIBLE) {
            if (!world.matchOver) {
                closeOverlay()
            }
        } else {
            onOpenPauseMenu()
        }
    }

    private fun showOverlay(panel: View, scrimColor: Int = 0xAA05080E.toInt(), dismissOnScrim: Boolean = true, centered: Boolean = true) {
        overlayContainer.removeAllViews()
        overlayContainer.setBackgroundColor(scrimColor)
        overlayContainer.setOnClickListener {
            if (dismissOnScrim) closeOverlay()
        }
        panel.isClickable = true
        val lp = FrameLayout.LayoutParams(
            if (centered) (resources.displayMetrics.widthPixels * 0.88f).toInt().coerceAtMost(1420) else ViewGroup.LayoutParams.WRAP_CONTENT,
            if (centered) (resources.displayMetrics.heightPixels * 0.86f).toInt().coerceAtMost(820) else ViewGroup.LayoutParams.WRAP_CONTENT,
            if (centered) Gravity.CENTER else Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply {
            if (!centered) topMargin = 14
        }
        overlayContainer.addView(panel, lp)
        overlayContainer.visibility = View.VISIBLE
        panel.alpha = 0f
        panel.scaleX = 0.96f
        panel.scaleY = 0.96f
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150L).start()
    }

    private fun closeOverlay() {
        synchronized(world) { world.paused = false }
        hudOverlay.customizePreviewMode = false
        overlayContainer.visibility = View.GONE
        overlayContainer.removeAllViews()
        hideSystemBars()
    }

    // ==================== 1. EMBEDDED SHOP & CRAFT/RECIPE TREE OVERLAY ====================

    override fun onOpenShop() {
        synchronized(world) { world.playerHero.moveIntent.set(0f, 0f) }
        val ph = world.playerHero
        var selectedCat = ItemCategory.CORE
        var inspectedItem = ItemCatalog.get(ph.quickBuyItemId ?: "bkb") ?: ItemCatalog.get("bkb") ?: ItemCatalog.allItems.first()

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = DotaRebornStyle.beveledPanel(0xED131926.toInt(), 0xED090D15.toInt(), DotaRebornStyle.BRONZE_GOLD, 16f, 3)
            setPadding(22, 16, 22, 16)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val goldTv = TextView(this).apply {
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(DotaRebornStyle.GOLD_BRIGHT)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(goldTv)

        if (world.mode == GameMode.SANDBOX) {
            header.addView(DotaRebornStyle.rebornButton(this, "+10k Gold", DotaRebornStyle.EMERALD_TOP, DotaRebornStyle.EMERALD_BOT, 0xFF34D399.toInt()) {
                synchronized(world) { ph.gold += 10000; ph.recalcStats() }
                onOpenShop()
            })
            header.addView(DotaRebornStyle.rebornButton(this, "+LVL", 0xFF1E3A8A.toInt(), 0xFF0F172A.toInt(), 0xFF60A5FA.toInt()) {
                synchronized(world) { ph.addXp(1500) }
                onOpenShop()
            })
            header.addView(DotaRebornStyle.rebornButton(this, "Reset CD", 0xFF7C2D12.toInt(), 0xFF451A03.toInt(), 0xFFF59E0B.toInt()) {
                synchronized(world) {
                    for (i in 0..3) ph.abilityCooldowns[i] = 0f
                    ph.hp = ph.maxHp; ph.mana = ph.maxMana
                }
            })
        }

        header.addView(DotaRebornStyle.rebornButton(this, "COURIER", 0xFF78350F.toInt(), 0xFF451A03.toInt(), DotaRebornStyle.BRONZE_GOLD) {
            val sent = synchronized(world) { world.dispatchCourier(ph) }
            Toast.makeText(this, if (sent) "Courier dispatched!" else "Stash empty!", Toast.LENGTH_SHORT).show()
        })
        header.addView(DotaRebornStyle.rebornButton(this, "✕ CLOSE", DotaRebornStyle.CRIMSON_TOP, DotaRebornStyle.CRIMSON_BOT, 0xFFF87171.toInt()) {
            closeOverlay()
        })
        panel.addView(header)

        val tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 8; bottomMargin = 8
            }
        }
        val bodyRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        val leftListScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.18f)
        }
        val itemListLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        leftListScroll.addView(itemListLayout)

        val treeScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.95f).apply {
                leftMargin = 14
            }
        }
        val treePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = DotaRebornStyle.beveledPanel(0xE00E1420.toInt(), 0xE0080B12.toInt(), DotaRebornStyle.BRONZE_DARK, 12f, 2)
            setPadding(16, 14, 16, 14)
        }
        treeScroll.addView(treePanel)

        bodyRow.addView(leftListScroll)
        bodyRow.addView(treeScroll)

        lateinit var refreshShop: () -> Unit

        fun renderCraftTree() {
            synchronized(world) {
                treePanel.removeAllViews()
                val effCost = world.effectiveItemCost(ph, inspectedItem)
                val canAfford = ph.gold >= effCost
                val ownedIds = (ph.inventory + ph.stash).map { it.itemId }

                val topHeader = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                DotaAssetLoader.getItemIcon(inspectedItem.id)?.let { bmp ->
                    topHeader.addView(ImageView(this).apply {
                        setImageBitmap(bmp)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        layoutParams = LinearLayout.LayoutParams(72, 54).apply { rightMargin = 12 }
                    })
                }
                val titleBox = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                titleBox.addView(TextView(this).apply {
                    text = inspectedItem.name.uppercase()
                    textSize = 14.5f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(DotaRebornStyle.GOLD_BRIGHT)
                })
                titleBox.addView(TextView(this).apply {
                    text = "Cost: ${effCost}g" + if (effCost < inspectedItem.cost) " (Base ${inspectedItem.cost}g)" else ""
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (canAfford) 0xFF4ADE80.toInt() else 0xFF94A3B8.toInt())
                })
                topHeader.addView(titleBox)
                treePanel.addView(topHeader)

                treePanel.addView(TextView(this).apply {
                    text = inspectedItem.description
                    textSize = 11.5f
                    setTextColor(0xFFE2E8F0.toInt())
                    setPadding(0, 8, 0, 10)
                })

                // Action row: PIN + BUY
                val actRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = 12
                    }
                }
                val isPinned = ph.quickBuyItemId == inspectedItem.id
                actRow.addView(DotaRebornStyle.rebornButton(
                    this,
                    if (isPinned) "★ PINNED" else "PIN QUICK-BUY",
                    if (isPinned) 0xFF1E3A8A.toInt() else DotaRebornStyle.SLATE_TOP,
                    DotaRebornStyle.SLATE_BOT,
                    if (isPinned) 0xFF38BDF8.toInt() else DotaRebornStyle.BRONZE_GOLD
                ) {
                    synchronized(world) { ph.quickBuyItemId = inspectedItem.id }
                    Toast.makeText(this, "Quick-Buy set to ${inspectedItem.name} craft tree!", Toast.LENGTH_SHORT).show()
                    renderCraftTree()
                })
                actRow.addView(DotaRebornStyle.rebornButton(
                    this,
                    "BUY (${effCost}g)",
                    if (canAfford) DotaRebornStyle.EMERALD_TOP else DotaRebornStyle.SLATE_TOP,
                    if (canAfford) DotaRebornStyle.EMERALD_BOT else DotaRebornStyle.SLATE_BOT,
                    if (canAfford) 0xFF34D399.toInt() else 0xFF475569.toInt()
                ) {
                    val ok = synchronized(world) {
                        if (world.buyItem(ph, inspectedItem.id)) {
                            world.dispatchCourier(ph)
                            true
                        } else false
                    }
                    if (ok) refreshShop()
                    else Toast.makeText(this, "Need ${effCost}g or free slot!", Toast.LENGTH_SHORT).show()
                })
                treePanel.addView(actRow)

                // Hierarchical Craft / Recipe Tree
                val tree = ItemCatalog.buildRecipeTree(inspectedItem.id, ownedIds)
                if (tree != null && tree.children.isNotEmpty()) {
                    treePanel.addView(TextView(this).apply {
                        text = "CRAFT / RECIPE TREE (Tap component to buy):"
                        textSize = 11.5f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(DotaRebornStyle.BRONZE_GOLD)
                        setPadding(0, 2, 0, 6)
                    })

                    fun addTreeNode(node: ItemCatalog.RecipeNode, depth: Int) {
                        val indent = depth * 22
                        val nodeAfford = !node.owned && ph.gold >= world.effectiveItemCost(ph, node.item)
                        val nodeCost = world.effectiveItemCost(ph, node.item)
                        val row = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            background = DotaRebornStyle.beveledPanel(
                                if (node.owned) 0xCC064E3B.toInt() else 0xCC161E2E.toInt(),
                                0xCC0B101A.toInt(),
                                if (node.owned) 0xFF34D399.toInt() else if (nodeAfford) DotaRebornStyle.BRONZE_GOLD else 0xFF334155.toInt(),
                                8f, 2
                            )
                            setPadding(10, 6, 10, 6)
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                                leftMargin = indent
                                bottomMargin = 5
                            }
                            setOnClickListener {
                                if (depth > 0 && !node.owned) {
                                    val bought = synchronized(world) {
                                        if (world.buyItem(ph, node.item.id)) {
                                            world.dispatchCourier(ph)
                                            true
                                        } else false
                                    }
                                    if (bought) {
                                        refreshShop()
                                    } else {
                                        inspectedItem = node.item
                                        renderCraftTree()
                                    }
                                }
                            }
                        }
                        if (depth > 0) {
                            row.addView(TextView(this).apply {
                                text = "└ "
                                textSize = 12f
                                setTextColor(DotaRebornStyle.BRONZE_GOLD)
                            })
                        }
                        DotaAssetLoader.getItemIcon(node.item.id)?.let { bmp ->
                            row.addView(ImageView(this).apply {
                                setImageBitmap(bmp)
                                scaleType = ImageView.ScaleType.CENTER_CROP
                                layoutParams = LinearLayout.LayoutParams(40, 30).apply { rightMargin = 8 }
                            })
                        }
                        row.addView(TextView(this).apply {
                            text = node.item.name
                            textSize = 11.5f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.WHITE)
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        })
                        row.addView(TextView(this).apply {
                            text = if (node.owned) "OWNED ✓" else "${nodeCost}g"
                            textSize = 11f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(if (node.owned) 0xFF4ADE80.toInt() else if (nodeAfford) DotaRebornStyle.GOLD_BRIGHT else 0xFF94A3B8.toInt())
                        })
                        treePanel.addView(row)
                        for (child in node.children) {
                            addTreeNode(child, depth + 1)
                        }
                    }
                    addTreeNode(tree, 0)

                    val recPrice = (inspectedItem.cost - inspectedItem.components.sumOf { ItemCatalog.get(it)?.cost ?: 0 }).coerceAtLeast(0)
                    if (recPrice > 0) {
                        treePanel.addView(TextView(this).apply {
                            text = "• Auto-combines automatically (Completion fee: ${recPrice}g)"
                            textSize = 10.5f
                            setTextColor(0xFF94A3B8.toInt())
                            setPadding(4, 2, 0, 6)
                        })
                    }
                }

                // "Upgrades Into" links
                val upgrades = ItemCatalog.getBuiltInto(inspectedItem.id)
                if (upgrades.isNotEmpty()) {
                    treePanel.addView(TextView(this).apply {
                        text = "UPGRADES INTO:"
                        textSize = 11.5f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(DotaRebornStyle.BRONZE_GOLD)
                        setPadding(0, 6, 0, 4)
                    })
                    for (up in upgrades) {
                        val uRow = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            background = DotaRebornStyle.beveledPanel(0xCC141B29.toInt(), 0xCC0A0E16.toInt(), 0xFF334155.toInt(), 8f, 1)
                            setPadding(8, 5, 8, 5)
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                                bottomMargin = 4
                            }
                            setOnClickListener {
                                inspectedItem = up
                                renderCraftTree()
                            }
                        }
                        DotaAssetLoader.getItemIcon(up.id)?.let { bmp ->
                            uRow.addView(ImageView(this).apply {
                                setImageBitmap(bmp)
                                scaleType = ImageView.ScaleType.CENTER_CROP
                                layoutParams = LinearLayout.LayoutParams(34, 26).apply { rightMargin = 8 }
                            })
                        }
                        uRow.addView(TextView(this).apply {
                            text = "${up.name} (${up.cost}g)"
                            textSize = 11f
                            setTextColor(0xFFE2E8F0.toInt())
                        })
                        treePanel.addView(uRow)
                    }
                }
            }
        }

        refreshShop = {
            synchronized(world) {
                goldTv.text = "DOTA 3 ARMORY  •  GOLD: ${ph.gold}g  •  STASH: ${ph.stash.size}  •  SHARD: ${if (ph.hasShard) "YES" else "NO"}"
                itemListLayout.removeAllViews()

                if (ph.inventory.isNotEmpty()) {
                    val invRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            bottomMargin = 8
                        }
                    }
                    ph.inventory.toList().forEachIndexed { idx, slot ->
                        val def = ItemCatalog.get(slot.itemId)
                        invRow.addView(DotaRebornStyle.rebornButton(this, "SELL ${def?.shortCode ?: "?"} +${((def?.cost ?: 0) * 0.5f).toInt()}g",
                            DotaRebornStyle.SLATE_TOP, DotaRebornStyle.SLATE_BOT, 0xFF64748B.toInt(), 10f) {
                            synchronized(world) { world.sellItem(ph, idx) }
                            refreshShop()
                        })
                    }
                    itemListLayout.addView(invRow)
                }

                for (item in ItemCatalog.allItems.filter { it.category == selectedCat }) {
                    val effCost = world.effectiveItemCost(ph, item)
                    val canAfford = ph.gold >= effCost
                    val isSelected = item.id == inspectedItem.id
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = DotaRebornStyle.beveledPanel(
                            if (isSelected) 0xEE1E2A3F.toInt() else 0xDD131A28.toInt(),
                            0xDD0A0E17.toInt(),
                            if (isSelected) DotaRebornStyle.GOLD_BRIGHT else if (canAfford) DotaRebornStyle.BRONZE_GOLD else 0xFF334155.toInt(),
                            10f, 2
                        )
                        setPadding(10, 7, 10, 7)
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            bottomMargin = 6
                        }
                        setOnClickListener {
                            inspectedItem = item
                            refreshShop()
                        }
                    }
                    DotaAssetLoader.getItemIcon(item.id)?.let { bmp ->
                        row.addView(ImageView(this).apply {
                            setImageBitmap(bmp)
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            layoutParams = LinearLayout.LayoutParams(52, 40).apply { rightMargin = 10 }
                        })
                    }
                    val info = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    info.addView(TextView(this).apply {
                        val treeBadge = if (item.components.isNotEmpty()) " [Craft]" else ""
                        text = "${item.name}$treeBadge — ${effCost}g"
                        textSize = 12.5f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(if (canAfford) DotaRebornStyle.GOLD_BRIGHT else 0xFF94A3B8.toInt())
                    })
                    info.addView(TextView(this).apply {
                        text = item.description
                        textSize = 10.5f
                        maxLines = 2
                        setTextColor(0xFFCBD5E1.toInt())
                    })
                    row.addView(info)

                    row.addView(DotaRebornStyle.rebornButton(this, "BUY",
                        if (canAfford) DotaRebornStyle.EMERALD_TOP else DotaRebornStyle.SLATE_TOP,
                        if (canAfford) DotaRebornStyle.EMERALD_BOT else DotaRebornStyle.SLATE_BOT,
                        if (canAfford) 0xFF34D399.toInt() else 0xFF475569.toInt(), 10.5f) {
                        val bought = synchronized(world) {
                            if (world.buyItem(ph, item.id)) {
                                world.dispatchCourier(ph)
                                true
                            } else false
                        }
                        if (bought) refreshShop()
                        else Toast.makeText(this, "Need ${effCost}g or inventory space!", Toast.LENGTH_SHORT).show()
                    })
                    itemListLayout.addView(row)
                }
            }
            renderCraftTree()
        }

        for (cat in ItemCategory.entries) {
            tabRow.addView(DotaRebornStyle.rebornButton(this, cat.title, DotaRebornStyle.SLATE_TOP, DotaRebornStyle.SLATE_BOT, DotaRebornStyle.BRONZE_GOLD, 11f) {
                selectedCat = cat
                refreshShop()
            })
        }

        panel.addView(tabRow)
        panel.addView(bodyRow)
        refreshShop()
        showOverlay(panel)
    }

    // ==================== 2. EMBEDDED TALENTS & HERO SHEET OVERLAY ====================

    override fun onOpenTalents() {
        synchronized(world) { world.playerHero.moveIntent.set(0f, 0f) }
        val ph = world.playerHero
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = DotaRebornStyle.beveledPanel(0xED131926.toInt(), 0xED090D15.toInt(), DotaRebornStyle.BRONZE_GOLD, 16f, 3)
            setPadding(24, 18, 24, 18)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        DotaAssetLoader.getHeroPortrait(ph.def.id)?.let { bmp ->
            header.addView(ImageView(this).apply {
                setImageBitmap(bmp)
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(96, 54).apply { rightMargin = 12 }
            })
        }
        header.addView(TextView(this).apply {
            text = "${ph.def.name.uppercase()} (Lv ${ph.level})  •  TALENT TREE & ATTRIBUTES"
            textSize = 15.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(DotaRebornStyle.GOLD_BRIGHT)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(DotaRebornStyle.rebornButton(this, "✕ CLOSE", DotaRebornStyle.CRIMSON_TOP, DotaRebornStyle.CRIMSON_BOT, 0xFFF87171.toInt()) {
            closeOverlay()
        })
        panel.addView(header)

        synchronized(world) {
            panel.addView(TextView(this).apply {
                background = DotaRebornStyle.beveledPanel(0xCC0D131F.toInt(), 0xCC080B12.toInt(), DotaRebornStyle.BRONZE_DARK, 10f, 1)
                setPadding(14, 10, 14, 10)
                text = "STR: ${ph.cachedStr.toInt()}   |   AGI: ${ph.cachedAgi.toInt()}   |   INT: ${ph.cachedInt.toInt()}   |   DMG: ${ph.cachedDamage.toInt()}\n" +
                    "ARMOR: ${"%.1f".format(ph.armor)}   |   MR: ${(ph.magicResist * 100).toInt()}%   |   AS: ${ph.cachedAttackSpeed.toInt()}   |   MS: ${ph.cachedMoveSpeed.toInt()}\n" +
                    "SPELL AMP: +${(ph.cachedSpellAmp * 100).toInt()}%   |   CDR: ${(ph.cachedCdr * 100).toInt()}%   |   LIFESTEAL: ${(ph.cachedLifesteal * 100).toInt()}%   |   EVA: ${(ph.cachedEvasion * 100).toInt()}%\n" +
                    "SCEPTER: ${if (ph.hasScepter) "ACTIVE" else "No"}   |   SHARD: ${if (ph.hasShard) "ACTIVE" else "No"}   |   AEGIS: ${if (ph.hasAegis) "YES" else "No"}"
                textSize = 12f
                setTextColor(0xFFE2E8F0.toInt())
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 10; bottomMargin = 12
                }
            })
        }

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val tiersLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(tiersLayout)

        fun renderTiers() {
            synchronized(world) {
                tiersLayout.removeAllViews()
                for (idx in ph.def.talents.indices.reversed()) {
                    val t = ph.def.talents[idx]
                    val chosen = ph.talentsChosen[idx]
                    val unlocked = ph.level >= t.level
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            bottomMargin = 8
                        }
                    }
                    val lBtn = DotaRebornStyle.rebornButton(
                        this, t.leftLabel,
                        if (chosen == 0) DotaRebornStyle.EMERALD_TOP else if (unlocked && chosen == -1) 0xFF1E3A8A.toInt() else DotaRebornStyle.SLATE_TOP,
                        DotaRebornStyle.SLATE_BOT,
                        if (chosen == 0) 0xFF34D399.toInt() else if (unlocked && chosen == -1) DotaRebornStyle.GOLD_BRIGHT else 0xFF475569.toInt(),
                        12f
                    ) {
                        val ok = synchronized(world) { world.chooseTalent(ph, idx, 0) }
                        if (ok) renderTiers()
                    }.apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    val lvlBadge = TextView(this).apply {
                        text = "${t.level}"
                        textSize = 14f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(if (unlocked) DotaRebornStyle.GOLD_BRIGHT else 0xFF64748B.toInt())
                        gravity = Gravity.CENTER
                        background = DotaRebornStyle.beveledPanel(0xEE182235.toInt(), 0xEE0B0F18.toInt(),
                            if (unlocked) DotaRebornStyle.BRONZE_GOLD else 0xFF334155.toInt(), 22f, 2)
                        layoutParams = LinearLayout.LayoutParams(54, 44).apply { leftMargin = 8; rightMargin = 8 }
                    }

                    val rBtn = DotaRebornStyle.rebornButton(
                        this, t.rightLabel,
                        if (chosen == 1) DotaRebornStyle.EMERALD_TOP else if (unlocked && chosen == -1) 0xFF1E3A8A.toInt() else DotaRebornStyle.SLATE_TOP,
                        DotaRebornStyle.SLATE_BOT,
                        if (chosen == 1) 0xFF34D399.toInt() else if (unlocked && chosen == -1) DotaRebornStyle.GOLD_BRIGHT else 0xFF475569.toInt(),
                        12f
                    ) {
                        val ok = synchronized(world) { world.chooseTalent(ph, idx, 1) }
                        if (ok) renderTiers()
                    }.apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    row.addView(lBtn)
                    row.addView(lvlBadge)
                    row.addView(rBtn)
                    tiersLayout.addView(row)
                }
            }
        }
        renderTiers()
        panel.addView(scroll)
        showOverlay(panel)
    }

    // ==================== 3. EMBEDDED SCOREBOARD & TACTICAL ORDERS OVERLAY ====================

    override fun onOpenScoreboard() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = DotaRebornStyle.beveledPanel(0xED131926.toInt(), 0xED090D15.toInt(), DotaRebornStyle.BRONZE_GOLD, 16f, 3)
            setPadding(24, 16, 24, 16)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        synchronized(world) {
            header.addView(TextView(this).apply {
                text = "BATTLE SCOREBOARD  •  RADIANT ${world.radiantKills} - ${world.direKills} DIRE"
                textSize = 15.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(DotaRebornStyle.GOLD_BRIGHT)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            header.addView(DotaRebornStyle.rebornButton(this, "✕ CLOSE", DotaRebornStyle.CRIMSON_TOP, DotaRebornStyle.CRIMSON_BOT, 0xFFF87171.toInt()) {
                closeOverlay()
            })
            panel.addView(header)

            // Tactical Ally Bot Orders strip inside Scoreboard
            val tacBar = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 6; bottomMargin = 8
                }
            }
            tacBar.addView(TextView(this).apply {
                text = "ALLY BOTS: "
                textSize = 11.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(DotaRebornStyle.BRONZE_GOLD)
            })
            for (cmd in TacticalCommand.entries) {
                val isOn = world.allyCommand == cmd
                tacBar.addView(DotaRebornStyle.rebornButton(
                    this, cmd.label,
                    if (isOn) 0xFF17253B.toInt() else DotaRebornStyle.SLATE_TOP,
                    DotaRebornStyle.SLATE_BOT,
                    if (isOn) cmd.colorHex else 0xFF475569.toInt(), 10.5f
                ) {
                    synchronized(world) { world.issueTacticalCommand(cmd) }
                    closeOverlay()
                })
            }
            panel.addView(tacBar)

            val scroll = ScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            }
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val mins = (world.gameTime / 60f).coerceAtLeast(0.5f)
            for (team in listOf(Team.RADIANT, Team.DIRE)) {
                list.addView(TextView(this).apply {
                    text = team.name
                    textSize = 13.5f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (team == Team.RADIANT) 0xFF2ECC71.toInt() else 0xFFE74C3C.toInt())
                    setPadding(0, 6, 0, 4)
                })
                for (h in world.heroes.filter { it.team == team }) {
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = DotaRebornStyle.beveledPanel(
                            if (h.isPlayer) 0xCC1E293B.toInt() else 0xCC0F1522.toInt(),
                            0xCC090D15.toInt(),
                            if (h.isPlayer) DotaRebornStyle.BRONZE_GOLD else 0xFF273244.toInt(), 8f, 1
                        )
                        setPadding(10, 6, 10, 6)
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            bottomMargin = 4
                        }
                    }
                    DotaAssetLoader.getHeroCirclePortrait(h.def.id, 44)?.let { bmp ->
                        row.addView(ImageView(this).apply {
                            setImageBitmap(bmp)
                            layoutParams = LinearLayout.LayoutParams(34, 34).apply { rightMargin = 8 }
                        })
                    }
                    val gpm = (h.totalGoldEarned / mins).toInt()
                    row.addView(TextView(this).apply {
                        text = "${if (h.isPlayer) "★ " else ""}${h.def.name} (Lv${h.level})   •   ${h.kills}/${h.deaths}/${h.assists}   •   CS ${h.lastHits}/${h.denies}   •   NW ${h.netWorth} (${gpm} GPM)"
                        textSize = 11.5f
                        setTextColor(if (h.isPlayer) DotaRebornStyle.GOLD_BRIGHT else 0xFFE2E8F0.toInt())
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    for (slot in h.inventory.take(6)) {
                        DotaAssetLoader.getItemIcon(slot.itemId)?.let { ibmp ->
                            row.addView(ImageView(this).apply {
                                setImageBitmap(ibmp)
                                layoutParams = LinearLayout.LayoutParams(28, 22).apply { leftMargin = 4 }
                            })
                        }
                    }
                    list.addView(row)
                }
            }
            scroll.addView(list)
            panel.addView(scroll)
        }
        showOverlay(panel)
    }

    // ==================== 4. EMBEDDED PAUSE & LIVE HUD CUSTOMIZER OVERLAY ====================

    override fun onOpenPauseMenu() {
        synchronized(world) { world.paused = true }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = DotaRebornStyle.beveledPanel(0xF0131926.toInt(), 0xF0080B12.toInt(), DotaRebornStyle.BRONZE_GOLD, 16f, 3)
            setPadding(36, 26, 36, 26)
        }
        panel.addView(TextView(this).apply {
            text = "MATCH PAUSED"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(DotaRebornStyle.GOLD_BRIGHT)
            setPadding(0, 0, 0, 16)
        })
        panel.addView(DotaRebornStyle.rebornButton(this, "RESUME BATTLE", DotaRebornStyle.EMERALD_TOP, DotaRebornStyle.EMERALD_BOT, 0xFF34D399.toInt(), 13f) {
            closeOverlay()
        })
        panel.addView(DotaRebornStyle.rebornButton(this, "CUSTOMIZE HUD SIZE & POSITION", 0xFF1E3A8A.toInt(), 0xFF0F172A.toInt(), 0xFF38BDF8.toInt(), 12.5f) {
            openHudCustomizer()
        })
        panel.addView(DotaRebornStyle.rebornButton(this, if (SoundEngine.enabled) "SFX AUDIO: ON" else "SFX AUDIO: OFF", DotaRebornStyle.SLATE_TOP, DotaRebornStyle.SLATE_BOT, DotaRebornStyle.BRONZE_GOLD, 12.5f) { btn ->
            SoundEngine.enabled = !SoundEngine.enabled
            btn.text = if (SoundEngine.enabled) "SFX AUDIO: ON" else "SFX AUDIO: OFF"
        })
        panel.addView(DotaRebornStyle.rebornButton(this, "QUIT TO MAIN MENU", DotaRebornStyle.CRIMSON_TOP, DotaRebornStyle.CRIMSON_BOT, 0xFFF87171.toInt(), 12.5f) {
            closeOverlay()
            finish()
        })
        showOverlay(panel, dismissOnScrim = false)
    }

    private fun openHudCustomizer() {
        hudOverlay.customizePreviewMode = true
        var custom = hudOverlay.hudCustomization

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = DotaRebornStyle.beveledPanel(0xDC0E1420.toInt(), 0xDC080B12.toInt(), 0xFF38BDF8.toInt(), 14f, 2)
            setPadding(20, 12, 20, 12)
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(TextView(this).apply {
            text = "HUD CUSTOMIZER (Live Preview)"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(DotaRebornStyle.GOLD_BRIGHT)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        top.addView(DotaRebornStyle.rebornButton(this, "RESET", DotaRebornStyle.SLATE_TOP, DotaRebornStyle.SLATE_BOT, 0xFFF59E0B.toInt(), 10.5f) {
            custom = HudPrefs.reset(this)
            hudOverlay.hudCustomization = custom
            hudOverlay.invalidate()
            openHudCustomizer()
        })
        top.addView(DotaRebornStyle.rebornButton(this, "SAVE & RESUME", DotaRebornStyle.EMERALD_TOP, DotaRebornStyle.EMERALD_BOT, 0xFF34D399.toInt(), 10.5f) {
            HudPrefs.save(this, custom)
            closeOverlay()
        })
        panel.addView(top)

        fun addSliderRow(label: String, minVal: Float, maxVal: Float, curVal: Float, fmtPct: Boolean = true, onUpdate: (Float) -> Unit) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 4
                }
            }
            val valTv = TextView(this).apply {
                textSize = 11f
                setTextColor(0xFF93C5FD.toInt())
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(68, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            fun fmt(v: Float) = if (fmtPct) "${(v * 100f).roundToInt()}%" else "${(v * 100f).roundToInt()}"
            valTv.text = fmt(curVal)

            row.addView(TextView(this).apply {
                text = label
                textSize = 11.5f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(168, LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            val sb = SeekBar(this).apply {
                max = 100
                progress = (((curVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f) * 100f).roundToInt()
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, p: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        val v = minVal + (p / 100f) * (maxVal - minVal)
                        valTv.text = fmt(v)
                        onUpdate(v)
                        hudOverlay.hudCustomization = custom.clamped()
                        HudPrefs.save(this@GameActivity, hudOverlay.hudCustomization)
                        hudOverlay.invalidate()
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            row.addView(sb)
            row.addView(valTv)
            panel.addView(row)
        }

        addSliderRow("Joystick Size", 0.75f, 1.28f, custom.joystickScale) { custom = custom.copy(joystickScale = it) }
        addSliderRow("Joystick X Pos", -0.05f, 0.06f, custom.joystickOffsetX, fmtPct = false) { custom = custom.copy(joystickOffsetX = it) }
        addSliderRow("Joystick Y Pos", -0.08f, 0.04f, custom.joystickOffsetY, fmtPct = false) { custom = custom.copy(joystickOffsetY = it) }
        addSliderRow("Skill & ATK Size", 0.75f, 1.20f, custom.skillScale) { custom = custom.copy(skillScale = it) }
        addSliderRow("Skill X Pos", -0.06f, 0.04f, custom.skillOffsetX, fmtPct = false) { custom = custom.copy(skillOffsetX = it) }
        addSliderRow("Skill Y Pos", -0.08f, 0.04f, custom.skillOffsetY, fmtPct = false) { custom = custom.copy(skillOffsetY = it) }
        addSliderRow("Console Size", 0.80f, 1.18f, custom.consoleScale) { custom = custom.copy(consoleScale = it) }
        addSliderRow("Minimap Size", 0.80f, 1.28f, custom.minimapScale) { custom = custom.copy(minimapScale = it) }

        val wrap = ScrollView(this).apply {
            addView(panel)
            layoutParams = ViewGroup.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.46f).toInt().coerceIn(420, 680),
                (resources.displayMetrics.heightPixels * 0.56f).toInt().coerceIn(240, 440)
            )
        }
        // Translucent scrim = transparent so user watches live HUD geometry resize/shift cleanly
        showOverlay(wrap, scrimColor = 0x22000000, dismissOnScrim = false, centered = false)
    }

    // ==================== 5. EMBEDDED MATCH SUMMARY OVERLAY ====================

    override fun onMatchEnded() {
        val won = world.winner == world.playerTeam
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = DotaRebornStyle.beveledPanel(
                0xF2121723.toInt(), 0xF2080B12.toInt(),
                if (won) 0xFF34D399.toInt() else 0xFFEF4444.toInt(), 18f, 3
            )
            setPadding(36, 28, 36, 28)
        }
        panel.addView(TextView(this).apply {
            text = if (won) "VICTORY!" else "DEFEAT"
            textSize = 32f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (won) 0xFF2ECC71.toInt() else 0xFFE74C3C.toInt())
        })
        val ph = world.playerHero
        val mvp = world.heroes.maxByOrNull { it.kills * 3 + it.assists - it.deaths + it.netWorth / 1200 }
        panel.addView(TextView(this).apply {
            text = "Final Score: Radiant ${world.radiantKills} - ${world.direKills} Dire\n" +
                "Your Stats (${ph.def.name}): ${ph.kills}/${ph.deaths}/${ph.assists}  •  LH/DN: ${ph.lastHits}/${ph.denies}\n" +
                "Hero Damage: ${ph.heroDamageDealt}  •  Tower Damage: ${ph.towerDamageDealt}  •  Net Worth: ${ph.netWorth}\n" +
                "Match MVP: ${mvp?.def?.name ?: "-"} (${mvp?.kills}/${mvp?.deaths}/${mvp?.assists})"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 14, 0, 20)
        })
        panel.addView(DotaRebornStyle.rebornButton(this, "RETURN TO MAIN MENU", DotaRebornStyle.CRIMSON_TOP, DotaRebornStyle.CRIMSON_BOT, DotaRebornStyle.GOLD_BRIGHT, 13f) {
            closeOverlay()
            finish()
        })
        showOverlay(panel, dismissOnScrim = false)
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
