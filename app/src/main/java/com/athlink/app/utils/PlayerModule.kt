package com.athlink.app.utils

import com.athlink.app.data.remote.PlayerDataSource
import com.athlink.app.data.remote.PlayerStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds the Firestore-backed player store; tests construct the repository with a fake instead. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {
    @Binds
    abstract fun bindPlayerStore(impl: PlayerDataSource): PlayerStore
}
