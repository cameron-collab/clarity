package com.example.clarity

import android.app.Application
import com.example.clarity.api.payment.BackendConnectionTokenProvider
import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.TerminalApplicationDelegate
import com.stripe.stripeterminal.external.callable.TerminalListener
import com.stripe.stripeterminal.external.models.ConnectionStatus
import com.stripe.stripeterminal.external.models.PaymentStatus
import com.stripe.stripeterminal.log.LogLevel
import com.stripe.stripeterminal.taptopay.TapToPay
import android.util.Log


class StripeTerminalApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (TapToPay.isInTapToPayProcess()) return

        TerminalApplicationDelegate.onCreate(this)

        if (!Terminal.isInitialized()) {
            Terminal.initTerminal(
                this,
                LogLevel.VERBOSE,
                BackendConnectionTokenProvider(),
                object : TerminalListener {
                    override fun onConnectionStatusChange(status: ConnectionStatus) {
                        Log.d("Terminal", "Connection status: $status")
                    }
                    override fun onPaymentStatusChange(status: PaymentStatus) {
                        Log.d("Terminal", "Payment status: $status")
                    }
                }
            )
        }
    }
}
