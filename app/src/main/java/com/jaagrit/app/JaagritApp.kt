package com.jaagrit.app

import android.app.Application
import android.util.Log

class JaagritApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Jaagrit application initialized")
    }

    companion object {
        const val TAG = "JAAGRIT"
    }
}
