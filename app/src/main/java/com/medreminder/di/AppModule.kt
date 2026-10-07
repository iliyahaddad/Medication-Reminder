package com.medreminder.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.medreminder.data.local.db.DoseLogDao
import com.medreminder.data.local.db.MedReminderDatabase
import com.medreminder.data.local.db.MedicationDao
import com.medreminder.data.local.migration.MIGRATION_1_2
import com.medreminder.data.repository.DoseLogRepositoryImpl
import com.medreminder.data.repository.MedicationRepositoryImpl
import com.medreminder.data.repository.SettingsRepositoryImpl
import com.medreminder.domain.repository.DoseLogRepository
import com.medreminder.domain.repository.MedicationRepository
import com.medreminder.domain.repository.SettingsRepository
import com.medreminder.domain.usecase.ScheduleAlarmsPort
import com.medreminder.scheduler.AlarmScheduler
import com.medreminder.util.Clock
import com.medreminder.util.SystemClock
import com.medreminder.util.SystemTimeZoneProvider
import com.medreminder.util.TimeZoneProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds @Singleton
    abstract fun bindMedicationRepository(impl: MedicationRepositoryImpl): MedicationRepository

    @Binds @Singleton
    abstract fun bindDoseLogRepository(impl: DoseLogRepositoryImpl): DoseLogRepository

    @Binds @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds @Singleton
    abstract fun bindScheduleAlarms(impl: AlarmScheduler): ScheduleAlarmsPort

    @Binds @Singleton
    abstract fun bindClock(impl: SystemClock): Clock

    @Binds @Singleton
    abstract fun bindTimeZoneProvider(impl: SystemTimeZoneProvider): TimeZoneProvider
}

@Module
@InstallIn(SingletonComponent::class)
object ProvidersModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MedReminderDatabase =
        Room.databaseBuilder(context, MedReminderDatabase::class.java, "medreminder.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides fun provideMedicationDao(db: MedReminderDatabase): MedicationDao = db.medicationDao()
    @Provides fun provideDoseLogDao(db: MedReminderDatabase): DoseLogDao = db.doseLogDao()

    @Provides @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore
}


private val Context.settingsDataStore: DataStore<Preferences> by
    androidx.datastore.preferences.preferencesDataStore(name = "settings")

