package com.frameglass.playerui

import android.content.Context
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.view.RoundedRectBlurRegion
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import kotlin.math.max

/**
 * The four layers of brief 4.4, bottom to top: video, controls, subtitles, sheets.
 *
 * This is the one aspect-fit container for the video view and both subtitle views. libass maps a script
 * onto the size of its view, and that must match the picture, so all three are centred on the same point
 * of the same fit box. Aspect modes and zoom are view transforms on the video only (see [VideoGeometry]).
 */
internal class VideoStage(context: Context) : ViewGroup(context) {
    val surface = SurfaceView(context)

    private var subtitleViews: List<View> = emptyList()
    private var mode = AspectMode.FIT
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var videoAspect = 0f
    private var clearance = 0

    var box = VideoBox(0, 0, 0, 0)
        private set

    private var subtitleBox = box

    /** Runs after layout, once the box is known. The activity uses it for text size and picture-in-picture. */
    var onBoxChanged: (() -> Unit)? = null

    /** Adds the layers in order. The subtitle views are not clickable, so touches reach the controls. */
    fun setLayers(controls: View, subtitles: List<View>, sheets: View) {
        addView(surface)
        addView(controls)
        subtitles.forEach { view ->
            view.isClickable = false
            view.isFocusable = false
            addView(view)
        }
        addView(sheets)
        subtitleViews = subtitles
    }

    fun setVideoAspect(aspect: Float) {
        if (aspect == videoAspect) return
        videoAspect = aspect
        requestLayout()
    }

    fun setPicture(mode: AspectMode, zoom: Float, panX: Float, panY: Float) {
        this.mode = mode
        this.zoom = zoom
        this.panX = panX
        this.panY = panY
        applyTransform()
        if (VideoGeometry.subtitleBox(width, height, box, mode) != subtitleBox) requestLayout()
    }

    /** Moves subtitles out of the way of whatever covers the bottom [clearancePx] of the window. */
    fun liftSubtitles(clearancePx: Int) {
        clearance = clearancePx
        val gapUnderVideo = max(0, height - subtitleBox.bottom)
        val target = -max(0, clearancePx - gapUnderVideo).toFloat()
        subtitleViews.forEach { it.animate().translationY(target).setDuration(LIFT_MILLIS).start() }
    }

    private fun applyTransform() {
        val transform = VideoGeometry.transform(width, height, box, mode, zoom, panX, panY)
        surface.scaleX = transform.scaleX
        surface.scaleY = transform.scaleY
        surface.translationX = transform.translationX
        surface.translationY = transform.translationY
    }

    /** The picture in window coordinates, cut to the window, for the picture-in-picture hint. */
    fun visibleVideoRect(): Rect {
        val bounds = RectF(0f, 0f, surface.width.toFloat(), surface.height.toFloat())
        surface.matrix.mapRect(bounds)
        bounds.offset(surface.left.toFloat(), surface.top.toFloat())
        val rect = Rect()
        bounds.round(rect)
        if (!rect.intersect(0, 0, width, height)) rect.setEmpty()
        val origin = IntArray(2)
        getLocationInWindow(origin)
        rect.offset(origin[0], origin[1])
        return rect
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        box = VideoGeometry.fit(width, height, videoAspect)
        subtitleBox = VideoGeometry.subtitleBox(width, height, box, mode)
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val target = boxOf(child)
            child.measure(
                MeasureSpec.makeMeasureSpec(target?.width ?: width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(target?.height ?: height, MeasureSpec.EXACTLY),
            )
        }
    }

    /** Null for the layers that fill the window. */
    private fun boxOf(child: View): VideoBox? = when (child) {
        surface -> box
        in subtitleViews -> subtitleBox
        else -> null
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val target = boxOf(child)
            if (target != null) {
                child.layout(target.left, target.top, target.right, target.bottom)
            } else {
                child.layout(0, 0, r - l, b - t)
            }
        }
        applyTransform()
        liftSubtitles(clearance)
        onBoxChanged?.invoke()
    }

    private companion object {
        const val LIFT_MILLIS = 220L
    }
}

/**
 * The hook for option 1 of brief 7.5. Gate 0 step 0.7 has not measured setBlurRegions on this phone, so
 * [ENABLED] stays false and the controls use the scrim fallback. If the gate passes: turn it on, and report
 * each control cluster from the controls layer through [update]. Regions live in the surface's own
 * coordinates, which is why they are mapped back through its transform, and they must be checked while zoomed (F-P6).
 */
internal class VideoBlurHook(private val stage: VideoStage) {
    fun update(windowRects: List<RectF>, cornerPx: Float, blurPx: Float) {
        if (!ENABLED || Build.VERSION.SDK_INT < BLUR_REGIONS_API) return
        val surface = stage.surface
        val inverse = Matrix().also { surface.matrix.invert(it) }
        stage.surface.setBlurRegions(
            windowRects.map { rect ->
                val local = RectF(rect).apply {
                    offset(-surface.left.toFloat(), -surface.top.toFloat())
                    inverse.mapRect(this)
                }
                RoundedRectBlurRegion().apply {
                    bounds = local
                    setCornerRadii(cornerPx)
                    blurRadius = blurPx
                    alpha = 1f
                }
            },
        )
    }

    fun clear() {
        if (!ENABLED || Build.VERSION.SDK_INT < BLUR_REGIONS_API) return
        stage.surface.setBlurRegions(emptyList())
    }

    companion object {
        const val ENABLED = false
        private const val BLUR_REGIONS_API = 37
    }
}
