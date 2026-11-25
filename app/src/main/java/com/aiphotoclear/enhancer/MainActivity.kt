package com.aiphotoclear.enhancer

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import kotlin.math.*

class BeforeAfterView(context: Context) : View(context) {
    var beforeBitmap: Bitmap? = null
    var afterBitmap: Bitmap? = null
    private var sliderFraction = 0.5f

    private var zoomScale = 1.0f
    private var panX = 0f
    private var panY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDraggingSlider = false

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoomScale = (zoomScale * detector.scaleFactor).coerceIn(1.0f, 4.5f)
            if (zoomScale <= 1.02f) {
                zoomScale = 1.0f
                panX = 0f
                panY = 0f
            }
            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (zoomScale > 1.2f) {
                resetZoom()
            } else {
                zoomScale = 2.5f
                panX = (width / 2f - e.x) * 1.2f
                panY = (height / 2f - e.y) * 1.2f
                invalidate()
            }
            return true
        }
    })

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 5f
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(10f, 0f, 4f, Color.parseColor("#88000000"))
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#111116")
        textSize = 28f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B3121218")
    }
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
        typeface = Typeface.DEFAULT_BOLD
    }

    fun resetZoom() {
        zoomScale = 1.0f
        panX = 0f
        panY = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val after = afterBitmap ?: return
        val before = beforeBitmap ?: after

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val baseScale = min(viewW / after.width, viewH / after.height)
        val drawW = after.width * baseScale * zoomScale
        val drawH = after.height * baseScale * zoomScale

        val maxPanX = max(0f, (drawW - viewW) / 2f + 100f)
        val maxPanY = max(0f, (drawH - viewH) / 2f + 100f)
        panX = panX.coerceIn(-maxPanX, maxPanX)
        panY = panY.coerceIn(-maxPanY, maxPanY)

        val left = (viewW - drawW) / 2f + panX
        val top = (viewH - drawH) / 2f + panY
        val destRect = RectF(left, top, left + drawW, top + drawH)

        canvas.drawBitmap(after, null, destRect, null)

        val splitX = viewW * sliderFraction
        canvas.save()
        canvas.clipRect(0f, 0f, splitX, viewH)
        canvas.drawBitmap(before, null, destRect, null)
        canvas.restore()

        canvas.drawLine(splitX, 0f, splitX, viewH, linePaint)
        val cy = viewH / 2f
        canvas.drawCircle(splitX, cy, 36f, handlePaint)
        canvas.drawText("◀▶", splitX, cy + 10f, arrowPaint)

        canvas.drawRoundRect(24f, 24f, 152f, 64f, 14f, 14f, badgePaint)
        canvas.drawText("BEFORE", 38f, 52f, badgeTextPaint)

        val zoomLabel = if (zoomScale > 1.05f) String.format(Locale.US, "AI 2K • %.1fx", zoomScale) else "AI 2K PRO"
        val badgeW = if (zoomScale > 1.05f) 230f else 180f
        canvas.drawRoundRect(viewW - badgeW - 24f, 24f, viewW - 24f, 64f, 14f, 14f, badgePaint)
        badgeTextPaint.color = Color.parseColor("#38BDF8")
        canvas.drawText(zoomLabel, viewW - badgeW - 8f, 52f, badgeTextPaint)
        badgeTextPaint.color = Color.WHITE
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (afterBitmap == null) return false
        parent?.requestDisallowInterceptTouchEvent(true)
        gestureDetector.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)

        if (event.pointerCount > 1) {
            isDraggingSlider = false
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                val splitX = width.toFloat() * sliderFraction
                isDraggingSlider = if (zoomScale > 1.05f) {
                    abs(event.x - splitX) < 60f
                } else {
                    true
                }
                if (isDraggingSlider) {
                    sliderFraction = (event.x / width.toFloat()).coerceIn(0.03f, 0.97f)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (scaleDetector.isInProgress) return true
                if (isDraggingSlider) {
                    sliderFraction = (event.x / width.toFloat()).coerceIn(0.03f, 0.97f)
                } else if (zoomScale > 1.05f) {
                    panX += (event.x - lastTouchX)
                    panY += (event.y - lastTouchY)
                }
                lastTouchX = event.x
                lastTouchY = event.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDraggingSlider = false
            }
        }
        return true
    }
}

class MainActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var originalBitmap: Bitmap? = null
    private var enhancedBitmap: Bitmap? = null

    // 4 Separate Independent Sliders (0 to 100)
    private var colorBoostVal = 35
    private var faceBeautyVal = 65
    private var brightnessVal = 50
    private var clarityVal = 55

    private lateinit var beforeAfterView: BeforeAfterView
    private lateinit var uploadDashboard: LinearLayout
    private lateinit var editorBottomPanel: LinearLayout
    private lateinit var progressOverlay: LinearLayout
    private lateinit var btnTopChange: TextView
    private lateinit var btnResetZoom: TextView
    private lateinit var rvRecentPhotos: RecyclerView

    private val recentUris = mutableListOf<Uri>()

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { loadSelectedImage(it) }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadRecentGalleryThumbnails()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        buildModernAIStudioUI()
        checkGalleryPermission()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    private fun roundBg(hex: String, radiusDp: Int = 16, strokeHex: String? = null, strokeDp: Int = 1): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(Color.parseColor(hex))
            if (strokeHex != null) setStroke(dp(strokeDp), Color.parseColor(strokeHex))
        }
    }

    private fun buildModernAIStudioUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#09090D"))
            layoutParams = ViewGroup.LayoutParams(-1, -1)
        }

        val topBar = RelativeLayout(this).apply {
            setBackgroundColor(Color.parseColor("#111118"))
            setPadding(dp(16), dp(38), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }

        val title = TextView(this).apply {
            text = "✨ AI Photo Clear 2K"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            layoutParams = RelativeLayout.LayoutParams(-2, -2).apply {
                addRule(RelativeLayout.ALIGN_PARENT_START)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
        }

        val topButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = RelativeLayout.LayoutParams(-2, -2).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
        }

        btnResetZoom = TextView(this).apply {
            visibility = View.GONE
            text = "1x Zoom"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#38BDF8"))
            background = roundBg("#1A2436", 14, "#38BDF8")
            setPadding(dp(10), dp(6), dp(10), dp(6))
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) }
            setOnClickListener { beforeAfterView.resetZoom() }
        }

        btnTopChange = TextView(this).apply {
            visibility = View.GONE
            text = "Change"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = roundBg("#232332", 14, "#3F3F55")
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { pickImageLauncher.launch("image/*") }
        }

        topButtons.addView(btnResetZoom)
        topButtons.addView(btnTopChange)
        topBar.addView(title)
        topBar.addView(topButtons)
        root.addView(topBar)

        val centerFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }

        beforeAfterView = BeforeAfterView(this).apply {
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }
        centerFrame.addView(beforeAfterView)

        uploadDashboard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(10))
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }

        val uploadCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundBg("#151520", 22, "#38BDF8", 2)
            setPadding(dp(20), dp(24), dp(20), dp(24))
            layoutParams = LinearLayout.LayoutParams(-1, -2)
            setOnClickListener { pickImageLauncher.launch("image/*") }
        }

        val uploadIcon = TextView(this).apply {
            text = "☁️"
            textSize = 36f
            gravity = Gravity.CENTER
            background = roundBg("#1F2436", 32)
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
        }

        val uploadMainText = TextView(this).apply {
            text = "फ़ोटो अपलोड करें (Select Photo)"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(4))
        }

        val uploadSubText = TextView(this).apply {
            text = "Color • Face Glow • Brightness • HD Clarity"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
        }

        val btnBrowse = TextView(this).apply {
            text = "Browse Gallery"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
            background = roundBg("#38BDF8", 14)
            setPadding(dp(22), dp(8), dp(22), dp(8))
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(12) }
        }

        uploadCard.addView(uploadIcon)
        uploadCard.addView(uploadMainText)
        uploadCard.addView(uploadSubText)
        uploadCard.addView(btnBrowse)
        uploadDashboard.addView(uploadCard)

        val recentHeader = TextView(this).apply {
            text = "Recent Photos (सीधे टैप करके चुनें)"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(dp(4), dp(16), 0, dp(8))
        }
        uploadDashboard.addView(recentHeader)

        rvRecentPhotos = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 3)
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            adapter = RecentPhotoAdapter()
        }
        uploadDashboard.addView(rvRecentPhotos)
        centerFrame.addView(uploadDashboard)

        progressOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setBackgroundColor(Color.parseColor("#CC09090D"))
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }
        val spinner = ProgressBar(this).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#38BDF8"))
        }
        val statusTxt = TextView(this).apply {
            text = "2K AI Studio प्रोसेस हो रहा है..."
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, dp(12), 0, 0)
        }
        progressOverlay.addView(spinner)
        progressOverlay.addView(statusTxt)
        centerFrame.addView(progressOverlay)

        root.addView(centerFrame)

        // 3. COMPACT 4-SLIDER BOTTOM PANEL
        editorBottomPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setBackgroundColor(Color.parseColor("#111118"))
            setPadding(dp(16), dp(8), dp(16), dp(20))
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }

        // Slider 1: Color Boost
        editorBottomPanel.addView(
            createControlSlider("🎨 Color Boost", "#38BDF8", colorBoostVal) { newVal ->
                colorBoostVal = newVal
                if (originalBitmap != null) runNaturalAIEnhance()
            }
        )

        // Slider 2: Face Beauty (Face Glow)
        editorBottomPanel.addView(
            createControlSlider("✨ Face Beauty (Glow)", "#F472B6", faceBeautyVal) { newVal ->
                faceBeautyVal = newVal
                if (originalBitmap != null) runNaturalAIEnhance()
            }
        )

        // Slider 3: Brightness (Light)
        editorBottomPanel.addView(
            createControlSlider("☀️ Light / Brightness", "#FACC15", brightnessVal) { newVal ->
                brightnessVal = newVal
                if (originalBitmap != null) runNaturalAIEnhance()
            }
        )

        // Slider 4: HD Clarity / Sharpness
        editorBottomPanel.addView(
            createControlSlider("💎 HD Clarity (2K Sharp)", "#34D399", clarityVal) { newVal ->
                clarityVal = newVal
                if (originalBitmap != null) runNaturalAIEnhance()
            }
        )

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(6) }
        }

        val btnNewUpload = TextView(this).apply {
            text = "+ New"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = roundBg("#232332", 14, "#3F3F55")
            layoutParams = LinearLayout.LayoutParams(0, -1, 0.8f).apply { marginEnd = dp(8) }
            setOnClickListener { pickImageLauncher.launch("image/*") }
        }

        val btnSave2K = TextView(this).apply {
            text = "⬇ Save 2K HD Photo"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            background = roundBg("#38BDF8", 14)
            layoutParams = LinearLayout.LayoutParams(0, -1, 1.8f)
            setOnClickListener { saveEnhanced2KToGallery() }
        }

        actionRow.addView(btnNewUpload)
        actionRow.addView(btnSave2K)
        editorBottomPanel.addView(actionRow)

        root.addView(editorBottomPanel)
        setContentView(root)
    }

    private fun createControlSlider(
        label: String,
        accentHex: String,
        initialProgress: Int,
        onRelease: (Int) -> Unit
    ): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(2) }
        }

        val topRow = RelativeLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }

        val tvName = TextView(this).apply {
            text = label
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#E2E8F0"))
        }

        val tvVal = TextView(this).apply {
            text = "$initialProgress%"
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor(accentHex))
            layoutParams = RelativeLayout.LayoutParams(-2, -2).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END)
            }
        }

        topRow.addView(tvName)
        topRow.addView(tvVal)
        box.addView(topRow)

        val seekBar = SeekBar(this).apply {
            max = 100
            progress = initialProgress
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(accentHex))
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(accentHex))
            layoutParams = LinearLayout.LayoutParams(-1, dp(26))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    tvVal.text = "$p%"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    onRelease(sb?.progress ?: initialProgress)
                }
            })
        }
        box.addView(seekBar)
        return box
    }

    private fun checkGalleryPermission() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
            loadRecentGalleryThumbnails()
        } else {
            permissionLauncher.launch(perm)
        }
    }

    private fun loadRecentGalleryThumbnails() {
        executor.execute {
            val list = mutableListOf<Uri>()
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                var count = 0
                while (cursor.moveToNext() && count < 18) {
                    val id = cursor.getLong(idCol)
                    list.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id))
                    count++
                }
            }
            mainHandler.post {
                recentUris.clear()
                recentUris.addAll(list)
                rvRecentPhotos.adapter?.notifyDataSetChanged()
            }
        }
    }

    private fun loadSelectedImage(uri: Uri) {
        try {
            val stream = contentResolver.openInputStream(uri)
            val bmp = BitmapFactory.decodeStream(stream)
            stream?.close()
            if (bmp != null) {
                originalBitmap = bmp
                enhancedBitmap = bmp
                uploadDashboard.visibility = View.GONE
                beforeAfterView.visibility = View.VISIBLE
                editorBottomPanel.visibility = View.VISIBLE
                btnTopChange.visibility = View.VISIBLE
                btnResetZoom.visibility = View.VISIBLE
                beforeAfterView.resetZoom()
                beforeAfterView.beforeBitmap = bmp
                beforeAfterView.afterBitmap = bmp
                beforeAfterView.invalidate()
                runNaturalAIEnhance()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "फ़ोटो लोड नहीं हो सकी", Toast.LENGTH_SHORT).show()
        }
    }

    private fun runNaturalAIEnhance() {
        val src = originalBitmap ?: return
        progressOverlay.visibility = View.VISIBLE

        executor.execute {
            val longEdge = max(src.width, src.height)
            val target2K = 2560
            val scale = if (longEdge < target2K) target2K.toFloat() / longEdge.toFloat() else 1.0f
            val newW = (src.width * scale).roundToInt()
            val newH = (src.height * scale).roundToInt()
            val upscaled = Bitmap.createScaledBitmap(src, newW, newH, true)

            val beautyFactor = faceBeautyVal / 100f
            val colorFactor = colorBoostVal / 100f
            val brightShift = (brightnessVal - 50) * 0.90f
            val detailAmount = (clarityVal / 100f) * 0.65f
            val smoothThreshold = (14 + (42 * beautyFactor)).roundToInt()

            val processed = applyFourControlStudioEngine(
                upscaled,
                smoothThreshold,
                detailAmount,
                beautyFactor,
                colorFactor,
                brightShift
            )

            mainHandler.post {
                enhancedBitmap = processed
                beforeAfterView.beforeBitmap = src
                beforeAfterView.afterBitmap = processed
                beforeAfterView.invalidate()
                progressOverlay.visibility = View.GONE
            }
        }
    }

    private fun applyFourControlStudioEngine(
        src: Bitmap,
        threshold: Int,
        detailAmount: Float,
        beautyFactor: Float,
        colorFactor: Float,
        brightShift: Float
    ): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val smoothPixels = pixels.copyOf()

        // Pass 1: Edge-Preserving Skin Smoothing
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val idx = row + x
                val c = pixels[idx]
                val cR = Color.red(c)
                val cG = Color.green(c)
                val cB = Color.blue(c)

                var sumR = cR * 2
                var sumG = cG * 2
                var sumB = cB * 2
                var weightSum = 2

                val neighbors = intArrayOf(
                    idx - w, idx + w, idx - 1, idx + 1,
                    idx - w - 1, idx - w + 1, idx + w - 1, idx + w + 1
                )
                for (nIdx in neighbors) {
                    val nc = pixels[nIdx]
                    val nR = Color.red(nc)
                    val nG = Color.green(nc)
                    val nB = Color.blue(nc)
                    val diff = (abs(cR - nR) + abs(cG - nG) + abs(cB - nB)) / 3
                    if (diff < threshold) {
                        sumR += nR
                        sumG += nG
                        sumB += nB
                        weightSum++
                    }
                }
                smoothPixels[idx] = Color.rgb(sumR / weightSum, sumG / weightSum, sumB / weightSum)
            }
        }

        // Pass 2: 4 Independent Controls (Clarity + Face Glow + Anti-Yellow Color + Brightness)
        val outPixels = IntArray(w * h)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val idx = row + x
                val c = smoothPixels[idx]
                var r = Color.red(c).toFloat()
                var g = Color.green(c).toFloat()
                var b = Color.blue(c).toFloat()

                // Control 4: Independent HD Clarity / Sharpness
                if (detailAmount > 0.01f && y in 1 until h - 1 && x in 1 until w - 1) {
                    val top = smoothPixels[idx - w]
                    val bot = smoothPixels[idx + w]
                    val left = smoothPixels[idx - 1]
                    val right = smoothPixels[idx + 1]
                    val avgR = (Color.red(top) + Color.red(bot) + Color.red(left) + Color.red(right)) * 0.25f
                    val avgG = (Color.green(top) + Color.green(bot) + Color.green(left) + Color.green(right)) * 0.25f
                    val avgB = (Color.blue(top) + Color.blue(bot) + Color.blue(left) + Color.blue(right)) * 0.25f

                    r += (r - avgR) * detailAmount
                    g += (g - avgG) * detailAmount
                    b += (b - avgB) * detailAmount
                }

                // Skin Mask
                val isGreen = g > r * 1.02f
                val isBrightYellow = (r > 145f && g > 110f && b < 80f && (g - b) > 50f)
                val isBlueSky = b > r * 1.05f
                val skinWeight: Float = if (isGreen || isBrightYellow || isBlueSky || r < 35f) {
                    0.0f
                } else {
                    val rgDiff = (r - g).coerceIn(0f, 80f) / 40f
                    val gbDiff = (g - b).coerceIn(-10f, 70f) / 35f
                    (min(rgDiff, gbDiff) * 1.2f).coerceIn(0.15f, 1.0f)
                }

                // Control 2: Face Beauty Glow (Gamma Lift + Cool Pink-White Glow)
                if (beautyFactor > 0.01f && skinWeight > 0.05f) {
                    val glowStrength = (beautyFactor * skinWeight).coerceIn(0f, 1f)
                    val gamma = 1.0f - (glowStrength * 0.34f)
                    var nr = 255f * (r.coerceIn(0f, 255f) / 255f).pow(gamma)
                    var ng = 255f * (g.coerceIn(0f, 255f) / 255f).pow(gamma)
                    var nb = 255f * (b.coerceIn(0f, 255f) / 255f).pow(gamma)

                    val screenFactor = glowStrength * 0.26f
                    nr += (255f - nr) * screenFactor
                    ng += (255f - ng) * (screenFactor * 1.03f)
                    nb += (255f - nb) * (screenFactor * 1.18f)

                    r = nr
                    g = ng
                    b = nb
                }

                // Control 1: Color Boost (Rich colors without yellowing skin)
                if (colorFactor > 0.01f) {
                    val gray = 0.299f * r + 0.587f * g + 0.114f * b
                    val boostMask = 1f - (skinWeight * 0.90f)
                    val satBoost = 1f + (colorFactor * 0.50f * boostMask)
                    r = gray + (r - gray) * satBoost
                    g = gray + (g - gray) * satBoost
                    b = gray + (b - gray) * satBoost
                }

                // Control 3: Light / Brightness
                r += brightShift
                g += brightShift
                b += brightShift

                outPixels[idx] = Color.rgb(
                    r.roundToInt().coerceIn(0, 255),
                    g.roundToInt().coerceIn(0, 255),
                    b.roundToInt().coerceIn(0, 255)
                )
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun saveEnhanced2KToGallery() {
        val bmp = enhancedBitmap ?: return
        val name = "AI_2K_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AI_Photo_Clear_2K")
            }
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        contentResolver.openOutputStream(uri)?.use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 98, out)
        }
        Toast.makeText(this, "✅ 2K HD फ़ोटो गैलरी में सेव हो गई!", Toast.LENGTH_LONG).show()
    }

    inner class RecentPhotoAdapter : RecyclerView.Adapter<RecentPhotoAdapter.VH>() {
        inner class VH(val img: ImageView) : RecyclerView.ViewHolder(img)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val side = (resources.displayMetrics.widthPixels - dp(52)) / 3
            val iv = ImageView(parent.context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = roundBg("#181822", 12)
                clipToOutline = true
                layoutParams = RecyclerView.LayoutParams(side, side).apply {
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                }
            }
            return VH(iv)
        }

        override fun getItemCount() = recentUris.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val uri = recentUris[position]
            holder.img.setImageURI(uri)
            holder.img.setOnClickListener { loadSelectedImage(uri) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
