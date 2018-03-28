package com.transage.hall.service;

import android.app.Service;
import android.app.Notification;
import android.content.Intent;
import android.content.Context;
import android.content.ContentResolver;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.IBinder;
import android.os.SystemProperties;
import android.provider.Settings;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.support.v4.widget.ViewDragHelper;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.telephony.TelephonyManager;
import android.telephony.PhoneStateListener;
import android.telecom.TelecomManager;
import android.telecom.CallAudioState;
import android.telecom.InCallService;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Locale;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;

import com.transage.hall.util.HallStateUtils;
import com.transage.hall.util.AudioModeProvider;
import com.transage.hall.HallApplication;
import com.transage.hall.R;

/**
 * Created by dongrp on 2017/2/27.
 */

public class HallService extends Service {
    @SuppressWarnings("unused")
    private static final String TAG = "HallService";
    private View mHallView;
    private Context mContext;
    private DisplayMetrics mDm;
    private AudioManager audioManager;
    private TelephonyManager telephonyManager;
    private TelecomManager telecomManager;
    private GestureDetector gestureDetector;
    private WindowManager windowManager;
    private WindowManager windowManager1;
    private WindowManager.LayoutParams params;
    private WindowManager.LayoutParams params1;
    private TextView mNameTV, mNumberTV, mCallTimeTV,alarm_lebal;
    private ImageView incallButton, alarmHallbutton;
    private ImageView animation_hall_left_dial, animation_hall_right_dial;
    private ImageView animation_hall_left_alarm, animation_hall_right_alarm;
    private AnimationDrawable hallLeftAnimationDrawable1, hallRightAnimationDrawable1;
    private AnimationDrawable hallLeftAnimationDrawable2, hallRightAnimationDrawable2;
    private ImageView mAnswerButton, mHangUpButton, mHallSnoozeButton, mHallDismissButton;
    private ViewGroup mDialHallContentView, mAlarmHallContentView, mStandbyHallContentView;
    private FrameLayout mDialLayoutHall, mAlarmLayoutHall, mStandbyLayoutHall;
//    private LinearLayout mStandbyLayoutHall;

    private static final String TECNO_KEYGUARD_HALL_ACTION = "tecno.keyguard.hall_1"; //0开盖 1合盖
    private static final String DELAYED_KEYGUARD_ACTION = "com.android.internal.policy.impl.PhoneWindowManager.DELAYED_KEYGUARD";
    private static final String DELAYED_LOCK_PROFILE_ACTION = "com.android.internal.policy.impl.PhoneWindowManager.DELAYED_LOCK";
    private static final String PRE_SHUTDOWN = "android.intent.action.ACTION_PRE_SHUTDOWN";
    /// M: add for IPO shut down update processm
    private static final String IPO_SHUTDOWN = "android.intent.action.ACTION_SHUTDOWN_IPO";
    private static final String IPO_BOOTUP = "android.intent.action.ACTION_PREBOOT_IPO";

    //A public action sent by xxx when snooze the alarm (after ALARM_ALERT_ACTION and before ALARM_DONE_ACTION)
    public static final String ALARM_SNOOZE_ACTION = "com.android.deskclock.ALARM_SNOOZE";
    //A public action sent by xxx when the alarm has dismiss.
    public static final String ALARM_DISMISS_ACTION = "com.android.deskclock.ALARM_DISMISS";
    //A public action sent by xxx when the alarm has started.
    public static final String ALARM_ALERT_ACTION = "com.android.deskclock.ALARM_ALERT";
    //A public action sent by xxx when the alarm has stopped for any reason.
    public static final String ALARM_DONE_ACTION = "com.android.deskclock.ALARM_DONE";

    public static final int PHONE_STATE_CHANGED = 368;
    CallManager mCM;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        IntentFilter filter = new IntentFilter();
        filter.addAction(DELAYED_KEYGUARD_ACTION);
        /// M: fix 441605, play sound after power off
        filter.addAction(PRE_SHUTDOWN);
        /// M: fix 629523, music state not set as pause
        filter.addAction(IPO_SHUTDOWN);
        filter.addAction(IPO_BOOTUP);
        filter.addAction(TECNO_KEYGUARD_HALL_ACTION);//霍尔广播action
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_DATE_CHANGED);
        filter.addAction(Intent.ACTION_NEW_OUTGOING_CALL);
        filter.addAction(Intent.ACTION_UNREAD_CHANGED);
        filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        filter.addAction("android.telecom.action.SHOW_MISSED_CALLS_NOTIFICATION_TO_SYSTEMUI");
        filter.addAction("com.android.deskclock.ALARM_SNOOZE");
        filter.addAction("com.android.deskclock.ALARM_DISMISS");
        filter.addAction("com.android.deskclock.ALARM_ALERT");
        filter.addAction("com.android.deskclock.ALARM_DONE");

        //将本service配置到phone进程中：注册Phone 、注册registerForPreciseCallStateChanged监听（电话状态改变后，通过mHandler进行msg回调）
        mCM = CallManager.getInstance();
        Phone phone = PhoneFactory.getDefaultPhone();
        mCM.registerPhone(phone);
        mCM.registerForPreciseCallStateChanged(mHandler, PHONE_STATE_CHANGED, null);

        mContext = getApplicationContext();
        mDm = getResources().getDisplayMetrics();
        telephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(MyPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);//注册电话状态监听
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        telecomManager = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
        mContext.registerReceiver(mBroadcastReceiver, filter);
        Log.d("HallService", "service-oncreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("HallService", "onStartCommand");
        initWindowParams();
        initView();
//        showHallView();
        //模拟霍尔广播，调试用的imageview
/*        ImageView imageView = new ImageView(mContext);
        imageView.setBackground(getDrawable(R.mipmap.ic_launcher));
        windowManager1.addView(imageView, params1);
        gestureDetector = new GestureDetector(this, new MyGestureListener());
        imageView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return gestureDetector.onTouchEvent(event);
            }
        });*/
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("PointService", "onDestroy");
        hallLeftAnimationDrawable1.stop();
        hallRightAnimationDrawable1.stop();
        hallLeftAnimationDrawable2.stop();
        hallRightAnimationDrawable2.stop();
        windowManager.removeView(mHallView);
        unregisterReceiver(mBroadcastReceiver);
    }

    /**
     * 初始化view
     */
    public void initView() {
        //this is the root view
        mHallView = LayoutInflater.from(mContext).inflate(R.layout.hall_activity, null, false);
/////////////////////////////////////////////////////////////////////////////////////////
        //Standby Hall FrameLayout
        mStandbyLayoutHall = (FrameLayout) mHallView.findViewById(R.id.standby_layout_hall);
        mStandbyHallContentView = (ViewGroup) mHallView.findViewById(R.id.standby_content_hall);
////////////////////////////////////////////////////////////////////////////////////////
        //Dial Hall FrameLayout
        mDialLayoutHall = (FrameLayout) mHallView.findViewById(R.id.dial_layout_hall);
        mDialHallContentView = (ViewGroup) mHallView.findViewById(R.id.dial_content_hall);
        animation_hall_left_dial = (ImageView) mHallView.findViewById(R.id.animation_hall_left_dial);
        animation_hall_right_dial = (ImageView) mHallView.findViewById(R.id.animation_hall_right_dial);
        incallButton = (ImageView) mHallView.findViewById(R.id.incall);
        mAnswerButton = (ImageView) mHallView.findViewById(R.id.answer);
        mHangUpButton = (ImageView) mHallView.findViewById(R.id.hang_up);
        mCallTimeTV = (TextView) mHallView.findViewById(R.id.incall_time);
        mNumberTV = (TextView) mHallView.findViewById(R.id.number);
        mNameTV = (TextView) mHallView.findViewById(R.id.name);

        incallButton.setOnTouchListener(new DialOnTouchListener());
        hallLeftAnimationDrawable1 = (AnimationDrawable) animation_hall_left_dial.getDrawable();
        hallRightAnimationDrawable1 = (AnimationDrawable) animation_hall_right_dial.getDrawable();
        hallLeftAnimationDrawable1.start();
        hallRightAnimationDrawable1.start();
///////////////////////////////////////////////////////////////////////////////////////////
        //Alarm Hall FrameLayout
        mAlarmLayoutHall = (FrameLayout) mHallView.findViewById(R.id.alarm_layout_hall);
        mAlarmHallContentView = (ViewGroup) mHallView.findViewById(R.id.alarm_content_hall);
        alarm_lebal = (TextView) mHallView.findViewById(R.id.title_hall);
        animation_hall_left_alarm = (ImageView) mHallView.findViewById(R.id.animation_hall_left_alarm);
        animation_hall_right_alarm = (ImageView) mHallView.findViewById(R.id.animation_hall_right_alarm);
        alarmHallbutton = (ImageView) mHallView.findViewById(R.id.alarm_hall);
        mHallSnoozeButton = (ImageView) mHallView.findViewById(R.id.snooze_hall);
        mHallDismissButton = (ImageView) mHallView.findViewById(R.id.dismiss_hall);
        alarmHallbutton.setOnTouchListener(new AlarmOnTouchListener());
        hallLeftAnimationDrawable2 = (AnimationDrawable) animation_hall_left_alarm.getDrawable();
        hallRightAnimationDrawable2 = (AnimationDrawable) animation_hall_right_alarm.getDrawable();
        hallLeftAnimationDrawable2.start();
        hallRightAnimationDrawable2.start();

        mHallView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        windowManager.addView(mHallView, params);
        hideHallView();
    }

    /**
     * 初始化WindowParams
     */
    private void initWindowParams() {
        windowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        params = new WindowManager.LayoutParams();
//        params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        params.type = WindowManager.LayoutParams.TYPE_TOP_MOST;
        params.format = PixelFormat.TRANSLUCENT;
//        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        params.flags = WindowManager.LayoutParams.FLAG_TOUCHABLE_WHEN_WAKING
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS;
        params.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        params.gravity = Gravity.TOP;
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.MATCH_PARENT;

        //添加调试用的imageview, 所使用的wm
        windowManager1 = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        params1 = new WindowManager.LayoutParams();
        params1.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        params1.format = PixelFormat.TRANSLUCENT;
        params1.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        params1.gravity = Gravity.START | Gravity.TOP;
        params1.width = 100;
        params1.height = 100;
    }

    /**
     * 电话界面 Touch 监听器
     */
    private int mLastX_1, mLastY_1;
    private int mOldLeft_1, mOldRight_1;

    class DialOnTouchListener implements View.OnTouchListener {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (mDialLayoutHall.getVisibility() == View.VISIBLE) {
                final int screenWidth = mDm.widthPixels;
                final int screenHeight = mDm.heightPixels;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        //android.util.Log.d("DialOnTouchListener", "DialActivity-----onTouch----ACTION_DOWN----");
                        mLastX_1 = (int) event.getRawX();// 获取触摸事件触摸位置的原始X坐标
                        mLastY_1 = (int) event.getRawY();
                        mOldLeft_1 = v.getLeft();
                        mOldRight_1 = v.getRight();
                        break;
                    case MotionEvent.ACTION_UP:
                        animation_hall_left_dial.setAlpha(1.0f);
                        animation_hall_right_dial.setAlpha(1.0f);
                        animation_hall_left_dial.postInvalidate();
                        animation_hall_right_dial.postInvalidate();
                        int dismissX = (int) mHangUpButton.getX();
                        int snoozeX = (int) mAnswerButton.getX() + mAnswerButton.getWidth();
                        //android.util.Log.d("DialOnTouchListener", "DialActivity----onTouch----ACTION_UP----snoozeX = " + snoozeX + " , dismissX = " + dismissX + " , mLastX_1 = " + mLastX_1);
                        if (mLastX_1 >= dismissX) {
                            isAnswerOrReject(false);
                            android.widget.Toast.makeText(mContext, "hang up", Toast.LENGTH_SHORT).show();
                        }
                        if (mLastX_1 <= snoozeX) {
                            isAnswerOrReject(true);
                            android.widget.Toast.makeText(mContext, "answer", Toast.LENGTH_SHORT).show();
                        }
                        v.layout(mOldLeft_1, v.getTop(), mOldRight_1, v.getBottom());
                        v.postInvalidate();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (telephonyManager.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK) {
                            break;
                        }
                        int dx = (int) event.getRawX() - mLastX_1;
                        int l = v.getLeft() + dx;
                        int b = v.getBottom();
                        int r = v.getRight() + dx;
                        int t = v.getTop();
                        // 下面判断移动是否超出屏幕
                        if (l < mAnswerButton.getX()) {
                            l = (int) mAnswerButton.getX();
                            r = l + v.getWidth();
                        }
                        if (r > mHangUpButton.getX() + mHangUpButton.getWidth()) {
                            r = (int) (mHangUpButton.getX() + mHangUpButton.getWidth());
                            l = r - v.getWidth();
                        }
                        v.layout(l, t, r, b);
                        mLastX_1 = (int) event.getRawX();
                        mLastY_1 = (int) event.getRawY();
                        v.postInvalidate();
                        if (mLastX_1 < screenWidth / 2) {
                            float alphaLeft = (mLastX_1 - 79) / ((float) mDialHallContentView.getWidth() / 2);
                            animation_hall_left_dial.setAlpha(alphaLeft);
                            animation_hall_right_dial.setAlpha(1.0f);
                            animation_hall_left_dial.postInvalidate();
                            animation_hall_right_dial.postInvalidate();
                        } else {
                            float alphaRight = (mDialHallContentView.getWidth() / 2 - mLastX_1 + mOldLeft_1) / ((float) mDialHallContentView.getWidth() / 2);
                            animation_hall_right_dial.setAlpha(alphaRight);
                            animation_hall_left_dial.setAlpha(1.0f);
                            animation_hall_right_dial.postInvalidate();
                            animation_hall_left_dial.postInvalidate();
                        }
                        break;
                }
            }

            if (telephonyManager.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK) {
                return false; //通话中 不处理touch事件，incallButton 不能滑动 可以单击
            } else {
                return true; //响铃状态：处理touch事件，incallButton可滑动，屏蔽click事件
            }
        }
    }


    /**
     * 闹钟界面 Touch 监听器
     */
    private int mLastX_2, mLastY_2;
    private int mOldLeft_2, mOldRight_2;

    class AlarmOnTouchListener implements View.OnTouchListener {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (mAlarmLayoutHall.getVisibility() == View.VISIBLE) {
                final int screenWidth = mDm.widthPixels;
                final int screenHeight = mDm.heightPixels;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        //android.util.Log.d("AlarmOnTouchListener", "AlarmActivity-----onTouch----ACTION_DOWN----");
                        mLastX_2 = (int) event.getRawX();// 获取触摸事件触摸位置的原始X坐标
                        mLastY_2 = (int) event.getRawY();
                        mOldLeft_2 = v.getLeft();
                        mOldRight_2 = v.getRight();
                        break;
                    case MotionEvent.ACTION_UP:
                        animation_hall_left_alarm.setAlpha(1.0f);
                        animation_hall_right_alarm.setAlpha(1.0f);
                        animation_hall_left_alarm.postInvalidate();
                        animation_hall_right_alarm.postInvalidate();
                        int dismissX = (int) mHallDismissButton.getX();
                        int snoozeX = (int) mHallSnoozeButton.getX() + mHallSnoozeButton.getWidth();
                        //android.util.Log.d("AlarmOnTouchListener", "AlarmActivity----onTouch----ACTION_UP----snoozeX = " + snoozeX + " , dismissX = " + dismissX + " , mLastX_2 = " + mLastX_2);
                        if (mLastX_2 >= dismissX) {
                            dismiss();
                            android.widget.Toast.makeText(mContext, "dismiss", Toast.LENGTH_SHORT).show();
                        }
                        if (mLastX_2 <= snoozeX) {
                            snooze();
                            android.widget.Toast.makeText(mContext, "snooze", Toast.LENGTH_SHORT).show();
                        }
                        v.layout(mOldLeft_2, v.getTop(), mOldRight_2, v.getBottom());
                        v.postInvalidate();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) event.getRawX() - mLastX_2;
                        int l = v.getLeft() + dx;
                        int b = v.getBottom();
                        int r = v.getRight() + dx;
                        int t = v.getTop();
                        // 下面判断移动是否超出屏幕
                        if (l < mHallSnoozeButton.getX()) {
                            l = (int) mHallSnoozeButton.getX();
                            r = l + v.getWidth();
                        }
                        if (r > mHallDismissButton.getX() + mHallDismissButton.getWidth()) {
                            r = (int) (mHallDismissButton.getX() + mHallDismissButton.getWidth());
                            l = r - v.getWidth();
                        }
                        v.layout(l, t, r, b);
                        mLastX_2 = (int) event.getRawX();
                        mLastY_2 = (int) event.getRawY();
                        v.postInvalidate();
                        if (mLastX_2 < screenWidth / 2) {
                            float alphaLeft = (mLastX_2 - 79) / ((float) mAlarmHallContentView.getWidth() / 2);
                            animation_hall_left_alarm.setAlpha(alphaLeft);
                            animation_hall_right_alarm.setAlpha(1.0f);
                            animation_hall_left_alarm.postInvalidate();
                            animation_hall_right_alarm.postInvalidate();
                        } else {
                            float alphaRight = (mAlarmHallContentView.getWidth() / 2 - mLastX_2 + mOldLeft_2) / ((float) mAlarmHallContentView.getWidth() / 2);
                            animation_hall_right_alarm.setAlpha(alphaRight);
                            animation_hall_left_alarm.setAlpha(1.0f);
                            animation_hall_right_alarm.postInvalidate();
                            animation_hall_left_alarm.postInvalidate();
                        }
                        break;
                }
            }
            return true;
        }
    }

    /**
     * 自定义手势监听类
     * 安卓中的GestureDetector提供了两个侦听器接口，OnGestureListener处理单击类消息，OnDoubleTapListener处理双击类消息。
     * 有时候我们并不需要处理上面所有手势，方便起见，Android提供了另外一个类SimpleOnGestureListener实现了上述两个接口中的方法，
     * 我们只需要继承SimpleOnGestureListener然后重写感兴趣的手势即可
     */
    class MyGestureListener extends GestureDetector.SimpleOnGestureListener {

        //按下，触摸屏按下时立刻触发
        @Override
        public boolean onDown(MotionEvent e) {
            //Toast.makeText(mContext, "按下 " + e.getAction(), Toast.LENGTH_SHORT).show();
            return false;
        }

        // 短按，触摸屏按下后片刻后抬起，会触发这个手势，如果迅速抬起则不会
        @Override
        public void onShowPress(MotionEvent e) {
            //Toast.makeText(mContext, "短按" + e.getAction(), Toast.LENGTH_SHORT).show();
        }

        // 抬起，手指离开触摸屏时触发(长按、滚动、滑动时，不会触发这个手势)
        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            //Toast.makeText(mContext, "抬起" + e.getAction(), Toast.LENGTH_SHORT).show();
            return false;
        }

        // 拖动，触摸屏按下后移动
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            //Toast.makeText(mContext, "拖动" + e2.getAction(), Toast.LENGTH_SHORT).show();
            //android.util.Log.d("MyGestureListener", "tttttt");
            return false;
        }

        // 长按，触摸屏按下后既不抬起也不移动，过一段时间后触发
        @Override
        public void onLongPress(MotionEvent e) {
            //Toast.makeText(mContext, "长按" + e.getAction(), Toast.LENGTH_SHORT).show();
            //longPress = true;

//            if (mStandbyLayoutHall.getVisibility() == View.VISIBLE) {
//                showAlarmView();
//            } else if (mAlarmLayoutHall.getVisibility() == View.VISIBLE) {
//                showDialView();
//            } else if (mDialLayoutHall.getVisibility() == View.VISIBLE) {
//                showStandbyView();
//            }
        }

        // 滑动，触摸屏按下后快速移动并抬起，会先触发滚动手势，跟着触发一个滑动手势
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            //Toast.makeText(mContext, "滑动" + e2.getAction(), Toast.LENGTH_SHORT).show();
            return false;
        }

        // 双击，手指在触摸屏上迅速点击第二下时触发
        @Override
        public boolean onDoubleTap(MotionEvent e) {
//            Toast.makeText(mContext, "开盖" + e.getAction(), Toast.LENGTH_SHORT).show();
//            Intent intent = new Intent();
//            intent.setAction("tecno.keyguard.halll");
//            intent.putExtra("state", 0);
//            sendBroadcast(intent);
            return false;
        }

        // 双击的按下跟抬起各触发一次
        @Override
        public boolean onDoubleTapEvent(MotionEvent e) {
            //Toast.makeText(mContext, "DOUBLE EVENT " + e.getAction(), Toast.LENGTH_SHORT).show();
            return false;
        }

        // 单击确认，即很快的按下并抬起，但并不连续点击第二下
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
//            Toast.makeText(mContext, "合盖" + e.getAction(), Toast.LENGTH_SHORT).show();
//            Intent intent = new Intent();
//            intent.setAction("tecno.keyguard.halll");
//            intent.putExtra("state", 1);
//            sendBroadcast(intent);
            if (mHallView.getVisibility() == View.VISIBLE) {
                Toast.makeText(mContext, "开", Toast.LENGTH_SHORT).show();
                hideHallView();
            } else if (mHallView.getVisibility() == View.GONE) {
                Toast.makeText(mContext, "关", Toast.LENGTH_SHORT).show();
                showHallView();
            }
            return false;
        }
    }

//////////Standby-Start///////////////////////////////////////////////////////////////////////////////////////////////////////////

    private static final int[] DATA_TIME_NUMB = new int[]{R.mipmap.hall_time_0, R.mipmap.hall_time_1, R.mipmap.hall_time_2,
            R.mipmap.hall_time_3, R.mipmap.hall_time_4, R.mipmap.hall_time_5, R.mipmap.hall_time_6, R.mipmap.hall_time_7,
            R.mipmap.hall_time_8, R.mipmap.hall_time_9};

    public void updateHallTimeImage() {
        final Calendar mCalendar = Calendar.getInstance();
        mCalendar.setTimeInMillis(System.currentTimeMillis());
        boolean is24Hour = DateFormat.is24HourFormat(mContext);
        android.util.Log.d(TAG, "HallService----updateHallTimeImage----is24Hour = " + is24Hour);
        String hour;
        if (is24Hour) {
            int apm = mCalendar.get(Calendar.AM_PM);
            if (apm == 0) {
                hour = Integer.toString(mCalendar.get(Calendar.HOUR));
            } else {
                hour = Integer.toString(mCalendar.get(Calendar.HOUR) + 12);
            }
        } else {
            if (mCalendar.get(Calendar.HOUR) == 0) {
                hour = Integer.toString(mCalendar.get(Calendar.HOUR) + 12);
            } else {
                hour = Integer.toString(mCalendar.get(Calendar.HOUR));
            }
        }
        String minute = Integer.toString(mCalendar.get(Calendar.MINUTE));
        ImageView ivHallHour1 = (ImageView) mHallView.findViewById(R.id.imageview_hour_1_hall);
        ImageView ivHallHour2 = (ImageView) mHallView.findViewById(R.id.imageview_hour_2_hall);
        ImageView ivHallMinute1 = (ImageView) mHallView.findViewById(R.id.imageview_minute_1_hall);
        ImageView ivHallMinute2 = (ImageView) mHallView.findViewById(R.id.imageview_minute_2_hall);
        android.util.Log.d(TAG, "HallService----updateHallTimeImage----hour = " + hour + " , minute = " + minute);
        if (hour.length() == 1) {
            ivHallHour1.setBackgroundResource(DATA_TIME_NUMB[0]);
            ivHallHour2.setBackgroundResource(DATA_TIME_NUMB[Integer.parseInt(hour)]);
        } else {
            ivHallHour1.setBackgroundResource(DATA_TIME_NUMB[Integer.parseInt(hour.substring(0, 1))]);
            ivHallHour2.setBackgroundResource(DATA_TIME_NUMB[Integer.parseInt(hour.substring(1, hour.length()))]);
        }
        if (minute.length() == 1) {
            ivHallMinute1.setBackgroundResource(DATA_TIME_NUMB[0]);
            ivHallMinute2.setBackgroundResource(DATA_TIME_NUMB[Integer.parseInt(minute)]);
        } else {
            ivHallMinute1.setBackgroundResource(DATA_TIME_NUMB[Integer.parseInt(minute.substring(0, 1))]);
            ivHallMinute2.setBackgroundResource(DATA_TIME_NUMB[Integer.parseInt(minute.substring(1, minute.length()))]);
        }
    }

    public void updateHallDataText() {
        if (null != mHallView) {
            TextView dataTextView = (TextView) mHallView.findViewById(R.id.textview_hall_data);
            dataTextView.setText(getData());
        }
    }

    public void updateHallSmsNumb(int missSmsNumb) {
        if (null != mHallView) {
            android.util.Log.d(TAG, "HallService----updateHallSmsNumb()----missSmsNumb = " + missSmsNumb);
            ImageView ivMissSms = (ImageView) mHallView.findViewById(R.id.imageview_miss_sms_hall);
            TextView tvMissSmsNumb = (TextView) mHallView.findViewById(R.id.textview_miss_sms_hall);
            LinearLayout llMissSms = (LinearLayout) mHallView.findViewById(R.id.linearlayout_miss_sms_hall);
            if (missSmsNumb > 0) {
                tvMissSmsNumb.setText(String.valueOf(missSmsNumb));
            }
            ivMissSms.setVisibility(missSmsNumb > 0 ? View.VISIBLE : View.GONE);
            tvMissSmsNumb.setVisibility(missSmsNumb > 0 ? View.VISIBLE : View.GONE);
            llMissSms.setVisibility(missSmsNumb > 0 ? View.VISIBLE : View.GONE);
        }
    }

    public void updateHallCallNumb(int missCallNumb) {
        if (null != mHallView) {
            android.util.Log.d(TAG, "HallService----updateHallCallNumb()----missCallNumb = " + missCallNumb);
            ImageView ivMissCall = (ImageView) mHallView.findViewById(R.id.imageview_miss_call_hall);
            TextView tvMissCallNumb = (TextView) mHallView.findViewById(R.id.textview_miss_call_hall);
            LinearLayout llMissCall = (LinearLayout) mHallView.findViewById(R.id.linearlayout_miss_call_hall);
            if (missCallNumb > 0) {
                tvMissCallNumb.setText(String.valueOf(missCallNumb));
            }
            ivMissCall.setVisibility(missCallNumb > 0 ? View.VISIBLE : View.GONE);
            tvMissCallNumb.setVisibility(missCallNumb > 0 ? View.VISIBLE : View.GONE);
            llMissCall.setVisibility(missCallNumb > 0 ? View.VISIBLE : View.GONE);
        }
    }

    public int getHallViewVisiable() {
        if (null != mHallView) {
            return mHallView.getVisibility();
        }
        return View.GONE;

    }

    private String getData() {
        final Calendar c = Calendar.getInstance();
        String year = String.valueOf(c.get(Calendar.YEAR)); // 鑾峰彇褰撳墠骞翠唤
        String month = String.valueOf(c.get(Calendar.MONTH) + 1);// 鑾峰彇褰撳墠鏈堜唤
        String day = String.valueOf(c.get(Calendar.DAY_OF_MONTH));// 鑾峰彇褰撳墠鏈堜唤鐨勬棩鏈熷彿鐮?
        String way = String.valueOf(c.get(Calendar.DAY_OF_WEEK));
        String monthDisplay = "";
        switch (month) {
            case "1":
                monthDisplay = mContext.getResources().getString(R.string.hall_view_date_month_1);
                break;
            case "2":
                monthDisplay = mContext.getResources().getString(R.string.hall_view_date_month_2);
                break;
            case "3":
                monthDisplay = mContext.getResources().getString(R.string.hall_view_date_month_3);
                break;
            case "4":
                monthDisplay = mContext.getResources().getString(R.string.hall_view_date_month_4);
                break;
            case "5":
                monthDisplay = mContext.getResources().getString(R.string.hall_view_date_month_5);
                break;
            case "6":
                monthDisplay = mContext.getResources().getString(R.string.hall_view_date_month_6);
                break;
            case "7":
                monthDisplay = mContext.getResources().getString(R.string.hall_view_date_month_7);
                break;
            case "8":
                monthDisplay = mContext.getResources().getString(R.string.hall_view_date_month_8);
                break;
            case "9":
                monthDisplay = mContext.getResources().getString(R.string.hall_view_date_month_9);
                break;
            case "10":
                monthDisplay = mContext.getResources().getString(R.string.hall_view_date_month_10);
                break;
            case "11":
                monthDisplay = mContext.getResources().getString(R.string.hall_view_date_month_11);
                break;
            case "12":
                monthDisplay = mContext.getResources().getString(R.string.hall_view_date_month_12);
                break;
        }
        if ("1".equals(way)) {
            way = mContext.getResources().getString(R.string.hall_view_date_sunday);
        } else if ("2".equals(way)) {
            way = mContext.getResources().getString(R.string.hall_view_date_monday);
        } else if ("3".equals(way)) {
            way = mContext.getResources().getString(R.string.hall_view_date_tuesday);
        } else if ("4".equals(way)) {
            way = mContext.getResources().getString(R.string.hall_view_date_wednesday);
        } else if ("5".equals(way)) {
            way = mContext.getResources().getString(R.string.hall_view_date_thursday);
        } else if ("6".equals(way)) {
            way = mContext.getResources().getString(R.string.hall_view_date_friday);
        } else if ("7".equals(way)) {
            way = mContext.getResources().getString(R.string.hall_view_date_saturday);
        }
        if ("zh".equals(Locale.getDefault().getLanguage())) {
            return monthDisplay + day + "日" + way;
        } else {
            return way + " , " + monthDisplay + " " + day;
        }
    }

//////////Standby-End//////////////////////////////////////////////////////////////////////////////////////////////////////////


///////////////////////////////////////////////Alarm-Start/////////////////////////////////////////////////////////////////////

    private void snooze() {
        android.util.Log.d(TAG, "Alarm---Snoozed");
        //AlarmStateManager.setSnoozeState(this, mAlarmInstance, false /* showToast */);
        //Events.sendAlarmEvent(R.string.action_snooze, R.string.label_deskclock);

        Intent intent = new Intent();
        intent.setAction(ALARM_SNOOZE_ACTION);
        //intent.setPackage("com.android.systemui");
        sendBroadcast(intent);
        showStandbyView();
    }

    private void dismiss() {
        android.util.Log.d(TAG, "Alarm---Dismissed");
        //AlarmStateManager.deleteInstanceAndUpdateParent(this, mAlarmInstance);
        //Events.sendAlarmEvent(R.string.action_dismiss, R.string.label_deskclock);

        Intent intent = new Intent();
        intent.setAction(ALARM_DISMISS_ACTION);
        //intent.setPackage("com.android.systemui");
        sendBroadcast(intent);
        showStandbyView();
    }

///////////////////////////////////////////////Alarm-End////////////////////////////////////////////////////////////////////////


//////////////////////////////////////////////////////////////////////////////////////Dial-Start///////////////////////////////


    private static boolean isMute = false; //是否静音
    private static boolean togglePhone = false; //是否免提（扬声器翻转）
    long callActivityTime = 0; //电话接通的时间戳

    private void isAnswerOrReject(boolean isAnswer) {
        if (isAnswer) {
            Log.d(TAG, "answerCall");
            telecomManager.acceptRingingCall();
            //updateUiToInCall();
        } else {
            Log.d(TAG, "rejectCall");
            telecomManager.endCall();
        }
    }

/*    public void updateUiToInCall() {
        Log.d(TAG, "updateUiToInCall");
        updateMuteAndSpeak();
        //mCallTimeTV.setVisibility(View.VISIBLE);
        //sendMsgGotoSleep();

    }*/

/*    public void updateUiToOutGoing() {
        Log.d(TAG, "updateUiToOutGoing");
        updateMuteAndSpeak();
        //sendMsgGotoSleep();
    }*/

    private void updateMuteAndSpeak() {
        changeButtonImage(incallButton, R.mipmap.hang_up);
        hideAnimationImg(true);
        if (audioManager.isSilentMode()) {
            Log.d(TAG, "is mute");
            changeButtonImage(mAnswerButton, R.mipmap.open_mute);
            isMute = true;
        } else {
            Log.d(TAG, "is not  mute");
            changeButtonImage(mAnswerButton, R.mipmap.mute);
            isMute = false;
        }

        if (audioManager.isSpeakerphoneOn()) {
            Log.d(TAG, "is speaker");  //免提状态
            changeButtonImage(mHangUpButton, R.mipmap.outside_incall);
            togglePhone = true;
        } else {
            Log.d(TAG, "is not speaker"); //听筒状态
            changeButtonImage(mHangUpButton, R.mipmap.speaker);
            togglePhone = false;
        }
/*
        if (AudioModeProvider.getInstance().getMute()) {
            Log.d(TAG, "is mute");
            changeButtonImage(mAnswerButton, R.mipmap.open_mute);
            isMute = true;
        } else {
            Log.d(TAG, "is not  mute");
            changeButtonImage(mAnswerButton, R.mipmap.mute);
            isMute = false;
        }

        if (AudioModeProvider.getInstance().getAudioMode() == CallAudioState.ROUTE_SPEAKER) {
            Log.d(TAG, "is speaker");  //免提状态
            changeButtonImage(mHangUpButton, R.mipmap.outside_incall);
            togglePhone = true;
        } else {
            Log.d(TAG, "is not speaker"); //听筒状态
            changeButtonImage(mHangUpButton, R.mipmap.speaker);
            togglePhone = false;
        }
*/
        mAnswerButton.setClickable(true);
        mHangUpButton.setClickable(true);
        View.OnClickListener mCallButtonClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switch (view.getId()) {
                    case R.id.answer:
                        Log.i(TAG, "isMute:" + isMute);
                        if (!isMute) {
                            silenceCall(!isMute, R.mipmap.open_mute);
                        } else {
                            silenceCall(!isMute, R.mipmap.mute);
                        }
                        break;
                    case R.id.hang_up:
                        Log.i(TAG, "togglePhone:" + togglePhone);
                        if (!togglePhone) {
                            togglePhone(!togglePhone, R.mipmap.outside_incall);
                        } else if (togglePhone) {
                            togglePhone(!togglePhone, R.mipmap.speaker);
                        }
                        break;
                    case R.id.incall:
                        Log.i(TAG, "click-incallbutton-to-hangup():");
                        hangup();
                        break;
                    default:
                        break;
                }
            }
        };
        incallButton.setOnClickListener(mCallButtonClickListener);
        mAnswerButton.setOnClickListener(mCallButtonClickListener);
        mHangUpButton.setOnClickListener(mCallButtonClickListener);
    }

    //处理并显示callTimer的子线程中回调来的通话时间
    Handler mHandler1 = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            mCallTimeTV.setText((String) msg.obj);
        }
    };

    //启动通话计时器，每隔1s计时一次
    public Timer callTimer = null;

    public void startCallTimer() {
        callTimer = new Timer();
        callTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                String callTimeElapsed = DateUtils.formatElapsedTime((System.currentTimeMillis() - callActivityTime) / 1000);
                Message msg = Message.obtain();
                msg.what = 0;
                msg.obj = callTimeElapsed;
                mHandler1.sendMessage(msg);
            }
        }, 0, 1 * 1000);
        mCallTimeTV.setText(null);
        mCallTimeTV.setVisibility(View.VISIBLE);
    }

    //停止通话计时器
    public void stopCallTimer() {
        mCallTimeTV.setVisibility(View.GONE);
        if (null != callTimer) {
            callTimer.cancel();
            callTimer = null;
        }
    }

/*
    private void setNameAndNumber() {
        HashMap<String, Object> callInfos = HallStateUtils.getInstance().getCallInfos();
        try {
            boolean nameIsNumber = (boolean) callInfos.get("nameIsNumber");
            String name = (String) callInfos.get("name");
            String number = (String) callInfos.get("number");
            if (nameIsNumber) {
                mNameTV.setText(null);
                mNumberTV.setText(name);
            } else {
                mNameTV.setText(name);
                mNumberTV.setText(number);
            }
        } catch (NullPointerException e) {
            Log.d(TAG, "setNameAndNumber had  exception");
            e.printStackTrace();
        }
    }

    private void setTypeFace() {
        Typeface roboteLight = Typeface
                .createFromFile("/system/fonts/Roboto-Light.ttf");
        mNameTV.setTypeface(roboteLight);
        mNumberTV.setTypeface(roboteLight);
        mCallTimeTV.setTypeface(roboteLight);
    }

    private void setCallTime(long mElapsedTime) {
        long durationSecond = mElapsedTime / 1000;
        long startSecond = System.currentTimeMillis() / 1000;
        final long duration = startSecond - durationSecond;
        String callTimeElapsed = DateUtils.formatElapsedTime(duration);
        Log.d(TAG, "callTimeElapsed :" + callTimeElapsed);
        mCallTimeTV.setText(callTimeElapsed);
    }

    private void sendMsgGotoSleep() {
        Log.d(TAG, "sendMsgGotoSleep" + mHallHandler.hasMessages(MESSAGE_GOTO_SLEEP));
        if (mHallHandler.hasMessages(MESSAGE_GOTO_SLEEP)) {
            mHallHandler.removeMessages(MESSAGE_GOTO_SLEEP);
        }
        Message msg = mHallHandler.obtainMessage();
        msg.what = MESSAGE_GOTO_SLEEP;
        mHallHandler.sendMessageDelayed(msg, 15 * 1000);
    }*/


    private void hideAnimationImg(boolean hide) {
        if (hide) {
            animation_hall_left_dial.setVisibility(View.INVISIBLE);
            animation_hall_right_dial.setVisibility(View.INVISIBLE);
        } else {
            animation_hall_left_dial.setVisibility(View.VISIBLE);
            animation_hall_right_dial.setVisibility(View.VISIBLE);
        }
    }

    private void silenceCall(boolean isSlience, int id) {
        Log.d(TAG, "turning on mute: " + isSlience);
        audioManager.setMicrophoneMute(!audioManager.isMicrophoneMute());
        changeButtonImage(mAnswerButton, id);
        isMute = isSlience;
    }

    private void togglePhone(boolean isTogglePhone, int id) {
        audioManager.setSpeakerphoneOn(isTogglePhone);
        togglePhone = isTogglePhone;
        changeButtonImage(mHangUpButton, id);
        Log.d(TAG, "toggleSpeakerphone----");
    }

    private void hangup() {
        boolean endCall = telecomManager.endCall();
        Log.d(TAG, "endCall----" + endCall);
    }

    private void changeButtonImage(ImageView imageview, int drawableId) {
        imageview.setBackground(getDrawable(drawableId));
    }

    private void resetDialView() {
        mAnswerButton.setBackground(getDrawable(R.mipmap.answer));
        mHangUpButton.setBackground(getDrawable(R.mipmap.hang_up));
        incallButton.setBackground(getDrawable(R.mipmap.incall));
        mNameTV.setText(null);
        mNumberTV.setText(null);
        mCallTimeTV.setText(null);
        mAnswerButton.setClickable(false);
        mHangUpButton.setClickable(false);
        hideAnimationImg(false);
    }


//////////////////////////////////////////////////////////////////////////////////////Dial-End/////////////////////////////////

    public void showHallView() {
        //OOBE状态
        if (!isOobeHasRun()) {
            android.util.Log.d(TAG, "HallService----showHallView OOBE not finished, should hide Hall View");
            hideHallView();
            return;
        }
        mHallView.setVisibility(View.VISIBLE);
        //电话状态
        int state = telephonyManager.getCallState();
        if (state == TelephonyManager.CALL_STATE_RINGING || state == TelephonyManager.CALL_STATE_OFFHOOK) {
            android.util.Log.d(TAG, "HallService----showHallView ---showDialView");
            showDialView();
            return;
        }
        //闹钟状态
        String alarmState = SystemProperties.get("persist.sys.alarm.state", "0");
        android.util.Log.d(TAG, "HallService----showHallView alarmState =" + alarmState);
        if ("1".equals(alarmState)) {
            android.util.Log.d(TAG, "HallService----showHallView ---showAlarmView");
            showAlarmView();
            return;
        }
        //待机状态
        if (null != mHallView) {
            android.util.Log.d(TAG, "HallService----showHallView ---showStandbyView");
            showStandbyView();
        }
    }

    public void hideHallView() {
        android.util.Log.d(TAG, "HallService----hideHallView mHallView = " + mHallView);
        if (null != mHallView) {
            android.util.Log.d(TAG, "hide hall view********");
            mHallView.setVisibility(View.GONE);
        }
    }

    public void showStandbyView() {
        android.util.Log.d(TAG, "HallService----showStandbyView");
        updateHallTimeImage();
        updateHallDataText();
        updateHallSmsNumb(mContext.getSharedPreferences("hall_unread", Context.MODE_PRIVATE).getInt("com.android.mms", 0));
        updateHallCallNumb(mContext.getSharedPreferences("hall_unread", Context.MODE_PRIVATE).getInt("com.android.contacts", 0));
        mStandbyLayoutHall.setVisibility(View.VISIBLE);
        mAlarmLayoutHall.setVisibility(View.GONE);
        mDialLayoutHall.setVisibility(View.GONE);
    }

    public void showAlarmView() {
        android.util.Log.d(TAG, "HallService----showAlarmView");
        mStandbyLayoutHall.setVisibility(View.GONE);
        mAlarmLayoutHall.setVisibility(View.VISIBLE);
        mDialLayoutHall.setVisibility(View.GONE);
        if (null != lable && "" != lable){
            alarm_lebal.setText(lable);
        }
    }

    public void showDialView() {
        android.util.Log.d(TAG, "HallService----showDialView");
        mStandbyLayoutHall.setVisibility(View.GONE);
        mAlarmLayoutHall.setVisibility(View.GONE);
        mDialLayoutHall.setVisibility(View.VISIBLE);
    }

    private static final String OOBE_HAS_RUN = "oobe_has_run";

    private boolean isOobeHasRun() {
        return Settings.System.getInt(mContext.getContentResolver(), OOBE_HAS_RUN, 0) == 1;
    }

    private String lable; //闹钟标签
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            android.util.Log.d(TAG, "onReceive----------------------action=" + action);
            if (TECNO_KEYGUARD_HALL_ACTION.equals(action)) { //霍尔皮套 开盖、合盖广播
                int status = intent.getIntExtra("state", 0);
                android.util.Log.d(TAG, "status:" + status);
                if (status == 0) { //开盖
                    hideHallView();
                } else if (status == 1) { //合盖
                    showHallView();
                }
            } else if (Intent.ACTION_UNREAD_CHANGED.equals(action)) { //未读电话、短信广播
/*                if(mStatusBarKeyguardViewManager == null){
                    return;
                }*/
                String EXTRA_UNREAD_NUMBER = "com.mediatek.intent.extra.UNREAD_NUMBER";
                String EXTRA_UNREAD_COMPONENT = "com.mediatek.intent.extra.UNREAD_COMPONENT";
                Bundle extra = intent.getExtras();
                ComponentName componentName = (ComponentName) extra.get(EXTRA_UNREAD_COMPONENT);
                int unreadNum = intent.getIntExtra(EXTRA_UNREAD_NUMBER, -1);
                String packagename = componentName.getPackageName();
                /*android.util.Log.d(TAG, "KeyguardViewMediator----BroadcastReceiver " +
                        "action = " + action +
                        " , packagename = " + packagename +
                        " , unreadNum = " + unreadNum);*/
                SharedPreferences sp = mContext.getSharedPreferences("hall_unread", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sp.edit();
                if ("com.android.mms".contains(packagename)) {
                    editor.putInt("com.android.mms", unreadNum);
                    editor.commit();
                    updateHallSmsNumb(unreadNum);
                    //mStatusBarKeyguardViewManager.updateHallSmsNumb(unreadNum);
                } else if ("com.android.dialer".contains(packagename)) {
                    editor.putInt("com.android.contacts", unreadNum);
                    editor.commit();
                    updateHallCallNumb(unreadNum);
                }
            } else if (Intent.ACTION_TIME_TICK.equals(action) || Intent.ACTION_TIME_CHANGED.equals(action)) { //时间变化广播
                updateHallTimeImage();
            } else if (Intent.ACTION_DATE_CHANGED.equals(action)) { //日期变化广播
                /*if(mStatusBarKeyguardViewManager == null){
                    return;
                }*/
                updateHallDataText();
            } else if ("com.android.deskclock.ALARM_ALERT".equals(action)) {  //闹钟响起广播
                android.util.Log.d(TAG, "onReceive----------------action==" + action);
                lable = intent.getStringExtra("lable");
                //非通话中的状态 可以弹出闹钟界面
                if (telephonyManager.getCallState() != TelephonyManager.CALL_STATE_OFFHOOK) {
                    showAlarmView();
                }
            } else if ("com.android.deskclock.ALARM_SNOOZE".equals(action) || "com.android.deskclock.ALARM_DISMISS".equals(action)
                    || "com.android.deskclock.ALARM_DONE".equals(action)) {  //闹钟处理广播
                android.util.Log.d(TAG, "onReceive----------------action==" + action);
                switch (telephonyManager.getCallState()) {
                    case TelephonyManager.CALL_STATE_RINGING: //响铃
                        android.util.Log.d(TAG, "onReceive--------PHONE_STATE == RINGING");
                        showDialView();
                        break;
                    case TelephonyManager.CALL_STATE_OFFHOOK: //摘机通话
                        android.util.Log.d(TAG, "onReceive--------PHONE_STATE == OFFHOOK");
                        //showDialView();
                        break;
                    case TelephonyManager.CALL_STATE_IDLE: //挂机状态
                        android.util.Log.d(TAG, "onReceive--------PHONE_STATE == STATE_IDLE");
                        showStandbyView();
                        break;
                    default:
                        showStandbyView();
                        break;
                }
            } else if (action.equals(Intent.ACTION_NEW_OUTGOING_CALL)) {  //去电广播
                String phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
                mNumberTV.setText(phoneNumber);
                mNameTV.setText(getContactNameFromPhoneNum(phoneNumber));
                android.widget.Toast.makeText(context, "out num:" + phoneNumber, Toast.LENGTH_SHORT).show();
                android.widget.Toast.makeText(context, "out  name:" + getContactNameFromPhoneNum(phoneNumber), Toast.LENGTH_SHORT).show();
            }
        }
    };

    //根据电话号码获取通讯录中对应联系人的名字
    private String getContactNameFromPhoneNum(String number) {
        if (TextUtils.isEmpty(number)) {
            return null;
        }

        final ContentResolver resolver = getContentResolver();

        Uri lookupUri = null;
        String[] projection = new String[]{ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.DISPLAY_NAME};
        Cursor cursor = null;
        try {
            lookupUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
            cursor = resolver.query(lookupUri, projection, null, null, null);
        } catch (Exception ex) {
            ex.printStackTrace();
            try {
                lookupUri = Uri.withAppendedPath(android.provider.Contacts.Phones.CONTENT_FILTER_URL, Uri.encode(number));
                cursor = resolver.query(lookupUri, projection, null, null, null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        String ret = null;
        if (cursor != null && cursor.getCount() > 0 && cursor.moveToFirst()) {
            ret = cursor.getString(1);
        }

        cursor.close();
        return ret;
    }

    //电话状态监听器
    private PhoneStateListener MyPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            super.onCallStateChanged(state, incomingNumber);
            android.util.Log.d(TAG, "incall_number=" + incomingNumber);
            android.util.Log.d(TAG, "PhoneStateListener%%%%%%%%%%%%%%%%state=" + state);
            switch (state) {
                case TelephonyManager.CALL_STATE_RINGING: //响铃 (来电)
                    showDialView();
                    mNumberTV.setText(incomingNumber);
                    mNameTV.setText(getContactNameFromPhoneNum(incomingNumber));
                    android.widget.Toast.makeText(mContext, "in name:" + getContactNameFromPhoneNum(incomingNumber), Toast.LENGTH_SHORT).show();
                    break;
                case TelephonyManager.CALL_STATE_OFFHOOK: //摘机通话
                    showDialView();
                    break;
                case TelephonyManager.CALL_STATE_IDLE: //挂机状态
                    android.util.Log.d(TAG, "sdfjks---idel");
                    stopCallTimer();
                    resetDialView();
                    //闹钟状态
                    String alarmState = SystemProperties.get("persist.sys.alarm.state", "0");
                    if ("1".equals(alarmState)) {
                        showAlarmView();
                    } else {
                        showStandbyView();
                    }
                    break;
                default:
                    showStandbyView();
                    break;
            }
        }
    };

    //接收CallState状态改变msg
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(android.os.Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case PHONE_STATE_CHANGED:
                    updatePhoneSateChange();
                    break;
                default:
                    break;
            }
        }
    };

    private void updatePhoneSateChange() {
        Call fgCall = mCM.getActiveFgCall();
        if (mCM.hasActiveRingingCall()) {
            fgCall = mCM.getFirstActiveRingingCall();
        }
        final Call.State state = fgCall.getState();
        Log.d(TAG, "state=====" + state);
        switch (state) {
            case IDLE: //挂机状态
                android.util.Log.d(TAG, "idel");
                stopCallTimer();
                break;
            case DIALING: //拨号中(去电)
                showDialView();
                updateMuteAndSpeak();
                break;
            case ACTIVE://接通状态（来电、去电）
                callActivityTime = System.currentTimeMillis();
                updateMuteAndSpeak();
                startCallTimer();
                break;
            default:
                break;
        }
    }

/*  Call.State 的所有状态
    public enum State {
        IDLE-挂机, ACTIVE-接通, HOLDING, DIALING-拨号中, ALERTING-振铃中, INCOMING-来电, WAITING-等待, DISCONNECTED-断开连接, DISCONNECTING-正在断开连接中;
    }*/


}

