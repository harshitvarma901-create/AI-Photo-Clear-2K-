package com.aiphotoclear.enhancer

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import kotlin.math.*

// Remini-Style Before / After Interactive Comparison View
class BeforeAfterView(context: Context) : View(context) {
    var beforeBitmap: Bitmap? = null
    var afterBitmap: Bitmap? = null
    private var sliderFraction = 0.5f

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD60A")
        strokeWidth = 6f
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD60A")
        style = Paint.Style.FILL
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 32f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AA000000")
    }
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 26f
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

        // 1. Draw Full Enhanced 2K "After" Image
        canvas.drawBitmap(after, null, destRect, null)

        // 2. Clip Left Side and Draw Original "Before" Image
        val splitX = left + (drawW * sliderFraction)
        canvas.save()
        canvas.clipRect(left, top, splitX, top + drawH)
        canvas.drawBitmap(before, null, destRect, null)
        canvas.restore()

        // 3. Draw Divider Line & Handle
        canvas.drawLine(splitX, top, splitX, top + drawH, linePaint)
        val cy = top + drawH / 2f
        canvas.drawCircle(splitX, cy, 34f, handlePaint)
        canvas.drawText("◀▶", splitX, cy + 11f, arrowPaint)

        // 4. Before / 2K After Labels
        canvas.drawRoundRect(left + 20f, top + 20f, left + 150f, top + 64f, 12f, 12f, badgePaint)
        canvas.drawText("BEFORE", left + 35f, top + 50f, badgeTextPaint)

        canvas.drawRoundRect(left + drawW - 185f, top + 20f, left + drawW - 20f, top + 64f, 12f, 12f, badgePaint)
        badgeTextPaint.color = Color.parseColor("#FFD60A")
        canvas.drawText("2K CLEAR", left + drawW - 170f, top + 50f, badgeTextPaint)
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

    private lateinit var beforeAfterView: BeforeAfterView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var progressOverlay: LinearLayout
    private lateinit var tvStatusText: TextView
    private lateinit var btnEnhance: TextView
    private lateinit var btnSave2K: TextView
    private lateinit var seekBoost: SeekBar

    private var boostStrength = 80 // 0 to 100

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { loadSelectedImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        buildReminiStyleUI()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    private fun roundBg(hex: String, radiusDp: Int = 16, strokeHex: String? = null): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(Color.parseColor(hex))
            if (strokeHex != null) setStroke(dp(1), Color.parseColor(strokeHex))
        }
    }

    private fun buildReminiStyleUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0B0E"))
            layoutParams = ViewGroup.LayoutParams(-1, -1)
        }

        // 1. TOP HEADER BAR
        val topBar = RelativeLayout(this).apply {
            setBackgroundColor(Color.parseColor("#121217"))
            setPadding(dp(18), dp(36), dp(18), dp(14))
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

        val btnSelectNew = TextView(this).apply {
            text = "+ Open Photo"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
            background = roundBg("#FFD60A", 14)
            setPadding(dp(14), dp(7), dp(14), dp(7))
            layoutParams = RelativeLayout.LayoutParams(-2, -2).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
            setOnClickListener { pickImageLauncher.launch("image/*") }
        }

        topBar.addView(title)
        topBar.addView(btnSelectNew)
        root.addView(topBar)

        // 2. CENTER BEFORE/AFTER COMPARISON CANVAS
        val centerFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }

        beforeAfterView = BeforeAfterView(this).apply {
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }
        centerFrame.addView(beforeAfterView)

        // Empty State Prompt
        emptyStateLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            setOnClickListener { pickImageLauncher.launch("image/*") }
        }

        val iconCircle = TextView(this).apply {
            text = "🖼️"
            textSize = 44f
            gravity = Gravity.CENTER
            background = roundBg("#1C1C24", 40, "#3A3A4A")
            layoutParams = LinearLayout.LayoutParams(dp(92), dp(92))
        }

        val hintTitle = TextView(this).apply {
            text = "गैलरी से कोई भी फोटो चुनें"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(6))
        }

        val hintSub = TextView(this).apply {
            text = "एक ही क्लिक में 2K HD क्लियर + कलर बूस्ट करें"
            textSize = 13f
            setTextColor(Color.parseColor("#9E9EA8"))
            gravity = Gravity.CENTER
        }

        emptyStateLayout.addView(iconCircle)
        emptyStateLayout.addView(hintTitle)
        emptyStateLayout.addView(hintSub)
        centerFrame.addView(emptyStateLayout)

        // Processing Spinner Overlay
        progressOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setBackgroundColor(Color.parseColor("#CC000000"))
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }
        val spinner = ProgressBar(this).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FFD60A"))
        }
        tvStatusText = TextView(this).apply {
            text = "2K क्लियर और कलर बूस्ट हो रहा है..."
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FFD60A"))
            setPadding(0, dp(14), 0, 0)
        }
        progressOverlay.addView(spinner)
        progressOverlay.addView(tvStatusText)
        centerFrame.addView(progressOverlay)

        root.addView(centerFrame)

        // 3. BOTTOM CONTROLS
        val bottomPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#14141A"))
            setPadding(dp(20), dp(14), dp(20), dp(26))
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }

        val sliderLabelRow = RelativeLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        val tvSliderTitle = TextView(this).apply {
            text = "Clear & Color Boost Power"
            textSize = 12f
            setTextColor(Color.LTGRAY)
        }
        val tvSliderVal = TextView(this).apply {
            text = "80%"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FFD60A"))
            layoutParams = RelativeLayout.LayoutParams(-2, -2).apply { addRule(RelativeLayout.ALIGN_PARENT_END) }
        }
        sliderLabelRow.addView(tvSliderTitle)
        sliderLabelRow.addView(tvSliderVal)
        bottomPanel.addView(sliderLabelRow)

        seekBoost = SeekBar(this).apply {
            max = 100
            progress = 80
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FFD60A"))
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FFD60A"))
            layoutParams = LinearLayout.LayoutParams(-1, dp(38))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    boostStrength = p
                    tvSliderVal.text = "$p%"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    if (originalBitmap != null) runOneClickEnhance()
                }
            })
        }
        bottomPanel.addView(seekBoost)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) }
        }

        btnEnhance = TextView(this).apply {
            text = "⚡ 1-Tap Enhance (2K)"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            background = roundBg("#FFD60A", 14)
            layoutParams = LinearLayout.LayoutParams(0, -1, 1.3f).apply { marginEnd = dp(8) }
            setOnClickListener {
                if (originalBitmap == null) pickImageLauncher.launch("image/*")
                else runOneClickEnhance()
            }
        }

        btnSave2K = TextView(this).apply {
            text = "⬇ Save 2K"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = roundBg("#252530", 14, "#48485A")
            layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
            setOnClickListener { saveEnhanced2KToGallery() }
        }

        btnRow.addView(btnEnhance)
        btnRow.addView(btnSave2K)
        bottomPanel.addView(btnRow)

        root.addView(bottomPanel)
        setContentView(root)
    }

    private fun loadSelectedImage(uri: Uri) {
        try {
            val stream = contentResolver.openInputStream(uri)
            val bmp = BitmapFactory.decodeStream(stream)
            stream?.close()
            if (bmp != null) {
                originalBitmap = bmp
                enhancedBitmap = bmp
                emptyStateLayout.visibility = View.GONE
                beforeAfterView.visibility = View.VISIBLE
                beforeAfterView.beforeBitmap = bmp
                beforeAfterView.afterBitmap = bmp
                beforeAfterView.invalidate()
                runOneClickEnhance()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "फोटो लोड नहीं हो सकी", Toast.LENGTH_SHORT).show()
        }
    }

    private fun runOneClickEnhance() {
        val src = originalBitmap ?: return
        progressOverlay.visibility = View.VISIBLE

        executor.execute {
            val longEdge = max(src.width, src.height)
            val target2K = 2560
            val scale = if (longEdge < target2K) target2K.toFloat() / longEdge.toFloat() else 1.0f
            val newW = (src.width * scale).roundToInt()
            val newH = (src.height * scale).roundToInt()
            val upscaled = Bitmap.createScaledBitmap(src, newW, newH, true)

            val factor = boostStrength / 100f
            val saturation = 1.0f + (0.45f * factor)
            val contrast = 1.0f + (0.22f * factor)
            val brightness = 6f * factor

            val colorBoosted = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(colorBoosted)
            val cm = ColorMatrix().apply { setSaturation(saturation) }
            cm.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, brightness,
                        0f, contrast, 0f, 0f, brightness,
                        0f, 0f, contrast, 0f, brightness,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(cm)
            }
            canvas.drawBitmap(upscaled, 0f, 0f, paint)

            val final2K = applyUnsharpMask(colorBoosted, amount = 0.50f + (0.55f * factor))

            mainHandler.post {
                enhancedBitmap = final2K
                beforeAfterView.beforeBitmap = src
                beforeAfterView.afterBitmap = final2K
                beforeAfterView.invalidate()
                progressOverlay.visibility = View.GONE
                Toast.makeText(this, "✨ 2K Clear + Color Boost तैयार!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyUnsharpMask(src: Bitmap, amount: Float): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val outPixels = pixels.copyOf()

        for (y in 1 until h - 1) {
            val rowOffset = y * w
            for (x in 1 until w - 1) {
                val idx = rowOffset + x
                val c = pixels[idx]
                val top = pixels[idx - w]
                val bot = pixels[idx + w]
                val left = pixels[idx - 1]
                val right = pixels[idx + 1]

                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)

                val avgR = (Color.red(top) + Color.red(bot) + Color.red(left) + Color.red(right)) shr 2
                val avgG = (Color.green(top) + Color.green(bot) + Color.green(left) + Color.green(right)) shr 2
                val avgB = (Color.blue(top) + Color.blue(bot) + Color.blue(left) + Color.blue(right)) shr 2

                val newR = (r + ((r - avgR) * amount)).roundToInt().coerceIn(0, 255)
                val newG = (g + ((g - avgG) * amount)).roundToInt().coerceIn(0, 255)
                val newB = (b + ((b - avgB) * amount)).roundToInt().coerceIn(0, 255)

                outPixels[idx] = Color.rgb(newR, newG, newB)
            }
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun saveEnhanced2KToGallery() {
        val bmp = enhancedBitmap ?: run {
            Toast.makeText(this, "पहले कोई फोटो चुनें", Toast.LENGTH_SHORT).show()
            return
        }
        val name = "ENHANCED_2K_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
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
        Toast.makeText(this, "✅ 2K HD फोटो गैलरी में सेव हो गई!", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
