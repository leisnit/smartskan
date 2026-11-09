package com.example.smartskan

import android.app.Application
import com.example.smartskan.ads.Ads
import com.example.smartskan.data.AppSettings
import com.example.smartskan.data.Purchases
import com.example.smartskan.data.Repository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class SmartSkanApplication : Application() {
    val repository: Repository by lazy { Repository(this) }
    val settings: AppSettings by lazy { AppSettings(this) }
    val purchases: Purchases by lazy { Purchases(this) }
    val ads: Ads by lazy { Ads(this) }

    override fun onCreate() {
        super.onCreate()
        // Required once before PDFBox is used (loads its font/glyph resources).
        PDFBoxResourceLoader.init(applicationContext)
    }
}
