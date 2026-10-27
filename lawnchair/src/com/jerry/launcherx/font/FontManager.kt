package com.jerry.launcherx.font

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.core.content.res.use
import androidx.lifecycle.lifecycleScope
import com.jerry.launcherx.preferences.BasePreferenceManager
import com.jerry.launcherx.preferences.PreferenceManager
import com.jerry.launcherx.util.lookupLifecycleOwner
import com.jerry.launcherx.util.runOnMainThread
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.SafeCloseable
import javax.inject.Inject
import kotlinx.coroutines.launch

@LauncherAppSingleton
class FontManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : SafeCloseable {

    private val fontCache = FontCache.INSTANCE.get(context)

    private val specMap = createFontMap()

    private val variableFonts = mutableMapOf<String, FontCache.Font>()

    /**
     * Google Sans Flex has no CJK coverage. On a non-English system the Latin labels render at
     * the custom weight while CJK characters fall back to the system font's regular weight,
     * which makes e.g. the app drawer look inconsistent. Use the platform font there instead.
     */
    private val useSystemFont = !Utilities.isEnglishLanguage(context)

    private fun createFontMap(): Map<Int, FontSpec> {
        val sansSerif = Typeface.SANS_SERIF
        val sansSerifMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL)

        val prefs = PreferenceManager.getInstance(context)
        return mapOf(
            R.id.font_base_icon to FontSpec(prefs.fontWorkspace, sansSerif),
            R.id.font_button to FontSpec(prefs.fontHeadingMedium, sansSerifMedium),
            R.id.font_heading to FontSpec(prefs.fontHeading, sansSerif),
            R.id.font_heading_medium to FontSpec(prefs.fontHeadingMedium, sansSerif),
            R.id.font_body to FontSpec(prefs.fontBody, sansSerif),
            R.id.font_body_medium to FontSpec(prefs.fontBodyMedium, sansSerif),
        )
    }

    /** True when the user kept the bundled default font for [type]. */
    private fun isDefaultFont(type: Int): Boolean = when (type) {
        R.id.font_base_icon -> PreferenceManager.getInstance(context).fontWorkspace.get() ==
            fontCache.uiText
        else -> false
    }

    fun overrideFont(textView: TextView, attrs: AttributeSet?) {
        try {
            val context = textView.context
            var fontType = -1
            var fontWeight = -1
            var ap = -1
            var fontFamily: String? = null
            context.obtainStyledAttributes(
                attrs,
                R.styleable.CustomFont,
            ).use { a ->
                fontType = a.getResourceId(R.styleable.CustomFont_customFontType, -1)
                fontWeight = a.getInt(R.styleable.CustomFont_customFontWeight, -1)
                ap = a.getResourceId(R.styleable.CustomFont_android_textAppearance, -1)
            }
            context.obtainStyledAttributes(attrs, FONT_FAMILY_ATTR).use { a ->
                fontFamily = a.getString(0)
            }

            if (ap != -1) {
                context.obtainStyledAttributes(ap, R.styleable.CustomFont).use { a ->
                    if (fontType == -1) {
                        fontType = a.getResourceId(R.styleable.CustomFont_customFontType, -1)
                    }
                    if (fontWeight == -1) {
                        fontWeight = a.getInt(R.styleable.CustomFont_customFontWeight, -1)
                    }
                }
                if (fontFamily == null) {
                    context.obtainStyledAttributes(ap, FONT_FAMILY_ATTR).use { a ->
                        fontFamily = a.getString(0)
                    }
                }
            }

            val gsfAxes = GoogleSansFlexVariableFont.axesFor(fontFamily)
            if (gsfAxes != null) {
                if (useSystemFont) {
                    // Let CJK and Latin share one family/weight on non-English systems.
                    applySystemFont(textView)
                    return
                }
                val font = variableFonts.getOrPut(fontFamily!!) {
                    fontCache.googleSansFlexVariable(gsfAxes)
                }
                applyFont(textView, font)
                return
            }

            if (fontType != -1) {
                if (useSystemFont && isDefaultFont(fontType)) {
                    applySystemFont(textView)
                    return
                }
                setCustomFont(textView, fontType, fontWeight)
            }
        } catch (_: Exception) {
        }
    }

    @JvmOverloads
    fun setCustomFont(textView: TextView, @IdRes type: Int, style: Int = -1) {
        val spec = specMap[type] ?: return
        applyFont(textView, spec.font.createWithWeight(style), spec.fallback)
    }

    private fun applySystemFont(textView: TextView) {
        textView.typeface = Typeface.SANS_SERIF
    }

    private fun applyFont(textView: TextView, font: FontCache.Font, fallback: Typeface? = null) {
        val lifecycleOwner = textView.context.lookupLifecycleOwner() ?: return
        lifecycleOwner.lifecycleScope.launch {
            val typeface = fontCache.getTypeface(font) ?: fallback ?: return@launch
            runOnMainThread {
                textView.typeface = typeface
            }
        }
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    class FontSpec(val loader: () -> FontCache.Font, val fallback: Typeface) {
        constructor(pref: BasePreferenceManager.FontPref, fallback: Typeface) : this(pref::get, fallback)

        val font get() = loader()
    }

    companion object {
        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getFontManager)

        private val FONT_FAMILY_ATTR = intArrayOf(android.R.attr.fontFamily)
    }
}
