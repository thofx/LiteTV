package com.cabletv.player.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
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
import xyz.doikki.videoplayer.controller.ControlWrapper;
import xyz.doikki.videoplayer.controller.IControlComponent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The channel column: numbers, names, the programme on air and its next.
 *
 * <p>A file of its own rather than a nested class: the list component was 1492 lines, and the
 * three adapters account for about 600 of them without sharing any state with it.
 */
public class ChannelListAdapter extends RecyclerView.Adapter<ChannelListAdapter.ChannelViewHolder> {
    private final Context mContext;
    private final ChannelNavigator mNavigator;
    private List<Channel> mChannels = new ArrayList<>();
    /** Flat playlist index of each row, which is also the number the viewer can type. */
    private List<Integer> mChannelNumbers = new ArrayList<>();
    private int mCurrentIndex = -1;
    private int mSelectedIndex = 0;
    private boolean mFocusOnChannels = true;
    private EpgManager mEpgManager;
    private RowClickCallback mRowClickCallback;

    private final RowLabelScroller mScroller;

    public ChannelListAdapter(Context context, ChannelNavigator navigator,
                              RowLabelScroller scroller) {
        mScroller = scroller;
        mContext = context;
        mNavigator = navigator;
    }

    public void setRowClickListener(RowClickCallback callback) {
        mRowClickCallback = callback;
    }

    public void setEpgManager(EpgManager epgManager) {
        mEpgManager = epgManager;
        notifyDataSetChanged();
    }

    public void updateData(List<Channel> channels, List<Integer> flatIndices,
                           int currentPosition, int selectedPosition) {
        mChannels = new ArrayList<>(channels);
        mChannelNumbers = new ArrayList<>(flatIndices);
        mCurrentIndex = currentPosition;
        mSelectedIndex = selectedPosition;
        notifyDataSetChanged();
    }

    public void setCurrentChannel(int position) {
        int oldIndex = mCurrentIndex;
        mCurrentIndex = position;
        if (oldIndex >= 0) {
            notifyItemChanged(oldIndex);
        }
        if (mCurrentIndex >= 0) {
            notifyItemChanged(mCurrentIndex);
        }
    }

    public void setSelectedChannel(int oldPosition, int newPosition) {
        mSelectedIndex = newPosition;
        if (oldPosition >= 0) {
            notifyItemChanged(oldPosition);
        }
        if (newPosition >= 0) {
            notifyItemChanged(newPosition);
        }
    }

    /** Re-runs the bind pass on the next frame, so the subtitle marquee starts once the rows
     *  know how wide they are. */
    void rebindAfterLayout(android.view.ViewGroup list) {
        list.post(() -> notifyDataSetChanged());
    }

    public void setFocusOnChannels(boolean focus) {
        if (mFocusOnChannels != focus) {
            mFocusOnChannels = focus;
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public ChannelViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
        LinearLayout view = new LinearLayout(mContext);
        view.setOrientation(LinearLayout.VERTICAL);
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                dp2px(ChannelListComponent.CHANNEL_ROW_HEIGHT_DP));
        lp.setMargins(dp2px(12), dp2px(2), dp2px(12), dp2px(2));
        view.setLayoutParams(lp);
        view.setPadding(dp2px(6), dp2px(4), dp2px(8), dp2px(4));
        return new ChannelViewHolder(view, mContext, mScroller);
    }

    @Override
    public void onBindViewHolder(@NonNull ChannelViewHolder holder, int position) {
        Channel channel = mChannels.get(position);
        int number = position < mChannelNumbers.size() ? mChannelNumbers.get(position) + 1 : 0;
        holder.setChannel(channel, number, position == mSelectedIndex, position == mCurrentIndex,
                mEpgManager, mFocusOnChannels,
                mNavigator != null && mNavigator.isFavorite(channel));
        holder.itemView.setOnClickListener(v -> {
            if (mRowClickCallback != null) {
                mRowClickCallback.onRowClick(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mChannels.size();
    }

    static class ChannelViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout container;
        private final View playingBar;
        private final TextView numberView;
        private final ChannelLogoView logoView;
        private final LinearLayout textContainer;
        private final TextView nameView;
        private final TextView epgView;
        private final TextView favoriteView;
        private final Context context;

        private final RowLabelScroller mLabelScroller;

        ChannelViewHolder(LinearLayout itemView, Context context, RowLabelScroller scroller) {
            super(itemView);
            mLabelScroller = scroller;
            this.context = context;
            this.container = itemView;
            container.setOrientation(LinearLayout.HORIZONTAL);
            container.setGravity(Gravity.CENTER_VERTICAL);

            // 正在播放：行最左侧的红色竖条；其他行 INVISIBLE 占位，台号保持对齐
            playingBar = new View(context);
            android.graphics.drawable.GradientDrawable bar = new android.graphics.drawable.GradientDrawable();
            bar.setColor(0xFFE11D48);
            bar.setCornerRadius(dp2px(2));
            playingBar.setBackground(bar);
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dp2px(3), dp2px(24));
            barLp.rightMargin = dp2px(4);
            container.addView(playingBar, barLp);

            // The number the viewer can type on the remote to reach this channel
            numberView = new TextView(context);
            numberView.setTextColor(0xFF8A8A93);
            numberView.setTextSize(13);
            numberView.setFontFeatureSettings("tnum");
            numberView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            numberView.setSingleLine(true);
            numberView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            numberView.setLayoutParams(new LinearLayout.LayoutParams(
                    dp2px(26),
                    LinearLayout.LayoutParams.MATCH_PARENT));
            container.addView(numberView);

            // 台标块：没有台标时是文字块，尺寸固定，整列对齐
            logoView = new ChannelLogoView(context, 6, 11);
            LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(
                    dp2px(48),
                    dp2px(32));
            logoParams.leftMargin = dp2px(10);
            logoView.setLayoutParams(logoParams);
            container.addView(logoView);

            // Text container
            textContainer = new LinearLayout(context);
            textContainer.setOrientation(LinearLayout.VERTICAL);
            textContainer.setGravity(Gravity.CENTER_VERTICAL);
            textContainer.setLayoutParams(new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1.0f));
            textContainer.setPadding(dp2px(12), 0, 0, 0);

            nameView = new TextView(context);
            nameView.setTextColor(0xFFE2E8F0);
            nameView.setTextSize(16);
            nameView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            nameView.setSingleLine(true);
            nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            nameView.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            textContainer.addView(nameView);

            epgView = new TextView(context);
            epgView.setTextColor(0xFF64748B);
            epgView.setTextSize(12);
            epgView.setMaxLines(1);
            epgView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            epgView.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            textContainer.addView(epgView);

            container.addView(textContainer);

            favoriteView = new TextView(context);
            favoriteView.setText("★");
            favoriteView.setTextColor(0xFFFBBF24);
            favoriteView.setTextSize(13);
            favoriteView.setGravity(Gravity.CENTER_VERTICAL);
            favoriteView.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT));
            container.addView(favoriteView);
        }

            /** Whether this holder's subtitle should be scrolling right now. */
            private boolean mEpgMarqueeWanted;

            /**
             * Starts or stops the subtitle marquee, and re-applies it once the row has been
             * measured: a marquee started before the view knows its width never runs, which is
             * what left a truncated subtitle sitting still.
             */
            private void applyEpgMarquee(boolean wanted) {
                mEpgMarqueeWanted = wanted;
                ChannelListComponent.applyMarquee(mLabelScroller, epgView, wanted);
            }

        void setChannel(Channel channel, int number, boolean isSelected, boolean isCurrent,
                        EpgManager epgManager, boolean focusOnChannels, boolean isFavorite) {
            nameView.setText(channel.name);
            numberView.setText(number > 0 ? String.valueOf(number) : "");
            favoriteView.setVisibility(isFavorite ? View.VISIBLE : View.GONE);

            logoView.bind(channel.name, channel.logo);

            playingBar.setVisibility(isCurrent ? View.VISIBLE : View.INVISIBLE);

            float scale = 1.0f;
            int epgColor;
            if (isSelected && focusOnChannels) {
                // 焦点行：白底深字，略微放大
                nameView.setTextColor(0xFF0A0A0B);
                numberView.setTextColor(isCurrent ? 0xFFE11D48 : 0xFF52525B);
                epgColor = 0xFF3F3F46;
                container.setBackgroundResource(R.drawable.bg_row_focus);
                scale = 1.04f;
            } else if (isSelected) {
                // 焦点在其他栏：淡白底
                nameView.setTextColor(0xFFF5F5F7);
                numberView.setTextColor(isCurrent ? 0xFFFB7185 : 0xFFA1A1AA);
                epgColor = 0xFFA1A1AA;
                container.setBackgroundResource(R.drawable.bg_row_selected);
            } else {
                nameView.setTextColor(0xFFF5F5F7);
                numberView.setTextColor(isCurrent ? 0xFFFB7185 : 0xFF8A8A93);
                epgColor = 0xFFA1A1AA;
                container.setBackgroundColor(0x00000000);
            }
            nameView.setTextSize(16);
            epgView.setTextColor(epgColor);
            container.animate().scaleX(scale).scaleY(scale).setDuration(150).start();

            // Show EPG info if enabled
            if (AppConfig.isEpgDisplayEnabled() && epgManager != null) {
                String epgInfo = epgManager.getCurrentProgramInfo(channel);
                if (epgInfo != null && !epgInfo.isEmpty()) {
                    epgView.setText(epgInfo);
                    epgView.setVisibility(android.view.View.VISIBLE);
                    // The programme name under the highlighted channel scrolls when it does not
                    // fit, which is the row the viewer is reading.
                    applyEpgMarquee(isSelected && focusOnChannels);
                } else {
                    epgView.setVisibility(android.view.View.GONE);
                }
            } else {
                epgView.setVisibility(android.view.View.GONE);
            }
        }

        private int dp2px(int dp) {
            return Math.round(context.getResources().getDisplayMetrics().density * dp);
        }
    }

    private int dp2px(int dp) {
        return Math.round(mContext.getResources().getDisplayMetrics().density * dp);
    }
}
