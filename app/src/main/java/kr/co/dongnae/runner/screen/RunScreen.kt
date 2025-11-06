// RunScreen.kt
package kr.co.dongnae.runner.screen

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.firebase.Timestamp
import kr.co.dongnae.runner.model.FirestoreUser
import kr.co.dongnae.runner.viewModel.RunViewModel

@Composable
fun RunScreen(
    navController: NavController,
    uid: String,
    viewModel: RunViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val user by viewModel.user.collectAsState()

    LaunchedEffect(uid) {
        viewModel.loadUser(uid)
    }

    RunContent(
        user = user,
        onLogout = {
            viewModel.logout(activity)
            navController.navigate("login") {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
                restoreState = false
            }
        },
        onRunningStart = {
            navController.navigate("running/$uid")
        },
        onRunningRecord = {
            navController.navigate("records/$uid")
        }
    )
}

@Composable
fun RunContent(
    user: FirestoreUser?,
    onLogout: () -> Unit,
    onRunningStart: () -> Unit,
    onRunningRecord: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val gradient = remember(colorScheme) {
        Brush.verticalGradient(
            colors = listOf(
                colorScheme.background,
                colorScheme.surfaceVariant,
                colorScheme.background
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        if (user == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colorScheme.primary)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Welcome back",
                        style = MaterialTheme.typography.labelLarge,
                        color = colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = user.name,
                        style = MaterialTheme.typography.displayMedium,
                        color = colorScheme.secondary
                    )
                    Text(
                        text = "오늘도 달려볼까요?",
                        style = MaterialTheme.typography.headlineMedium,
                        color = colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    color = colorScheme.surfaceVariant,
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        InfoRow(label = "이메일", value = user.email)
                        InfoRow(label = "활동 지역", value = user.region)
                        user.last_login?.let {
                            InfoRow(label = "마지막 로그인", value = it.toDate().toString())
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = colorScheme.surfaceVariant,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "러닝 메뉴",
                            style = MaterialTheme.typography.titleLarge,
                            color = colorScheme.onBackground
                        )
                        RunnerButton(text = "러닝 시작", onClick = onRunningStart)
                        RunnerButton(text = "러닝 기록", onClick = onRunningRecord)
                        RunnerButton(text = "로그아웃", onClick = onLogout)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RunScreenPreview() {
    val fakeUser = FirestoreUser(
        uid = "dummyUid",
        name = "홍길동",
        email = "hong@example.com",
        region = "서울",
        created_at = Timestamp.now(),
        last_login = Timestamp.now()
    )
    RunContent(
        user = fakeUser,
        onLogout = {},
        onRunningStart = {},
        onRunningRecord = {},
    )
}