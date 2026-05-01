package com.dhanuk.debtbro.presentation.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhanuk.debtbro.presentation.theme.OnSurfaceDark
import com.dhanuk.debtbro.presentation.theme.PrimaryGreen
import com.dhanuk.debtbro.presentation.theme.SubtitleGray

data class AppLanguage(
    val code: String,
    val nativeName: String,
    val englishName: String,
    val flag: String
)

val SUPPORTED_LANGUAGES = listOf(
    AppLanguage("en", "English", "English", "🇺🇸"),
    AppLanguage("hi", "हिंदी", "Hindi", "🇮🇳"),
    AppLanguage("es", "Español", "Spanish", "🇪🇸"),
    AppLanguage("pt", "Português", "Portuguese", "🇧🇷"),
    AppLanguage("ar", "العربية", "Arabic", "🇸🇦"),
    AppLanguage("fr", "Français", "French", "🇫🇷"),
    AppLanguage("de", "Deutsch", "German", "🇩🇪"),
    AppLanguage("zh", "中文", "Chinese", "🇨🇳"),
    AppLanguage("ja", "日本語", "Japanese", "🇯🇵"),
    AppLanguage("ko", "한국어", "Korean", "🇰🇷"),
    AppLanguage("ru", "Русский", "Russian", "🇷🇺"),
    AppLanguage("tr", "Türkçe", "Turkish", "🇹🇷"),
    AppLanguage("it", "Italiano", "Italian", "🇮🇹"),
    AppLanguage("id", "Bahasa Indonesia", "Indonesian", "🇮🇩"),
    AppLanguage("bn", "বাংলা", "Bengali", "🇧🇩"),
    AppLanguage("ta", "தமிழ்", "Tamil", "🇮🇳"),
    AppLanguage("te", "తెలుగు", "Telugu", "🇮🇳"),
    AppLanguage("mr", "मराठी", "Marathi", "🇮🇳"),
    AppLanguage("gu", "ગુજરાતી", "Gujarati", "🇮🇳"),
    AppLanguage("pa", "ਪੰਜਾਬੀ", "Punjabi", "🇮🇳"),
    AppLanguage("ur", "اردو", "Urdu", "🇵🇰"),
    AppLanguage("sw", "Kiswahili", "Swahili", "🇰🇪"),
    AppLanguage("nl", "Nederlands", "Dutch", "🇳🇱"),
    AppLanguage("pl", "Polski", "Polish", "🇵🇱"),
    AppLanguage("vi", "Tiếng Việt", "Vietnamese", "🇻🇳")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectorGrid(
    selectedCode: String,
    onLanguageSelected: (AppLanguage) -> Unit
) {
    var query by remember { mutableStateOf("") }
    
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search language...", color = SubtitleGray) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = SubtitleGray) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = PrimaryGreen,
                unfocusedBorderColor = Color(0xFF333333),
                focusedTextColor = OnSurfaceDark,
                unfocusedTextColor = OnSurfaceDark,
                cursorColor = PrimaryGreen
            ),
            shape = RoundedCornerShape(12.dp)
        )
        
        Spacer(Modifier.height(12.dp))
        
        val filtered = SUPPORTED_LANGUAGES.filter {
            query.isEmpty() ||
            it.englishName.contains(query, true) ||
            it.nativeName.contains(query, true)
        }
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(320.dp)
        ) {
            items(filtered) { lang ->
                val isSelected = lang.code == selectedCode
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLanguageSelected(lang) }
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = if (isSelected) PrimaryGreen else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) 
                            PrimaryGreen.copy(alpha = 0.15f) 
                        else Color(0xFF1E1E1E)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(lang.flag, fontSize = 20.sp)
                        Column {
                            Text(
                                lang.nativeName,
                                color = if (isSelected) PrimaryGreen else OnSurfaceDark,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                lang.englishName,
                                color = SubtitleGray,
                                fontSize = 11.sp
                            )
                        }
                        if (isSelected) {
                            Spacer(Modifier.weight(1f))
                            Icon(
                                Icons.Default.CheckCircle,
                                null,
                                tint = PrimaryGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
