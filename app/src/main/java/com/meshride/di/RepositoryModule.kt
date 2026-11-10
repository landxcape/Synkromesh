package com.meshride.di

import com.meshride.data.repository.AudioEngineRepositoryImpl
import com.meshride.data.repository.BluetoothScoRepositoryImpl
import com.meshride.data.repository.IntercomRepositoryImpl
import com.meshride.data.repository.MeshNetworkRepositoryImpl
import com.meshride.domain.repository.AudioEngineRepository
import com.meshride.domain.repository.BluetoothScoRepository
import com.meshride.domain.repository.IntercomRepository
import com.meshride.domain.repository.MeshNetworkRepository
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

    @Binds
    @Singleton
    abstract fun bindAudioEngineRepository(
        impl: AudioEngineRepositoryImpl
    ): AudioEngineRepository

    @Binds
    @Singleton
    abstract fun bindMeshNetworkRepository(
        impl: MeshNetworkRepositoryImpl
    ): MeshNetworkRepository
}
