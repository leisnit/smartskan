package com.example.smartskan

import android.app.Application
import com.example.smartskan.data.Repository

class SmartSkanApplication : Application() {
    val repository: Repository by lazy { Repository(this) }
}
