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
        textSize = 30f
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val after = afterBitmap ?: return
        val before = beforeBitmap ?: after

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val scale = min(viewW / after.width, viewH / after.height)
        val drawW = after.width * scale
        val drawH = after.height * scale
        val left = (viewW - drawW) / 2f
        val top = (viewH - drawH) / 2f
        val destRect = RectF(left, top, left + drawW, top + drawH)

        canvas.drawBitmap(after, null, destRect, null)

        val splitX = left + (drawW * sliderFraction)
        canvas.save()
        canvas.clipRect(left, top, splitX, top + drawH)
        canvas.drawBitmap(before, null, destRect, null)
        canvas.restore()

        canvas.drawLine(splitX, top, splitX, top + drawH, linePaint)
        val cy = top + drawH / 2f
        canvas.drawCircle(splitX, cy, 34f, handlePaint)
        canvas.drawText("◀▶", splitX, cy + 10f, arrowPaint)

        canvas.drawRoundRect(left + 20f, top + 20f, left + 148f, top + 60f, 14f, 14f, badgePaint)
        canvas.drawText("BEFORE", left + 34f, top + 48f, badgeTextPaint)

        canvas.drawRoundRect(left + drawW - 196f, top + 20f, left + drawW - 20f, top + 60f, 14f, 14f, badgePaint)
        badgeTextPaint.color = Color.parseColor("#38BDF8")
        canvas.drawText("AI 2K PRO", left + drawW - 180f, top + 48f, badgeTextPaint)
        badgeTextPaint.color = Color.WHITE
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (afterBitmap == null) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val after = afterBitmap ?: return true
                val scale = min(width.toFloat() / after.width, height.toFloat() / after.height)
                val drawW = after.width * scale
                val left = (width.toFloat() - drawW) / 2f
                sliderFraction = ((event.x - left) / drawW).coerceIn(0.02f, 0.98f)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}

class MainActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var originalBitmap: Bitmap? = null
    private var enhancedBitmap: Bitmap? = null

    private var colorBoostVal = 35
    private var faceBeautyVal = 65
    private var brightnessVal = 50

    private lateinit var beforeAfterView: BeforeAfterView
    private lateinit var uploadDashboard: LinearLayout
    private lateinit var editorBottomPanel: LinearLayout
    private lateinit var progressOverlay: LinearLayout
    private lateinit var btnTopChange: TextView
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
            setPadding(dp(18), dp(38), dp(18), dp(14))
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }

        val title = TextView(this).apply {
            text = "✨ AI Photo Clear 2K"
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            layoutParams = RelativeLayout.LayoutParams(-2, -2).apply {
                addRule(RelativeLayout.ALIGN_PARENT_START)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
        }

        btnTopChange = TextView(this).apply {
            visibility = View.GONE
            text = "Change Photo"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = roundBg("#232332", 14, "#3F3F55")
            setPadding(dp(12), dp(6), dp(12), dp(6))
            layoutParams = RelativeLayout.LayoutParams(-2, -2).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
            setOnClickListener { pickImageLauncher.launch("image/*") }
        }

        topBar.addView(title)
        topBar.addView(btnTopChange)
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
            text = "Color Boost • Face Beauty • Brightness • 2K Clear"
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
            text = "2K AI Enhance प्रोसेस हो रहा है..."
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, dp(12), 0, 0)
        }
        progressOverlay.addView(spinner)
        progressOverlay.addView(statusTxt)
        centerFrame.addView(progressOverlay)

        root.addView(centerFrame)

        editorBottomPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setBackgroundColor(Color.parseColor("#111118"))
            setPadding(dp(18), dp(12), dp(18), dp(24))
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }

        editorBottomPanel.addView(
            createControlSlider("🎨 Color Boost", "#38BDF8", colorBoostVal) { newVal ->
                colorBoostVal = newVal
                if (originalBitmap != null) runNaturalAIEnhance()
            }
        )

        editorBottomPanel.addView(
            createControlSlider("✨ Face Beauty", "#F472B6", faceBeautyVal) { newVal ->
                faceBeautyVal = newVal
                if (originalBitmap != null) runNaturalAIEnhance()
            }
        )

        editorBottomPanel.addView(
            createControlSlider("☀️ Brightness", "#FACC15", brightnessVal) { newVal ->
                brightnessVal = newVal
                if (originalBitmap != null) runNaturalAIEnhance()
            }
        )

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) }
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
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) }
        }

        val topRow = RelativeLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }

        val tvName = TextView(this).apply {
            text = label
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#E2E8F0"))
        }

        val tvVal = TextView(this).apply {
            text = "$initialProgress%"
            textSize = 12f
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
            layoutParams = LinearLayout.LayoutParams(-1, dp(32))
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
            val smoothThreshold = (10 + (32 * beautyFactor)).roundToInt()
            val detailAmount = 0.26f + (0.14f * (1f - beautyFactor * 0.4f))
            val beautyProcessed = applyBilateralAndDetailPass(upscaled, smoothThreshold, detailAmount)

            val sat = 1.0f + (0.35f * (colorBoostVal / 100f))
            val brightnessShift = ((brightnessVal - 50) * 0.85f) + (beautyFactor * 6f)
            val contrast = 1.03f + (0.06f * (colorBoostVal / 100f))

            val final2K = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(final2K)
            val cm = ColorMatrix().apply { setSaturation(sat) }
            cm.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, brightnessShift,
                        0f, contrast, 0f, 0f, brightnessShift,
                        0f, 0f, contrast, 0f, brightnessShift,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(cm)
            }
            canvas.drawBitmap(beautyProcessed, 0f, 0f, paint)

            mainHandler.post {
                enhancedBitmap = final2K
                beforeAfterView.beforeBitmap = src
                beforeAfterView.afterBitmap = final2K
                beforeAfterView.invalidate()
                progressOverlay.visibility = View.GONE
            }
        }
    }

    private fun applyBilateralAndDetailPass(src: Bitmap, threshold: Int, detailAmount: Float): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val smoothPixels = pixels.copyOf()

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

                val neighbors = intArrayOf(idx - w, idx + w, idx - 1, idx + 1)
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

        val outPixels = smoothPixels.copyOf()
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val idx = row + x
                val c = smoothPixels[idx]
                val top = smoothPixels[idx - w]
                val bot = smoothPixels[idx + w]
                val left = smoothPixels[idx - 1]
                val right = smoothPixels[idx + 1]

                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)

                val avgR = (Color.red(top) + Color.red(bot) + Color.red(left) + Color.red(right)) shr 2
                val avgG = (Color.green(top) + Color.green(bot) + Color.green(left) + Color.green(right)) shr 2
                val avgB = (Color.blue(top) + Color.blue(bot) + Color.blue(left) + Color.blue(right)) shr 2

                val newR = (r + ((r - avgR) * detailAmount)).roundToInt().coerceIn(0, 255)
                val newG = (g + ((g - avgG) * detailAmount)).roundToInt().coerceIn(0, 255)
                val newB = (b + ((b - avgB) * detailAmount)).roundToInt().coerceIn(0, 255)

                outPixels[idx] = Color.rgb(newR, newG, newB)
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
