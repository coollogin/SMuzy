package io.github.coollogin.smuzy.ui.components

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import androidx.core.content.ContextCompat

import io.github.coollogin.smuzy.model.UserTagCount
import com.google.android.material.color.MaterialColors

import kotlin.math.max

/**
 * Simple wrapping tag cloud: chips flow left to right and wrap onto new lines, each row is
 * centred. All tags share the same chip style regardless of their song count.
 */
class UserTagCloudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewGroup(context, attrs) {

    private val rows = ArrayList<MutableList<View>>()

    fun interface OnTagClickListener {
        fun onTagClick(tag: String)
    }

    private var onTagClickListener: OnTagClickListener? = null

    fun setOnTagClickListener(listener: OnTagClickListener?) {
        onTagClickListener = listener
    }

    fun setTags(tags: List<UserTagCount>) {
        removeAllViews()
        rows.clear()

        if (tags.isEmpty()) {
            requestLayout()
            return
        }

        val backgroundColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorSecondaryContainer
        )
        val textColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOnSecondaryContainer
        )

        for (tag in tags) {
            addView(createChip(tag, backgroundColor, textColor))
        }

        requestLayout()
        invalidate()
    }

    private fun createChip(
        tag: UserTagCount,
        backgroundColor: Int,
        textColor: Int,
    ): TextView {
        return TextView(context).apply {
            text = tag.tag
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP)
            setTextColor(textColor)
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(HORIZONTAL_PADDING_DP), dp(VERTICAL_PADDING_DP), dp(HORIZONTAL_PADDING_DP), dp(VERTICAL_PADDING_DP))
            background = GradientDrawable().apply {
                cornerRadius = dp(CORNER_RADIUS_DP).toFloat()
                setColor(backgroundColor)
            }

            val selectable = TypedValue()
            if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, selectable, true)) {
                foreground = ContextCompat.getDrawable(context, selectable.resourceId)
            }

            isClickable = true
            isFocusable = true
            setOnClickListener { onTagClickListener?.onTagClick(tag.tag) }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val innerWidth = max(0, width - paddingLeft - paddingRight)

        rows.clear()

        var currentRow = ArrayList<View>()
        var currentRowWidth = 0
        var currentRowHeight = 0
        var totalHeight = 0

        for (index in 0 until childCount) {
            val child = getChildAt(index)
            child.measure(
                MeasureSpec.makeMeasureSpec(innerWidth, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            )

            val childWidth = child.measuredWidth
            val spacing = if (currentRow.isEmpty()) 0 else dp(CHIP_SPACING_DP)

            if (currentRow.isNotEmpty() && currentRowWidth + spacing + childWidth > innerWidth) {
                rows.add(currentRow)
                totalHeight += currentRowHeight + dp(CHIP_SPACING_DP)
                currentRow = ArrayList()
                currentRowWidth = 0
                currentRowHeight = 0
            }

            if (currentRow.isNotEmpty()) currentRowWidth += dp(CHIP_SPACING_DP)
            currentRow.add(child)
            currentRowWidth += childWidth
            currentRowHeight = max(currentRowHeight, child.measuredHeight)
        }

        if (currentRow.isNotEmpty()) {
            rows.add(currentRow)
            totalHeight += currentRowHeight
        }

        setMeasuredDimension(width, totalHeight + paddingTop + paddingBottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val innerWidth = max(0, r - l - paddingLeft - paddingRight)
        val spacing = dp(CHIP_SPACING_DP)
        var y = paddingTop

        for (row in rows) {
            var rowWidth = 0
            for (index in row.indices) {
                rowWidth += row[index].measuredWidth
                if (index > 0) rowWidth += spacing
            }

            var x = paddingLeft + (innerWidth - rowWidth) / 2
            var rowHeight = 0

            for (child in row) {
                child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
                x += child.measuredWidth + spacing
                rowHeight = max(rowHeight, child.measuredHeight)
            }

            y += rowHeight + spacing
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val TEXT_SP = 16f
        const val HORIZONTAL_PADDING_DP = 12
        const val VERTICAL_PADDING_DP = 6
        const val CORNER_RADIUS_DP = 14
        const val CHIP_SPACING_DP = 6
    }
}
