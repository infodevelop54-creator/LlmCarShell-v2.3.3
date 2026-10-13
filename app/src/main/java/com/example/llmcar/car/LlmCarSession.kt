package com.example.llmcar.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator

class LlmCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator =
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = LlmCarSession()
}

class LlmCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = LlmCarScreen(carContext)
}

class LlmCarScreen(private val ctx: CarContext) : Screen(ctx) {
    override fun onGetTemplate(): Template {
        val open = Action.Builder()
            .setTitle("Открыть ассистента")
            .setOnClickListener {
                val launchIntent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
                if (launchIntent != null) {
                    ctx.startCarApp(launchIntent)
                }
            }
            .build()
        return MessageTemplate.Builder("LLM Car Shell\nГолосовой ассистент готов")
            .setHeaderAction(Action.APP_ICON)
            .addAction(open)
            .build()
    }
}
