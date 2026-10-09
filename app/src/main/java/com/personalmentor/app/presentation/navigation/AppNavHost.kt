package com.personalmentor.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.personalmentor.app.presentation.MainViewModel
import com.personalmentor.app.presentation.agent.AgentScreen
import com.personalmentor.app.presentation.agent.ApprovalDialog
import com.personalmentor.app.presentation.agent.ApprovalViewModel
import com.personalmentor.app.presentation.chat.ChatScreen
import com.personalmentor.app.presentation.knowledge.KnowledgeScreen
import com.personalmentor.app.presentation.settings.SettingsScreen
import com.personalmentor.app.presentation.tasks.TasksScreen

object Routes {
    const val CHAT = "chat"
    const val TASKS = "tasks"
    const val KNOWLEDGE = "knowledge"
    const val SETTINGS = "settings"
    const val AGENT = "agent"
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    // Asks the user before the agent does something risky; shown on top of whichever screen is open.
    val approvalViewModel: ApprovalViewModel = hiltViewModel()
    val pendingApproval by approvalViewModel.pending.collectAsStateWithLifecycle()
    pendingApproval?.let { request ->
        ApprovalDialog(
            request = request,
            onApprove = { approvalViewModel.resolve(true) },
            onDecline = { approvalViewModel.resolve(false) },
        )
    }

    NavHost(navController = navController, startDestination = Routes.CHAT) {
        composable(Routes.CHAT) {
            val viewModel: MainViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            ChatScreen(
                uiState = uiState,
                onInputChange = viewModel::onInputChange,
                onSend = viewModel::onSend,
                onModeChange = viewModel::onModeChange,
                onClearChat = viewModel::onClearChat,
                onOpenTasks = { navController.navigate(Routes.TASKS) },
                onOpenKnowledge = { navController.navigate(Routes.KNOWLEDGE) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenAgent = { navController.navigate(Routes.AGENT) },
                onReport = viewModel::onReport,
                onErrorShown = viewModel::onErrorShown,
            )
        }
        composable(Routes.TASKS) {
            TasksScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.KNOWLEDGE) {
            KnowledgeScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.AGENT) {
            AgentScreen(onBack = { navController.popBackStack() })
        }
    }
}
