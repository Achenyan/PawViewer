package com.pawviewer.ui

import android.app.DatePickerDialog
import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.pawviewer.R
import com.pawviewer.core.PostFilter
import com.pawviewer.core.Util
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** 「类型 + 时间 + AI」筛选对话框，最新 / 作者作品页共用。 */
object FilterDialog {

    fun show(context: Context, current: PostFilter, onApply: (PostFilter) -> Unit) {
        val pad = Util.dp(context, 20)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, Util.dp(context, 8), pad, 0)
        }

        fun label(text: Int) {
            root.addView(TextView(context).apply {
                setText(text)
                setPadding(0, Util.dp(context, 12), 0, Util.dp(context, 4))
            })
        }

        // ---------------- 类型 ----------------
        label(R.string.filter_type)
        val typeGroup = RadioGroup(context).apply { orientation = RadioGroup.HORIZONTAL }
        val rbAll = RadioButton(context).apply { id = 1001; setText(R.string.filter_type_all) }
        val rbPack = RadioButton(context).apply { id = 1002; setText(R.string.filter_type_pack) }
        val rbVideo = RadioButton(context).apply { id = 1003; setText(R.string.filter_type_video) }
        typeGroup.addView(rbAll)
        typeGroup.addView(rbPack)
        typeGroup.addView(rbVideo)
        when (current.type) {
            PostFilter.TYPE_PACK -> typeGroup.check(rbPack.id)
            PostFilter.TYPE_VIDEO -> typeGroup.check(rbVideo.id)
            else -> typeGroup.check(rbAll.id)
        }
        root.addView(typeGroup)

        // ---------------- 时间 ----------------
        label(R.string.filter_time)
        val dateGroup = RadioGroup(context).apply { orientation = RadioGroup.HORIZONTAL }
        val rbAny = RadioButton(context).apply { id = 2001; setText(R.string.filter_time_any) }
        val rbBefore = RadioButton(context).apply { id = 2002; setText(R.string.filter_time_before) }
        val rbAfter = RadioButton(context).apply { id = 2003; setText(R.string.filter_time_after) }
        dateGroup.addView(rbAny)
        dateGroup.addView(rbBefore)
        dateGroup.addView(rbAfter)
        when (current.dateMode) {
            PostFilter.DATE_BEFORE -> dateGroup.check(rbBefore.id)
            PostFilter.DATE_AFTER -> dateGroup.check(rbAfter.id)
            else -> dateGroup.check(rbAny.id)
        }
        root.addView(dateGroup)

        // 主题色底板 + 右侧小一号提示
        var epoch = if (current.epoch > 0) current.epoch else System.currentTimeMillis()
        val fmt = SimpleDateFormat("yyyy 年 MM 月 dd 日", Locale.getDefault())
        val dateCard = MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = Util.dp(context, 8) }
            radius = Util.dp(context, 10).toFloat()
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.brand))
            cardElevation = 0f
            isClickable = true
            isFocusable = true
        }
        val dateRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Util.dp(context, 14), Util.dp(context, 10), Util.dp(context, 14), Util.dp(context, 10))
        }
        val tvDate = TextView(context).apply {
            text = fmt.format(epoch)
            setTextColor(ContextCompat.getColor(context, R.color.on_brand))
            textSize = 16f
        }
        val tvHint = TextView(context).apply {
            setText(R.string.filter_date_hint)
            setTextColor(ContextCompat.getColor(context, R.color.on_brand))
            alpha = 0.75f
            textSize = 12f // 比正文小一号
        }
        dateRow.addView(tvDate, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        dateRow.addView(tvHint, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        dateCard.addView(dateRow)
        dateCard.setOnClickListener {
            val cal = Calendar.getInstance().apply { timeInMillis = epoch }
            DatePickerDialog(
                context,
                { _, y, m, d ->
                    val c = Calendar.getInstance().apply {
                        set(y, m, d, 0, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    epoch = c.timeInMillis
                    tvDate.text = fmt.format(c.time)
                    // 选了具体日期就切到对应模式（默认「早于」）
                    if (dateGroup.checkedRadioButtonId == rbAny.id) dateGroup.check(rbBefore.id)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH),
            ).show()
        }
        root.addView(dateCard)

        // ---------------- AI 筛选 ----------------
        val cbAi = CheckBox(context).apply {
            setText(R.string.filter_ai)
            isChecked = current.excludeAi
            setPadding(0, Util.dp(context, 12), 0, 0)
            buttonTintList = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(),
                ),
                intArrayOf(
                    ContextCompat.getColor(context, R.color.brand),
                    ContextCompat.getColor(context, R.color.outline),
                ),
            )
        }
        root.addView(cbAi)

        AlertDialog.Builder(context)
            .setTitle(R.string.filter_title)
            .setView(root)
            .setPositiveButton(R.string.filter_apply) { _, _ ->
                val type = when (typeGroup.checkedRadioButtonId) {
                    rbPack.id -> PostFilter.TYPE_PACK
                    rbVideo.id -> PostFilter.TYPE_VIDEO
                    else -> PostFilter.TYPE_ALL
                }
                val dateMode = when (dateGroup.checkedRadioButtonId) {
                    rbBefore.id -> PostFilter.DATE_BEFORE
                    rbAfter.id -> PostFilter.DATE_AFTER
                    else -> PostFilter.DATE_ANY
                }
                onApply(PostFilter(type, dateMode, epoch, cbAi.isChecked))
            }
            .setNeutralButton(R.string.filter_reset) { _, _ -> onApply(PostFilter()) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
