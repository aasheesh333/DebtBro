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
import com.dhanuk.debtbro.presentation.theme.BackgroundDark
import com.dhanuk.debtbro.presentation.theme.PrimaryGreen
import com.dhanuk.debtbro.presentation.theme.SubtitleGray
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onOnboardingComplete: () -> Unit) {
    val viewModel: OnboardingViewModel = hiltViewModel()
    val pagerState = rememberPagerState(pageCount = { 5 })
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val name by viewModel.userName.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Skip button top-right for pages 2, 3, 4
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp, end = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (pagerState.currentPage in 1..3) {
                    TextButton(
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(4) }
                        }
                    ) {
                        Text("Skip", color = SubtitleGray)
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

            // Bottom section: dots and button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Animated dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    repeat(5) { index ->
                        val isSelected = pagerState.currentPage == index
                        val width by animateDpAsState(targetValue = if (isSelected) 24.dp else 8.dp, label = "dotWidth")
                        Box(
                            Modifier
                                .height(8.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(if (isSelected) PrimaryGreen else Color.DarkGray)
                        )
                    }
                }

                // Action button
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
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, disabledContainerColor = Color(0xFF333333)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        if (pagerState.currentPage < 4) "Next →" else "Let's Go 🚀",
                        color = if (pagerState.currentPage < 4 || name.isNotBlank()) Color.Black else SubtitleGray,
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
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Text("💸", fontSize = 80.sp)
        Text(
            "DebtBro",
            color = PrimaryGreen,
            fontSize = 48.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            "Money memory with a sense of humor",
            color = SubtitleGray,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Text(
            "🌍 Choose your language",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        LanguageSelectorGrid(
            selectedCode = selectedLanguage,
            onLanguageSelected = onLanguageSelected
        )
    }
}

@Composable
fun Page2Track() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("💸", fontSize = 96.sp)
        Spacer(Modifier.height(24.dp))
        Text(
            "Track Who Owes You",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Every loan, chai, trip split, and awkward IOU in one clean place.",
            color = SubtitleGray,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeaturePill("💰 They Owe Me")
            FeaturePill("😅 I Owe Them")
            FeaturePill("📊 Analytics")
        }
    }
}

@Composable
fun Page3Roasts() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🤖", fontSize = 96.sp)
        Spacer(Modifier.height(24.dp))
        Text(
            "AI Roasts Your Broke Friends",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                "Hey bro, still waiting on that ₹500 for pizza. Unless you're paying me in exposure?",
                color = Color.White,
                modifier = Modifier.padding(16.dp),
                fontSize = 15.sp
            )
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeaturePill("😊 Mild")
            FeaturePill("😏 Medium")
            FeaturePill("🔥 Savage")
        }
    }
}

@Composable
fun Page4Sync() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("☁️", fontSize = 96.sp)
        Spacer(Modifier.height(24.dp))
        Text(
            "Never Lose Your Data",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Sign in with Google anytime from Settings to sync across all devices.",
            color = SubtitleGray,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Add Debt", color = PrimaryGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("→", color = SubtitleGray)
            Text("Auto Sync", color = PrimaryGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("→", color = SubtitleGray)
            Text("Access Anywhere", color = PrimaryGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun Page5Name(name: String, onNameChange: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("👋", fontSize = 72.sp)
        Spacer(Modifier.height(24.dp))
        Text(
            "What should we call you?",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { 
                if (it.length <= 30) {
                    onNameChange(it.trimStart())
                }
            },
            label = { Text("Your name") },
            placeholder = { Text("e.g. Rahul, Priya...") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Words
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryGreen,
                unfocusedBorderColor = Color(0xFF333333),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                Text("${name.length}/30", color = SubtitleGray, fontSize = 11.sp)
            }
        )
    }
}

@Composable
fun FeaturePill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF2A2A2A))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
