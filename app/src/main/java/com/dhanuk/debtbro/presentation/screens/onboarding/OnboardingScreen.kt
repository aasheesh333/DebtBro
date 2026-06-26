package com.dhanuk.debtbro.presentation.screens.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhanuk.debtbro.presentation.components.LanguageSelectorGrid
import com.dhanuk.debtbro.presentation.theme.LocalExtraColors
import com.dhanuk.debtbro.presentation.theme.PrimaryGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onOnboardingComplete: () -> Unit) {
    val viewModel: OnboardingViewModel = hiltViewModel()
    val pagerState = rememberPagerState(pageCount = { 5 })
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val extra = LocalExtraColors.current
    val name by viewModel.userName.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp, end = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (pagerState.currentPage in 1..3) {
                    TextButton(onClick = { scope.launch { pagerState.animateScrollToPage(4) } }) {
                        Text("Skip", color = extra.subtitleGray)
                    }
                } else {
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> Page1Welcome(selectedLanguage) { viewModel.setLanguage(it.code) }
                    1 -> Page2Track()
                    2 -> Page3Roasts()
                    3 -> Page4Sync()
                    4 -> Page5Name(name) { viewModel.onNameChange(it) }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    repeat(5) { index ->
                        val isSelected = pagerState.currentPage == index
                        val width by animateDpAsState(targetValue = if (isSelected) 24.dp else 8.dp, label = "dotWidth")
                        Box(
                            Modifier.height(8.dp).width(width).clip(CircleShape)
                                .background(if (isSelected) PrimaryGreen else MaterialTheme.colorScheme.outline)
                        )
                    }
                }

                Button(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (pagerState.currentPage < 4) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        } else {
                            viewModel.completeOnboarding(onOnboardingComplete)
                        }
                    },
                    enabled = pagerState.currentPage < 4 || name.isNotBlank(),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, disabledContainerColor = MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(
                        if (pagerState.currentPage < 4) "Next \u2192" else "Let's Go \uD83D\uDE80",
                        color = if (pagerState.currentPage < 4 || name.isNotBlank()) MaterialTheme.colorScheme.onPrimary else extra.subtitleGray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun Page1Welcome(selectedLanguage: String, onLanguageSelected: (com.dhanuk.debtbro.presentation.components.AppLanguage) -> Unit) {
    val extra = LocalExtraColors.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Text("\uD83D\uDCB8", fontSize = 80.sp)
        Text("DebtBro", color = PrimaryGreen, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
        Text("Money memory with a sense of humor", color = extra.subtitleGray, fontSize = 16.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Text("\uD83C\uDF0D Choose your language", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
        LanguageSelectorGrid(selectedCode = selectedLanguage, onLanguageSelected = onLanguageSelected)
    }
}

@Composable
fun Page2Track() {
    val extra = LocalExtraColors.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("\uD83D\uDCB8", fontSize = 96.sp)
        Spacer(Modifier.height(24.dp))
        Text("Track Who Owes You", color = MaterialTheme.colorScheme.onSurface, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text("Every loan, chai, trip split, and awkward IOU in one clean place.", color = extra.subtitleGray, fontSize = 16.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeaturePill("\uD83D\uDCB0 They Owe Me")
            FeaturePill("\uD83D\uDE05 I Owe Them")
            FeaturePill("\uD83D\uDCCA Analytics")
        }
    }
}

@Composable
fun Page3Roasts() {
    val extra = LocalExtraColors.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("\uD83E\uDD16", fontSize = 96.sp)
        Spacer(Modifier.height(24.dp))
        Text("AI Roasts Your Broke Friends", color = MaterialTheme.colorScheme.onSurface, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                "Hey bro, still waiting on that \u20B9500 for pizza. Unless you're paying me in exposure?",
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(16.dp),
                fontSize = 15.sp
            )
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeaturePill("\uD83D\uDE0A Mild")
            FeaturePill("\uD83D\uDE0F Medium")
            FeaturePill("\uD83D\uDD25 Savage")
        }
    }
}

@Composable
fun Page4Sync() {
    val extra = LocalExtraColors.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("\u2601\uFE0F", fontSize = 96.sp)
        Spacer(Modifier.height(24.dp))
        Text("Never Lose Your Data", color = MaterialTheme.colorScheme.onSurface, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text("Sign in with Google anytime from Settings to sync across all devices.", color = extra.subtitleGray, fontSize = 16.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Add Debt", color = PrimaryGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("\u2192", color = extra.subtitleGray)
            Text("Auto Sync", color = PrimaryGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("\u2192", color = extra.subtitleGray)
            Text("Access Anywhere", color = PrimaryGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun Page5Name(name: String, onNameChange: (String) -> Unit) {
    val extra = LocalExtraColors.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("\uD83D\uDC4B", fontSize = 72.sp)
        Spacer(Modifier.height(24.dp))
        Text("What should we call you?", color = MaterialTheme.colorScheme.onSurface, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 30) onNameChange(it.trimStart()) },
            label = { Text("Your name") },
            placeholder = { Text("e.g. Rahul, Priya...") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, capitalization = KeyboardCapitalization.Words),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryGreen,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { Text("${name.length}/30", color = extra.subtitleGray, fontSize = 11.sp) }
        )
    }
}

@Composable
fun FeaturePill(text: String) {
    val extra = LocalExtraColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
