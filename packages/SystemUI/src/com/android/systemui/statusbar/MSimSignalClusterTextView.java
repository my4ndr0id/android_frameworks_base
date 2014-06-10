/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.systemui.statusbar;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.MSimTelephonyManager;
import android.telephony.TelephonyManager;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.CharacterStyle;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.telephony.MSimConstants;
import com.android.systemui.statusbar.SignalClusterView.SettingsObserver;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.R;

// Intimately tied to the design of res/layout/msim_signal_cluster_text_view.xml
public class MSimSignalClusterTextView
    extends LinearLayout {

    private static final int SIGNAL_CLUSTER_STYLE_NORMAL   = 0;
    private static final int SIGNAL_CLUSTER_STYLE_TEXT     = 1;
    private static final int SIGNAL_CLUSTER_STYLE_HIDDEN   = 2;

    private boolean mAttached;
    private boolean mAirplaneMode;
    private int mSignalClusterStyle;
    private int[] mPhoneStates;

    private SignalStrength[] signalStrengths;

    ViewGroup mMobileGroups[];
    TextView mMobileSignalTexts[];
    private int[] mMobileGroupResourceIds = {R.id.mobile_signal_text_combo,
                                            R.id.mobile_signal_text_combo_sub2};
    private int[] mMobileSignalTextResourceIds = {R.id.mobile_signal_text,
                                                 R.id.mobile_signal_text_sub2};
    private int mNumPhones = MSimTelephonyManager.getDefault().getPhoneCount();
    private PhoneStateListener[] mPhoneStateListeners;

    Handler mHandler;

    int[] dBm;

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_SIGNAL_TEXT), false, this);
        }

        @Override public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    public MSimSignalClusterTextView(Context context) {
        this(context, null);
    }

    public MSimSignalClusterTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MSimSignalClusterTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        dBm = new int[mNumPhones];
        mPhoneStates = new int[mNumPhones];
        signalStrengths = new SignalStrength[mNumPhones];
        mMobileGroups = new ViewGroup[mNumPhones];
        mMobileSignalTexts = new TextView[mNumPhones];
        mPhoneStateListeners = new PhoneStateListener[mNumPhones];
        for(int i=0; i < mNumPhones; i++) {
            dBm[i] = 0;
            mPhoneStateListeners[i] = new PhoneStateListener(i) {
                @Override
                public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                    if (signalStrength != null) {
                        dBm[mSubscription] = signalStrength.getDbm();
                    } else {
                        dBm[mSubscription] = 0;
                    }
        
                    // update text if it's visible
                    if (mAttached) {
                        updateSettings();
                    }
                }
            
                public void onServiceStateChanged(ServiceState serviceState) {
                    mAirplaneMode = serviceState.getState() == ServiceState.STATE_POWER_OFF;
                    updateSettings();
                }
            };
        }

        mHandler = new Handler();

        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        for(int i=0; i < mNumPhones; i++) {
            mMobileGroups[i]      = (ViewGroup) findViewById(mMobileGroupResourceIds[i]);
            mMobileSignalTexts[i] = (TextView) findViewById(mMobileSignalTextResourceIds[i]);
        }

        if (!mAttached) {
            mAttached = true;
            for(int i=0; i < mNumPhones; i++) {
                ((MSimTelephonyManager) getContext().getSystemService(
                        Context.MSIM_TELEPHONY_SERVICE)).listen(
                    mPhoneStateListeners[i], PhoneStateListener.LISTEN_SERVICE_STATE
                    | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
            }

            updateSettings();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mAttached) {
            mAttached = false;
        }
        super.onDetachedFromWindow();
    }

    private String getSignalLevelString(int dBm) {
        if (dBm == 0) {
            return "-\u221e"; // -oo ('minus infinity')
        }
        return Integer.toString(dBm);
    }

    final void updateSignalText() {

        for(int i=0; i < mNumPhones; i++) {
            if (mAirplaneMode || dBm[i] == 0) {
                mMobileGroups[i].setVisibility(View.GONE);
                continue;
            } else if (mSignalClusterStyle == SIGNAL_CLUSTER_STYLE_TEXT) {
                mMobileGroups[i].setVisibility(View.VISIBLE);
                mMobileSignalTexts[i].setText(getSignalLevelString(dBm[i]));
            } else {
                mMobileGroups[i].setVisibility(View.GONE);
            }
        }
    }

    private void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();
        mSignalClusterStyle = (Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_SIGNAL_TEXT, SIGNAL_CLUSTER_STYLE_NORMAL));
        updateSignalText();
    }
}
