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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhanuk.debtbro.presentation.theme.LocalExtraColors
import com.dhanuk.debtbro.presentation.theme.PrimaryGreen

data class AppLanguage(
    val code: String,
    val nativeName: String,
    val englishName: String,
    val flag: String
)

val SUPPORTED_LANGUAGES = listOf(
    AppLanguage("en", "English", "English", "\uD83C\uDDFA\uD83C\uDDF8"),
    AppLanguage("hi", "\u0939\u093F\u0902\u0926\u0940", "Hindi", "\uD83C\uDDEE\uD83C\uDDF3"),
    AppLanguage("es", "Espa\u00F1ol", "Spanish", "\uD83C\uDDEA\uD83C\uDDF8"),
    AppLanguage("pt", "Portugu\u00EAs", "Portuguese", "\uD83C\uDDE7\uD83C\uDDF7"),
    AppLanguage("ar", "\u0627\u0644\u0639\u0631\u0628\u064A\u0629", "Arabic", "\uD83C\uDDF8\uD83C\uDDE6"),
    AppLanguage("fr", "Fran\u00E7ais", "French", "\uD83C\uDDEB\uD83C\uDDF7"),
    AppLanguage("de", "Deutsch", "German", "\uD83C\uDDE9\uD83CuDDEA"),
    AppLanguage("zh", "\u4E2D\u6587", "Chinese", "\uD83C\uDDE8\uD83C\uDDF3"),
    AppLanguage("ja", "\u65E5\u672C\u8A9E", "Japanese", "\uD83C\uDEF7\uD83C\uDDF5"),
    AppLanguage("ko", "\uD55C\uAD6D\uC5B4", "Korean", "\uD83C\uDDF0\uD83C\uDDF7"),
    AppLanguage("ru", "\u0420\u0443\u0441\u0441\u043A\u0438\u0439", "Russian", "\uD83C\uDDF7\uD83C\uDDFA"),
    AppLanguage("tr", "T\u00FCrk\u00E7e", "Turkish", "\uD83C\uDDF9\uD83C\uDDF7"),
    AppLanguage("it", "Italiano", "Italian", "\uD83C\uDDEE\uD83C\uDDF9"),
    AppLanguage("id", "Bahasa Indonesia", "Indonesian", "\uD83C\uDDEE\uD83C\uDDE9"),
    AppLanguage("bn", "\u09AC\u09BE\u0982\u09B2\u09BE", "Bengali", "\uD83C\uDDE7\uD83C\uDDF9"),
    AppLanguage("ta", "\u0BA4\u0BAE\u0BBF\u0BB4\u0BCD", "Tamil", "\uD83C\uDDEE\uD83C\uDDF3"),
    AppLanguage("te", "\u0C24\u0C46\u0C32\u0C41\u0C17\u0C41", "Telugu", "\uD83C\uDDEE\uD83C\uDDF3"),
    AppLanguage("mr", "\u092E\u0930\u093E\u0920\u0940", "Marathi", "\uD83C\uDDEE\uD83C\uDDF3"),
    AppLanguage("gu", "\u0A97\u0AC1\u0A9C\u0AB0\u0ABE\u0AA4\u0AC0", "Gujarati", "\uD83C\uDDEE\uD83C\uDDF3"),
    AppLanguage("pa", "\u0A2A\u0A70\u0A1C\u0A3E\u0A2C\u0A40", "Punjabi", "\uD83C\uDDEE\uD83C\uDDF3"),
    AppLanguage("ur", "\u0627\u0631\u062F\u0648", "Urdu", "\uD83C\uDDF5\uD83C\uDDF0"),
    AppLanguage("sw", "Kiswahili", "Swahili", "\uD83C\uDDF0\uD83C\uDDEA"),
    AppLanguage("nl", "Nederlands", "Dutch", "\uD83C\uDDF3\uD83C\uDDF1"),
    AppLanguage("pl", "Polski", "Polish", "\uD83C\uDDF5\uD83C\uDDF1"),
    AppLanguage("vi", "Ti\u1EBFng Vi\u1EC7t", "Vietnamese", "\uD83C\uDDFB\uD83C\uDDF3")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectorGrid(
    selectedCode: String,
    onLanguageSelected: (AppLanguage) -> Unit
) {
    val extra = LocalExtraColors.current
    var query by remember { mutableStateOf("") }
    
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search language...", color = extra.subtitleGray) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = extra.subtitleGray) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryGreen,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
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
                            color = if (isSelected) PrimaryGreen else androidx.compose.ui.graphics.Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) PrimaryGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
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
                                color = if (isSelected) PrimaryGreen else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(lang.englishName, color = extra.subtitleGray, fontSize = 11.sp)
                        }
                        if (isSelected) {
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.CheckCircle, null, tint = PrimaryGreen, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
