/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.google.gson.Gson;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    String TAG = MyWatchFace.class.getSimpleName();
    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;


    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }


    private static class EngineHandler extends Handler {

        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
        private static final long TIMEOUT_MS = 5000;
        private GoogleApiClient googleApiClient;
        //the DataApi.DataListener - this will get notified every time we change something in the data layer
        private final DataApi.DataListener onDataChangedListener = new DataApi.DataListener() {
            @Override
            public void onDataChanged(DataEventBuffer dataEvents) {
                Log.v(TAG, "onDataChanged");

                for (DataEvent event : dataEvents) {

                    Log.d(" - onDataChanged",
                            "Event received: " + event.getDataItem().getUri());

                    String eventUri = event.getDataItem().getUri().toString();

                    if (eventUri.contains("/myapp/myevent")) {

                        DataMapItem dataItem = DataMapItem.fromDataItem(event.getDataItem());
                        String[] data = dataItem.getDataMap().getStringArray("contents");

                        Log.d("- onDataChanged", "Sending timeline to the listener");


                    }
                }
                for (DataEvent event : dataEvents) {
                    if (event.getType() == DataEvent.TYPE_CHANGED) {
                        DataItem item = event.getDataItem();
                        processConfigurationFor(item);
                    }
                }

                dataEvents.release();
            }
        };
        //a ResultCallback - this will notify us every time the googleApiClient connects
        private final ResultCallback<DataItemBuffer> onConnectedResultCallback = new ResultCallback<DataItemBuffer>() {
            @Override
            public void onResult(DataItemBuffer dataItems) {
                for (DataItem item : dataItems) {
                    processConfigurationFor(item);
                }

                dataItems.release();
                invalidate();
            }
        };

        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTextPaint;
        Paint mDatePaint;
        Paint mHourPaint;
        Paint mMinutePaint;
        Paint mColonPaint;
        Paint mMajorDegreePaint;
        Paint mMinorDegreePaint;
        Paint mSeparator;
        float separatorWidth;
        float timeTextSize;
        float dateTextSize;
        float tempTextSize;
        private final Typeface BOLD_TYPEFACE =
                Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
        private final Typeface NORMAL_TYPEFACE =
                Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
        boolean mAmbient;
        Calendar mCalendar;
        double mDataTempMajor;
        double mDataTempMinor;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        float mXOffset;
        float mYOffset;
        float mDateTopMargin;
        float mSeparatorTopMargin;
        float mSeparatorBottomMargin;
        float mTempBaseToTop;

        float mTempImageWidth;
        float mTempImageHeight;

        float mTempImageMarginTop;
        float mTempImageMarginRight;
        float mTempMinorMarginLeft;
        private Drawable weatherIcon;

        Rect tempBounds = new Rect(0, 0, 0, 0);
        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            googleApiClient = new GoogleApiClient.Builder(MyWatchFace.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = MyWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            mDateTopMargin = resources.getDimension(R.dimen.date_top_margin);

            mSeparatorTopMargin = resources.getDimension(R.dimen.line_separator_top_margin);
            mSeparatorBottomMargin = resources.getDimension(R.dimen.line_separator_bottom_margin);
            mTempBaseToTop = resources.getDimension(R.dimen.temp_base_to_top);

            mTempImageHeight = resources.getDimension(R.dimen.temp_image_height);
            mTempImageWidth = resources.getDimension(R.dimen.temp_image_width);

            mTempImageMarginTop = resources.getDimension(R.dimen.temp_image_margin_top);
            mTempImageMarginRight = resources.getDimension(R.dimen.temp_image_margin_right);
            mTempMinorMarginLeft = resources.getDimension(R.dimen.temp_image_margin_right);
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(ContextCompat.getColor(MyWatchFace.this, R.color.background));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(ContextCompat.getColor(MyWatchFace.this, R.color.digital_text));


            mDatePaint = createTextPaint(ContextCompat.getColor(MyWatchFace.this, R.color.date_color));
            mHourPaint = createTextPaint(ContextCompat.getColor(MyWatchFace.this, R.color.hours_color), BOLD_TYPEFACE);
            mMinutePaint = createTextPaint(ContextCompat.getColor(MyWatchFace.this, R.color.minutes_color), NORMAL_TYPEFACE);
            mColonPaint = mHourPaint;
            mMajorDegreePaint = createTextPaint(ContextCompat.getColor(MyWatchFace.this, R.color.major_temp_color), BOLD_TYPEFACE);
            mMinorDegreePaint = createTextPaint(ContextCompat.getColor(MyWatchFace.this, R.color.minor_temp_color), NORMAL_TYPEFACE);
            mSeparator = createTextPaint(ContextCompat.getColor(MyWatchFace.this, R.color.separator_color));

            separatorWidth = resources.getDimension(R.dimen.line_separator_width);

            mCalendar = Calendar.getInstance();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            releaseGoogleApiClient();
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        private Paint createTextPaint(int defaultInteractiveColor, Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(defaultInteractiveColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            if (visible) {

                googleApiClient.connect();
            } else {

                releaseGoogleApiClient();
            }


            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void releaseGoogleApiClient() {
            if (googleApiClient != null && googleApiClient.isConnected()) {
                Wearable.DataApi.removeListener(googleApiClient, onDataChangedListener);
                googleApiClient.disconnect();
            }
        }


        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            timeTextSize = resources.getDimension(R.dimen.digital_text_size);
            dateTextSize = resources.getDimension(R.dimen.date_text_size);
            tempTextSize = resources.getDimension(R.dimen.temperature_text_size);

            mTextPaint.setTextSize(timeTextSize);
            mDatePaint.setTextSize(dateTextSize);
            mHourPaint.setTextSize(timeTextSize);
            mMinutePaint.setTextSize(timeTextSize);
            mColonPaint.setTextSize(timeTextSize);
            mMajorDegreePaint.setTextSize(tempTextSize);
            mMinorDegreePaint.setTextSize(tempTextSize);


        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        // onTimeTick called  every minute  to update wtach face in ambient mode
        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
                            .show();
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            float boundWidth = bounds.width();
            float boundheight = bounds.width();

            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }


            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            // drawing the clock
            String hours = String.format("%d", mCalendar.get(Calendar.HOUR));
            String colon = String.format(":");
            String minutes = String.format("%02d", mCalendar.get(Calendar.MINUTE));

            float x = boundWidth / 2 - (mHourPaint.measureText(hours) + mHourPaint.measureText(colon) + mMinutePaint.measureText(minutes)) / 2;
            Log.e(TAG, "Height" + boundheight);
            Log.e(TAG, "mYOffset" + mYOffset);
            float y = mYOffset;
            canvas.drawText(hours, x, y, mHourPaint);
            x += mHourPaint.measureText(hours);
            canvas.drawText(colon, x, y, mColonPaint);
            x += mHourPaint.measureText(colon);
            canvas.drawText(minutes, x, y, mMinutePaint);
            x += mMinutePaint.measureText(minutes);


            //drawing date

            String date = new SimpleDateFormat("EEE, MMM d yyyy").format(mCalendar.getTime());
            x = boundWidth / 2 - mDatePaint.measureText(date) / 2;
            y = mYOffset + mDateTopMargin;
            canvas.drawText(date, x, y, mDatePaint);

            //drawing separator
            x = boundWidth / 2 - separatorWidth / 2;
            y = mYOffset + mDateTopMargin + mSeparatorTopMargin;
            canvas.drawLine(x, y, x + separatorWidth, y, mSeparator);

            //drawing image
            String majorTemp = String.format(getString(R.string.format_temperature), (int) mDataTempMajor);
            String minorTemp = String.format(getString(R.string.format_temperature), (int) mDataTempMinor);
            x = boundWidth / 2 - (mTempImageWidth + mTempImageMarginRight + mMajorDegreePaint.measureText(majorTemp) + mTempMinorMarginLeft + mMinorDegreePaint.measureText(minorTemp)) / 2;
            y = mYOffset + mDateTopMargin + mSeparatorTopMargin + mSeparatorBottomMargin + mTempBaseToTop - mTempImageHeight + mTempImageMarginTop;
//            Drawable d = getResources().getDrawable(R.mipmap.ic_launcher, null);
//            d.setBounds((int) x, (int) y, (int) x + (int) mTempImageWidth, (int) y + (int) mTempImageHeight);
//            d.draw(canvas);

            if (weatherIcon != null) {
                weatherIcon.setBounds((int) x, (int) y, (int) x + (int) mTempImageWidth, (int) y + (int) mTempImageHeight);
                weatherIcon.draw(canvas);
            }
            //drawing Temp major
            x = x + mTempImageWidth + mTempImageMarginRight;
            y = mYOffset + mDateTopMargin + mSeparatorTopMargin + mSeparatorBottomMargin + mTempBaseToTop;
            canvas.drawText(majorTemp, x, y, mMajorDegreePaint);

            //drawing Temp minor
            x += mTempMinorMarginLeft + mMajorDegreePaint.measureText(majorTemp);
            canvas.drawText(minorTemp, x, y, mMinorDegreePaint);

        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        private void processConfigurationFor(DataItem item) {

            Log.v(TAG, "processConfigurationFor" + new Gson().toJson(item.getUri()));
            if ("/ubiquitous_watch_face_config".equals(item.getUri().getPath())) {
                DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                if (dataMap.containsKey("KEY_MAJOR_TEMP")) {
                    mDataTempMajor = dataMap.getDouble("KEY_MAJOR_TEMP");
                }

                if (dataMap.containsKey("KEY_MINOR_TEMP")) {
                    mDataTempMinor = dataMap.getDouble("KEY_MINOR_TEMP");
                }
                if (dataMap.containsKey("KEY_WEATHER_IMAGE")) {
                    Log.e(TAG, "dataMap contain image");
                    Asset asset = dataMap.getAsset("KEY_WEATHER_IMAGE");
                    loadBitmapFromAsset(asset);

                } else {
                    Log.e(TAG, "waether image is  null");
                }
                invalidate();

            }
        }

        @Override
        public void onConnected(Bundle bundle) {
            Log.d(TAG, "connected GoogleAPI");
            Wearable.DataApi.addListener(googleApiClient, onDataChangedListener);
            Wearable.DataApi.getDataItems(googleApiClient).setResultCallback(onConnectedResultCallback);
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.e(TAG, "suspended GoogleAPI");
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            Log.e(TAG, "connectionFailed GoogleAPI");
        }

        public void loadBitmapFromAsset(Asset asset) {
            new BitmapWorkerTask().execute(asset);
        }

        class BitmapWorkerTask extends AsyncTask<Asset, Void, Bitmap> {
            Asset asset;

            @Override
            protected Bitmap doInBackground(Asset... params) {
                asset = params[0];
                if (asset == null) {
                    throw new IllegalArgumentException("Asset must be non-null");
                }
                ConnectionResult result = googleApiClient.blockingConnect(TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (!result.isSuccess()) {
                    Log.e(TAG, "loadBitmapFromAsset not sucess");
                    return null;
                }
                // convert asset into a file descriptor and block until it's ready
                InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                        googleApiClient, asset).await().getInputStream();
                googleApiClient.disconnect();

                if (assetInputStream == null) {
                    Log.w(TAG, "Requested an unknown Asset.");
                    return null;
                }
                // decode the stream into a bitmap
                return BitmapFactory.decodeStream(assetInputStream);
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                super.onPostExecute(bitmap);
                weatherIcon = new BitmapDrawable(getResources(), bitmap);
                invalidate();
            }
        }
    }


}
