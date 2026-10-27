package com.jerry.launcherx.modern.search

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT

class UniversalSearchOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val searchEngine = UniversalSearchEngine(context)
    private var isExpanded = false
    private var animating = false

    private val backdropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6000000")
        style = Paint.Style.FILL
    }

    private val searchContainer: LinearLayout
    private val searchBar: LinearLayout
    private val searchInput: EditText
    private val searchIcon: ImageView
    private val clearButton: ImageView
    private val resultsContainer: ScrollView
    private val resultsLayout: LinearLayout
    private val categoryHeaders = mutableMapOf<String, TextView>()

    var onResultClicked: ((UniversalSearchEngine.SearchResult) -> Unit)? = null
    var onDismissed: (() -> Unit)? = null

    init {
        setWillNotDraw(false)
        visibility = GONE

        searchContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
            setPadding(32, 64, 32, 32)
        }

        searchBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(MATCH_PARENT, (48 * resources.displayMetrics.density).toInt())
            setPadding(16, 0, 16, 0)
            background = createRoundedBackground(Color.parseColor("#33FFFFFF"), 24f)
        }

        searchIcon = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_search)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                (32 * resources.displayMetrics.density).toInt(),
                (32 * resources.displayMetrics.density).toInt()
            )
        }

        searchInput = EditText(context).apply {
            hint = "Search apps, contacts, files..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#88FFFFFF"))
            background = null
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            layoutParams = LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply {
                marginStart = 12
                marginEnd = 12
            }
        }

        clearButton = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                (24 * resources.displayMetrics.density).toInt(),
                (24 * resources.displayMetrics.density).toInt()
            )
            visibility = GONE
        }

        searchBar.addView(searchIcon)
        searchBar.addView(searchInput)
        searchBar.addView(clearButton)

        resultsContainer = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply {
                topMargin = 16
            }
            isVerticalScrollBarEnabled = true
            overScrollMode = OVER_SCROLL_ALWAYS
        }

        resultsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }

        resultsContainer.addView(resultsLayout)
        searchContainer.addView(searchBar)
        searchContainer.addView(resultsContainer)
        addView(searchContainer)

        setupListeners()
    }

    private fun setupListeners() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                clearButton.visibility = if (query.isNotEmpty()) VISIBLE else GONE
                performSearch(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        searchInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                val query = searchInput.text.toString()
                if (query.isNotEmpty()) {
                    val results = searchEngine.search(query)
                    if (results.isNotEmpty()) {
                        onResultClicked?.invoke(results.first())
                    }
                }
                true
            } else false
        }

        clearButton.setOnClickListener {
            searchInput.text.clear()
        }

        setOnClickListener { view ->
            if (view == this) {
                dismiss()
            }
        }
    }

    fun show() {
        if (isExpanded || animating) return
        visibility = VISIBLE
        isExpanded = true
        animating = true
        alpha = 0f
        animate().alpha(1f).setDuration(250)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                animating = false
                searchInput.requestFocus()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
            }.start()
    }

    fun dismiss() {
        if (!isExpanded || animating) return
        animating = true
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
        animate().alpha(0f).setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                visibility = GONE
                isExpanded = false
                animating = false
                alpha = 1f
                searchInput.text.clear()
                resultsLayout.removeAllViews()
                onDismissed?.invoke()
            }.start()
    }

    fun isShowing() = isExpanded

    private fun performSearch(query: String) {
        resultsLayout.removeAllViews()
        categoryHeaders.clear()

        if (query.isBlank()) return

        val results = searchEngine.search(query)
        val grouped = results.groupBy { it.type }

        for ((type, typeResults) in grouped) {
            val header = TextView(context).apply {
                text = type.name.lowercase().replaceFirstChar { it.uppercase() }
                setTextColor(Color.parseColor("#AAFFFFFF"))
                textSize = 12f
                setPadding(0, 16, 0, 8)
            }
            resultsLayout.addView(header)
            categoryHeaders[type.name] = header

            for (result in typeResults) {
                val itemView = createResultItem(result)
                resultsLayout.addView(itemView)
            }
        }

        if (results.isEmpty()) {
            val emptyText = TextView(context).apply {
                text = "No results found"
                setTextColor(Color.parseColor("#66FFFFFF"))
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, 32, 0, 0)
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            }
            resultsLayout.addView(emptyText)
        }
    }

    private fun createResultItem(result: UniversalSearchEngine.SearchResult): View {
        val item = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, (56 * resources.displayMetrics.density).toInt())
            setPadding(12, 8, 12, 8)
            background = createRoundedBackground(Color.parseColor("#11FFFFFF"), 12f)
        }

        result.icon?.let { icon ->
            val iconView = ImageView(context).apply {
                setImageDrawable(icon)
                layoutParams = LinearLayout.LayoutParams(
                    (36 * resources.displayMetrics.density).toInt(),
                    (36 * resources.displayMetrics.density).toInt()
                )
            }
            item.addView(iconView)
        }

        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                marginStart = 12
            }
        }

        val titleView = TextView(context).apply {
            text = result.title
            setTextColor(Color.WHITE)
            textSize = 14f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        val subtitleView = TextView(context).apply {
            text = result.subtitle
            setTextColor(Color.parseColor("#88FFFFFF"))
            textSize = 11f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        textContainer.addView(titleView)
        textContainer.addView(subtitleView)
        item.addView(textContainer)

        item.setOnClickListener {
            onResultClicked?.invoke(result)
        }

        return item
    }

    private fun createRoundedBackground(color: Int, radiusDp: Float): android.graphics.drawable.GradientDrawable {
        val r = radiusDp * resources.displayMetrics.density
        return android.graphics.drawable.GradientDrawable().apply {
            this.color = android.content.res.ColorStateList.valueOf(color)
            this.cornerRadius = r
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            if (isExpanded) {
                dismiss()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && event.y < searchBar.top) {
            dismiss()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        if (isExpanded) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backdropPaint)
        }
        super.onDraw(canvas)
    }
}
