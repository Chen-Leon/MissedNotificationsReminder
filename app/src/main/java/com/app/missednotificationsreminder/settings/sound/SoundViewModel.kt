package com.app.missednotificationsreminder.settings.sound

import android.Manifest
import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.app.missednotificationsreminder.binding.model.BaseViewModel
import com.app.missednotificationsreminder.binding.util.bindWithPreferences
import com.app.missednotificationsreminder.di.qualifiers.ForApplication
import com.app.missednotificationsreminder.di.qualifiers.ReminderRingtone
import com.f2prateek.rx.preferences.Preference
import com.tbruyelle.rxpermissions.RxPermissions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * The view model for the sound view
 */
@FlowPreview
@ExperimentalCoroutinesApi
class SoundViewModel @Inject constructor(
        @param:ReminderRingtone private val ringtone: Preference<String>,
        @param:ForApplication private val context: Context) : BaseViewModel() {

    private val _viewState = MutableStateFlow(SoundViewState(
            ringtone = "",
            ringtoneName = ""))

    val viewState: StateFlow<SoundViewState> = _viewState

    init {
        Timber.d("init")
        viewModelScope.launch {
            launch {
                _viewState.bindWithPreferences(ringtone,
                        { newValue, vs ->
                            vs.copy(ringtone = newValue)
                        },
                        { it.ringtone })
            }
        }
        viewState
                .onEach { viewState ->
                    if (RxPermissions.getInstance(context).isGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                        val ringtoneName = viewState.ringtone.takeIf { it.isNotEmpty() }
                                ?.let { Uri.parse(it) }
                                ?.let { RingtoneManager.getRingtone(context, it) }
                                ?.getTitle(context)
                                ?: ""
                        process(SoundViewStatePartialChanges.RingtoneTitleChange(ringtoneName))
                    }
                }
                .launchIn(viewModelScope)
    }


    fun process(event: SoundViewStatePartialChanges) {
        Timber.d("process() called: with event=${event}")
        viewModelScope.launch {
            _viewState.apply { value = event.reduce(value) }
        }
    }
}