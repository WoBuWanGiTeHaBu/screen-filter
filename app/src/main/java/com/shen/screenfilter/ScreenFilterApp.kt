package com.shen.screenfilter

import android.app.Application
import com.shen.screenfilter.data.FilterRepository

class ScreenFilterApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FilterRepository.init(this)
    }
}
