package com.nike.accel.bo;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;

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
import com.nike.accel.ui.widgets.gauges.IBaseGpsListener;
import com.nike.accel.ui.widgets.gauges.IGauge;
import com.nike.accel.ui.widgets.gauges.IGaugeUI;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;


public class AccelGauge implements IGauge, IBaseGpsListener {

    private static final int GAUGE_MODE_DEMO = 3;
    private static final int GAUGE_MODE_CURRENT_TEST = 2;
    private static final int GAUGE_MODE_GPS =1;
    private static final int GAUGE_MODE_ADDOFFSET = 0;

    private static float OFFSET;


    private final int MAX_GAUGE_VALUE = 50;
    private final int MAX_GAUGE_VALUE_BATTERY = 100;
    private final int MAX_GAUGE_VALUE_CURRENT = 300;
    private final int MIN_GAUGE_VALUE_CURRENT = -200;

    private float nCORRENTE = 0;

    private int nLOOP = 800;
    private int maxLOOP = 1000;  /*para esperar 5 segundos para atualização*/

    private IGaugeUI mIGaugeUI;
    private Context mContext;

    private float[] mSpeedValues = new float[2];

    private GaugeTimerTask mGaugeTimerTask;
    private Timer mTimerUpdate;
    private int mGaugeMode;

    private float mDemoSpeedValue;
    private boolean mDemoDecrement;
    private boolean mDemoCurrentDecrement;


    private CLocation mLocation;
    private NetworkInfo wifiCheck;
    

    public AccelGauge(IGaugeUI iGaugeUI, Context context) {
        mIGaugeUI = iGaugeUI;
        mContext = context;
        init();
    }

    private void init() {
        mGaugeMode = GAUGE_MODE_DEMO;

        LocationManager locationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        this.updateSpeedGPS(null);

        mIGaugeUI.setIGauge(this);
        setDisplayMode();
        startTimerTask();

        mSpeedValues[0] = 0;
        mSpeedValues[1] = 0;

        updateGaugeBattery(82);
    }

    @Override
    public void onClick() {
        mGaugeMode = mGaugeMode == GAUGE_MODE_DEMO ? GAUGE_MODE_ADDOFFSET : ++mGaugeMode;
       // final String TAG = "----------- onCLick()";
       // Log.i(TAG, String.valueOf(mGaugeMode));
        setDisplayMode();
    }

    private void setDisplayMode() {

        mIGaugeUI.set7SegmentLabel_Battery("%");
        mIGaugeUI.set7SegmentLabel_Current("A");

        switch (mGaugeMode) {

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

            case GAUGE_MODE_DEMO:
                mIGaugeUI.set7SegmentLabel(mContext.getResources().getString(R.string.m_per_second));
                mIGaugeUI.setMajorLabel(mContext.getResources().getString(R.string.demo));
                mIGaugeUI.setMinorLabel("");
                break;
        }

        updateGauge();
    }

    private void startTimerTask() {
        mGaugeTimerTask = new GaugeTimerTask();
        mTimerUpdate = new Timer();
        int SENSOR_READ_RATE = 50;
        mTimerUpdate.scheduleAtFixedRate(mGaugeTimerTask, 0, SENSOR_READ_RATE);
    }

    private class GaugeTimerTask extends TimerTask {
        @Override
        public void run() {
            updateGauge();
        }
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

    private void shutdown() {
        mTimerUpdate.cancel();
    }


    private void updateDisplayValue(float value) {
        DecimalFormat df;

        if (value < 1)
            df = new DecimalFormat("0.0");
        else
            df = new DecimalFormat("###.0");

        String text = df.format(value);
        mIGaugeUI.set7SegmentDisplayValue(text);

    }

    private void updateDisplayValue_Battery(float value) {
        DecimalFormat df;

        if (value < 1)
            df = new DecimalFormat("0");
        else
            df = new DecimalFormat("###");

        String text = df.format(value);
        mIGaugeUI.set7SegmentDisplayValue_Battery(text);

    }

    private void updateDisplayValue_Current(float value) {
        DecimalFormat df;

        if (value < 1)
            df = new DecimalFormat("0");
        else
            df = new DecimalFormat("###");

        String text = df.format(value);
        mIGaugeUI.set7SegmentDisplayValue_Current(text);

    }

    private void updateGauge() {

        if (nLOOP < maxLOOP)
            nLOOP ++;
        else{
            nLOOP = 0;
            Log.i("---LOOP", "entrou");
            batteryRequest();
        }

        switch (mGaugeMode) {

            case GAUGE_MODE_ADDOFFSET:
                addFiveGPSSpeed(mLocation);
                break;

            case GAUGE_MODE_GPS:
                updateSpeedGPS(mLocation);
                break;

            case GAUGE_MODE_CURRENT_TEST:
                updateSpeedGPSandCurrent(mLocation);
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

                break;

        }
    }

    private void updateGaugeBattery(float value){
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


    private void addFiveGPSSpeed(CLocation location){
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
            OFFSET = nCurrentSpeed;
        else
            OFFSET = 5f;

        setGaugePointerValue(Float.parseFloat(strCurrentSpeed)+OFFSET);
        updateDisplayValue(Float.parseFloat(strCurrentSpeed)+OFFSET);
    }

    private void updateSpeedGPS(CLocation location){
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
    }

    private void updateSpeedGPSandCurrent(CLocation location){

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

    }


    //Methods do IBaseGPSLinseter

    @Override
    public void onLocationChanged(Location location) {
        if(location != null)
        {
            //this.getSpeedGPS(myLocation);
            mLocation = new CLocation(location, true);
        }

    }

   /* private boolean useMetricUnits() {
        // TODO Auto-generated method stub
       // CheckBox chkUseMetricUnits = (CheckBox) this.findViewById(R.id.chkMetricUnits);
        return true;
    }*/

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

    private void batteryRequest() {
        String TAG = "------------- batteryRequest()";

        ConnectivityManager connectionManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        wifiCheck = connectionManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        if (!wifiCheck.isConnected()) {
            Log.i(TAG, "Não conectado");
            updateGaugeBattery(80);

        } else {
            Log.i(TAG, "Conectado");
            String result = "";
            String SOC;
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

            try {
                assert is != null;
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "iso-8859-1"), 8);
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                is.close();
                result = sb.toString();

            } catch (Exception e) {
                Log.e("log_tag", "Error converting result " + e.toString());
                updateGaugeBattery(80);
            }

            try {
                JSONArray jArray = new JSONArray(result);
                for (int i = 0; i < jArray.length(); i++) {
                    JSONObject json_data = jArray.getJSONObject(i);
                    SOC = (json_data.getString("soh"));
                    updateGaugeBattery(Float.parseFloat(SOC));
                }
            } catch (JSONException e) {
                Log.e("log_tag", "Error parsing data " + e.toString());
            }
        }
    }
}
