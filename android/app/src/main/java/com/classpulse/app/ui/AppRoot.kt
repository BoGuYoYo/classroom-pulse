package com.classpulse.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.classpulse.app.data.SessionStatus
import com.classpulse.app.data.model.User
import com.classpulse.app.ui.auth.AuthViewModel
import com.classpulse.app.ui.components.PulseMark
import com.classpulse.app.ui.components.PulsePage
import com.classpulse.app.ui.components.Wordmark
import com.classpulse.app.ui.course.CourseViewModel
import com.classpulse.app.ui.courses.CoursesViewModel
import com.classpulse.app.ui.screens.AuthScreen
import com.classpulse.app.ui.screens.CourseScreen
import com.classpulse.app.ui.screens.HomeScreen
import com.classpulse.app.ui.theme.*

@Composable
fun AppRoot() {
    val appViewModel: AppViewModel = viewModel()
    val session by appViewModel.sessionStatus.collectAsStateWithLifecycle()

    when (val status = session) {
        SessionStatus.Loading -> SplashScreen()
        SessionStatus.LoggedOut -> {
            val authViewModel: AuthViewModel = viewModel()
            AuthScreen(authViewModel)
        }

        is SessionStatus.LoggedIn -> {
            MainNavigation(
                user = status.user,
                onLogout = appViewModel::logout,
            )
        }
    }
}

@Composable
private fun MainNavigation(user: User, onLogout: () -> Unit) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            val coursesViewModel: CoursesViewModel = viewModel()
            HomeScreen(
                user = user,
                viewModel = coursesViewModel,
                onOpenCourse = { courseId -> navController.navigate("course/$courseId") },
                onLogout = onLogout,
            )
        }
        composable("course/{courseId}") { entry ->
            val courseId = entry.arguments?.getString("courseId")?.toLongOrNull() ?: return@composable
            val courseViewModel: CourseViewModel = viewModel(key = "course-$courseId")
            CourseScreen(
                courseId = courseId,
                user = user,
                viewModel = courseViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun SplashScreen() {
    PulsePage {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(Mint.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                PulseMark(size = 58.dp)
            }
            Spacer(Modifier.height(20.dp))
            Wordmark()
            Spacer(Modifier.height(16.dp))
            Text(
                "让每一次课堂，都有迹可循。",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = Teal,
            )
        }
        Text(
            "CLASS PULSE · v1.0",
            style = MaterialTheme.typography.labelSmall,
            color = Muted.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 24.dp),
        )
    }
}
