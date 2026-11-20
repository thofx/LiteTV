package com.cabletv.player.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;
import com.cabletv.player.R;
import com.cabletv.player.config.AppConfig;
import com.cabletv.player.config.ChannelNavigator;
import com.cabletv.player.epg.EpgCache;
import com.cabletv.player.epg.EpgManager;
import com.cabletv.player.model.Channel;
import android.util.Log;
import com.bumptech.glide.Glide;
import xyz.doikki.videoplayer.controller.ControlWrapper;
import xyz.doikki.videoplayer.controller.IControlComponent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The programme column: the guide for the selected channel, by day.
 *
 * <p>A file of its own rather than a nested class: the list component was 1492 lines, and the
 * three adapters account for about 600 of them without sharing any state with it.
 */
public class ProgramListAdapter extends RecyclerView.Adapter<ProgramListAdapter.ProgramViewHolder> {
    private final Context mContext;
    private final List<EpgManager.Program> mPrograms;
    private Channel mChannel;
    private int mSelectedIndex = -1;
    private boolean mFocused = false;
    private RowClickCallback mRowClickCallback;

    private final RowLabelScroller mScroller;

    public ProgramListAdapter(Context context, List<EpgManager.Program> programs,
                              RowLabelScroller scroller) {
        mScroller = scroller;
        mContext = context;
        mPrograms = new ArrayList<>(programs);
    }

    public void setChannel(Channel channel) {
        mChannel = channel;
        notifyDataSetChanged();
    }

    void setRowClickListener(RowClickCallback callback) {
        mRowClickCallback = callback;
    }

    public void setSelectedIndex(int index) {
        if (mSelectedIndex == index) return;
        int oldIndex = mSelectedIndex;
        mSelectedIndex = index;
        if (oldIndex >= 0 && oldIndex < mPrograms.size()) {
            notifyItemChanged(oldIndex);
        }
        if (mSelectedIndex >= 0 && mSelectedIndex < mPrograms.size()) {
            notifyItemChanged(mSelectedIndex);
        }
    }

    public void setFocused(boolean focused) {
        if (mFocused != focused) {
            mFocused = focused;
            notifyDataSetChanged();
        }
    }

    /** Re-runs the bind pass on the next frame, so the subtitle marquee starts once the rows
     *  know how wide they are. */
    void rebindAfterLayout(android.view.ViewGroup list) {
        list.post(() -> notifyDataSetChanged());
    }

    @NonNull
    @Override
    public ProgramViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
        LinearLayout view = new LinearLayout(mContext);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                dp2px(ChannelListComponent.PROGRAM_ROW_HEIGHT_DP));
        lp.setMargins(dp2px(12), dp2px(2), dp2px(12), dp2px(2));
        view.setLayoutParams(lp);
        view.setPadding(dp2px(10), dp2px(4), dp2px(10), dp2px(4));
        return new ProgramViewHolder(view, mContext, mScroller);
    }

    @Override
    public void onBindViewHolder(@NonNull ProgramViewHolder holder, int position) {
        EpgManager.Program program = mPrograms.get(position);
        holder.setProgram(program, position == mSelectedIndex, mFocused, mChannel);
        holder.itemView.setOnClickListener(v -> {
            if (mRowClickCallback != null) {
                mRowClickCallback.onRowClick(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mPrograms.size();
    }

    /**
     * 该行是否是当天第一个节目；是则返回日期标题（如「今天 · 11月20日 周五」），否则 null。
     * 由 {@link DayHeaderDecoration} 画在行上方。
     */
    @Nullable
    String dayHeaderAt(int position) {
        EpgManager.Program program = getProgram(position);
        if (program == null) {
            return null;
        }
        long day = EpgCache.localDayNumber(program.startTime);
        EpgManager.Program prev = getProgram(position - 1);
        if (prev != null && EpgCache.localDayNumber(prev.startTime) == day) {
            return null;
        }
        return dayLabel(mContext, day - EpgCache.localDayNumber(System.currentTimeMillis()),
                program.startTime);
    }

    static String dayLabel(Context context, long offset, long timeMs) {
        String date = CineViews.dateFormat().format(new java.util.Date(timeMs));
        int prefix = offset == 0 ? R.string.day_today
                : offset == -1 ? R.string.day_yesterday
                : offset == 1 ? R.string.day_tomorrow : 0;
        return prefix == 0 ? date : context.getString(prefix) + " · " + date;
    }

    @Nullable
    EpgManager.Program getProgram(int index) {
        return index >= 0 && index < mPrograms.size() ? mPrograms.get(index) : null;
    }

    static class ProgramViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout container;
        private final TextView timeView;
        private final TextView titleView;
        private final TextView liveBadgeView;
        private final ImageView catchupBadgeView;
        private final Context context;

        private final RowLabelScroller mLabelScroller;

        ProgramViewHolder(LinearLayout itemView, Context context, RowLabelScroller scroller) {
            super(itemView);
            mLabelScroller = scroller;
            this.context = context;
            this.container = itemView;
            container.setGravity(Gravity.CENTER_VERTICAL);

            timeView = new TextView(context);
            timeView.setTextColor(ProgramViewHolder.TIME_NORMAL);
            timeView.setTextSize(13);
            timeView.setSingleLine(true);
            timeView.setFontFeatureSettings("tnum");
            timeView.setLayoutParams(new LinearLayout.LayoutParams(
                    dp2px(52),
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            container.addView(timeView);

            titleView = new TextView(context);
            titleView.setTextColor(0xFFF5F5F7);
            titleView.setTextSize(15);
            titleView.setMaxLines(1);
            titleView.setLayoutParams(new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1.0f));
            titleView.setPadding(dp2px(4), 0, dp2px(6), 0);
            container.addView(titleView);

            liveBadgeView = new TextView(context);
            liveBadgeView.setText(R.string.hud_live);
            liveBadgeView.setTextColor(0xFFFFFFFF);
            liveBadgeView.setTextSize(10);
            liveBadgeView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            android.graphics.drawable.GradientDrawable liveBg = new android.graphics.drawable.GradientDrawable();
            liveBg.setColor(0xFFE11D48);
            liveBg.setCornerRadius(dp2px(4));
            liveBadgeView.setBackground(liveBg);
            liveBadgeView.setPadding(dp2px(6), dp2px(2), dp2px(6), dp2px(2));
            liveBadgeView.setVisibility(View.GONE);
            container.addView(liveBadgeView);

            // 可回看：低调的灰色回放图标，焦点行变深色
            catchupBadgeView = new ImageView(context);
            catchupBadgeView.setImageResource(R.drawable.ic_replay_small);
            catchupBadgeView.setVisibility(View.GONE);
            container.addView(catchupBadgeView, new LinearLayout.LayoutParams(dp2px(16), dp2px(16)));
        }

        /** Whether this holder's title should be scrolling right now. */
        private boolean mTitleMarqueeWanted;

        /** See {@code ChannelViewHolder.applyEpgMarquee}; the guide rows need the same. */
        private void applyTitleMarquee(boolean wanted) {
            mTitleMarqueeWanted = wanted;
            ChannelListComponent.applyMarquee(mLabelScroller, titleView, wanted);
        }

        void setProgram(EpgManager.Program program, boolean isSelected, boolean focused) {
            setProgram(program, isSelected, focused, null);
        }

        void setProgram(EpgManager.Program program, boolean isSelected, boolean focused, @Nullable Channel channel) {
            titleView.setText(program.title);

            // 只显示开始时间：结束时间就是下一行的开始
            if (program.startTime > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                timeView.setText(sdf.format(new java.util.Date(program.startTime)));
            } else {
                timeView.setText("");
            }

            // A title that does not fit scrolls while this row is the highlighted one
            applyTitleMarquee(isSelected && focused);

            long currentTime = System.currentTimeMillis();
            boolean isCurrentProgram = currentTime >= program.startTime && currentTime < program.endTime;
            boolean isPastProgram = program.endTime <= currentTime;
            boolean focusRow = isSelected && focused;

            liveBadgeView.setVisibility(isCurrentProgram ? View.VISIBLE : View.GONE);
            boolean replayable = isPastProgram && channel != null && channel.hasCatchup()
                    && AppConfig.isCatchupBadgeEnabled();
            catchupBadgeView.setVisibility(replayable ? View.VISIBLE : View.GONE);
            catchupBadgeView.setColorFilter(focusRow ? 0xFF52525B : 0xFF71717A);

            int titleColor;
            if (focusRow) {
                container.setBackgroundResource(R.drawable.bg_row_focus);
                titleColor = 0xFF0A0A0B;
            } else if (isSelected) {
                container.setBackgroundResource(R.drawable.bg_row_selected);
                titleColor = 0xFFF5F5F7;
            } else if (isCurrentProgram) {
                container.setBackgroundResource(R.drawable.bg_row_live);
                titleColor = 0xFFF5F5F7;
            } else {
                container.setBackgroundColor(0x00000000);
                // 已播出的节目弱化
                titleColor = isPastProgram ? 0xFFA1A1AA : 0xFFF5F5F7;
            }
            titleView.setTextColor(titleColor);
            titleView.setTypeface(isCurrentProgram ? android.graphics.Typeface.DEFAULT_BOLD
                    : android.graphics.Typeface.DEFAULT);
            float scale = focusRow ? 1.03f : 1.0f;
            container.animate().scaleX(scale).scaleY(scale).setDuration(150).start();

            // The time is coloured on every bind, not only when the programme is current
            if (focusRow) {
                timeView.setTextColor(0xFF52525B);
            } else {
                timeView.setTextColor(isCurrentProgram ? TIME_CURRENT : TIME_NORMAL);
            }
        }

        /** Colour of a time stamp: the programme on air is picked out; the rest stay quiet. */
        static final int TIME_CURRENT = 0xFFFB7185;
        static final int TIME_NORMAL = 0xFF8A8A93;

        private int dp2px(int dp) {
            return Math.round(context.getResources().getDisplayMetrics().density * dp);
        }
    }

    private int dp2px(int dp) {
        return Math.round(mContext.getResources().getDisplayMetrics().density * dp);
    }
}
