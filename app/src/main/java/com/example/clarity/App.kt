package com.example.clarity

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Terminal initialization moved to MainActivity
        // This ensures permissions are granted first
    }
}