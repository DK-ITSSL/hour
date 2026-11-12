package com.hour.car

import android.content.Context

object CarGatewayFactory {
    fun create(context: Context): CarGateways {
        val app = context.applicationContext
        return CarGateways(
            signals = PleosCarSignalSource(app),
            ux = PleosUxRestrictionSource(app),
            voice = PleosVoice(app),
            flavor = "pleos",
        )
    }
}
