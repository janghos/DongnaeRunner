// RunScreen.kt
package kr.co.dongnae.runner.screen

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
                popUpTo("run/$uid") { inclusive = true }
            }
        }
    )
}

@Composable
fun RunContent(
    user: FirestoreUser?,
    onLogout: () -> Unit
) {
    if (user == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🏃 러닝 기록 화면", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(24.dp))
            Text("이름: ${user.name}", fontWeight = FontWeight.Medium)
            Text("이메일: ${user.email}", fontWeight = FontWeight.Medium)
            Text("지역: ${user.region}", fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onLogout) {
                Text("로그아웃")
            }
        }
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
        onLogout = {}
    )
}