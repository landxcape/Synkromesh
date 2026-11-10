package com.landxcape.synkromesh.di

import com.landxcape.synkromesh.data.repository.AudioEngineRepositoryImpl
import com.landxcape.synkromesh.data.repository.BluetoothScoRepositoryImpl
import com.landxcape.synkromesh.data.repository.IntercomRepositoryImpl
import com.landxcape.synkromesh.data.repository.MeshNetworkRepositoryImpl
import com.landxcape.synkromesh.domain.repository.AudioEngineRepository
import com.landxcape.synkromesh.domain.repository.BluetoothScoRepository
import com.landxcape.synkromesh.domain.repository.IntercomRepository
import com.landxcape.synkromesh.domain.repository.MeshNetworkRepository
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
