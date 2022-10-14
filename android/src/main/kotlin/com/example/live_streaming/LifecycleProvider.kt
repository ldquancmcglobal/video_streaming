package com.example.live_streaming

import androidx.annotation.Nullable
import androidx.lifecycle.Lifecycle

interface LifecycleProvider{
    @Nullable
    fun getLifecycle(): Lifecycle?
}