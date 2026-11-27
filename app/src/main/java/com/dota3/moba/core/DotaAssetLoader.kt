package com.dota3.moba.core

import android.content.Context
import android.graphics.*
import java.util.concurrent.ConcurrentHashMap

object DotaAssetLoader {
    private var appContext: Context? = null
    private val cache = ConcurrentHashMap<String, Bitmap>()
    private val circleCache = ConcurrentHashMap<String, Bitmap>()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun getHeroPortrait(heroId: String): Bitmap? = loadBitmap("dota/heroes/$heroId.png")

    fun getHeroCirclePortrait(heroId: String, size: Int = 96): Bitmap? {
        val key = "${heroId}_c_$size"
        circleCache[key]?.let { return it }
        val src = getHeroPortrait(heroId) ?: return null
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path().apply { addCircle(size * 0.5f, size * 0.5f, size * 0.5f, Path.Direction.CW) }
        c.clipPath(path)
        // Center-crop horizontal hero portrait into square circle
        val cropW = src.height.coerceAtMost(src.width)
        val left = (src.width - cropW) / 2
        val srcRect = Rect(left, 0, left + cropW, src.height)
        val dstRect = Rect(0, 0, size, size)
        c.drawBitmap(src, srcRect, dstRect, p)
        circleCache[key] = out
        return out
    }

    fun getAbilityIcon(abilityId: String): Bitmap? = loadBitmap("dota/abilities/$abilityId.png")

    fun getAbilityCircleIcon(abilityId: String, size: Int = 96): Bitmap? {
        val key = "ab_${abilityId}_c_$size"
        circleCache[key]?.let { return it }
        val src = getAbilityIcon(abilityId) ?: return null
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path().apply { addCircle(size * 0.5f, size * 0.5f, size * 0.5f, Path.Direction.CW) }
        c.clipPath(path)
        c.drawBitmap(src, Rect(0, 0, src.width, src.height), Rect(0, 0, size, size), p)
        circleCache[key] = out
        return out
    }

    fun getItemIcon(itemId: String): Bitmap? = loadBitmap("dota/items/$itemId.png")

    fun getSplashBanner(): Bitmap? = loadBitmap("dota/splash_banner.png")

    private fun loadBitmap(assetPath: String): Bitmap? {
        cache[assetPath]?.let { return it }
        val ctx = appContext ?: return null
        return try {
            ctx.assets.open(assetPath).use { stream ->
                BitmapFactory.decodeStream(stream)?.also { bmp ->
                    cache[assetPath] = bmp
                }
            }
        } catch (_: Throwable) {
            null
        }
    }
}
