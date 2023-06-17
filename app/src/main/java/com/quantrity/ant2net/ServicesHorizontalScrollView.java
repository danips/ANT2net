package com.quantrity.ant2net;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;

public class ServicesHorizontalScrollView extends HorizontalScrollView {
    ImageView r;
    ImageView l;
    private boolean disable_scroll = false;

    public ServicesHorizontalScrollView(Context context) {
        super(context);
    }

    public ServicesHorizontalScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ServicesHorizontalScrollView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setArrows(ImageView r, ImageView l) {
        this.r = r;
        this.l = l;
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        if (!disable_scroll) {
            if (l <= 0) this.l.setVisibility(INVISIBLE);
            else this.l.setVisibility(VISIBLE);

            int maxScrollX = this.getChildAt(0).getMeasuredWidth() - this.getMeasuredWidth();
            if (l < maxScrollX) this.r.setVisibility(VISIBLE);
            else this.r.setVisibility(INVISIBLE);
        }
        super.onScrollChanged(l, t, oldl, oldt);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (this.r != null) {
            if (this.getChildAt(0).getWidth() <= this.getWidth() + this.r.getWidth() + this.l.getWidth()) {
                this.r.setVisibility(GONE);
                this.l.setVisibility(GONE);
                disable_scroll = true;
            } else if (this.getChildAt(0).getWidth() > this.getWidth()) {
                this.r.setVisibility(VISIBLE);
                this.l.setVisibility(INVISIBLE);
            } else {
                this.r.setVisibility(INVISIBLE);
                this.l.setVisibility(INVISIBLE);
            }
        }
    }
}
