package com.nike.accel.ui.widgets.gauges;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.nike.accel.R;

/**
 * A gauge that is displayed with colored segments arranged in a circular arc
 * with values increasing in the clockwise direction. The gauge also contains
 * a 7 segment numerical display in the middle with a small text label to the right of
 * it that can be used for things like units. Beneath the numerical display are two
 * labels. The label directly beneath the numerical display has a smaller text
 * size while the one below this is larger. A button at the bottom of the gauge
 * can be used to provide feedback to the hosting client which can perform
 * whatever logic is necessary that corresponds to what is displayed.
 */
public class MultiColoredScaleGauge extends LinearLayout implements IGaugeUI {
    private Context mContext;
    private IGauge mIGauge;

    private ImageView mImageViewGauge;
    private ImageView mImageViewScaleBg;
    private ImageView mImageViewPointer;
    private TextView mTextView7SegmentValue;
    private TextView mTextView7SegmentLabel;
    private TextView mTextViewTopLabel;
    private TextView mTextViewBottomLabel;

  //  private ImageView mImageViewConnection;

    private ImageView mImageViewPointer_battery;
    private ImageView mImageViewScaleBg_battery;
    private TextView mTextView7SegmentValue_battery;
    private TextView mTextView7SegmentLabel_battery;

    private ImageView mImageViewPointer_current;
    private ImageView mImageViewScaleBg_current;
    private ImageView mImageViewScaleBg_current_pos;
    private TextView mTextView7SegmentValue_current;
    private TextView mTextView7SegmentLabel_current;


   // private boolean mConnected;


    public MultiColoredScaleGauge(Context context) {
        super(context);
        this.mContext = context;
    }

    public MultiColoredScaleGauge(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mContext = context;
    }

    private void initialize(Context context) {

        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        final View vRoot = inflater.inflate(R.layout.gauge_multi_colored_scale, null, false);

        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        vRoot.setLayoutParams(layoutParams);
        addView(vRoot);

        mImageViewGauge = (ImageView) vRoot.findViewById(R.id.iv_gauge);
        mImageViewGauge.setDrawingCacheEnabled(true);

        //gauge central
        mTextView7SegmentLabel = (TextView) vRoot.findViewById(R.id.tv_7_segment_label);
        mTextViewTopLabel = (TextView) vRoot.findViewById(R.id.tv_top_label);
        mTextViewBottomLabel = (TextView) vRoot.findViewById(R.id.tv_bottom_label);

        Typeface font = Typeface.createFromAsset(mContext.getAssets(), "fonts/dseg7modern_regular.ttf");
        mTextView7SegmentValue = (TextView) vRoot.findViewById(R.id.tv_7_segment_value);
        mTextView7SegmentValue.setTypeface(font);

        mImageViewScaleBg = (ImageView) vRoot.findViewById(R.id.iv_scale_bg);
        mImageViewScaleBg.setDrawingCacheEnabled(true);
        mImageViewPointer = (ImageView) vRoot.findViewById(R.id.iv_pointer);

        //gauge bateria
        mTextView7SegmentLabel_battery = (TextView) vRoot.findViewById(R.id.tv_7_segment_label2);

        mTextView7SegmentValue_battery = (TextView) vRoot.findViewById(R.id.tv_7_segment_value2);
        mTextView7SegmentValue_battery.setTypeface(font);

        mImageViewScaleBg_battery = (ImageView) vRoot.findViewById(R.id.iv_scale_bg2);
        mImageViewScaleBg_battery.setDrawingCacheEnabled(true);
        mImageViewPointer_battery = (ImageView) vRoot.findViewById(R.id.iv_pointer2);

        //gauge corrente
        mTextView7SegmentLabel_current = (TextView) vRoot.findViewById(R.id.tv_7_segment_label3);

        mTextView7SegmentValue_current = (TextView) vRoot.findViewById(R.id.tv_7_segment_value3);
        mTextView7SegmentValue_current.setTypeface(font);

        mImageViewScaleBg_current = (ImageView) vRoot.findViewById(R.id.iv_scale_bg3);
        mImageViewScaleBg_current.setDrawingCacheEnabled(true);
        mImageViewPointer_current = (ImageView) vRoot.findViewById(R.id.iv_pointer3);

        mImageViewScaleBg_current_pos = (ImageView) vRoot.findViewById(R.id.iv_scale_bg4);
        mImageViewScaleBg_current_pos.setDrawingCacheEnabled(true);

        final ImageView iv_button = (ImageView) vRoot.findViewById(R.id.iv_button);

        vRoot.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                vRoot.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                float xCenter = getWidth() / 2;
                float yCenter = getHeight() / 2;

                mImageViewPointer.setPivotX(mImageViewPointer.getWidth() / 2);
                mImageViewPointer.setPivotY(getResources().getDimension(R.dimen.pointer_y_position));

                mImageViewPointer_battery.setPivotX(mImageViewPointer_battery.getWidth() / 2);
                mImageViewPointer_battery.setPivotY(getResources().getDimension(R.dimen.pointer_y_position2));

                mImageViewPointer_current.setPivotX(mImageViewPointer_current.getWidth() / 2);
                mImageViewPointer_current.setPivotY(getResources().getDimension(R.dimen.pointer_y_position2));

                setPointerValue(0);
            }
        });

        iv_button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIGauge != null)
                    mIGauge.onClick();
            }
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        initialize(mContext);
    }


    /**
     * Sets the gauge's value. Must be a number between 0 and 10 (inclusively).
     *
     * @param value
     */
    @Override
    public void setPointerValue(final float value) {
        ((Activity) mContext).runOnUiThread(new Runnable() {
            public void run() {
                // Calculate the angle that corresponds to the value.
                float angle = 6 * value + 210;  // 6 para ajuste de parâmetros

                ((GradientScale) mImageViewScaleBg).setArcAngle(angle - 210);
                mImageViewScaleBg.invalidate();
                mImageViewPointer.setRotation(angle);
            }
        });
    }

    @Override
    public void set7SegmentDisplayValue(final String value) {
        ((Activity) mContext).runOnUiThread(new Runnable() {
            public void run() {
                mTextView7SegmentValue.setText(value);
            }
        });
    }

    @Override
    public void set7SegmentLabel(String text) {
        mTextView7SegmentLabel.setText(text);
    }

    @Override
    public void setPointerValue_Battery(final float value) {
        ((Activity) mContext).runOnUiThread(new Runnable() {
            public void run() {
                // Calculate the angle that corresponds to the value.

                float angle = (float) ((1.5 * (value)) + 210); // 1.5 para acertar parâmetros para este gauge

                ((GradientScale_Battery) mImageViewScaleBg_battery).setArcAngle(angle - 210);
                mImageViewScaleBg_battery.invalidate();
                mImageViewPointer_battery.setRotation(angle);
            }
        });
    }

    @Override
    public void set7SegmentLabel_Battery(String text) {
        mTextView7SegmentLabel_battery.setText(text);
    }

    @Override
    public void set7SegmentDisplayValue_Battery(final String value) {
        ((Activity) mContext).runOnUiThread(new Runnable() {
            public void run() {
                mTextView7SegmentValue_battery.setText(value);
            }
        });
    }

    @Override
    public void setPointerValue_Current(final float value) {
        ((Activity) mContext).runOnUiThread(new Runnable() {
            public void run() {
                // Calculate the angle that corresponds to the value.
                if(value < 0) {
                    float angle = (float) ((0.3 * (-value)) + 90); //0.3 para acertar parâmetros para este gauge + 90

                    ((GradientScale_Current_Negative) mImageViewScaleBg_current).setArcAngle(angle - 90);
                    mImageViewScaleBg_current.invalidate();
                    mImageViewPointer_current.setRotation(angle);
                } else {
                    float angle = (float) ((0.3 * (value)) + 90); //0.3 para acertar parâmetros para este gauge + 90

                    ((GradientScale_Current_Positive) mImageViewScaleBg_current_pos).setArcAngle(angle - 90);
                    mImageViewScaleBg_current_pos.invalidate();
                    mImageViewPointer_current.setRotation(-angle +180);
                }
            }
        });
    }

    @Override
    public void set7SegmentLabel_Current(String text) {
        mTextView7SegmentLabel_current.setText(text);
    }

    @Override
    public void set7SegmentDisplayValue_Current(final String value) {
        ((Activity) mContext).runOnUiThread(new Runnable() {
            public void run() {
                mTextView7SegmentValue_current.setText(value);
            }
        });
    }



    @Override
    public void setMinorLabel(String text) {
        mTextViewTopLabel.setText(text);
    }

    @Override
    public void setMajorLabel(String text) {
        mTextViewBottomLabel.setText(text);
    }

    @Override
    public void setIGauge(IGauge iGauge) {
        mIGauge = iGauge;
    }

  /*  @Override
    public void setConnected(boolean connected) {
       // mConnected = connected;

        ((Activity) mContext).runOnUiThread(new Runnable() {
            public void run() {
             //   if (mConnected)
             //       mImageViewConnection.setImageDrawable(getResources().getDrawable(R.drawable.connected));
             //   else
             //       mImageViewConnection.setImageDrawable(getResources().getDrawable(R.drawable.not_connected));
            }
        });
    }*/
}
