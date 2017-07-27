package com.nike.accel.bo;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.Formatter;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.lang.*;

import com.nike.accel.R;
import com.nike.accel.data.local.Preferences;
import com.nike.accel.ui.widgets.gauges.IBaseGpsListener;
import com.nike.accel.ui.widgets.gauges.IGauge;
import com.nike.accel.ui.widgets.gauges.IGaugeData;
import com.nike.accel.ui.widgets.gauges.IGaugeUI;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;

/**
 * Encapsulates the business logic that retrieves accelerometer motion and displays it on the
 * screen. It also manages the different modes on the gauge where the user can switch between
 * displaying acceleration, current distance traveled, total distance traveled and running an
 * automated demo mode.
 * <p/>
 * This gauge is independent of the UI that is used to display the data. Communicating with
 * the gauge that handles UI logic is done through the IGauge interface.
 */
public class AccelGauge implements IGauge, IBaseGpsListener {

    public static final int GAUGE_MODE_DEMO = 3;
    public static final int GAUGE_MODE_CURRENT_TEST = 2;
    public static final int GAUGE_MODE_GPS =1;
    public static final int GAUGE_MODE_ADDOFFSET = 0;

    public static float OFFSET;


    private final int MAX_GAUGE_VALUE = 50;
    private final int MAX_GAUGE_VALUE_BATTERY = 100;
    private final int MAX_GAUGE_VALUE_CURRENT = 300;
    private final int MIN_GAUGE_VALUE_CURRENT = -200;

    private final int SENSOR_READ_RATE = 50; // ms
   // public static final int TEXT_UPDATE_INTERVAL = 100; // ms

    private float nCORRENTE = 0;
    private float nBATERIA = 0;

    private int nLOOP = 800;
    private int maxLOOP = 1000;  //para esperar 5 segundos para atualização

    private IGaugeUI mIGaugeUI;
    private IGaugeData mIGaugeData;
    private Context mContext;
  //  private SensorManager mSensorManager;
  //  private Sensor mSensor;

    private float[] mSpeedValues = new float[2];
    private float[] mBatteryValues = new float[2];

    private GaugeTimerTask mGaugeTimerTask;
    private Timer mTimerUpdate;
//    private float mTotalDistance;
    private float mTotalDistanceSinceAppFirstStarted;
    private int mGaugeMode;
  //  private long mTimeForLastDisplayUpdate;
 //   private float mAveSpeed;
//    private float mAccumulatedSpeed;
    private boolean mDisplayRequiresUpdate;
 //   private int mTotalAveSpeedPoints;
    private float mDemoSpeedValue;
    private boolean mDemoDecrement;
    private boolean mDemoCurrentDecrement;
    private boolean mDemoBatteryDecrement;
 //   private boolean mIgnoreSensorData;
  //  private float mMaxSensorVal;
 //   private float mSpeed;
  //  private boolean mFirstAccelDataRead;

    private CLocation mLocation;
    private NetworkInfo wifiCheck;
    


    /**
     * The constructor for the gauge.
     *
     * @param iGaugeUI   This is the interface that is used to do the actual display of data.
     * @param iGaugeData This interface allows this gauge to communicate back to the client
     *                   to notify it whenever data is available. The client can then execute
     *                   other tasks such as storing the data to a database or sending it to
     *                   a server.
     * @param context
     */
    public AccelGauge(IGaugeUI iGaugeUI, IGaugeData iGaugeData, Context context) {
        mIGaugeUI = iGaugeUI;
        mIGaugeData = iGaugeData;
        mContext = context;
        init();
    }

    /**
     * Initializes the gauge.
     */
    private void init() {
        // Set the gauge mode to whatever it was when the app last shut down .
        mGaugeMode = Preferences.getGaugeMode(mContext);
        mTotalDistanceSinceAppFirstStarted = Preferences.getTotalDistance(mContext);

        LocationManager locationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        this.updateSpeedGPS(null);



        mIGaugeUI.setIGauge(this);
        setDisplayMode();
        startTimerTask();

        mSpeedValues[0] = 0;
        mSpeedValues[1] = 0;
        //registerSensor();

        updateGaugeBattery(80);
    }


    /**
     * Gets called whenever the user taps on the gauge's mode button.
     */
    @Override
    public void onClick() {
        mGaugeMode = mGaugeMode == GAUGE_MODE_DEMO ? GAUGE_MODE_ADDOFFSET : ++mGaugeMode;

        final String TAG = "----------- onCLick()";
        Log.i(TAG, String.valueOf(mGaugeMode));
        Preferences.saveGaugeMode(mGaugeMode, mContext);
        setDisplayMode();
    }

    /**
     * Initializes the gauge based upon the display mode: speed, current distance, total distance,
     * demo.
     */
    private void setDisplayMode() {

        mIGaugeUI.set7SegmentLabel_Battery("%");
        mIGaugeUI.set7SegmentLabel_Current("A");

        switch (mGaugeMode) {
         /*   case GAUGE_MODE_SPEED:
                mIGaugeUI.set7SegmentLabel(mContext.getResources().getString(R.string.m_per_second));
                mIGaugeUI.setMajorLabel(mContext.getResources().getString(R.string.acelerometro));
                mIGaugeUI.setMinorLabel("");
                break;
            */
            case GAUGE_MODE_ADDOFFSET:
                mIGaugeUI.set7SegmentLabel(mContext.getResources().getString(R.string.m_per_second));
                mIGaugeUI.setMajorLabel(mContext.getResources().getString(R.string.cinco));
                mIGaugeUI.setMinorLabel("");
                break;

            case GAUGE_MODE_GPS:
                mIGaugeUI.set7SegmentLabel(mContext.getResources().getString(R.string.m_per_second));
                mIGaugeUI.setMajorLabel(mContext.getResources().getString(R.string.gps));
                mIGaugeUI.setMinorLabel("");
                break;

            case GAUGE_MODE_CURRENT_TEST:
                mIGaugeUI.set7SegmentLabel(mContext.getResources().getString(R.string.m_per_second));
                mIGaugeUI.setMajorLabel(mContext.getResources().getString(R.string.current));
                mIGaugeUI.setMinorLabel("");
                break;

         /*   case GAUGE_MODE_KMMulti:
                mIGaugeUI.set7SegmentLabel("");
                mIGaugeUI.setMajorLabel(mContext.getResources().getString(R.string.outro));
                mIGaugeUI.setMinorLabel("");
                break;

            case GAUGE_MODE_KMDiv:
                mIGaugeUI.set7SegmentLabel("");
                mIGaugeUI.setMajorLabel(mContext.getResources().getString(R.string.outro2));
                mIGaugeUI.setMinorLabel("");
                break;
            */
            case GAUGE_MODE_DEMO:
                mIGaugeUI.set7SegmentLabel(mContext.getResources().getString(R.string.m_per_second));
                mIGaugeUI.setMajorLabel(mContext.getResources().getString(R.string.demo));
                mIGaugeUI.setMinorLabel("");
                break;
        }

        mDisplayRequiresUpdate = true;
        updateGauge();
    }




    /**
     * Starts a timer task that periodically updates the gauge's UI with new sensor data.
     */
    private void startTimerTask() {
        mGaugeTimerTask = new GaugeTimerTask();
        mTimerUpdate = new Timer();
        mTimerUpdate.scheduleAtFixedRate(mGaugeTimerTask, 0, SENSOR_READ_RATE);
    }

    @Override
    public void onAppDestroy() {
        shutdown();
    }

    @Override
    public void onAppStop() {
    }

    @Override
    public void onAppPause() {
    }

    @Override
    public void onAppResume() {
    }

    /**
     * Handles cleaning up resources when the app is being shut down.
     */
    private void shutdown() {
       // unregisterSensor();
        mTimerUpdate.cancel();
    }

    /**
     * Registers a listener to retrieve data from the accelerometer.
     */
 /*   private void registerSensor() {
        // Get the acceleromter sensor.
        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);

        if (mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mMaxSensorVal = mSensor.getMaximumRange();
            mSensorManager.registerListener(this, mSensor, SENSOR_READ_RATE * 1000);
        }
    }
*/
    /**
     * Unregisters the listener used for retrieving accelerometer data.
     */
   /* private void unregisterSensor() {
        if (mSensorManager != null) {
            mSensorManager.unregisterListener(this);
            mSensor = null;
            mSensorManager = null;
        }
    }*/

    /**
     * Enables or disables the processing of sensor data. Used internally for test purposes only.
     *
     * @param ignore If set to true, sensor data will be ignored.
     */
  /*  public void setIgnoreSensorData(boolean ignore) {
        mMaxSensorVal = mSensor.getMaximumRange();
        mIgnoreSensorData = ignore;
    }*/

    /**
     * Resets all data values to their normal initialized state as though the app was being
     * started for the very first time. Used internally for test purposes only.
     */
    public void resetGaugeForFirstUse() {
        mGaugeMode = GAUGE_MODE_ADDOFFSET;
      //  mTotalDistance = 0;
      //  mAccumulatedSpeed = 0;
      //  mTotalAveSpeedPoints = 0;
      //  mTimeForLastDisplayUpdate = System.currentTimeMillis();
        mDisplayRequiresUpdate = false;
        mTotalDistanceSinceAppFirstStarted = 0;
      //  mMaxSensorVal = 1;
      //  mFirstAccelDataRead = false;
        Preferences.saveGaugeMode(GAUGE_MODE_ADDOFFSET, mContext);
        Preferences.saveTotalDistance(0, mContext);

        ((Activity) mContext).runOnUiThread(new Runnable() {
            public void run() {
                setDisplayMode();
            }
        });
    }

    /**
     * Sets the total distance since the app first started. Used internally for test purposes only.
     *
     * @param distance The total distance.
     */
    public void setTotalDistanceSinceAppFirstStarted(float distance) {
        mTotalDistanceSinceAppFirstStarted = distance;
        mDisplayRequiresUpdate = true;
    }

  /*  @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
    }*/

   /* @Override
    public final void onSensorChanged(SensorEvent event) {
        if (mIgnoreSensorData)
            return;

        processSensorData(event.values);
    }*/

    /**
     * Processes data from the sensor.
     *
     * @param data A 3 dimensional array containing acceleration data for each axis: x, y, z
     */
  //  public void processSensorData(float[] data) {
        //final String TAG = "processSensorData()";
       // Log.i(TAG, String.valueOf(data[0]));
       // Log.i(TAG, String.valueOf(data[1]));
       // Log.i(TAG, String.valueOf(data[2]));
        /*
         * For this demo, we won't calculate true speed because it involves removing the force of
         * gravity from the acceleration and this is a process that involves tweaking a formula
         * that itself is dependent upon a specific acceleromter chip used to retrieve sensor data.
         *
         * Since our goal is just to show total movement, we'll calculate speed by pretending
         * that the acceleration values are "axis location" values and calculate a virtual speed by
         * taking the difference between the current locations on each axis and their previous
         * locations and scaling up to the sensor's maximum range and then scaling again to
         * get the value within a range of zero to ten.
         */

   /*     if (!mFirstAccelDataRead) {
            mPrevSpeedValues[0] = data[0];
            mPrevSpeedValues[1] = data[1];
            mPrevSpeedValues[2] = data[2];

            mTimeForLastDisplayUpdate = System.currentTimeMillis();

            mFirstAccelDataRead = true;

            return;
        }

        float speed = (Math.abs(data[0] - mPrevSpeedValues[0]) +
                Math.abs(data[1] - mPrevSpeedValues[1]) +
                Math.abs(data[2] - mPrevSpeedValues[2])) / mMaxSensorVal * 10 * 2;

        // When the device is not moving, there is a minimal amount of internal jitter that
        // will show up on the gauge's needle. To prevent the needle from moving when no
        // real motion is applied, only record the speed when it exceeds a minimal threshold.

        if (speed < 2)
            speed = 0;

        if (speed > 0)
            mTotalDistance += speed * .001;

        if (speed > MAX_GAUGE_VALUE)
            speed = MAX_GAUGE_VALUE;

        mSpeed = speed;

        mPrevSpeedValues[0] = data[0];
        mPrevSpeedValues[1] = data[1];
        mPrevSpeedValues[2] = data[2];

        mAccumulatedSpeed += speed;
        mTotalAveSpeedPoints++;

        // Update the display at a regular time interval.
        long timeDiff = System.currentTimeMillis() - mTimeForLastDisplayUpdate;

        if ((timeDiff >= TEXT_UPDATE_INTERVAL) && (mTotalAveSpeedPoints > 0) && !mDisplayRequiresUpdate) {
            if (mAccumulatedSpeed > 0)
                Preferences.saveTotalDistance(getTotalDistanceSinceAppFirstStarted(), mContext);

            mAveSpeed = mAccumulatedSpeed / mTotalAveSpeedPoints;
            mAccumulatedSpeed = 0;
            mTotalAveSpeedPoints = 0;
            mDisplayRequiresUpdate = true;
        }

        mIGaugeData.dataAvailable(speed, mAveSpeed, mTotalDistance, getTotalDistanceSinceAppFirstStarted());

    }*/

    /**
     * Returns the total distance traveled since the app first started. By "first started", this
     * means the very first time the app started. If you close the app and then start it again,
     * this would be the second time it was started.
     *
     * @return
     */
  /*  private float getTotalDistanceSinceAppFirstStarted() {
        return mTotalDistanceSinceAppFirstStarted + mTotalDistance;
    }
*/
    /**
     * Updates the gauge's value on the 7 segment display.
     *
     * @param value The value to display.
     */
    private void updateDisplayValue(float value) {
        DecimalFormat df;

        if (value < 1)
            df = new DecimalFormat("0.0");
        else
            df = new DecimalFormat("###.0");

        String text = df.format(value);
        mIGaugeUI.set7SegmentDisplayValue(text);

       // mTimeForLastDisplayUpdate = System.currentTimeMillis();
        mDisplayRequiresUpdate = false;
    }

    private void updateDisplayValue_Battery(float value) {
        DecimalFormat df;

        if (value < 1)
            df = new DecimalFormat("0");
        else
            df = new DecimalFormat("###");

        String text = df.format(value);
        mIGaugeUI.set7SegmentDisplayValue_Battery(text);

       // mTimeForLastDisplayUpdate = System.currentTimeMillis();
        mDisplayRequiresUpdate = false;
    }

    private void updateDisplayValue_Current(float value) {
        DecimalFormat df;

        if (value < 1)
            df = new DecimalFormat("0");
        else
            df = new DecimalFormat("###");

        String text = df.format(value);
        mIGaugeUI.set7SegmentDisplayValue_Current(text);

      //  mTimeForLastDisplayUpdate = System.currentTimeMillis();
        mDisplayRequiresUpdate = false;
    }

    /**
     * Updates the gauge with data which includes the data on the 7 segment display as well
     * as the position of the pointer.
     */
    private void updateGauge() {
        //updateGaugeBattery(90);
        //updateGaugeCurrent(-100);
        final String TAG = "updateGauge()";
        //Log.i(TAG, "entrou");

        if (nLOOP < maxLOOP)
            nLOOP ++;
        else{
            nLOOP = 0;
            Log.i("---LOOP", "entrou");
            batteryRequest();
        }


        switch (mGaugeMode) {
          /*  case GAUGE_MODE_SPEED:

                mIGaugeUI.setPointerValue(mSpeed);
                updateDisplayValue(mSpeed);

                if (mDisplayRequiresUpdate)
                    updateDisplayValue(mAveSpeed);

                break;

            case GAUGE_MODE_KMMulti:
                getSpeedLocation(mLocation);
                break;

            case GAUGE_MODE_KMDiv:
                getSpeedLocation2(mLocation);
                break;

          */

            case GAUGE_MODE_ADDOFFSET:
                addFiveGPSSpeed(mLocation);
                break;

            case GAUGE_MODE_GPS:
                updateSpeedGPS(mLocation);
              //  Log.i(TAG, String.valueOf(mLocation));
                break;

            case GAUGE_MODE_CURRENT_TEST:
                updateSpeedGPSandCurrent(mLocation);
               // Log.i(TAG, String.valueOf(mLocation));
                break;

            case GAUGE_MODE_DEMO:


                if (!mDemoDecrement && (mDemoSpeedValue >= MAX_GAUGE_VALUE))
                    mDemoDecrement = true;
                else if (mDemoDecrement && (mDemoSpeedValue <= 0))
                    mDemoDecrement = false;

                if (mDemoDecrement)
                    mDemoSpeedValue = mDemoSpeedValue - (10f / 300f);
                else
                    mDemoSpeedValue = mDemoSpeedValue + (10f / 300f);

                setGaugePointerValue(mDemoSpeedValue);
                updateDisplayValue(mDemoSpeedValue);

                if (!mDemoCurrentDecrement && (nCORRENTE >= MAX_GAUGE_VALUE_CURRENT))
                    mDemoCurrentDecrement = true;
                else if (mDemoCurrentDecrement && (nCORRENTE <= MIN_GAUGE_VALUE_CURRENT))
                    mDemoCurrentDecrement = false;

                if(mDemoCurrentDecrement)
                    nCORRENTE = nCORRENTE - (100f / 150f);
                else
                    nCORRENTE = nCORRENTE + (100f / 150f);

                updateGaugeCurrent(nCORRENTE);

          /*      if (!mDemoBatteryDecrement && (nBATERIA >= MAX_GAUGE_VALUE_BATTERY))
                    mDemoBatteryDecrement = true;
                else if (mDemoBatteryDecrement && (nBATERIA <= 0))
                    mDemoBatteryDecrement = false;

                if(mDemoBatteryDecrement)
                    nBATERIA = nBATERIA - (5f / 150f);
                else
                    nBATERIA = nBATERIA + (5f / 150f);

                updateGaugeBattery(nBATERIA);*/
                break;

        }
    }

    private void updateGaugeBattery(float value){

       // mBatteryValues[1] = value;

      /*  while (true)
        {
            if (mBatteryValues[0] < mBatteryValues[1]) {
                setGaugePointerValue_Battery(mBatteryValues[0]);
                updateDisplayValue_Battery(mBatteryValues[0]);
                ++mBatteryValues[0];

            } else if (Math.abs(mBatteryValues[1] - mBatteryValues[0]) < 1) {
                setGaugePointerValue_Battery(value);
                updateDisplayValue_Battery(value);
                mBatteryValues[0] = mBatteryValues[1];
                break;
            } else {
                setGaugePointerValue_Battery(mBatteryValues[0]);
                updateDisplayValue_Battery(mBatteryValues[0]);
                --mBatteryValues[0];
            }
        }*/

        setGaugePointerValue_Battery(value);
        updateDisplayValue_Battery(value);
    }

    private void updateGaugeCurrent(float value){
        setGaugePointerValue_Current(value);
        updateDisplayValue_Current(value);
    }


    /**
     * Sets the gauge's pointer to the specified value.
     *
     * @param value The value to set.
     */
    private void setGaugePointerValue(float value) {
        if (value > MAX_GAUGE_VALUE)
            mIGaugeUI.setPointerValue(MAX_GAUGE_VALUE);
        else
            mIGaugeUI.setPointerValue(value);

    }

    private void setGaugePointerValue_Battery(float value) {
        if (value > MAX_GAUGE_VALUE_BATTERY)
            mIGaugeUI.setPointerValue_Battery(MAX_GAUGE_VALUE_BATTERY);
        else
            mIGaugeUI.setPointerValue_Battery(value);

    }

    private void setGaugePointerValue_Current(float value) {
        if (value > 0 && value > MAX_GAUGE_VALUE_CURRENT)
            mIGaugeUI.setPointerValue_Current(MAX_GAUGE_VALUE_CURRENT);
         else if(value <0 && value < MIN_GAUGE_VALUE_CURRENT)
            mIGaugeUI.setPointerValue_Current(MIN_GAUGE_VALUE_CURRENT);
        else
            mIGaugeUI.setPointerValue_Current(value);

    }



    /**
     * Used to periodically update the gauge.
     */
    class GaugeTimerTask extends TimerTask {
        @Override
        public void run() {
            updateGauge();
        }
    }

   /* private void getSpeedLocation(CLocation location){
       // setGaugePointerValue(mDemoSpeedValue);
       // updateDisplayValue(mDemoSpeedValue);
        final String TAG = "updateSpeedGPS-KM-H()";

        float nCurrentSpeed = 0;

        if(location != null)
        {
            //location.setUseMetricunits(this.useMetricUnits());
            nCurrentSpeed = location.getSpeed();
        }

        Formatter fmt = new Formatter(new StringBuilder());
        fmt.format(Locale.US, "%5.1f", nCurrentSpeed);
        String strCurrentSpeed = fmt.toString();
        strCurrentSpeed = strCurrentSpeed.replace(' ', '0');

       // Log.i(TAG, strCurrentSpeed);

        //String strUnits = "meters/second";

        //TextView txtCurrentSpeed = (TextView) this.findViewById(R.id.txtCurrentSpeed);
        //txtCurrentSpeed.setText(strCurrentSpeed + " " + strUnits);

        Log.i(TAG, String.valueOf(Float.parseFloat(strCurrentSpeed)*3.6f));

        setGaugePointerValue(Float.parseFloat(strCurrentSpeed)*3.6f);
        updateDisplayValue(Float.parseFloat(strCurrentSpeed)*3.6f);


    }*/

   /* private void getSpeedLocation2(CLocation location){
        // setGaugePointerValue(mDemoSpeedValue);
        // updateDisplayValue(mDemoSpeedValue);
        final String TAG = "updateSpeedGPS-KM-H()";

        float nCurrentSpeed = 0;

        if(location != null)
        {
            //location.setUseMetricunits(this.useMetricUnits());
            nCurrentSpeed = location.getSpeed();
        }

        Formatter fmt = new Formatter(new StringBuilder());
        fmt.format(Locale.US, "%5.1f", nCurrentSpeed);
        String strCurrentSpeed = fmt.toString();
        strCurrentSpeed = strCurrentSpeed.replace(' ', '0');

        // Log.i(TAG, strCurrentSpeed);

        //String strUnits = "meters/second";

        //TextView txtCurrentSpeed = (TextView) this.findViewById(R.id.txtCurrentSpeed);
        //txtCurrentSpeed.setText(strCurrentSpeed + " " + strUnits);

        Log.i(TAG, String.valueOf(Float.parseFloat(strCurrentSpeed)/3.6f));

        setGaugePointerValue(Float.parseFloat(strCurrentSpeed)/3.6f);
        updateDisplayValue(Float.parseFloat(strCurrentSpeed)/3.6f);


    } */

    private void addFiveGPSSpeed(CLocation location){
        //setGaugePointerValue(mDemoSpeedValue);
        // updateDisplayValue(mDemoSpeedValue);
        // TODO Auto-generated method stub

        final String TAG = "Add5GPSSpeed()";

        float nCurrentSpeed = 0;

        if(location != null)
        {
            //location.setUseMetricunits(this.useMetricUnits());
            nCurrentSpeed = location.getSpeed();
        }

        Formatter fmt = new Formatter(new StringBuilder());
        fmt.format(Locale.US, "%5.1f", nCurrentSpeed);
        String strCurrentSpeed = fmt.toString();
        strCurrentSpeed = strCurrentSpeed.replace(' ', '0');

       // Log.i(TAG, strCurrentSpeed);

        //String strUnits = "meters/second";

        if(nCurrentSpeed < 5)
            OFFSET = nCurrentSpeed;
        else
            OFFSET = 5f;

        //TextView txtCurrentSpeed = (TextView) this.findViewById(R.id.txtCurrentSpeed);
        //txtCurrentSpeed.setText(strCurrentSpeed + " " + strUnits);

       // Log.i(TAG, String.valueOf(Float.parseFloat(strCurrentSpeed)+OFFSET));

        setGaugePointerValue(Float.parseFloat(strCurrentSpeed)+OFFSET);
        updateDisplayValue(Float.parseFloat(strCurrentSpeed)+OFFSET);
    }

    private void updateSpeedGPS(CLocation location){
        //setGaugePointerValue(mDemoSpeedValue);
        // updateDisplayValue(mDemoSpeedValue);
        // TODO Auto-generated method stub

        final String TAG = "updateSpeedGPS()";

        float nCurrentSpeed = 0;

        if(location != null)
        {
            //location.setUseMetricunits(this.useMetricUnits());
            nCurrentSpeed = location.getSpeed();
        }

        Formatter fmt = new Formatter(new StringBuilder());
        fmt.format(Locale.US, "%5.1f", nCurrentSpeed);
        String strCurrentSpeed = fmt.toString();
        strCurrentSpeed = strCurrentSpeed.replace(' ', '0');

       // Log.i(TAG, strCurrentSpeed);

        //String strUnits = "meters/second";


        if(nCurrentSpeed < 5)
            OFFSET = Float.parseFloat(strCurrentSpeed);
        else
            OFFSET = 5f;

        mSpeedValues[1] = Float.parseFloat(strCurrentSpeed);

        if(mSpeedValues[0] < mSpeedValues[1]){
            setGaugePointerValue(mSpeedValues[0]+OFFSET);
            updateDisplayValue(mSpeedValues[0]+OFFSET);
            mSpeedValues[0] = mSpeedValues[0] + (100f/300f);
        } else if(Math.abs(mSpeedValues[1] - mSpeedValues[0]) < 1){
            setGaugePointerValue(Float.parseFloat(strCurrentSpeed)+OFFSET);
            updateDisplayValue(Float.parseFloat(strCurrentSpeed)+OFFSET);
            mSpeedValues[0] = mSpeedValues[1];
        } else {
            setGaugePointerValue(mSpeedValues[0]+OFFSET);
            updateDisplayValue(mSpeedValues[0]+OFFSET);
            mSpeedValues[0] = mSpeedValues[0] - (100f/300f);
        }

        //TextView txtCurrentSpeed = (TextView) this.findViewById(R.id.txtCurrentSpeed);
        //txtCurrentSpeed.setText(strCurrentSpeed + " " + strUnits);

      // Log.i(TAG, strCurrentSpeed);


    }

    private void updateSpeedGPSandCurrent(CLocation location){
        //setGaugePointerValue(mDemoSpeedValue);
        // updateDisplayValue(mDemoSpeedValue);
        // TODO Auto-generated method stub

        final String TAG = "updateSpeedGPS()";

        float nCurrentSpeed = 0;

        if(location != null)
        {
            nCurrentSpeed = location.getSpeed();
        }

        Formatter fmt = new Formatter(new StringBuilder());
        fmt.format(Locale.US, "%5.1f", nCurrentSpeed);
        String strCurrentSpeed = fmt.toString();
        strCurrentSpeed = strCurrentSpeed.replace(' ', '0');

        if(nCurrentSpeed < 5)
            OFFSET = Float.parseFloat(strCurrentSpeed);
        else
            OFFSET = 5f;

        mSpeedValues[1] = Float.parseFloat(strCurrentSpeed);

        if(mSpeedValues[0] < mSpeedValues[1]){
            setGaugePointerValue(mSpeedValues[0]+OFFSET);
            updateDisplayValue(mSpeedValues[0]+OFFSET);
            ++mSpeedValues[0];
            updateGaugeCurrent(Math.abs(mSpeedValues[1] - mSpeedValues[0])*6);

        } else if(Math.abs(mSpeedValues[1] - mSpeedValues[0]) < 1){
            setGaugePointerValue(Float.parseFloat(strCurrentSpeed)+OFFSET);
            updateDisplayValue(Float.parseFloat(strCurrentSpeed)+OFFSET);
            mSpeedValues[0] = mSpeedValues[1];
            updateGaugeCurrent(0);

        } else {
            setGaugePointerValue(mSpeedValues[0]+OFFSET);
            updateDisplayValue(mSpeedValues[0]+OFFSET);
            --mSpeedValues[0];
            updateGaugeCurrent(Math.abs(mSpeedValues[1] - mSpeedValues[0])*-4);
        }

        //updateGaugeCurrent(nCORRENTE);
    }


    //Methods do IBaseGPSLinseter

    @Override
    public void onLocationChanged(Location location) {
        if(location != null)
        {
            CLocation myLocation = new CLocation(location, this.useMetricUnits());
            //this.getSpeedGPS(myLocation);
            mLocation = myLocation;
        }

    }

    private boolean useMetricUnits() {
        // TODO Auto-generated method stub
       // CheckBox chkUseMetricUnits = (CheckBox) this.findViewById(R.id.chkMetricUnits);
        return true;
    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onGpsStatusChanged(int event) {

    }

    public void batteryRequest()//da todas las posiciones dado un email
    {
        String TAG = "------------- batteryRequest()";
        ConnectivityManager connectionManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        wifiCheck = connectionManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        if (!wifiCheck.isConnected()) {
            Log.i(TAG, "Não conectado");
            updateGaugeBattery(80);

        } else {
            Log.i(TAG, "Conectado");
            // https://api.twitter.com/1/statuses/user_timeline.json?include_entities=true&include_rts=true&screen_name=charliesheen&count=2
            //  List<String> tweetsList= new ArrayList<String>();
            String result = "";
            String SOC = "";

            //http get
            InputStream is = null;
            try {
                HostnameVerifier hostnameVerifier = org.apache.http.conn.ssl.SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;

                DefaultHttpClient client = new DefaultHttpClient();

                SchemeRegistry registry = new SchemeRegistry();
                SSLSocketFactory socketFactory = SSLSocketFactory.getSocketFactory();
                socketFactory.setHostnameVerifier((X509HostnameVerifier) hostnameVerifier);
                registry.register(new Scheme("https", socketFactory, 443));
                SingleClientConnManager mgr = new SingleClientConnManager(client.getParams(), registry);
                DefaultHttpClient httpClient = new DefaultHttpClient(mgr, client.getParams());

                HttpsURLConnection.setDefaultHostnameVerifier(hostnameVerifier);
                HttpGet httpget = new HttpGet("https://mobilis.eco.br/api/public/tablet");
                HttpResponse response = httpClient.execute(httpget);
                HttpEntity entity = response.getEntity();


                is = entity.getContent();
            } catch (Exception e) {
                Log.e("log_tag", "Error in http connection " + e.toString());
            }
            //convert response to string
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "iso-8859-1"), 8);
                StringBuilder sb = new StringBuilder();
                String line = null;
                while ((line = reader.readLine()) != null) {
                    sb.append(line + "\n");
                }
                is.close();
                result = sb.toString();
                // Log.i(TAG, result);
            } catch (Exception e) {
                Log.e("log_tag", "Error converting result " + e.toString());
                updateGaugeBattery(80);
            }
            //parse json data
            try {
                JSONArray jArray = new JSONArray(result);
                for (int i = 0; i < jArray.length(); i++) {
                    JSONObject json_data = jArray.getJSONObject(i);
                    SOC = (json_data.getString("soh")); //text es el nombre del campo del tweet
                    //  Log.i("-----------SOC", SOC);
                    updateGaugeBattery(Float.parseFloat(SOC));
                }
            } catch (JSONException e) {
                Log.e("log_tag", "Error parsing data " + e.toString());
            }
        }
    }
}
