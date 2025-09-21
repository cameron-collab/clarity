package com.example.clarity

import android.app.Application
import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.log.LogLevel
import com.example.clarity.api.payment.BackendConnectionTokenProvider

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        if (!Terminal.isInitialized()) {
            Terminal.initTerminal(
                this,
                LogLevel.VERBOSE,
                BackendConnectionTokenProvider(),   // talks to /terminal/connection_token
                object : com.stripe.stripeterminal.external.callable.TerminalListener {}
            )
        }
    }
}
