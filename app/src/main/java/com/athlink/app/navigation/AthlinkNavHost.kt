package com.athlink.app.navigation

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.athlink.app.data.model.UserRole
import com.athlink.app.ui.screens.auth.LoginScreen
import com.athlink.app.ui.screens.auth.SignupScreen
import com.athlink.app.ui.screens.auth.SplashScreen
import com.athlink.app.ui.screens.coach.CoachDashboardScreen
import com.athlink.app.ui.screens.coach.CoachProfileScreen
import com.athlink.app.ui.screens.coach.ManageSessionsScreen
import com.athlink.app.ui.screens.organisation.CreateEventScreen
import com.athlink.app.ui.screens.organisation.OrgDashboardScreen
import com.athlink.app.ui.screens.player.*
import com.athlink.app.viewmodel.AuthViewModel

@Composable
fun AthlinkNavHost() {
    val rootNavController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.state.collectAsState()

    NavHost(navController = rootNavController, startDestination = NavRoutes.SPLASH) {

        // ── Splash ────────────────────────────────────────────────────────────
        composable(NavRoutes.SPLASH) {
            SplashScreen(
                onFinished = {
                    val dest = if (authState.isLoggedIn) {
                        when (authState.user?.role) {
                            UserRole.COACH -> NavRoutes.COACH_NAV
                            UserRole.ORGANISATION -> NavRoutes.ORG_NAV
                            else -> NavRoutes.PLAYER_NAV
                        }
                    } else NavRoutes.LOGIN
                    rootNavController.navigate(dest) {
                        popUpTo(NavRoutes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        // ── Auth ──────────────────────────────────────────────────────────────
        composable(NavRoutes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    val dest = when (authState.user?.role) {
                        UserRole.COACH -> NavRoutes.COACH_NAV
                        UserRole.ORGANISATION -> NavRoutes.ORG_NAV
                        else -> NavRoutes.PLAYER_NAV
                    }
                    rootNavController.navigate(dest) { popUpTo(NavRoutes.LOGIN) { inclusive = true } }
                },
                onNavigateToSignup = { rootNavController.navigate(NavRoutes.SIGNUP) },
                viewModel = authViewModel
            )
        }

        composable(NavRoutes.SIGNUP) {
            SignupScreen(
                onSignupSuccess = {
                    val dest = when (authState.user?.role) {
                        UserRole.COACH -> NavRoutes.COACH_NAV
                        UserRole.ORGANISATION -> NavRoutes.ORG_NAV
                        else -> NavRoutes.PLAYER_NAV
                    }
                    rootNavController.navigate(dest) { popUpTo(NavRoutes.LOGIN) { inclusive = true } }
                },
                onNavigateToLogin = { rootNavController.popBackStack() },
                viewModel = authViewModel
            )
        }

        // ── Player Nav Graph ──────────────────────────────────────────────────
        composable(NavRoutes.PLAYER_NAV) {
            val playerNavController = rememberNavController()
            val user = authState.user ?: return@composable

            NavHost(navController = playerNavController, startDestination = NavRoutes.PLAYER_HOME) {

                composable(NavRoutes.PLAYER_HOME) {
                    PlayerDashboardScreen(
                        user = user,
                        navController = playerNavController,
                        onCoachClick = { coachId -> playerNavController.navigate(NavRoutes.playerBook(coachId)) },
                        onLogout = {
                            authViewModel.logout()
                            rootNavController.navigate(NavRoutes.LOGIN) { popUpTo(0) { inclusive = true } }
                        }
                    )
                }

                composable(NavRoutes.PLAYER_SEARCH) {
                    SearchCoachScreen(
                        onBookCoach = { coach -> playerNavController.navigate(NavRoutes.playerBook(coach.uid)) },
                        onBack = { playerNavController.navigate(NavRoutes.PLAYER_HOME) { launchSingleTop = true } }
                    )
                }

                composable(
                    route = NavRoutes.PLAYER_BOOK,
                    arguments = listOf(navArgument("coachId") { type = NavType.StringType })
                ) { backStack ->
                    val coachId = backStack.arguments?.getString("coachId") ?: ""
                    BookSessionScreen(
                        coachId = coachId,
                        user = user,
                        onBack = { playerNavController.popBackStack() },
                        onBookingConfirmed = {
                            playerNavController.navigate(NavRoutes.PLAYER_HOME) {
                                popUpTo(NavRoutes.PLAYER_HOME) { inclusive = false }
                            }
                        }
                    )
                }

                composable(NavRoutes.PLAYER_CHAT) {
                    PlayerChatListScreen(
                        user = user,
                        onThreadClick = { threadId, receiverName ->
                            playerNavController.navigate(NavRoutes.chatThread(threadId, receiverName))
                        },
                        onBack = { playerNavController.navigate(NavRoutes.PLAYER_HOME) { launchSingleTop = true } }
                    )
                }

                composable(
                    route = NavRoutes.PLAYER_CHAT_THREAD,
                    arguments = listOf(
                        navArgument("threadId") { type = NavType.StringType },
                        navArgument("receiverName") { type = NavType.StringType }
                    )
                ) { backStack ->
                    val threadId = backStack.arguments?.getString("threadId") ?: ""
                    val receiverName = backStack.arguments?.getString("receiverName") ?: "Coach"
                    ChatScreen(
                        threadId = threadId,
                        receiverName = receiverName,
                        user = user,
                        onBack = { playerNavController.popBackStack() }
                    )
                }

                composable(NavRoutes.PLAYER_PROFILE) {
                    PlayerProfileScreen(
                        user = user,
                        onLogout = {
                            authViewModel.logout()
                            rootNavController.navigate(NavRoutes.LOGIN) { popUpTo(0) { inclusive = true } }
                        },
                        onBack = { playerNavController.navigate(NavRoutes.PLAYER_HOME) { launchSingleTop = true } }
                    )
                }
            }
        }

        // ── Coach Nav Graph ───────────────────────────────────────────────────
        composable(NavRoutes.COACH_NAV) {
            val coachNavController = rememberNavController()
            val user = authState.user ?: return@composable

            NavHost(navController = coachNavController, startDestination = NavRoutes.COACH_HOME) {

                composable(NavRoutes.COACH_HOME) {
                    CoachDashboardScreen(
                        user = user,
                        navController = coachNavController,
                        onLogout = {
                            authViewModel.logout()
                            rootNavController.navigate(NavRoutes.LOGIN) { popUpTo(0) { inclusive = true } }
                        }
                    )
                }

                composable(NavRoutes.COACH_SESSIONS) {
                    ManageSessionsScreen(
                        user = user,
                        onBack = { coachNavController.navigate(NavRoutes.COACH_HOME) { launchSingleTop = true } }
                    )
                }

                composable(NavRoutes.COACH_PROFILE) {
                    CoachProfileScreen(
                        user = user,
                        onLogout = {
                            authViewModel.logout()
                            rootNavController.navigate(NavRoutes.LOGIN) { popUpTo(0) { inclusive = true } }
                        },
                        onBack = { coachNavController.navigate(NavRoutes.COACH_HOME) { launchSingleTop = true } }
                    )
                }
            }
        }

        // ── Organisation Nav Graph ────────────────────────────────────────────
        composable(NavRoutes.ORG_NAV) {
            val orgNavController = rememberNavController()
            val user = authState.user ?: return@composable

            NavHost(navController = orgNavController, startDestination = NavRoutes.ORG_HOME) {

                composable(NavRoutes.ORG_HOME) {
                    OrgDashboardScreen(
                        user = user,
                        navController = orgNavController,
                        onLogout = {
                            authViewModel.logout()
                            rootNavController.navigate(NavRoutes.LOGIN) { popUpTo(0) { inclusive = true } }
                        },
                        onCreateEvent = { orgNavController.navigate(NavRoutes.ORG_CREATE_EVENT) }
                    )
                }

                composable(NavRoutes.ORG_CREATE_EVENT) {
                    CreateEventScreen(
                        user = user,
                        onBack = { orgNavController.popBackStack() },
                        onEventCreated = {
                            orgNavController.navigate(NavRoutes.ORG_HOME) {
                                popUpTo(NavRoutes.ORG_HOME) { inclusive = false }
                            }
                        }
                    )
                }

                composable(NavRoutes.ORG_PROFILE) {
                    // Re-use PlayerProfileScreen styled for org
                    PlayerProfileScreen(
                        user = user,
                        onLogout = {
                            authViewModel.logout()
                            rootNavController.navigate(NavRoutes.LOGIN) { popUpTo(0) { inclusive = true } }
                        },
                        onBack = { orgNavController.navigate(NavRoutes.ORG_HOME) { launchSingleTop = true } }
                    )
                }
            }
        }
    }
}
