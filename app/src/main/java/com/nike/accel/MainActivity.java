package com.nike.accel;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.ViewTreeObserver;

import com.nike.accel.bo.AccelGauge;
import com.nike.accel.ui.widgets.gauges.MultiColoredScaleGauge;

public class MainActivity extends AppCompatActivity {

    public AccelGauge mAccelGauge;
    public MultiColoredScaleGauge mGauge;
    private Context mContext;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = this;
        mGauge = (MultiColoredScaleGauge) findViewById(R.id.gauge);
        mGauge.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mGauge.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                mAccelGauge = new AccelGauge(mGauge, mContext);
            }
        });


    }

    /**
     * "Note: There's no guarantee that onSaveInstanceState() will be called before your activity is destroyed,
     * because there are cases in which it won't be necessary to save the state (such as when the user leaves
     * your activity using the Back button, because the user is explicitly closing the activity). If the system
     * calls onSaveInstanceState(), it does so before onStop() and possibly before onPause()."
     * <p/>
     * Taken from: http://developer.android.com/guide/components/activities.html
     */
  /*  @Override
    public void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
    }
*/
    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mAccelGauge != null) {
            mAccelGauge.onAppDestroy();
            mAccelGauge = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }
}
