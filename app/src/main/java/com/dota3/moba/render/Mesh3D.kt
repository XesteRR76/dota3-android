package com.dota3.moba.render

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Lightweight OpenGL ES 2.0 3D Mesh builder (positions, normals, vertex colors).
 * Stride = 9 floats per vertex (x, y, z, nx, ny, nz, r, g, b).
 */
class Mesh3D(
    vertices: FloatArray,
    indices: ShortArray
) {
    private val vboId: Int
    private val iboId: Int
    val indexCount: Int = indices.size

    init {
        val buffers = IntArray(2)
        GLES20.glGenBuffers(2, buffers, 0)
        vboId = buffers[0]
        iboId = buffers[1]

        val vBuf: FloatBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(vertices)
                position(0)
            }
        val iBuf: ShortBuffer = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder()).asShortBuffer().apply {
                put(indices)
                position(0)
            }

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertices.size * 4, vBuf, GLES20.GL_STATIC_DRAW)

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, iboId)
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, indices.size * 2, iBuf, GLES20.GL_STATIC_DRAW)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    fun draw(posAttr: Int, normAttr: Int, colAttr: Int) {
        val stride = 9 * 4
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, iboId)

        GLES20.glEnableVertexAttribArray(posAttr)
        GLES20.glVertexAttribPointer(posAttr, 3, GLES20.GL_FLOAT, false, stride, 0)

        GLES20.glEnableVertexAttribArray(normAttr)
        GLES20.glVertexAttribPointer(normAttr, 3, GLES20.GL_FLOAT, false, stride, 3 * 4)

        GLES20.glEnableVertexAttribArray(colAttr)
        GLES20.glVertexAttribPointer(colAttr, 3, GLES20.GL_FLOAT, false, stride, 6 * 4)

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, 0)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    companion object {
        fun createBox(w: Float = 1f, h: Float = 1f, d: Float = 1f): Mesh3D {
            val hx = w * 0.5f; val hy = h * 0.5f; val hz = d * 0.5f
            val faces = arrayOf(
                // Front (+z)
                floatArrayOf(-hx, -hy, hz, 0f, 0f, 1f, 1f, 1f, 1f),
                floatArrayOf(hx, -hy, hz, 0f, 0f, 1f, 1f, 1f, 1f),
                floatArrayOf(hx, hy, hz, 0f, 0f, 1f, 1f, 1f, 1f),
                floatArrayOf(-hx, hy, hz, 0f, 0f, 1f, 1f, 1f, 1f),
                // Back (-z)
                floatArrayOf(hx, -hy, -hz, 0f, 0f, -1f, 1f, 1f, 1f),
                floatArrayOf(-hx, -hy, -hz, 0f, 0f, -1f, 1f, 1f, 1f),
                floatArrayOf(-hx, hy, -hz, 0f, 0f, -1f, 1f, 1f, 1f),
                floatArrayOf(hx, hy, -hz, 0f, 0f, -1f, 1f, 1f, 1f),
                // Top (+y)
                floatArrayOf(-hx, hy, hz, 0f, 1f, 0f, 1f, 1f, 1f),
                floatArrayOf(hx, hy, hz, 0f, 1f, 0f, 1f, 1f, 1f),
                floatArrayOf(hx, hy, -hz, 0f, 1f, 0f, 1f, 1f, 1f),
                floatArrayOf(-hx, hy, -hz, 0f, 1f, 0f, 1f, 1f, 1f),
                // Bottom (-y)
                floatArrayOf(-hx, -hy, -hz, 0f, -1f, 0f, 1f, 1f, 1f),
                floatArrayOf(hx, -hy, -hz, 0f, -1f, 0f, 1f, 1f, 1f),
                floatArrayOf(hx, -hy, hz, 0f, -1f, 0f, 1f, 1f, 1f),
                floatArrayOf(-hx, -hy, hz, 0f, -1f, 0f, 1f, 1f, 1f),
                // Right (+x)
                floatArrayOf(hx, -hy, hz, 1f, 0f, 0f, 1f, 1f, 1f),
                floatArrayOf(hx, -hy, -hz, 1f, 0f, 0f, 1f, 1f, 1f),
                floatArrayOf(hx, hy, -hz, 1f, 0f, 0f, 1f, 1f, 1f),
                floatArrayOf(hx, hy, hz, 1f, 0f, 0f, 1f, 1f, 1f),
                // Left (-x)
                floatArrayOf(-hx, -hy, -hz, -1f, 0f, 0f, 1f, 1f, 1f),
                floatArrayOf(-hx, -hy, hz, -1f, 0f, 0f, 1f, 1f, 1f),
                floatArrayOf(-hx, hy, hz, -1f, 0f, 0f, 1f, 1f, 1f),
                floatArrayOf(-hx, hy, -hz, -1f, 0f, 0f, 1f, 1f, 1f)
            )
            val verts = FloatArray(faces.size * 9)
            for (i in faces.indices) System.arraycopy(faces[i], 0, verts, i * 9, 9)
            val idx = ShortArray(36)
            for (f in 0 until 6) {
                val base = (f * 4).toShort()
                idx[f * 6 + 0] = base
                idx[f * 6 + 1] = (base + 1).toShort()
                idx[f * 6 + 2] = (base + 2).toShort()
                idx[f * 6 + 3] = base
                idx[f * 6 + 4] = (base + 2).toShort()
                idx[f * 6 + 5] = (base + 3).toShort()
            }
            return Mesh3D(verts, idx)
        }

        fun createCylinder(segs: Int = 12, topRadius: Float = 0.5f, botRadius: Float = 0.5f, height: Float = 1f): Mesh3D {
            val vertsList = ArrayList<Float>()
            val idxList = ArrayList<Short>()
            val halfH = height * 0.5f

            fun addV(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float) {
                vertsList.add(x); vertsList.add(y); vertsList.add(z)
                vertsList.add(nx); vertsList.add(ny); vertsList.add(nz)
                vertsList.add(1f); vertsList.add(1f); vertsList.add(1f)
            }

            // Side vertices
            for (i in 0..segs) {
                val a = (i.toFloat() / segs) * 6.283185f
                val ca = cos(a); val sa = sin(a)
                addV(ca * botRadius, -halfH, sa * botRadius, ca, 0.15f, sa)
                addV(ca * topRadius, halfH, sa * topRadius, ca, 0.15f, sa)
            }
            for (i in 0 until segs) {
                val b0 = (i * 2).toShort()
                val t0 = (i * 2 + 1).toShort()
                val b1 = ((i + 1) * 2).toShort()
                val t1 = ((i + 1) * 2 + 1).toShort()
                idxList.add(b0); idxList.add(b1); idxList.add(t1)
                idxList.add(b0); idxList.add(t1); idxList.add(t0)
            }
            // Top cap
            val topCenterIdx = (vertsList.size / 9).toShort()
            addV(0f, halfH, 0f, 0f, 1f, 0f)
            for (i in 0..segs) {
                val a = (i.toFloat() / segs) * 6.283185f
                addV(cos(a) * topRadius, halfH, sin(a) * topRadius, 0f, 1f, 0f)
            }
            for (i in 0 until segs) {
                idxList.add(topCenterIdx)
                idxList.add((topCenterIdx + 1 + i + 1).toShort())
                idxList.add((topCenterIdx + 1 + i).toShort())
            }
            // Bottom cap
            val botCenterIdx = (vertsList.size / 9).toShort()
            addV(0f, -halfH, 0f, 0f, -1f, 0f)
            for (i in 0..segs) {
                val a = (i.toFloat() / segs) * 6.283185f
                addV(cos(a) * botRadius, -halfH, sin(a) * botRadius, 0f, -1f, 0f)
            }
            for (i in 0 until segs) {
                idxList.add(botCenterIdx)
                idxList.add((botCenterIdx + 1 + i).toShort())
                idxList.add((botCenterIdx + 1 + i + 1).toShort())
            }
            return Mesh3D(vertsList.toFloatArray(), idxList.toShortArray())
        }

        fun createCrystal(segs: Int = 6, radius: Float = 0.5f, height: Float = 1.0f): Mesh3D {
            val verts = ArrayList<Float>()
            val idx = ArrayList<Short>()
            val halfH = height * 0.5f
            fun addTri(
                x0: Float, y0: Float, z0: Float,
                x1: Float, y1: Float, z1: Float,
                x2: Float, y2: Float, z2: Float
            ) {
                val ux = x1 - x0; val uy = y1 - y0; val uz = z1 - z0
                val vx = x2 - x0; val vy = y2 - y0; val vz = z2 - z0
                var nx = uy * vz - uz * vy
                var ny = uz * vx - ux * vz
                var nz = ux * vy - uy * vx
                val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.001f)
                nx /= len; ny /= len; nz /= len
                val base = (verts.size / 9).toShort()
                verts.add(x0); verts.add(y0); verts.add(z0); verts.add(nx); verts.add(ny); verts.add(nz); verts.add(1f); verts.add(1f); verts.add(1f)
                verts.add(x1); verts.add(y1); verts.add(z1); verts.add(nx); verts.add(ny); verts.add(nz); verts.add(1f); verts.add(1f); verts.add(1f)
                verts.add(x2); verts.add(y2); verts.add(z2); verts.add(nx); verts.add(ny); verts.add(nz); verts.add(1f); verts.add(1f); verts.add(1f)
                idx.add(base); idx.add((base + 1).toShort()); idx.add((base + 2).toShort())
            }
            for (i in 0 until segs) {
                val a0 = (i.toFloat() / segs) * 6.283185f
                val a1 = ((i + 1).toFloat() / segs) * 6.283185f
                val x0 = cos(a0) * radius; val z0 = sin(a0) * radius
                val x1 = cos(a1) * radius; val z1 = sin(a1) * radius
                addTri(0f, halfH, 0f, x1, 0.08f * halfH, z1, x0, 0.08f * halfH, z0)
                addTri(0f, -halfH, 0f, x0, 0.08f * halfH, z0, x1, 0.08f * halfH, z1)
            }
            return Mesh3D(verts.toFloatArray(), idx.toShortArray())
        }

        fun createTorus(majorSegs: Int = 24, minorSegs: Int = 8, majorR: Float = 0.92f, minorR: Float = 0.08f): Mesh3D {
            val verts = ArrayList<Float>()
            val idx = ArrayList<Short>()
            for (i in 0..majorSegs) {
                val u = (i.toFloat() / majorSegs) * 6.283185f
                val cu = cos(u); val su = sin(u)
                for (j in 0..minorSegs) {
                    val v = (j.toFloat() / minorSegs) * 6.283185f
                    val cv = cos(v); val sv = sin(v)
                    val x = (majorR + minorR * cv) * cu
                    val y = minorR * sv
                    val z = (majorR + minorR * cv) * su
                    val nx = cv * cu
                    val ny = sv
                    val nz = cv * su
                    verts.add(x); verts.add(y); verts.add(z)
                    verts.add(nx); verts.add(ny); verts.add(nz)
                    verts.add(1f); verts.add(1f); verts.add(1f)
                }
            }
            val row = minorSegs + 1
            for (i in 0 until majorSegs) {
                for (j in 0 until minorSegs) {
                    val a = (i * row + j).toShort()
                    val b = ((i + 1) * row + j).toShort()
                    val c = ((i + 1) * row + j + 1).toShort()
                    val d = (i * row + j + 1).toShort()
                    idx.add(a); idx.add(b); idx.add(c)
                    idx.add(a); idx.add(c); idx.add(d)
                }
            }
            return Mesh3D(verts.toFloatArray(), idx.toShortArray())
        }

        fun createCurvedBlade(): Mesh3D {
            // Bold forward-extending (+z) curved katana/glaive blade centered near hilt at z=0..1
            val verts = ArrayList<Float>()
            val idx = ArrayList<Short>()
            val segs = 6
            for (i in 0..segs) {
                val t = i.toFloat() / segs
                val z = t * 1.0f
                val curveX = t * t * 0.22f
                val halfH = (1f - t * 0.65f) * 0.14f
                val halfW = (1f - t * 0.78f) * 0.085f
                fun pv(x: Float, y: Float, zPos: Float, nx: Float, ny: Float, nz: Float) {
                    verts.add(x); verts.add(y); verts.add(zPos)
                    verts.add(nx); verts.add(ny); verts.add(nz)
                    verts.add(1f); verts.add(1f); verts.add(1f)
                }
                pv(curveX, halfH, z, 0f, 1f, 0f)
                pv(curveX + halfW, 0f, z, 1f, 0f, 0f)
                pv(curveX, -halfH, z, 0f, -1f, 0f)
                pv(curveX - halfW, 0f, z, -1f, 0f, 0f)
            }
            for (i in 0 until segs) {
                val b0 = i * 4
                val b1 = (i + 1) * 4
                for (k in 0 until 4) {
                    val kNext = (k + 1) % 4
                    idx.add((b0 + k).toShort()); idx.add((b1 + k).toShort()); idx.add((b1 + kNext).toShort())
                    idx.add((b0 + k).toShort()); idx.add((b1 + kNext).toShort()); idx.add((b0 + kNext).toShort())
                }
            }
            return Mesh3D(verts.toFloatArray(), idx.toShortArray())
        }

        fun createCrescentArc(segs: Int = 14, innerR: Float = 0.58f, outerR: Float = 1.0f, spanDeg: Float = 145f): Mesh3D {
            val verts = ArrayList<Float>()
            val idx = ArrayList<Short>()
            val halfSpan = Math.toRadians(spanDeg * 0.5).toFloat()
            for (i in 0..segs) {
                val t = i.toFloat() / segs
                val a = -halfSpan + t * halfSpan * 2f
                val ca = cos(a); val sa = sin(a)
                val taper = sin(t * Math.PI.toFloat())
                val rIn = outerR - (outerR - innerR) * taper
                verts.add(sa * rIn); verts.add(0f); verts.add(ca * rIn)
                verts.add(0f); verts.add(1f); verts.add(0f)
                verts.add(1f); verts.add(1f); verts.add(1f)

                verts.add(sa * outerR); verts.add(0f); verts.add(ca * outerR)
                verts.add(0f); verts.add(1f); verts.add(0f)
                verts.add(1f); verts.add(1f); verts.add(1f)
            }
            for (i in 0 until segs) {
                val i0 = (i * 2).toShort()
                val o0 = (i * 2 + 1).toShort()
                val i1 = ((i + 1) * 2).toShort()
                val o1 = ((i + 1) * 2 + 1).toShort()
                idx.add(i0); idx.add(o1); idx.add(i1)
                idx.add(i0); idx.add(o0); idx.add(o1)
            }
            return Mesh3D(verts.toFloatArray(), idx.toShortArray())
        }

        fun createDome(rings: Int = 7, sectors: Int = 16, radius: Float = 0.5f): Mesh3D {
            val verts = ArrayList<Float>()
            val idx = ArrayList<Short>()
            for (r in 0..rings) {
                val v = (r.toFloat() / rings) * (Math.PI.toFloat() * 0.5f)
                val sp = sin(v); val cp = cos(v)
                for (s in 0..sectors) {
                    val theta = (s.toFloat() / sectors) * 6.283185f
                    val nx = sp * cos(theta)
                    val ny = cp
                    val nz = sp * sin(theta)
                    verts.add(nx * radius); verts.add(ny * radius); verts.add(nz * radius)
                    verts.add(nx); verts.add(ny); verts.add(nz)
                    verts.add(1f); verts.add(1f); verts.add(1f)
                }
            }
            for (r in 0 until rings) {
                for (s in 0 until sectors) {
                    val a = (r * (sectors + 1) + s).toShort()
                    val b = ((r + 1) * (sectors + 1) + s).toShort()
                    val c = ((r + 1) * (sectors + 1) + s + 1).toShort()
                    val d = (r * (sectors + 1) + s + 1).toShort()
                    idx.add(a); idx.add(b); idx.add(c)
                    idx.add(a); idx.add(c); idx.add(d)
                }
            }
            return Mesh3D(verts.toFloatArray(), idx.toShortArray())
        }

        fun createSphere(rings: Int = 10, sectors: Int = 14, radius: Float = 0.5f): Mesh3D {
            val verts = ArrayList<Float>()
            val idx = ArrayList<Short>()
            for (r in 0..rings) {
                val v = r.toFloat() / rings
                val phi = v * Math.PI.toFloat()
                val sp = sin(phi); val cp = cos(phi)
                for (s in 0..sectors) {
                    val u = s.toFloat() / sectors
                    val theta = u * 6.283185f
                    val nx = sp * cos(theta)
                    val ny = cp
                    val nz = sp * sin(theta)
                    verts.add(nx * radius); verts.add(ny * radius); verts.add(nz * radius)
                    verts.add(nx); verts.add(ny); verts.add(nz)
                    verts.add(1f); verts.add(1f); verts.add(1f)
                }
            }
            for (r in 0 until rings) {
                for (s in 0 until sectors) {
                    val a = (r * (sectors + 1) + s).toShort()
                    val b = ((r + 1) * (sectors + 1) + s).toShort()
                    val c = ((r + 1) * (sectors + 1) + s + 1).toShort()
                    val d = (r * (sectors + 1) + s + 1).toShort()
                    idx.add(a); idx.add(b); idx.add(c)
                    idx.add(a); idx.add(c); idx.add(d)
                }
            }
            return Mesh3D(verts.toFloatArray(), idx.toShortArray())
        }

        fun createRing(segs: Int = 28, innerR: Float = 0.85f, outerR: Float = 1.0f): Mesh3D {
            val verts = ArrayList<Float>()
            val idx = ArrayList<Short>()
            for (i in 0..segs) {
                val a = (i.toFloat() / segs) * 6.283185f
                val ca = cos(a); val sa = sin(a)
                verts.add(ca * innerR); verts.add(0f); verts.add(sa * innerR)
                verts.add(0f); verts.add(1f); verts.add(0f)
                verts.add(1f); verts.add(1f); verts.add(1f)

                verts.add(ca * outerR); verts.add(0f); verts.add(sa * outerR)
                verts.add(0f); verts.add(1f); verts.add(0f)
                verts.add(1f); verts.add(1f); verts.add(1f)
            }
            for (i in 0 until segs) {
                val i0 = (i * 2).toShort()
                val o0 = (i * 2 + 1).toShort()
                val i1 = ((i + 1) * 2).toShort()
                val o1 = ((i + 1) * 2 + 1).toShort()
                idx.add(i0); idx.add(i1); idx.add(o1)
                idx.add(i0); idx.add(o1); idx.add(o0)
            }
            return Mesh3D(verts.toFloatArray(), idx.toShortArray())
        }

        fun createTerrainMesh(
            gridSize: Int,
            mapSize: Float,
            heightFn: (Float, Float) -> Float,
            colorFn: (Float, Float, Float) -> Triple<Float, Float, Float>
        ): Mesh3D {
            val count = gridSize + 1
            val verts = FloatArray(count * count * 9)
            val step = mapSize / gridSize
            val heights = FloatArray(count * count)

            for (gz in 0..gridSize) {
                for (gx in 0..gridSize) {
                    val wx = gx * step
                    val wz = gz * step
                    heights[gz * count + gx] = heightFn(wx, wz)
                }
            }

            for (gz in 0..gridSize) {
                for (gx in 0..gridSize) {
                    val wx = gx * step
                    val wz = gz * step
                    val h = heights[gz * count + gx]
                    val hL = heights[gz * count + (gx - 1).coerceAtLeast(0)]
                    val hR = heights[gz * count + (gx + 1).coerceAtMost(gridSize)]
                    val hD = heights[(gz - 1).coerceAtLeast(0) * count + gx]
                    val hU = heights[(gz + 1).coerceAtMost(gridSize) * count + gx]

                    var nx = hL - hR
                    var ny = 2f * step
                    var nz = hD - hU
                    val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.001f)
                    nx /= len; ny /= len; nz /= len

                    val (cr, cg, cb) = colorFn(wx, wz, h)
                    val base = (gz * count + gx) * 9
                    verts[base + 0] = wx
                    verts[base + 1] = h
                    verts[base + 2] = wz
                    verts[base + 3] = nx
                    verts[base + 4] = ny
                    verts[base + 5] = nz
                    verts[base + 6] = cr
                    verts[base + 7] = cg
                    verts[base + 8] = cb
                }
            }

            val idx = ShortArray(gridSize * gridSize * 6)
            var ptr = 0
            for (gz in 0 until gridSize) {
                for (gx in 0 until gridSize) {
                    val a = (gz * count + gx).toShort()
                    val b = ((gz + 1) * count + gx).toShort()
                    val c = ((gz + 1) * count + gx + 1).toShort()
                    val d = (gz * count + gx + 1).toShort()
                    idx[ptr++] = a; idx[ptr++] = b; idx[ptr++] = c
                    idx[ptr++] = a; idx[ptr++] = c; idx[ptr++] = d
                }
            }
            return Mesh3D(verts, idx)
        }
    }
}
