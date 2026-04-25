package io.github.nicechester.gobirdie.ui.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nicechester.gobirdie.sync.SyncManager
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val syncManager: SyncManager,
) : ViewModel()
