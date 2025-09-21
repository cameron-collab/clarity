package com.example.clarity.api.payment

import com.example.clarity.api.RetrofitProvider
import com.example.clarity.api.GlobalfacesApi.ConnectionTokenOut
import com.stripe.stripeterminal.external.callable.ConnectionTokenCallback
import com.stripe.stripeterminal.external.callable.ConnectionTokenProvider
import com.stripe.stripeterminal.external.models.ConnectionTokenException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BackendConnectionTokenProvider : ConnectionTokenProvider {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun fetchConnectionToken(callback: ConnectionTokenCallback) {
        scope.launch {
            try {
                val resp: ConnectionTokenOut =
                    RetrofitProvider.api.createTerminalConnectionToken()
                callback.onSuccess(resp.secret)
            } catch (t: Throwable) {
                // Stripe requires ConnectionTokenException
                callback.onFailure(ConnectionTokenException(t.toString()))
            }
        }
    }
}
