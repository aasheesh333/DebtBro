package com.dhanuk.debtbro.presentation.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.debtbro.data.datastore.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val prefs: AppPreferences) : ViewModel() {
    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()
    private val _groqKey = MutableStateFlow("")
    val groqKey: StateFlow<String> = _groqKey.asStateFlow()
    fun onNameChange(name: String) { _userName.value = name }
    fun onGroqKeyChange(key: String) { _groqKey.value = key }
    fun completeOnboarding(onDone: () -> Unit) = viewModelScope.launch {
        prefs.setOnboardingComplete(_userName.value)
        if (_groqKey.value.isNotBlank()) prefs.saveGroqKey(_groqKey.value)
        onDone()
    }
}
