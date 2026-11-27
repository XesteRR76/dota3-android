package com.dota3.moba.ui

import android.content.Context
import android.opengl.GLSurfaceView
import android.widget.FrameLayout
import com.dota3.moba.engine.GameWorld
import com.dota3.moba.render.World3DRenderer

class Game3DView(
    context: Context,
    val world: GameWorld,
    listener: GameHudListener
) : FrameLayout(context) {

    private val glView: GLSurfaceView
    private val renderer3D: World3DRenderer
    private val hudOverlay: MlbbHudOverlayView

    init {
        renderer3D = World3DRenderer(world) { dt ->
            world.step(dt)
        }
        glView = GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
            preserveEGLContextOnPause = true
            setRenderer(renderer3D)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        hudOverlay = MlbbHudOverlayView(context, world, renderer3D, listener)

        addView(glView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(hudOverlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun onResume() {
        glView.onResume()
    }

    fun onPause() {
        glView.onPause()
    }
}
