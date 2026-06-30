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
import com.dhanuk.debtbro.presentation.theme.UITokens
import com.dhanuk.debtbro.util.LocalizedString
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
                        Text(LocalizedString.get("skip"), color = extra.subtitleGray)
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
                modifier = Modifier.fillMaxWidth().padding(UITokens.SheetContentPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(UITokens.SpaceXS),
                    modifier = Modifier.padding(bottom = UITokens.SheetBottomPadding)
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
                        if (pagerState.currentPage < 4) LocalizedString.get("next_arrow") else LocalizedString.get("lets_go"),
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
        Text(LocalizedString.get("app_tagline"), color = extra.subtitleGray, fontSize = 16.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Text(LocalizedString.get("choose_language"), color = MaterialTheme.colorScheme.onSurface, fontSize = UITokens.FontSubhead, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = UITokens.CardInnerPadding))
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
        Text(LocalizedString.get("track_who_owes"), color = MaterialTheme.colorScheme.onSurface, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(LocalizedString.get("track_desc"), color = extra.subtitleGray, fontSize = 16.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeaturePill(LocalizedString.get("pill_they_owe"))
            FeaturePill(LocalizedString.get("pill_i_owe"))
            FeaturePill(LocalizedString.get("pill_analytics"))
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
        Text(LocalizedString.get("ai_roasts_title"), color = MaterialTheme.colorScheme.onSurface, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = UITokens.ShapeLarge
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
            FeaturePill(LocalizedString.get("mild"))
            FeaturePill(LocalizedString.get("medium"))
            FeaturePill(LocalizedString.get("savage"))
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
        Text(LocalizedString.get("never_lose_data"), color = MaterialTheme.colorScheme.onSurface, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(LocalizedString.get("sync_settings_desc"), color = extra.subtitleGray, fontSize = 16.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(LocalizedString.get("add_debt"), color = PrimaryGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("\u2192", color = extra.subtitleGray)
            Text(LocalizedString.get("auto_sync"), color = PrimaryGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("\u2192", color = extra.subtitleGray)
            Text(LocalizedString.get("access_anywhere"), color = PrimaryGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
        Text(LocalizedString.get("what_call_you"), color = MaterialTheme.colorScheme.onSurface, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 30) onNameChange(it.trimStart()) },
            label = { Text(LocalizedString.get("your_name_label")) },
            placeholder = { Text(LocalizedString.get("name_example")) },
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
            .clip(UITokens.ShapeLarge)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = UITokens.FontSmall, fontWeight = FontWeight.SemiBold)
    }
}
