package com.artem.myarchive

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.artem.myarchive.data.AppDatabase
import com.artem.myarchive.data.FileStorage
import com.artem.myarchive.data.Repository

class App : Application(), ImageLoaderFactory {

    val repository: Repository by lazy {
        Repository(AppDatabase.create(this), FileStorage(this))
    }

    // Coil умеет показывать превью (первый кадр) для видео.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(VideoFrameDecoder.Factory()) }
        .crossfade(true)
        .build()
}

val Context.repository: Repository get() = (applicationContext as App).repository
