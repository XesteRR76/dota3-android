package com.dota3.moba.engine

import com.dota3.moba.core.DotaMath
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Grid-based Dota Fog of War (96x96 cells over 4800x4800 map).
 * 0 = Unexplored, 1 = Explored (dimmed), 2 = Currently Visible.
 */
class FogOfWar(private val gridSize: Int = 96) {
    private val cellSize = DotaMath.MAP_SIZE / gridSize
    private val state = ByteArray(gridSize * gridSize)
    private val trueSight = BooleanArray(gridSize * gridSize)

    fun beginFrame() {
        for (i in state.indices) {
            if (state[i] == 2.toByte()) state[i] = 1
            trueSight[i] = false
        }
    }

    fun revealCircle(wx: Float, wy: Float, radius: Float, grantsTrueSight: Boolean = false) {
        val rCells = ceil(radius / cellSize).toInt()
        val cx = (wx / cellSize).toInt().coerceIn(0, gridSize - 1)
        val cy = (wy / cellSize).toInt().coerceIn(0, gridSize - 1)
        val rSq = radius * radius

        val minX = (cx - rCells).coerceAtLeast(0)
        val maxX = (cx + rCells).coerceAtMost(gridSize - 1)
        val minY = (cy - rCells).coerceAtLeast(0)
        val maxY = (cy + rCells).coerceAtMost(gridSize - 1)

        for (gy in minY..maxY) {
            val cellWy = (gy + 0.5f) * cellSize
            val dy = cellWy - wy
            for (gx in minX..maxX) {
                val cellWx = (gx + 0.5f) * cellSize
                val dx = cellWx - wx
                if (dx * dx + dy * dy <= rSq) {
                    val idx = gy * gridSize + gx
                    state[idx] = 2
                    if (grantsTrueSight) trueSight[idx] = true
                }
            }
        }
    }

    fun getCellState(gx: Int, gy: Int): Int {
        if (gx < 0 || gy < 0 || gx >= gridSize || gy >= gridSize) return 0
        return state[gy * gridSize + gx].toInt()
    }

    fun isVisible(wx: Float, wy: Float): Boolean {
        val gx = floor(wx / cellSize).toInt().coerceIn(0, gridSize - 1)
        val gy = floor(wy / cellSize).toInt().coerceIn(0, gridSize - 1)
        return state[gy * gridSize + gx].toInt() == 2
    }

    fun hasTrueSight(wx: Float, wy: Float): Boolean {
        val gx = floor(wx / cellSize).toInt().coerceIn(0, gridSize - 1)
        val gy = floor(wy / cellSize).toInt().coerceIn(0, gridSize - 1)
        return trueSight[gy * gridSize + gx]
    }

    fun size(): Int = gridSize
    fun cellWorldSize(): Float = cellSize
}
