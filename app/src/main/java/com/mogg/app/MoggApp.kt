package com.mogg.app

import android.app.Application
import com.mogg.app.data.Store
import com.mogg.app.notify.Reminders

class MoggApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Reminders.createChannel(this)
        Reminders.scheduleAll(this)
    }
}
