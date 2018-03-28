package com.transage.hall;

import android.app.Application;
import android.content.Intent;

import com.transage.hall.service.HallService;

/**
 * Created by dongrp on 2017/2/27.
 */

public class HallApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Intent intent = new Intent(this, HallService.class);
        startService(intent);
    }
}
