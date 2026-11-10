package com.meshride.di

import com.meshride.data.repository.BluetoothScoRepositoryImpl
import com.meshride.data.repository.IntercomRepositoryImpl
import com.meshride.domain.repository.BluetoothScoRepository
import com.meshride.domain.repository.IntercomRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindIntercomRepository(
        impl: IntercomRepositoryImpl
    ): IntercomRepository

    @Binds
    @Singleton
    abstract fun bindBluetoothScoRepository(
        impl: BluetoothScoRepositoryImpl
    ): BluetoothScoRepository
}
