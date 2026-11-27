package com.dota3.moba.ui

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.dota3.moba.core.DotaAssetLoader
import com.dota3.moba.core.SoundEngine
import com.dota3.moba.data.ItemCatalog
import com.dota3.moba.engine.GameWorld
import com.dota3.moba.model.*

class GameActivity : AppCompatActivity(), GameHudListener {

    private lateinit var world: GameWorld
    private lateinit var gameView: Game3DView
    private var activeDialog: AlertDialog? = null

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

        world = GameWorld(heroId, facetIdx, team, lane, mode, diff)
        gameView = Game3DView(this, world, this)
        setContentView(gameView)
    }

    override fun onResume() {
        super.onResume()
        gameView.onResume()
    }

    override fun onPause() {
        gameView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        activeDialog?.dismiss()
        super.onDestroy()
    }

    override fun onOpenShop() {
        world.playerHero.moveIntent.set(0f, 0f)
        activeDialog?.dismiss()
        val ph = world.playerHero
        var selectedCat = ItemCategory.CORE

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0E131D.toInt())
            setPadding(24, 18, 24, 18)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val goldTv = TextView(this).apply {
            text = "DOTA 3 SHOP  •  GOLD: ${ph.gold}g  •  STASH: ${ph.stash.size}"
            textSize = 15.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFF5B041.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(goldTv)

        if (world.mode == GameMode.SANDBOX) {
            header.addView(smallBtn("+10,000g", 0xFF065F46.toInt()) {
                ph.gold += 10000
                ph.recalcStats()
                goldTv.text = "DOTA 3 SHOP  •  GOLD: ${ph.gold}g  •  STASH: ${ph.stash.size}"
            })
            header.addView(smallBtn("+LVL UP", 0xFF1E3A8A.toInt()) {
                ph.addXp(1500)
                Toast.makeText(this, "Level ${ph.level}!", Toast.LENGTH_SHORT).show()
            })
            header.addView(smallBtn("RESET CD", 0xFF7C2D12.toInt()) {
                for (i in 0..3) ph.abilityCooldowns[i] = 0f
                ph.hp = ph.maxHp
                ph.mana = ph.maxMana
            })
        }

        header.addView(smallBtn("CLOSE", 0xFF7F1D1D.toInt()) { activeDialog?.dismiss() })
        root.addView(header)

        val tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 10; bottomMargin = 10
            }
        }
        val itemListLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        fun refreshItems() {
            goldTv.text = "DOTA 3 SHOP  •  GOLD: ${ph.gold}g  •  STASH: ${ph.stash.size}  •  SHARD: ${if (ph.hasShard) "YES" else "NO"}"
            itemListLayout.removeAllViews()

            if (ph.inventory.isNotEmpty()) {
                val invRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = 10
                    }
                }
                ph.inventory.forEachIndexed { idx, slot ->
                    val def = ItemCatalog.get(slot.itemId)
                    invRow.addView(smallBtn("SELL ${def?.shortCode ?: "?"} (+${((def?.cost ?: 0) * 0.5f).toInt()}g)", 0xFF334155.toInt()) {
                        world.sellItem(ph, idx)
                        refreshItems()
                    })
                }
                itemListLayout.addView(invRow)
            }

            ph.neutralItemId?.let { nid ->
                ItemCatalog.getNeutral(nid)?.let { n ->
                    itemListLayout.addView(TextView(this).apply {
                        text = "Equipped Neutral (Tier ${n.tier}): ${n.name} — ${n.description}"
                        textSize = 12f
                        setTextColor(0xFFD8B4FE.toInt())
                        setPadding(0, 0, 0, 8)
                    })
                }
            }

            for (item in ItemCatalog.allItems.filter { it.category == selectedCat }) {
                val effCost = world.effectiveItemCost(ph, item)
                val canAfford = ph.gold >= effCost
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = boxBg(0xFF151C2B.toInt(), if (canAfford) item.color else 0xFF334155.toInt())
                    setPadding(12, 8, 12, 8)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = 8
                    }
                }
                DotaAssetLoader.getItemIcon(item.id)?.let { bmp ->
                    row.addView(ImageView(this).apply {
                        setImageBitmap(bmp)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        layoutParams = LinearLayout.LayoutParams(56, 42).apply { rightMargin = 10 }
                    })
                }
                val info = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                info.addView(TextView(this).apply {
                    text = "${item.name} — ${effCost}g" + if (effCost < item.cost) " (Base ${item.cost}g)" else ""
                    textSize = 13.5f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (canAfford) 0xFFF5B041.toInt() else 0xFF94A3B8.toInt())
                })
                info.addView(TextView(this).apply {
                    text = item.description
                    textSize = 11f
                    setTextColor(0xFFCBD5E1.toInt())
                })
                row.addView(info)

                row.addView(smallBtn("PIN", 0xFF1E293B.toInt()) {
                    ph.quickBuyItemId = item.id
                    Toast.makeText(this, "Pinned ${item.name} to Quick-Buy", Toast.LENGTH_SHORT).show()
                })
                row.addView(smallBtn("BUY", if (canAfford) 0xFF15803D.toInt() else 0xFF334155.toInt()) {
                    if (world.buyItem(ph, item.id)) {
                        world.dispatchCourier(ph)
                        refreshItems()
                    } else {
                        Toast.makeText(this, "Need ${effCost}g or inventory space!", Toast.LENGTH_SHORT).show()
                    }
                })
                itemListLayout.addView(row)
            }
        }

        for (cat in ItemCategory.entries) {
            tabRow.addView(smallBtn(cat.title, 0xFF1E293B.toInt()) {
                selectedCat = cat
                refreshItems()
            })
        }
        root.addView(tabRow)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 520)
        }
        scroll.addView(itemListLayout)
        root.addView(scroll)

        refreshItems()
        activeDialog = AlertDialog.Builder(this).setView(root).create().also { it.show() }
    }

    override fun onOpenTalents() {
        world.playerHero.moveIntent.set(0f, 0f)
        activeDialog?.dismiss()
        val ph = world.playerHero
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0E131D.toInt())
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
                layoutParams = LinearLayout.LayoutParams(92, 52).apply { rightMargin = 12 }
            })
        }
        header.addView(TextView(this).apply {
            text = "${ph.def.name.uppercase()} (Lv ${ph.level})  •  TALENTS & STATS"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFF5B041.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(smallBtn("CLOSE", 0xFF7F1D1D.toInt()) { activeDialog?.dismiss() })
        root.addView(header)

        root.addView(TextView(this).apply {
            text = "STR: ${ph.cachedStr.toInt()}  |  AGI: ${ph.cachedAgi.toInt()}  |  INT: ${ph.cachedInt.toInt()}  |  DMG: ${ph.cachedDamage.toInt()}\n" +
                "ARMOR: ${"%.1f".format(ph.armor)}  |  MR: ${(ph.magicResist * 100).toInt()}%  |  AS: ${ph.cachedAttackSpeed.toInt()}  |  MS: ${ph.cachedMoveSpeed.toInt()}\n" +
                "SPELL AMP: +${(ph.cachedSpellAmp * 100).toInt()}%  |  CDR: ${(ph.cachedCdr * 100).toInt()}%  |  LIFESTEAL: ${(ph.cachedLifesteal * 100).toInt()}%  |  EVA: ${(ph.cachedEvasion * 100).toInt()}%\n" +
                "SCEPTER: ${if (ph.hasScepter) "ACTIVE" else "No"}  |  SHARD: ${if (ph.hasShard) "ACTIVE" else "No"}  |  AEGIS: ${if (ph.hasAegis) "YES" else "No"}"
            textSize = 12f
            setTextColor(0xFFCBD5E1.toInt())
            setPadding(0, 8, 0, 12)
        })

        val tiersLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun renderTiers() {
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
                val lBtn = Button(this).apply {
                    text = t.leftLabel
                    textSize = 12f
                    setTextColor(Color.WHITE)
                    background = boxBg(
                        if (chosen == 0) 0xFF065F46.toInt() else if (unlocked && chosen == -1) 0xFF1E3A8A.toInt() else 0xFF1E293B.toInt(),
                        if (chosen == 0) 0xFF34D399.toInt() else 0xFF475569.toInt()
                    )
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener {
                        if (world.chooseTalent(ph, idx, 0)) renderTiers()
                    }
                }
                val lvlBadge = TextView(this).apply {
                    text = "Lv ${t.level}"
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (unlocked) 0xFFF5B041.toInt() else 0xFF64748B.toInt())
                    gravity = Gravity.CENTER
                    setPadding(16, 0, 16, 0)
                }
                val rBtn = Button(this).apply {
                    text = t.rightLabel
                    textSize = 12f
                    setTextColor(Color.WHITE)
                    background = boxBg(
                        if (chosen == 1) 0xFF065F46.toInt() else if (unlocked && chosen == -1) 0xFF1E3A8A.toInt() else 0xFF1E293B.toInt(),
                        if (chosen == 1) 0xFF34D399.toInt() else 0xFF475569.toInt()
                    )
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener {
                        if (world.chooseTalent(ph, idx, 1)) renderTiers()
                    }
                }
                row.addView(lBtn)
                row.addView(lvlBadge)
                row.addView(rBtn)
                tiersLayout.addView(row)
            }
        }
        renderTiers()
        root.addView(tiersLayout)

        activeDialog = AlertDialog.Builder(this).setView(root).create().also { it.show() }
    }

    override fun onOpenScoreboard() {
        activeDialog?.dismiss()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0E131D.toInt())
            setPadding(24, 18, 24, 18)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "SCOREBOARD  •  RADIANT ${world.radiantKills} - ${world.direKills} DIRE"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFF5B041.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(smallBtn("CLOSE", 0xFF7F1D1D.toInt()) { activeDialog?.dismiss() })
        root.addView(header)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 500)
        }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val mins = (world.gameTime / 60f).coerceAtLeast(0.5f)
        for (team in listOf(Team.RADIANT, Team.DIRE)) {
            list.addView(TextView(this).apply {
                text = team.name
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (team == Team.RADIANT) 0xFF2ECC71.toInt() else 0xFFE74C3C.toInt())
                setPadding(0, 8, 0, 4)
            })
            for (h in world.heroes.filter { it.team == team }) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(4, 4, 4, 4)
                }
                DotaAssetLoader.getHeroCirclePortrait(h.def.id, 44)?.let { bmp ->
                    row.addView(ImageView(this).apply {
                        setImageBitmap(bmp)
                        layoutParams = LinearLayout.LayoutParams(34, 34).apply { rightMargin = 8 }
                    })
                }
                val gpm = (h.totalGoldEarned / mins).toInt()
                row.addView(TextView(this).apply {
                    text = "${if (h.isPlayer) "★ " else ""}${h.def.name} (Lv${h.level})  |  ${h.kills}/${h.deaths}/${h.assists}  |  CS: ${h.lastHits}/${h.denies}  |  NW: ${h.netWorth} (${gpm} GPM)"
                    textSize = 11.5f
                    setTextColor(if (h.isPlayer) 0xFFFDE047.toInt() else 0xFFE2E8F0.toInt())
                })
                for (slot in h.inventory.take(6)) {
                    DotaAssetLoader.getItemIcon(slot.itemId)?.let { ibmp ->
                        row.addView(ImageView(this).apply {
                            setImageBitmap(ibmp)
                            layoutParams = LinearLayout.LayoutParams(26, 20).apply { leftMargin = 4 }
                        })
                    }
                }
                list.addView(row)
            }
        }
        scroll.addView(list)
        root.addView(scroll)
        activeDialog = AlertDialog.Builder(this).setView(root).create().also { it.show() }
    }

    override fun onOpenPauseMenu() {
        world.paused = true
        activeDialog?.dismiss()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF0E131D.toInt())
            setPadding(32, 24, 32, 24)
        }
        root.addView(TextView(this).apply {
            text = "GAME PAUSED"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFF5B041.toInt())
            setPadding(0, 0, 0, 16)
        })
        root.addView(smallBtn("RESUME MATCH", 0xFF15803D.toInt()) {
            world.paused = false
            activeDialog?.dismiss()
        })
        root.addView(smallBtn(if (SoundEngine.enabled) "SOUND: ON" else "SOUND: OFF", 0xFF1E293B.toInt()) { btn ->
            SoundEngine.enabled = !SoundEngine.enabled
            btn.text = if (SoundEngine.enabled) "SOUND: ON" else "SOUND: OFF"
        })
        root.addView(smallBtn("QUIT TO MAIN MENU", 0xFF7F1D1D.toInt()) {
            activeDialog?.dismiss()
            finish()
        })
        activeDialog = AlertDialog.Builder(this).setView(root).setCancelable(false).create().also { it.show() }
    }

    override fun onMatchEnded() {
        activeDialog?.dismiss()
        val won = world.winner == world.playerTeam
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF0E131D.toInt())
            setPadding(32, 24, 32, 24)
        }
        root.addView(TextView(this).apply {
            text = if (won) "VICTORY!" else "DEFEAT"
            textSize = 30f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (won) 0xFF2ECC71.toInt() else 0xFFE74C3C.toInt())
        })
        val ph = world.playerHero
        val mvp = world.heroes.maxByOrNull { it.kills * 3 + it.assists - it.deaths + it.netWorth / 1200 }
        root.addView(TextView(this).apply {
            text = "Final Score: Radiant ${world.radiantKills} - ${world.direKills} Dire\n" +
                "Your Stats (${ph.def.name}): ${ph.kills}/${ph.deaths}/${ph.assists}  •  LH/DN: ${ph.lastHits}/${ph.denies}\n" +
                "Hero Damage: ${ph.heroDamageDealt}  •  Tower Damage: ${ph.towerDamageDealt}  •  Net Worth: ${ph.netWorth}\n" +
                "Match MVP: ${mvp?.def?.name ?: "-"} (${mvp?.kills}/${mvp?.deaths}/${mvp?.assists})"
            textSize = 13.5f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 18)
        })
        root.addView(smallBtn("RETURN TO MAIN MENU", 0xFFB71C1C.toInt()) {
            activeDialog?.dismiss()
            finish()
        })
        activeDialog = AlertDialog.Builder(this).setView(root).setCancelable(false).create().also { it.show() }
    }

    private fun smallBtn(label: String, bg: Int, onClick: (Button) -> Unit): Button = Button(this).apply {
        text = label
        textSize = 11.5f
        setTextColor(Color.WHITE)
        setTypeface(null, Typeface.BOLD)
        background = boxBg(bg, 0xFF64748B.toInt())
        setPadding(16, 10, 16, 10)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(6, 6, 6, 6)
        }
        setOnClickListener { onClick(this) }
    }

    private fun boxBg(fill: Int, stroke: Int) = GradientDrawable().apply {
        setColor(fill)
        setStroke(2, stroke)
        cornerRadius = 10f
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
