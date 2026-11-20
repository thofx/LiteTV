package com.cabletv.player.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 节目单的日期标题：在每天第一个节目上方留出空间，画「今天 · 11月20日 周五」和一条分隔线。
 *
 * <p>用 ItemDecoration 而不是插入标题行：数据下标不变，上下移动光标不用跳过标题。
 */
class DayHeaderDecoration extends RecyclerView.ItemDecoration {

    private final int mHeaderHeight;
    private final int mPadStart;
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLine = new Paint();
    private final float mDensity;

    DayHeaderDecoration(Context context) {
        mDensity = context.getResources().getDisplayMetrics().density;
        mHeaderHeight = Math.round(30 * mDensity);
        mPadStart = Math.round(16 * mDensity);
        mText.setColor(CineViews.color(context, com.cabletv.player.R.color.cine_text_2));
        mText.setTextSize(12 * context.getResources().getDisplayMetrics().scaledDensity);
        mText.setTypeface(Typeface.DEFAULT_BOLD);
        mText.setLetterSpacing(0.04f);
        mLine.setColor(CineViews.color(context, com.cabletv.player.R.color.cine_hairline));
        mLine.setStrokeWidth(Math.max(1, Math.round(mDensity)));
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
            @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        String header = headerFor(parent, parent.getChildAdapterPosition(view));
        outRect.set(0, header != null ? mHeaderHeight : 0, 0, 0);
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent,
            @NonNull RecyclerView.State state) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            String header = headerFor(parent, parent.getChildAdapterPosition(child));
            if (header == null) {
                continue;
            }
            RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) child.getLayoutParams();
            float bottom = child.getTop() - lp.topMargin - 8 * mDensity;
            float x = parent.getPaddingLeft() + mPadStart;
            c.drawText(header, x, bottom, mText);
            float lineStart = x + mText.measureText(header) + 12 * mDensity;
            float lineY = bottom - (mText.descent() + mText.ascent()) / 2f;
            float lineEnd = parent.getWidth() - parent.getPaddingRight() - mPadStart;
            if (lineEnd > lineStart) {
                c.drawLine(lineStart, lineY, lineEnd, lineY, mLine);
            }
        }
    }

    private static String headerFor(RecyclerView parent, int position) {
        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        if (position == RecyclerView.NO_POSITION || !(adapter instanceof ProgramListAdapter)) {
            return null;
        }
        return ((ProgramListAdapter) adapter).dayHeaderAt(position);
    }
}
