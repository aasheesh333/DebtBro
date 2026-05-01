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
    
    private val _selectedLanguage = MutableStateFlow("en")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    fun onNameChange(name: String) { _userName.value = name }
    
    fun setLanguage(code: String) {
        _selectedLanguage.value = code
        viewModelScope.launch {
            prefs.setLanguage(code)
        }
    }

    fun completeOnboarding(onDone: () -> Unit) = viewModelScope.launch {
        prefs.setOnboardingComplete(_userName.value)
        onDone()
    }
}
