package io.github.nicechester.gobirdie.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.nicechester.gobirdie.core.data.CourseStore
import io.github.nicechester.gobirdie.core.data.RoundStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideRoundStore(@ApplicationContext ctx: Context) = RoundStore(ctx)

    @Provides
    @Singleton
    fun provideCourseStore(@ApplicationContext ctx: Context) = CourseStore(ctx)
}
