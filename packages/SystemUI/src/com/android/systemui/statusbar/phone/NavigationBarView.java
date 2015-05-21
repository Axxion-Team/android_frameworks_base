/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.systemui.statusbar.phone;


import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.LayoutTransition;
import android.animation.LayoutTransition.TransitionListener;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.ActivityManagerNative;
import android.app.StatusBarManager;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;

import com.android.internal.util.slim.ActionConfig;
import com.android.internal.util.slim.ActionConstants;
import com.android.internal.util.slim.ActionHelper;
import com.android.internal.util.slim.ColorHelper;
import com.android.internal.util.slim.DeviceUtils;
import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.DelegateViewHelper;
import com.android.systemui.statusbar.policy.KeyButtonView;
import com.android.systemui.statusbar.policy.DeadZone;
import com.android.systemui.statusbar.policy.KeyButtonView;

import java.net.URISyntaxException;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;

import java.util.ArrayList;
import java.util.List;

public class NavigationBarView extends LinearLayout {
    private static final boolean DEBUG = false;
    private final static String TAG = "PhoneStatusBar/NavigationBarView";

    // slippery nav bar when everything is disabled, e.g. during setup
    final static boolean SLIPPERY_WHEN_DISABLED = true;

    // Definitions for navbar menu button customization
    private final static int SHOW_RIGHT_MENU = 0;
    private final static int SHOW_LEFT_MENU = 1;
    private final static int SHOW_BOTH_MENU = 2;

    private final static int MENU_VISIBILITY_ALWAYS = 0;
    private final static int MENU_VISIBILITY_NEVER = 1;
    private final static int MENU_VISIBILITY_SYSTEM = 2;

    private static final int KEY_MENU_RIGHT = 0;
    private static final int KEY_MENU_LEFT = 1;
    private static final int KEY_IME_SWITCHER = 2;

	// Legacy menu tweaks by @siracuervo
	private boolean mLegacyMenuLayout;
	private String mCustomLeftShortcutUri;
	private String mCustomLeftLongShortcutUri;
	private String mCustomRightShortcutUri;
	private String mCustomRightLongShortcutUri;
	private	int mLegacyMenuLeftAction;   
	private	int mLegacyMenuLeftLongAction;   
	private	String leftaction;
	private	int leftdrawable;
	private	int mLegacyMenuRightAction;   
	private	int mLegacyMenuRightLongAction;        
	private	String rightaction;
	private	int rightdrawable;
    // End of legacy menu tweaks
    private int mMenuVisibility;
    private int mMenuSetting;
    private boolean mOverrideMenuKeys;
    private boolean mIsImeButtonVisible = false;

    final Display mDisplay;
    View mCurrentView = null;
    View[] mRotatedViews = new View[4];

    int mBarSize;
    boolean mVertical;
    boolean mScreenOn;
    boolean mLeftInLandscape;

    boolean mShowMenu;
    int mDisabledFlags = 0;
    int mNavigationIconHints = 0;

    private BackButtonDrawable mBackIcon, mBackLandIcon, mBackAltIcon;

    private Drawable mRecentIcon;
    private Drawable mRecentLandIcon;

    private int mRippleColor;

    private NavigationBarViewTaskSwitchHelper mTaskSwitchHelper;
    protected DelegateViewHelper mDelegateHelper;

    private DeadZone mDeadZone;
    private final NavigationBarTransitions mBarTransitions;

    private int mNavBarButtonColor;
    private int mNavBarButtonColorMode;
    private boolean mAppIsBinded = false;

    private FrameLayout mRot0;
    private FrameLayout mRot90;

    private ArrayList<ActionConfig> mButtonsConfig;
    private List<Integer> mButtonIdList;

    private KeyButtonView.LongClickCallback mCallback;

    // workaround for LayoutTransitions leaving the nav buttons in a weird state (bug 5549288)
    final static boolean WORKAROUND_INVALID_LAYOUT = true;
    final static int MSG_CHECK_INVALID_LAYOUT = 8686;

    // performs manual animation in sync with layout transitions
    private final NavTransitionListener mTransitionListener = new NavTransitionListener();

    private OnVerticalChangedListener mOnVerticalChangedListener;
    private boolean mIsLayoutRtl;
    private boolean mDelegateIntercepted;

    private class NavTransitionListener implements TransitionListener {
        private boolean mBackTransitioning;
        private boolean mHomeAppearing;
        private long mStartDelay;
        private long mDuration;
        private TimeInterpolator mInterpolator;

        @Override
        public void startTransition(LayoutTransition transition, ViewGroup container,
                View view, int transitionType) {
            if (view.getId() == R.id.back) {
                mBackTransitioning = true;
            } else if (view.getId() == R.id.home && transitionType == LayoutTransition.APPEARING) {
                mHomeAppearing = true;
                mStartDelay = transition.getStartDelay(transitionType);
                mDuration = transition.getDuration(transitionType);
                mInterpolator = transition.getInterpolator(transitionType);
            }
        }

        @Override
        public void endTransition(LayoutTransition transition, ViewGroup container,
                View view, int transitionType) {
            if (view.getId() == R.id.back) {
                mBackTransitioning = false;
            } else if (view.getId() == R.id.home && transitionType == LayoutTransition.APPEARING) {
                mHomeAppearing = false;
            }
        }

        public void onBackAltCleared() {
            // When dismissing ime during unlock, force the back button to run the same appearance
            // animation as home (if we catch this condition early enough).
            if (getBackButton() == null && getHomeButton() == null) return;
            if (!mBackTransitioning && getBackButton().getVisibility() == VISIBLE
                    && mHomeAppearing && getHomeButton().getAlpha() == 0) {
                getBackButton().setAlpha(0);
                ValueAnimator a = ObjectAnimator.ofFloat(getBackButton(), "alpha", 0, 1);
                a.setStartDelay(mStartDelay);
                a.setDuration(mDuration);
                a.setInterpolator(mInterpolator);
                a.start();
            }
        }
    }

    private final OnClickListener mImeSwitcherClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            ((InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE))
                    .showInputMethodPicker();
        }
    };

    private class H extends Handler {
        public void handleMessage(Message m) {
            switch (m.what) {
                case MSG_CHECK_INVALID_LAYOUT:
                    final String how = "" + m.obj;
                    final int w = getWidth();
                    final int h = getHeight();
                    final int vw = mCurrentView.getWidth();
                    final int vh = mCurrentView.getHeight();

                    if (h != vh || w != vw) {
                        Log.w(TAG, String.format(
                            "*** Invalid layout in navigation bar (%s this=%dx%d cur=%dx%d)",
                            how, w, h, vw, vh));
                        if (WORKAROUND_INVALID_LAYOUT) {
                            requestLayout();
                        }
                    }
                    break;
            }
        }
    }

    public NavigationBarView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mDisplay = ((WindowManager)context.getSystemService(
                Context.WINDOW_SERVICE)).getDefaultDisplay();

        final Resources res = getContext().getResources();
        mBarSize = res.getDimensionPixelSize(R.dimen.navigation_bar_size);
        mVertical = false;
        mShowMenu = false;
        mDelegateHelper = new DelegateViewHelper(this);
        mTaskSwitchHelper = new NavigationBarViewTaskSwitchHelper(context);
        getIcons(res);

        mBarTransitions = new NavigationBarTransitions(this);

        mButtonsConfig = ActionHelper.getNavBarConfig(mContext);
        mButtonIdList = new ArrayList<Integer>();
    }

    public BarTransitions getBarTransitions() {
        return mBarTransitions;
    }

    public void setDelegateView(View view) {
        mDelegateHelper.setDelegateView(view);
    }

    public void setBar(BaseStatusBar phoneStatusBar) {
        mTaskSwitchHelper.setBar(phoneStatusBar);
        mDelegateHelper.setBar(phoneStatusBar);
    }

    public void setOnVerticalChangedListener(OnVerticalChangedListener onVerticalChangedListener) {
        mOnVerticalChangedListener = onVerticalChangedListener;
        notifyVerticalChangedListener(mVertical);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        initDownStates(event);
        if (!mDelegateIntercepted && mTaskSwitchHelper.onTouchEvent(event)) {
            return true;
        }
        if (mDeadZone != null && event.getAction() == MotionEvent.ACTION_OUTSIDE) {
            mDeadZone.poke(event);
        }
        if (mDelegateHelper != null && mDelegateIntercepted) {
            boolean ret = mDelegateHelper.onInterceptTouchEvent(event);
            if (ret) return true;
        }
        return super.onTouchEvent(event);
    }

    private void initDownStates(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mDelegateIntercepted = false;
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        initDownStates(event);
        boolean intercept = mTaskSwitchHelper.onInterceptTouchEvent(event);
        if (!intercept) {
            mDelegateIntercepted = mDelegateHelper.onInterceptTouchEvent(event);
            intercept = mDelegateIntercepted;
        } else {
            MotionEvent cancelEvent = MotionEvent.obtain(event);
            cancelEvent.setAction(MotionEvent.ACTION_CANCEL);
            mDelegateHelper.onInterceptTouchEvent(cancelEvent);
            cancelEvent.recycle();
        }
        return intercept;
    }

    private H mHandler = new H();

    public List<Integer> getButtonIdList() {
        return mButtonIdList;
    }

    public View getCurrentView() {
        return mCurrentView;
    }

    public View getRecentsButton() {
        return mCurrentView.findViewById(R.id.recent_apps);
    }

    public View getLeftMenuButton() {
        return mCurrentView.findViewById(R.id.menu_left);
    }

    public View getRightMenuButton() {
        return mCurrentView.findViewById(R.id.menu);
    }

    public View getCustomButton(int buttonId) {
        return mCurrentView.findViewById(buttonId);
    }

    public View getBackButton() {
        return mCurrentView.findViewById(R.id.back);
    }

    public View getHomeButton() {
        return mCurrentView.findViewById(R.id.home);
    }

    public View getImeSwitchButton() {
        return mCurrentView.findViewById(R.id.ime_switcher);
    }

    public void setOverrideMenuKeys(boolean b) {
        mOverrideMenuKeys = b;
        setMenuVisibility(mShowMenu, true /* force */);
    }

    private void getIcons(Resources res) {
        mBackIcon = new BackButtonDrawable(res.getDrawable(R.drawable.ic_sysbar_back));
        mBackLandIcon = new BackButtonDrawable(res.getDrawable(R.drawable.ic_sysbar_back_land));
        mRecentIcon = res.getDrawable(R.drawable.ic_sysbar_recent);
        mRecentLandIcon = res.getDrawable(R.drawable.ic_sysbar_recent_land);
    }

    @Override
    public void setLayoutDirection(int layoutDirection) {
        getIcons(getContext().getResources());
        updateSettings();

        super.setLayoutDirection(layoutDirection);
    }

    public void notifyScreenOn(boolean screenOn) {
        mScreenOn = screenOn;
        setDisabledFlags(mDisabledFlags, true);
    }

    public void setPinningCallback(KeyButtonView.LongClickCallback c) {
        mCallback = c;
    }

    private void makeBar() {
        if (mButtonsConfig.isEmpty() || mButtonsConfig == null) {
            return;
        }

        mButtonIdList.clear();

        mRippleColor = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.NAVIGATION_BAR_GLOW_TINT, -2, UserHandle.USER_CURRENT);

        ((LinearLayout) mRot0.findViewById(R.id.nav_buttons)).removeAllViews();
        ((LinearLayout) mRot0.findViewById(R.id.lights_out)).removeAllViews();
        ((LinearLayout) mRot90.findViewById(R.id.nav_buttons)).removeAllViews();
        ((LinearLayout) mRot90.findViewById(R.id.lights_out)).removeAllViews();

        for (int i = 0; i <= 1; i++) {
            boolean landscape = (i == 1);

            LinearLayout navButtonLayout = (LinearLayout) (landscape ? mRot90
                    .findViewById(R.id.nav_buttons) : mRot0
                    .findViewById(R.id.nav_buttons));

            LinearLayout lightsOut = (LinearLayout) (landscape ? mRot90
                    .findViewById(R.id.lights_out) : mRot0
                    .findViewById(R.id.lights_out));

            // add left menu
            KeyButtonView leftMenuKeyView = generateMenuKey(landscape, KEY_MENU_LEFT);
            leftMenuKeyView.setLongClickCallback(mCallback);
            addButton(navButtonLayout, leftMenuKeyView, landscape);
            addLightsOutButton(lightsOut, leftMenuKeyView, landscape, true);

            mAppIsBinded = false;
            ActionConfig actionConfig;

            for (int j = 0; j < mButtonsConfig.size(); j++) {
                actionConfig = mButtonsConfig.get(j);
                KeyButtonView v = generateKey(landscape,
                        actionConfig.getClickAction(),
                        actionConfig.getLongpressAction(),
                        actionConfig.getIcon());
                v.setTag((landscape ? "key_land_" : "key_") + j);

                addButton(navButtonLayout, v, landscape);
                addLightsOutButton(lightsOut, v, landscape, false);

                if (mButtonsConfig.size() == 3
                        && j != (mButtonsConfig.size() - 1)) {
                    // add separator view here
                    View separator = new View(mContext);
                    separator.setLayoutParams(getSeparatorLayoutParams(landscape));
                    addButton(navButtonLayout, separator, landscape);
                    addLightsOutButton(lightsOut, separator, landscape, true);
                }

            }

            KeyButtonView rightMenuKeyView = generateMenuKey(landscape, KEY_MENU_RIGHT);
            rightMenuKeyView.setLongClickCallback(mCallback);
            addButton(navButtonLayout, rightMenuKeyView, landscape);
            addLightsOutButton(lightsOut, rightMenuKeyView, landscape, true);

            View imeSwitcher = generateMenuKey(landscape, KEY_IME_SWITCHER);
            addButton(navButtonLayout, imeSwitcher, landscape);
            addLightsOutButton(lightsOut, imeSwitcher, landscape, true);
        }
        setMenuVisibility(mShowMenu, true);
    }

    public void recreateNavigationBar() {
        updateSettings();
    }

    private KeyButtonView generateKey(boolean landscape, String clickAction,
            String longpress,
            String iconUri) {

        KeyButtonView v = new KeyButtonView(mContext, null);
        v.setClickAction(clickAction);
        v.setLongpressAction(longpress);
        int i = mContext.getResources().getDimensionPixelSize(R.dimen.navigation_key_width);
        v.setLayoutParams(getLayoutParams(landscape, i));
        v.setScaleType(KeyButtonView.ScaleType.CENTER_INSIDE);

        if (clickAction.equals(ActionConstants.ACTION_BACK)) {
            v.setId(R.id.back);
        } else if (clickAction.equals(ActionConstants.ACTION_HOME)) {
            v.setId(R.id.home);
        } else if (clickAction.equals(ActionConstants.ACTION_RECENTS)) {
            v.setId(R.id.recent_apps);
        } else {
            int buttonId = v.generateViewId();
            v.setId(buttonId);
            mButtonIdList.add(buttonId);
        }


        boolean colorize = true;
        if (iconUri != null && !iconUri.equals(ActionConstants.ICON_EMPTY)
                && !iconUri.startsWith(ActionConstants.SYSTEM_ICON_IDENTIFIER)
                && mNavBarButtonColorMode == 1) {
            colorize = false;
        } else if (!clickAction.startsWith("**")) {
            final int[] appIconPadding = getAppIconPadding();
            if (landscape) {
                v.setPaddingRelative(appIconPadding[1], appIconPadding[0],
                        appIconPadding[3], appIconPadding[2]);
            } else {
                v.setPaddingRelative(appIconPadding[0], appIconPadding[1],
                        appIconPadding[2], appIconPadding[3]);
            }
            if (mNavBarButtonColorMode != 0) {
                colorize = false;
            }
            mAppIsBinded = true;
        }

        Drawable d = ActionHelper.getActionIconImage(mContext, clickAction, iconUri);
        if (d != null) {
            if (colorize && mNavBarButtonColorMode != 3) {
                v.setImageBitmap(ColorHelper.getColoredBitmap(d, mNavBarButtonColor));
            } else {
                v.setImageDrawable(d);
            }
        }
        v.setRippleColor(mRippleColor);
        return v;
    }


	public void getLegacyLeftMenuAction (Context context) {
		mCustomLeftShortcutUri = Settings.System.getString(context.getContentResolver(),
				Settings.System.LEGACY_MENU_LEFT_SHORTCUT_URI);
        mLegacyMenuLeftAction = Settings.System.getInt(context.getContentResolver(),
                Settings.System.LEGACY_MENU_LEFT_ACTION, 0);

		if (mLegacyMenuLeftAction == 0) {
				leftaction = ActionConstants.ACTION_MENU;
				leftdrawable = R.drawable.ic_sysbar_menu;
		} else if (mLegacyMenuLeftAction == 1) {
				leftaction = ActionConstants.ACTION_HOME;
				leftdrawable = R.drawable.ic_sysbar_home;
		} else if (mLegacyMenuLeftAction == 2) {
				leftaction = ActionConstants.ACTION_BACK;
				leftdrawable = R.drawable.ic_sysbar_back;
		} else if (mLegacyMenuLeftAction == 3) {
				leftaction = ActionConstants.ACTION_RECENTS;			
				leftdrawable = R.drawable.ic_sysbar_recent;
		} else if (mLegacyMenuLeftAction == 4) {
				leftaction = ActionConstants.ACTION_VOICE_SEARCH;			
				leftdrawable = R.drawable.ic_sysbar_search;					
		} else if (mLegacyMenuLeftAction == 5) {
				leftaction = ActionConstants.ACTION_SEARCH;
				leftdrawable = R.drawable.ic_sysbar_search;
		} else if (mLegacyMenuLeftAction == 6) {
				leftaction = ActionConstants.ACTION_POWER;
				leftdrawable = R.drawable.ic_sysbar_sleep;		
		} else if (mLegacyMenuLeftAction == 7) {
				leftaction = ActionConstants.ACTION_NOTIFICATIONS;
				leftdrawable = R.drawable.ic_sysbar_notifications;
		} else if (mLegacyMenuLeftAction == 8) {
				leftaction = ActionConstants.ACTION_SETTINGS_PANEL;
				leftdrawable = R.drawable.ic_sysbar_qs; 
		} else if (mLegacyMenuLeftAction == 9) {
				leftaction = ActionConstants.ACTION_SCREENSHOT;
				leftdrawable = R.drawable.ic_sysbar_screenshot; 
		} else if (mLegacyMenuLeftAction == 10) {
				leftaction = ActionConstants.ACTION_SCREENRECORD;
				leftdrawable = R.drawable.ic_sysbar_screenrecord;
		} else if (mLegacyMenuLeftAction == 11) {
				leftaction = ActionConstants.ACTION_IME;
				leftdrawable = R.drawable.ic_ime_switcher_default; 
		} else if (mLegacyMenuLeftAction == 12) {
				leftaction = ActionConstants.ACTION_LAST_APP;
				leftdrawable = R.drawable.ic_sysbar_lastapp; 										
		} else if (mLegacyMenuLeftAction == 13) {
				leftaction = ActionConstants.ACTION_KILL;
				leftdrawable = R.drawable.ic_sysbar_killtask;
		} else if (mLegacyMenuLeftAction == 14) {
				leftaction = ActionConstants.ACTION_ASSIST;
				leftdrawable = R.drawable.ic_sysbar_search; 
		} else if (mLegacyMenuLeftAction == 15) {
				leftaction = ActionConstants.ACTION_VIB;
				leftdrawable = R.drawable.ic_sysbar_vib; 						
		} else if (mLegacyMenuLeftAction == 16) {
				leftaction = ActionConstants.ACTION_VIB_SILENT;
				leftdrawable = R.drawable.ic_sysbar_ring_vib_silent; 
		} else if (mLegacyMenuLeftAction == 17) {
				leftaction = ActionConstants.ACTION_SILENT;
				leftdrawable = R.drawable.ic_sysbar_silent; 
		} else if (mLegacyMenuLeftAction == 18) {
				leftaction = ActionConstants.ACTION_POWER_MENU;
				leftdrawable = R.drawable.ic_sysbar_power;	
		} else if (mLegacyMenuLeftAction == 19) {
				leftaction = ActionConstants.ACTION_TORCH;
				leftdrawable = R.drawable.ic_sysbar_torch; 
		} else if (mLegacyMenuLeftAction == 20) {
		// must be a shortcut
				leftaction = mCustomLeftShortcutUri;
		}
	}	
	
	public void getLegacyRightMenuAction (Context context) {
        mLegacyMenuRightAction = Settings.System.getInt(context.getContentResolver(),
                Settings.System.LEGACY_MENU_RIGHT_ACTION, 0); 
                           
		mCustomRightShortcutUri = Settings.System.getString(context.getContentResolver(),
				Settings.System.LEGACY_MENU_RIGHT_SHORTCUT_URI);     
				                       
		if (mLegacyMenuRightAction == 0) {
				rightaction = ActionConstants.ACTION_MENU;
				rightdrawable = R.drawable.ic_sysbar_menu;
		} else if (mLegacyMenuRightAction == 1) {
				rightaction = ActionConstants.ACTION_HOME;
				rightdrawable = R.drawable.ic_sysbar_home;
		} else if (mLegacyMenuRightAction == 2) {
				rightaction = ActionConstants.ACTION_BACK;
				rightdrawable = R.drawable.ic_sysbar_back;
		} else if (mLegacyMenuRightAction == 3) {
				rightaction = ActionConstants.ACTION_RECENTS;			
				rightdrawable = R.drawable.ic_sysbar_recent;
		} else if (mLegacyMenuRightAction == 4) {
				rightaction = ActionConstants.ACTION_VOICE_SEARCH;			
				rightdrawable = R.drawable.ic_sysbar_search;					
		} else if (mLegacyMenuRightAction == 5) {
				rightaction = ActionConstants.ACTION_SEARCH;
				rightdrawable = R.drawable.ic_sysbar_search;
		} else if (mLegacyMenuRightAction == 6) {
				rightaction = ActionConstants.ACTION_POWER;
				rightdrawable = R.drawable.ic_sysbar_sleep;		
		} else if (mLegacyMenuRightAction == 7) {
				rightaction = ActionConstants.ACTION_NOTIFICATIONS;
				rightdrawable = R.drawable.ic_sysbar_notifications;
		} else if (mLegacyMenuRightAction == 8) {
				rightaction = ActionConstants.ACTION_SETTINGS_PANEL;
				rightdrawable = R.drawable.ic_sysbar_qs; 
		} else if (mLegacyMenuRightAction == 9) {
				rightaction = ActionConstants.ACTION_SCREENSHOT;
				rightdrawable = R.drawable.ic_sysbar_screenshot; 
		} else if (mLegacyMenuRightAction == 10) {
				rightaction = ActionConstants.ACTION_SCREENRECORD;
				rightdrawable = R.drawable.ic_sysbar_screenrecord;
		} else if (mLegacyMenuRightAction == 11) {
				rightaction = ActionConstants.ACTION_IME;
				rightdrawable = R.drawable.ic_ime_switcher_default; 
		} else if (mLegacyMenuRightAction == 12) {
				rightaction = ActionConstants.ACTION_LAST_APP;
				rightdrawable = R.drawable.ic_sysbar_lastapp; 										
		} else if (mLegacyMenuRightAction == 13) {
				rightaction = ActionConstants.ACTION_KILL;
				rightdrawable = R.drawable.ic_sysbar_killtask;
		} else if (mLegacyMenuRightAction == 14) {
				rightaction = ActionConstants.ACTION_ASSIST;
				rightdrawable = R.drawable.ic_sysbar_search; 
		} else if (mLegacyMenuRightAction == 15) {
				rightaction = ActionConstants.ACTION_VIB;
				rightdrawable = R.drawable.ic_sysbar_vib; 						
		} else if (mLegacyMenuRightAction == 16) {
				rightaction = ActionConstants.ACTION_VIB_SILENT;
				rightdrawable = R.drawable.ic_sysbar_ring_vib_silent; 
		} else if (mLegacyMenuRightAction == 17) {
				rightaction = ActionConstants.ACTION_SILENT;
				rightdrawable = R.drawable.ic_sysbar_silent; 
		} else if (mLegacyMenuRightAction == 18) {
				rightaction = ActionConstants.ACTION_POWER_MENU;
				rightdrawable = R.drawable.ic_sysbar_power;	
		} else if (mLegacyMenuRightAction == 19) {
				rightaction = ActionConstants.ACTION_TORCH;
				rightdrawable = R.drawable.ic_sysbar_torch; 
		} else if (mLegacyMenuRightAction == 20) {
		// must be a shortcut
				rightaction = mCustomRightShortcutUri;
		}
    }
    
    private KeyButtonView generateMenuKey(boolean landscape, int keyId) {
		// Action IDs
        mLegacyMenuLeftLongAction = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LEGACY_MENU_LEFT_LONG_ACTION, 24);       
        mLegacyMenuRightLongAction = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LEGACY_MENU_RIGHT_LONG_ACTION, 24);  
        mLegacyMenuLeftAction = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LEGACY_MENU_LEFT_ACTION, 0);
        mLegacyMenuRightAction = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LEGACY_MENU_RIGHT_ACTION, 0);
		// Shortcut/app uri if exist
		mCustomLeftShortcutUri = Settings.System.getString(mContext.getContentResolver(),
				Settings.System.LEGACY_MENU_LEFT_SHORTCUT_URI);
		mCustomRightShortcutUri = Settings.System.getString(mContext.getContentResolver(),
				Settings.System.LEGACY_MENU_RIGHT_SHORTCUT_URI);
		mCustomLeftLongShortcutUri = Settings.System.getString(mContext.getContentResolver(),
				Settings.System.LEGACY_MENU_LEFT_LONG_SHORTCUT_URI);
		mCustomRightLongShortcutUri = Settings.System.getString(mContext.getContentResolver(),
				Settings.System.LEGACY_MENU_RIGHT_LONG_SHORTCUT_URI);
						
        Drawable d = null;
        KeyButtonView v = new KeyButtonView(mContext, null);
        int width = mContext.getResources().getDimensionPixelSize(R.dimen.navigation_extra_key_width);
        v.setLayoutParams(getLayoutParams(landscape, width));
        v.setScaleType(KeyButtonView.ScaleType.CENTER_INSIDE);
        if (keyId == KEY_MENU_LEFT || keyId == KEY_MENU_RIGHT) {
            v.setClickAction(ActionConstants.ACTION_MENU);
            v.setLongpressAction(ActionConstants.ACTION_NULL);
            if (keyId == KEY_MENU_LEFT) {
                v.setId(R.id.menu_left);
					// Take the actions
					getLegacyLeftMenuAction(mContext);   
					// set the action             
					v.setClickAction(leftaction);
					// set the correct drawable, "leftdrawable" is defined in getLegacyLeftMenuAction method
					if (mLegacyMenuLeftAction == 20) {
						PackageManager pm = mContext.getPackageManager();					
						try {
							d =	pm.getActivityIcon(Intent.parseUri(mCustomLeftShortcutUri, 0));

						} catch (NameNotFoundException e) {
						} catch (URISyntaxException e) {
							e.printStackTrace();
						}
						final int[] appIconPadding = getAppIconPadding();
							if (landscape) {
								v.setPaddingRelative(appIconPadding[1], appIconPadding[0],
								appIconPadding[3], appIconPadding[2]);
							} else {
								v.setPaddingRelative(appIconPadding[0], appIconPadding[1],
								appIconPadding[2], appIconPadding[3]);
							}
					// Take normal drawable
					} else {
							d = mContext.getResources().getDrawable(leftdrawable); 
					}		
					// set the longpress action						
					if (mLegacyMenuLeftLongAction == 0) {
						v.setLongpressAction(ActionConstants.ACTION_MENU);
					} else if (mLegacyMenuLeftLongAction == 1) {
						v.setLongpressAction(ActionConstants.ACTION_HOME);
					} else if (mLegacyMenuLeftLongAction == 2) {
						v.setLongpressAction(ActionConstants.ACTION_BACK);
					} else if (mLegacyMenuLeftLongAction == 3) {
						v.setLongpressAction(ActionConstants.ACTION_RECENTS);			
					} else if (mLegacyMenuLeftLongAction == 4) {
						v.setLongpressAction(ActionConstants.ACTION_VOICE_SEARCH);				
					} else if (mLegacyMenuLeftLongAction == 5) {
						v.setLongpressAction(ActionConstants.ACTION_SEARCH);
					} else if (mLegacyMenuLeftLongAction == 6) {
						v.setLongpressAction(ActionConstants.ACTION_POWER);
					} else if (mLegacyMenuLeftLongAction == 7) {
						v.setLongpressAction(ActionConstants.ACTION_NOTIFICATIONS);
					} else if (mLegacyMenuLeftLongAction == 8) {
						v.setLongpressAction(ActionConstants.ACTION_SETTINGS_PANEL);
					} else if (mLegacyMenuLeftLongAction == 9) {
						v.setLongpressAction(ActionConstants.ACTION_SCREENSHOT);
					} else if (mLegacyMenuLeftLongAction == 10) {
						v.setLongpressAction(ActionConstants.ACTION_SCREENRECORD);
					} else if (mLegacyMenuLeftLongAction == 11) {
						v.setLongpressAction(ActionConstants.ACTION_IME);
					} else if (mLegacyMenuLeftLongAction == 12) {
						v.setLongpressAction(ActionConstants.ACTION_LAST_APP);
					} else if (mLegacyMenuLeftLongAction == 13) {
						v.setLongpressAction(ActionConstants.ACTION_KILL);
					} else if (mLegacyMenuLeftLongAction == 14) {
						v.setLongpressAction(ActionConstants.ACTION_ASSIST);
					} else if (mLegacyMenuLeftLongAction == 15) {
						v.setLongpressAction(ActionConstants.ACTION_VIB);
					} else if (mLegacyMenuLeftLongAction == 16) {
						v.setLongpressAction(ActionConstants.ACTION_VIB_SILENT);
					} else if (mLegacyMenuLeftLongAction == 17) {
						v.setLongpressAction(ActionConstants.ACTION_SILENT);
					} else if (mLegacyMenuLeftLongAction == 18) {
						v.setLongpressAction(ActionConstants.ACTION_POWER_MENU);
					} else if (mLegacyMenuLeftLongAction == 19) {
						v.setLongpressAction(ActionConstants.ACTION_TORCH);
					} else if (mLegacyMenuLeftLongAction == 20) {		
						v.setLongpressAction(ActionConstants.ACTION_MEDIA_PREVIOUS);
					} else if (mLegacyMenuLeftLongAction == 21) {
						v.setLongpressAction(ActionConstants.ACTION_MEDIA_NEXT);	
					} else if (mLegacyMenuLeftLongAction == 22) {			
						v.setLongpressAction(ActionConstants.ACTION_MEDIA_PLAY_PAUSE);
					} else if (mLegacyMenuLeftLongAction == 23) {			
						v.setLongpressAction(mCustomLeftLongShortcutUri);						
					} else if (mLegacyMenuLeftLongAction == 24) {													
						v.setLongpressAction(ActionConstants.ACTION_NULL);				
					}
			} else {
                v.setId(R.id.menu);
				getLegacyRightMenuAction(mContext);
				v.setClickAction(rightaction);
				if (mLegacyMenuRightAction == 20) {
					PackageManager pm = mContext.getPackageManager();					
					try {
						d =	pm.getActivityIcon(Intent.parseUri(mCustomRightShortcutUri, 0));
					} catch (NameNotFoundException e) {
					} catch (URISyntaxException e) {
						e.printStackTrace();
					}		
					final int[] appIconPadding = getAppIconPadding();
						if (landscape) {
							v.setPaddingRelative(appIconPadding[1], appIconPadding[0],
							appIconPadding[3], appIconPadding[2]);
						} else {
							v.setPaddingRelative(appIconPadding[0], appIconPadding[1],
							appIconPadding[2], appIconPadding[3]);
						}
				// Take normal drawable
				} else {
						d = mContext.getResources().getDrawable(rightdrawable);   
				}					
					if (mLegacyMenuRightLongAction == 0) {
						v.setLongpressAction(ActionConstants.ACTION_MENU);
					} else if (mLegacyMenuRightLongAction == 1) {
						v.setLongpressAction(ActionConstants.ACTION_HOME);
					} else if (mLegacyMenuRightLongAction == 2) {
						v.setLongpressAction(ActionConstants.ACTION_BACK);
					} else if (mLegacyMenuRightLongAction == 3) {
						v.setLongpressAction(ActionConstants.ACTION_RECENTS);			
					} else if (mLegacyMenuRightLongAction == 4) {
						v.setLongpressAction(ActionConstants.ACTION_VOICE_SEARCH);				
					} else if (mLegacyMenuRightLongAction == 5) {
						v.setLongpressAction(ActionConstants.ACTION_SEARCH);
					} else if (mLegacyMenuRightLongAction == 6) {
						v.setLongpressAction(ActionConstants.ACTION_POWER);
					} else if (mLegacyMenuRightLongAction == 7) {
						v.setLongpressAction(ActionConstants.ACTION_NOTIFICATIONS);
					} else if (mLegacyMenuRightLongAction == 8) {
						v.setLongpressAction(ActionConstants.ACTION_SETTINGS_PANEL);
					} else if (mLegacyMenuRightLongAction == 9) {
						v.setLongpressAction(ActionConstants.ACTION_SCREENSHOT);
					} else if (mLegacyMenuRightLongAction == 10) {
						v.setLongpressAction(ActionConstants.ACTION_SCREENRECORD);
					} else if (mLegacyMenuRightLongAction == 11) {
						v.setLongpressAction(ActionConstants.ACTION_IME);
					} else if (mLegacyMenuRightLongAction == 12) {
						v.setLongpressAction(ActionConstants.ACTION_LAST_APP);
					} else if (mLegacyMenuRightLongAction == 13) {
						v.setLongpressAction(ActionConstants.ACTION_KILL);
					} else if (mLegacyMenuRightLongAction == 14) {
						v.setLongpressAction(ActionConstants.ACTION_ASSIST);
					} else if (mLegacyMenuRightLongAction == 15) {
						v.setLongpressAction(ActionConstants.ACTION_VIB);
					} else if (mLegacyMenuRightLongAction == 16) {
						v.setLongpressAction(ActionConstants.ACTION_VIB_SILENT);
					} else if (mLegacyMenuRightLongAction == 17) {
						v.setLongpressAction(ActionConstants.ACTION_SILENT);
					} else if (mLegacyMenuRightLongAction == 18) {
						v.setLongpressAction(ActionConstants.ACTION_POWER_MENU);
					} else if (mLegacyMenuRightLongAction == 19) {
						v.setLongpressAction(ActionConstants.ACTION_TORCH);
					} else if (mLegacyMenuRightLongAction == 20) {		
						v.setLongpressAction(ActionConstants.ACTION_MEDIA_PREVIOUS);
					} else if (mLegacyMenuRightLongAction == 21) {
						v.setLongpressAction(ActionConstants.ACTION_MEDIA_NEXT);	
					} else if (mLegacyMenuRightLongAction == 22) {			
						v.setLongpressAction(ActionConstants.ACTION_MEDIA_PLAY_PAUSE);
					} else if (mLegacyMenuRightLongAction == 23) {			
						v.setLongpressAction(mCustomRightLongShortcutUri);						
					} else if (mLegacyMenuRightLongAction == 24) {													
						v.setLongpressAction(ActionConstants.ACTION_NULL);				
					}				
			}                
            v.setVisibility(View.INVISIBLE);
            v.setContentDescription(getResources().getString(R.string.accessibility_menu));
        } else if (keyId == KEY_IME_SWITCHER) {
            v.setClickAction(ActionConstants.ACTION_IME);
            v.setId(R.id.ime_switcher);
            v.setVisibility(View.GONE);
            d = mContext.getResources().getDrawable(R.drawable.ic_ime_switcher_default);
        }

        if (mNavBarButtonColorMode != 3) {
            if (d instanceof VectorDrawable) {
                d.setTint(mNavBarButtonColor);
                v.setImageDrawable(d);
            } else {
                v.setImageBitmap(ColorHelper.getColoredBitmap(d, mNavBarButtonColor));
            }
        } else {
            v.setImageDrawable(d);
        }
        v.setRippleColor(mRippleColor);

        return v;
    }

    private int[] getAppIconPadding() {
        int[] padding = new int[4];
        // left
        padding[0] = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources()
                .getDisplayMetrics());
        // top
        padding[1] = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources()
                .getDisplayMetrics());
        // right
        padding[2] = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources()
                .getDisplayMetrics());
        // bottom
        padding[3] = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5,
                getResources()
                        .getDisplayMetrics());
        return padding;
    }

    private LayoutParams getLayoutParams(boolean landscape, int dp) {
        float px = dp * getResources().getDisplayMetrics().density;
        return landscape ?
                new LayoutParams(LayoutParams.MATCH_PARENT, dp, 1f) :
                new LayoutParams(dp, LayoutParams.MATCH_PARENT, 1f);
    }

    private LayoutParams getSeparatorLayoutParams(boolean landscape) {
        float px = 25 * getResources().getDisplayMetrics().density;
        return landscape ?
                new LayoutParams(LayoutParams.MATCH_PARENT, (int) px) :
                new LayoutParams((int) px, LayoutParams.MATCH_PARENT);
    }

    private void addLightsOutButton(LinearLayout root, View v, boolean landscape, boolean empty) {
        ImageView addMe = new ImageView(mContext);
        addMe.setLayoutParams(v.getLayoutParams());
        addMe.setImageResource(empty ? R.drawable.ic_sysbar_lights_out_dot_large
                : R.drawable.ic_sysbar_lights_out_dot_small);
        addMe.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        addMe.setVisibility(empty ? View.INVISIBLE : View.VISIBLE);

        if (landscape) {
            root.addView(addMe, 0);
        } else {
            root.addView(addMe);
        }
    }

    private void addButton(ViewGroup root, View addMe, boolean landscape) {
        if (landscape) {
            root.addView(addMe, 0);
        } else {
            root.addView(addMe);
        }
    }

    public void setNavigationIconHints(int hints) {
        setNavigationIconHints(hints, false);
    }

    public void setNavigationIconHints(int hints, boolean force) {
        if (!force && hints == mNavigationIconHints) return;
        final boolean backAlt = (hints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) != 0;
        if ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) != 0 && !backAlt) {
            mTransitionListener.onBackAltCleared();
        }
        if (DEBUG) {
            android.widget.Toast.makeText(getContext(),
                "Navigation icon hints = " + hints,
                500).show();
        }

        mNavigationIconHints = hints;

        final boolean showImeButton = ((hints & StatusBarManager.NAVIGATION_HINT_IME_SHOWN) != 0);
        if (getImeSwitchButton() != null)
            getImeSwitchButton().setVisibility(showImeButton ? View.VISIBLE : View.GONE);
            mIsImeButtonVisible = showImeButton;

        // Update menu button in case the IME state has changed.
        setMenuVisibility(mShowMenu, true);
        setDisabledFlags(mDisabledFlags, true);
    }

    public void setDisabledFlags(int disabledFlags) {
        setDisabledFlags(disabledFlags, false);
    }

    public void setDisabledFlags(int disabledFlags, boolean force) {
        if (!force && mDisabledFlags == disabledFlags) return;

        mDisabledFlags = disabledFlags;

        final boolean disableHome = ((disabledFlags & View.STATUS_BAR_DISABLE_HOME) != 0);
        boolean disableRecent = ((disabledFlags & View.STATUS_BAR_DISABLE_RECENT) != 0);
        final boolean disableBack = ((disabledFlags & View.STATUS_BAR_DISABLE_BACK) != 0)
                && ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) == 0);
        final boolean keyguardProbablyEnabled =
                (mDisabledFlags & View.STATUS_BAR_DISABLE_HOME) != 0;


        if (SLIPPERY_WHEN_DISABLED) {
            setSlippery(disableHome && disableRecent && disableBack);
        }

        ViewGroup navButtons = (ViewGroup) mCurrentView.findViewById(R.id.nav_buttons);
        if (navButtons != null) {
            LayoutTransition lt = navButtons.getLayoutTransition();
            if (lt != null) {
                if (!lt.getTransitionListeners().contains(mTransitionListener)) {
                    lt.addTransitionListener(mTransitionListener);
                }
                if (!mScreenOn && mCurrentView != null) {
                    lt.disableTransitionType(
                            LayoutTransition.CHANGE_APPEARING |
                            LayoutTransition.CHANGE_DISAPPEARING |
                            LayoutTransition.APPEARING |
                            LayoutTransition.DISAPPEARING);
                }
            }
        }
        if (inLockTask() && disableRecent && !disableHome) {
            // Don't hide recents when in lock task, it is used for exiting.
            // Unless home is hidden, then in DPM locked mode and no exit available.
            disableRecent = false;
        }

        if (mButtonsConfig != null && !mButtonsConfig.isEmpty()) {
            for (int j = 0; j < mButtonsConfig.size(); j++) {
                View v = (View) findViewWithTag((mVertical ? "key_land_" : "key_") + j);
                if (v != null) {
                    int vid = v.getId();
                    if (vid == R.id.back) {
                        v.setVisibility(disableBack ? View.INVISIBLE : View.VISIBLE);
                    } else if (vid == R.id.recent_apps) {
                        v.setVisibility(disableRecent ? View.INVISIBLE : View.VISIBLE);
                    } else { // treat all other buttons as same rule as home
                        v.setVisibility(disableHome ? View.INVISIBLE : View.VISIBLE);
                    }
                }
            }
        }

        mBarTransitions.applyBackButtonQuiescentAlpha(mBarTransitions.getMode(), true /*animate*/);
    }

    private boolean inLockTask() {
        try {
            return ActivityManagerNative.getDefault().isInLockTaskMode();
        } catch (RemoteException e) {
            return false;
        }
    }

    private void setVisibleOrGone(View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? VISIBLE : GONE);
        }
    }

    public void setSlippery(boolean newSlippery) {
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) getLayoutParams();
        if (lp != null) {
            boolean oldSlippery = (lp.flags & WindowManager.LayoutParams.FLAG_SLIPPERY) != 0;
            if (!oldSlippery && newSlippery) {
                lp.flags |= WindowManager.LayoutParams.FLAG_SLIPPERY;
            } else if (oldSlippery && !newSlippery) {
                lp.flags &= ~WindowManager.LayoutParams.FLAG_SLIPPERY;
            } else {
                return;
            }
            WindowManager wm = (WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE);
            wm.updateViewLayout(this, lp);
        }
    }

    public void setMenuVisibility(final boolean show) {
        setMenuVisibility(show, false);
    }

    public void setMenuVisibility(final boolean show, final boolean force) {
        if (!force && mShowMenu == show) {
            return;
        }

        mLegacyMenuLayout = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.LEGACY_MENU_LAYOUT, 1) == 1; 
                    
        mShowMenu = show;

        View leftMenuKeyView = getLeftMenuButton();
        View rightMenuKeyView = getRightMenuButton();
		View imeSwitcherView = getImeSwitchButton();
	
	if (mLegacyMenuLayout) {
        if (mOverrideMenuKeys) {
            leftMenuKeyView.setVisibility(View.VISIBLE);
            rightMenuKeyView.setVisibility(View.VISIBLE);
            return;
        } else if (mMenuVisibility == MENU_VISIBILITY_NEVER) {
            leftMenuKeyView.setVisibility(View.INVISIBLE);
            rightMenuKeyView.setVisibility(
                    mIsImeButtonVisible ? View.GONE : View.INVISIBLE);
        }


        // Only show Menu if IME switcher not shown.
        final boolean shouldShow =
                ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_IME_SHOWN) == 0);
            boolean showLeftMenuButton = (mMenuVisibility == MENU_VISIBILITY_ALWAYS || show)
                && (mMenuSetting == SHOW_LEFT_MENU || mMenuSetting == SHOW_BOTH_MENU);
            boolean showRightMenuButton = (mMenuVisibility == MENU_VISIBILITY_ALWAYS || show)
                && (mMenuSetting == SHOW_RIGHT_MENU || mMenuSetting == SHOW_BOTH_MENU)
                && shouldShow;

        leftMenuKeyView.setVisibility(showLeftMenuButton ? View.VISIBLE : View.INVISIBLE);
        rightMenuKeyView.setVisibility(showRightMenuButton ? View.VISIBLE
                : (mIsImeButtonVisible ? View.GONE : View.INVISIBLE));
        mShowMenu = show;
		} else { 
			leftMenuKeyView.setVisibility(View.GONE);
			rightMenuKeyView.setVisibility(View.GONE);
			imeSwitcherView.setVisibility(View.GONE);
		}
    }

    @Override
    public void onFinishInflate() {
        mRot0 = (FrameLayout) findViewById(R.id.rot0);
        mRot90 = (FrameLayout) findViewById(R.id.rot90);

        mRotatedViews[Surface.ROTATION_0] =
                mRotatedViews[Surface.ROTATION_180] = findViewById(R.id.rot0);
        mRotatedViews[Surface.ROTATION_90] = findViewById(R.id.rot90);

        mRotatedViews[Surface.ROTATION_270] = mRotatedViews[Surface.ROTATION_90];

        mCurrentView = mRotatedViews[Surface.ROTATION_0];
        updateSettings();

        if (getImeSwitchButton() != null)
            getImeSwitchButton().setOnClickListener(mImeSwitcherClickListener);

        updateRTLOrder();
    }

    public boolean isVertical() {
        return mVertical;
    }

    public void setLeftInLandscape(boolean leftInLandscape) {
        mLeftInLandscape = leftInLandscape;
        mDeadZone.setStartFromRight(leftInLandscape);
    }

    public void reorient() {
        final int rot = mDisplay.getRotation();
        for (int i=0; i<4; i++) {
            mRotatedViews[i].setVisibility(View.GONE);
        }

        if (Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.NAVIGATION_BAR_CAN_MOVE,
                DeviceUtils.isPhone(mContext) ? 1 : 0, UserHandle.USER_CURRENT) != 1) {
            mCurrentView = mRotatedViews[Surface.ROTATION_0];
        } else {
            mCurrentView = mRotatedViews[rot];
        }
        mCurrentView.setVisibility(View.VISIBLE);

        if (getImeSwitchButton() != null)
            getImeSwitchButton().setOnClickListener(mImeSwitcherClickListener);

        mDeadZone = (DeadZone) mCurrentView.findViewById(R.id.deadzone);
        mDeadZone.setStartFromRight(mLeftInLandscape);

        // force the low profile & disabled states into compliance
        mBarTransitions.init(mVertical);
        setDisabledFlags(mDisabledFlags, true /* force */);
        setMenuVisibility(mShowMenu, true /* force */);

        if (DEBUG) {
            Log.d(TAG, "reorient(): rot=" + mDisplay.getRotation());
        }

        // swap to x coordinate if orientation is not in vertical
        if (mDelegateHelper != null) {
            mDelegateHelper.setSwapXY(mVertical);
        }
        updateTaskSwitchHelper();

        setNavigationIconHints(mNavigationIconHints, true);
    }

    private void updateTaskSwitchHelper() {
        boolean isRtl = (getLayoutDirection() == View.LAYOUT_DIRECTION_RTL);
        mTaskSwitchHelper.setBarState(mVertical, isRtl);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        List<View> views = new ArrayList<View>();
        final View back = getBackButton();
        final View home = getHomeButton();
        final View recent = getRecentsButton();
        if (back != null) {
            views.add(back);
        }
        if (home != null) {
            views.add(home);
        }
        if (recent != null) {
            views.add(recent);
        }
        for (int i = 0; i < mButtonIdList.size(); i++) {
            final View customButton = getCustomButton(mButtonIdList.get(i));
            if (customButton != null) {
                views.add(customButton);
            }
        }
        mDelegateHelper.setInitialTouchRegion(views);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (DEBUG) Log.d(TAG, String.format(
                    "onSizeChanged: (%dx%d) old: (%dx%d)", w, h, oldw, oldh));

        final boolean newVertical = w > 0 && h > w;
        if (newVertical != mVertical) {
            mVertical = newVertical;
            //Log.v(TAG, String.format("onSizeChanged: h=%d, w=%d, vert=%s", h, w, mVertical?"y":"n"));
            reorient();
            notifyVerticalChangedListener(newVertical);
        }

        postCheckForInvalidLayout("sizeChanged");
        super.onSizeChanged(w, h, oldw, oldh);
    }

    private void notifyVerticalChangedListener(boolean newVertical) {
        if (mOnVerticalChangedListener != null) {
            mOnVerticalChangedListener.onVerticalChanged(newVertical);
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateRTLOrder();
        updateTaskSwitchHelper();
    }

    /**
     * In landscape, the LinearLayout is not auto mirrored since it is vertical. Therefore we
     * have to do it manually
     */
    private void updateRTLOrder() {
        boolean isLayoutRtl = getResources().getConfiguration()
                .getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        if (mIsLayoutRtl != isLayoutRtl) {

            // We swap all children of the 90 and 270 degree layouts, since they are vertical
            View rotation90 = mRotatedViews[Surface.ROTATION_90];
            swapChildrenOrderIfVertical(rotation90.findViewById(R.id.nav_buttons));
            adjustExtraKeyGravity(rotation90, isLayoutRtl);

            View rotation270 = mRotatedViews[Surface.ROTATION_270];
            if (rotation90 != rotation270) {
                swapChildrenOrderIfVertical(rotation270.findViewById(R.id.nav_buttons));
                adjustExtraKeyGravity(rotation270, isLayoutRtl);
            }
            mIsLayoutRtl = isLayoutRtl;
        }
    }

    private void adjustExtraKeyGravity(View navBar, boolean isLayoutRtl) {
        View menu = navBar.findViewById(R.id.menu);
        View imeSwitcher = navBar.findViewById(R.id.ime_switcher);
        if (menu != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) menu.getLayoutParams();
            lp.gravity = isLayoutRtl ? Gravity.BOTTOM : Gravity.TOP;
            menu.setLayoutParams(lp);
        }
        if (imeSwitcher != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) imeSwitcher.getLayoutParams();
            lp.gravity = isLayoutRtl ? Gravity.BOTTOM : Gravity.TOP;
            imeSwitcher.setLayoutParams(lp);
        }
    }

    /**
     * Swaps the children order of a LinearLayout if it's orientation is Vertical
     *
     * @param group The LinearLayout to swap the children from.
     */
    private void swapChildrenOrderIfVertical(View group) {
        if (group instanceof LinearLayout) {
            LinearLayout linearLayout = (LinearLayout) group;
            if (linearLayout.getOrientation() == VERTICAL) {
                int childCount = linearLayout.getChildCount();
                ArrayList<View> childList = new ArrayList<>(childCount);
                for (int i = 0; i < childCount; i++) {
                    childList.add(linearLayout.getChildAt(i));
                }
                linearLayout.removeAllViews();
                for (int i = childCount - 1; i >= 0; i--) {
                    linearLayout.addView(childList.get(i));
                }
            }
        }
    }

    /*
    @Override
    protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
        if (DEBUG) Log.d(TAG, String.format(
                    "onLayout: %s (%d,%d,%d,%d)",
                    changed?"changed":"notchanged", left, top, right, bottom));
        super.onLayout(changed, left, top, right, bottom);
    }

    // uncomment this for extra defensiveness in WORKAROUND_INVALID_LAYOUT situations: if all else
    // fails, any touch on the display will fix the layout.
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (DEBUG) Log.d(TAG, "onInterceptTouchEvent: " + ev.toString());
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            postCheckForInvalidLayout("touch");
        }
        return super.onInterceptTouchEvent(ev);
    }
    */


    private String getResourceName(int resId) {
        if (resId != 0) {
            final android.content.res.Resources res = getContext().getResources();
            try {
                return res.getResourceName(resId);
            } catch (android.content.res.Resources.NotFoundException ex) {
                return "(unknown)";
            }
        } else {
            return "(null)";
        }
    }

    private void postCheckForInvalidLayout(final String how) {
        mHandler.obtainMessage(MSG_CHECK_INVALID_LAYOUT, 0, 0, how).sendToTarget();
    }

    private static String visibilityToString(int vis) {
        switch (vis) {
            case View.INVISIBLE:
                return "INVISIBLE";
            case View.GONE:
                return "GONE";
            default:
                return "VISIBLE";
        }
    }

    private void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();

        mNavBarButtonColor = Settings.System.getIntForUser(resolver,
                Settings.System.NAVIGATION_BAR_BUTTON_TINT, -2, UserHandle.USER_CURRENT);

        if (mNavBarButtonColor == -2) {
            mNavBarButtonColor = mContext.getResources()
                    .getColor(R.color.navigationbar_button_default_color);
        }

        mNavBarButtonColorMode = Settings.System.getIntForUser(resolver,
                Settings.System.NAVIGATION_BAR_BUTTON_TINT_MODE, 0, UserHandle.USER_CURRENT);

        mButtonsConfig = ActionHelper.getNavBarConfig(mContext);

        mMenuSetting = Settings.System.getIntForUser(resolver,
                Settings.System.MENU_LOCATION, SHOW_RIGHT_MENU,
                UserHandle.USER_CURRENT);

        mMenuVisibility = Settings.System.getIntForUser(resolver,
                Settings.System.MENU_VISIBILITY, MENU_VISIBILITY_SYSTEM,
                UserHandle.USER_CURRENT);

        mLegacyMenuLayout = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.LEGACY_MENU_LAYOUT, 1) == 1;  
                    
        mLegacyMenuLeftAction = Settings.System.getInt(resolver,
                Settings.System.LEGACY_MENU_LEFT_ACTION, 0);
                
        mLegacyMenuRightAction = Settings.System.getInt(resolver,
                Settings.System.LEGACY_MENU_RIGHT_ACTION, 0);             
                
        mLegacyMenuLeftLongAction = Settings.System.getInt(resolver,
                Settings.System.LEGACY_MENU_LEFT_LONG_ACTION, 22);
                
        mLegacyMenuRightLongAction = Settings.System.getInt(resolver,
                Settings.System.LEGACY_MENU_RIGHT_LONG_ACTION, 22);           
                
		mCustomLeftShortcutUri = Settings.System.getString(resolver,
				Settings.System.LEGACY_MENU_LEFT_SHORTCUT_URI);
				
		mCustomRightShortcutUri = Settings.System.getString(resolver,
				Settings.System.LEGACY_MENU_RIGHT_SHORTCUT_URI);
				
		mCustomLeftLongShortcutUri = Settings.System.getString(resolver,
				Settings.System.LEGACY_MENU_LEFT_LONG_SHORTCUT_URI);								
				
		mCustomRightLongShortcutUri = Settings.System.getString(resolver,
				Settings.System.LEGACY_MENU_RIGHT_LONG_SHORTCUT_URI);	
        // construct the navigationbar
        makeBar();

    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("NavigationBarView {");
        final Rect r = new Rect();
        final Point size = new Point();
        mDisplay.getRealSize(size);

        pw.println(String.format("      this: " + PhoneStatusBar.viewInfo(this)
                        + " " + visibilityToString(getVisibility())));

        getWindowVisibleDisplayFrame(r);
        final boolean offscreen = r.right > size.x || r.bottom > size.y;
        pw.println("      window: "
                + r.toShortString()
                + " " + visibilityToString(getWindowVisibility())
                + (offscreen ? " OFFSCREEN!" : ""));

        pw.println(String.format("      mCurrentView: id=%s (%dx%d) %s",
                        getResourceName(mCurrentView.getId()),
                        mCurrentView.getWidth(), mCurrentView.getHeight(),
                        visibilityToString(mCurrentView.getVisibility())));

        pw.println(String.format("      disabled=0x%08x vertical=%s menu=%s",
                        mDisabledFlags,
                        mVertical ? "true" : "false",
                        mShowMenu ? "true" : "false"));

        final View back = getBackButton();
        final View home = getHomeButton();
        final View recent = getRecentsButton();
        final View rightMenu = getRightMenuButton();
        final View leftMenu = getLeftMenuButton();

        if (back != null)
            dumpButton(pw, "back", back);

        if (home != null)
            dumpButton(pw, "home", home);

        if (recent != null)
            dumpButton(pw, "rcnt", recent);

        if (rightMenu != null)
            dumpButton(pw, "rightMenu", rightMenu);

        if (leftMenu != null)
            dumpButton(pw, "leftMenu", leftMenu);

        pw.println("    }");
    }

    private static void dumpButton(PrintWriter pw, String caption, View button) {
        pw.print("      " + caption + ": ");
        if (button == null) {
            pw.print("null");
        } else {
            pw.print(PhoneStatusBar.viewInfo(button)
                    + " " + visibilityToString(button.getVisibility())
                    + " alpha=" + button.getAlpha()
                    );
            if (button instanceof KeyButtonView) {
                pw.print(" drawingAlpha=" + ((KeyButtonView)button).getDrawingAlpha());
                pw.print(" quiescentAlpha=" + ((KeyButtonView)button).getQuiescentAlpha());
            }
        }
        pw.println();
    }

    public interface OnVerticalChangedListener {
        void onVerticalChanged(boolean isVertical);
    }
}
