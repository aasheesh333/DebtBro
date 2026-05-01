package com.dhanuk.debtbro.presentation.screens.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhanuk.debtbro.presentation.theme.BackgroundDark
import com.dhanuk.debtbro.presentation.theme.PrimaryGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onOnboardingComplete: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val pages = listOf(
        Triple("💸", "Track Who Owes You", "Every loan, chai, trip split, and awkward IOU in one clean place."),
        Triple("🤖", "AI Roasts Your Broke Friends", "Generate funny WhatsApp nudges that make payment reminders less painful."),
        Triple("🏆", "Get Paid. Stay Sane.", "See totals, overdue debts, and the leaderboard of financial chaos.")
    )
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    val name by viewModel.userName.collectAsStateWithLifecycle()
    val key by viewModel.groqKey.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(BackgroundDark).padding(24.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (page < 3) {
                    Text(pages[page].first, fontSize = 88.sp)
                    Text(pages[page].second, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                    Text(pages[page].third, color = Color.LightGray, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
                } else {
                    Text("DebtBro", color = PrimaryGreen, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
                    OutlinedTextField(value = name, onValueChange = viewModel::onNameChange, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 30.dp))
                    OutlinedTextField(value = key, onValueChange = viewModel::onGroqKeyChange, label = { Text("Groq API key (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(12.dp)) {
            repeat(4) { index -> Box(Modifier.size(if (pagerState.currentPage == index) 12.dp else 8.dp).background(if (pagerState.currentPage == index) PrimaryGreen else Color.DarkGray, CircleShape)) }
        }
        Button(
            onClick = { if (pagerState.currentPage < 3) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } else viewModel.completeOnboarding(onOnboardingComplete) },
            enabled = pagerState.currentPage < 3 || name.isNotBlank(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (pagerState.currentPage < 3) "Next →" else "Let's Go 🚀", color = BackgroundDark, fontWeight = FontWeight.Bold) }
    }
}
