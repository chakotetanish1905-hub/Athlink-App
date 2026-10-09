package com.athlink.app.navigation

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.athlink.app.data.model.OrganisationLanding
import com.athlink.app.data.model.PlayerLanding
import com.athlink.app.data.model.UserRole
import com.athlink.app.ui.screens.auth.LoginScreen
import com.athlink.app.ui.screens.auth.SignupScreen
import com.athlink.app.ui.screens.auth.SplashScreen
import com.athlink.app.ui.screens.coach.CoachDashboardScreen
import com.athlink.app.ui.screens.coach.CoachProfileScreen
import com.athlink.app.ui.screens.coach.ManageSessionsScreen
import com.athlink.app.ui.screens.organisation.CreateEventScreen
import com.athlink.app.ui.screens.organisation.OrgDashboardScreen
import com.athlink.app.ui.screens.organisation.OrgEventsScreen
import com.athlink.app.ui.screens.organisation.OrgGateScreen
import com.athlink.app.ui.screens.organisation.OrgProfileScreen
import com.athlink.app.ui.screens.organisation.OrgRequestsScreen
import com.athlink.app.ui.screens.organisation.OrganisationOnboardingScreen
import com.athlink.app.ui.screens.organisation.OrganisationVerificationStatusScreen
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
            // Wait for BOTH the splash animation and the stored-session check, so a signed-in user
            // on a slow network isn't sent to Login (or into a role graph with no user yet).
            var splashDone by remember { mutableStateOf(false) }
            SplashScreen(onFinished = { splashDone = true })
            LaunchedEffect(splashDone, authState.isCheckingSession, authState.isLoggedIn) {
                if (!splashDone || authState.isCheckingSession) return@LaunchedEffect
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
        // Every player enters through PLAYER_GATE, which sends players whose profile is not COMPLETE
        // (new signups, players who left onboarding midway, accounts from before onboarding) into
        // onboarding, and everyone else to the dashboard.
        composable(NavRoutes.PLAYER_NAV) {
            val playerNavController = rememberNavController()
            val user = authState.user ?: return@composable
            val playerLogout: () -> Unit = {
                authViewModel.logout()
                rootNavController.navigate(NavRoutes.LOGIN) { popUpTo(0) { inclusive = true } }
            }

            NavHost(navController = playerNavController, startDestination = NavRoutes.PLAYER_GATE) {

                composable(NavRoutes.PLAYER_GATE) {
                    PlayerGateScreen(
                        user = user,
                        onRoute = { landing, freshUser ->
                            freshUser?.let { authViewModel.onUserUpdated(it) }
                            val dest = if (landing == PlayerLanding.DASHBOARD) NavRoutes.PLAYER_HOME else NavRoutes.PLAYER_ONBOARDING
                            playerNavController.navigate(dest) { popUpTo(NavRoutes.PLAYER_GATE) { inclusive = true } }
                        },
                        onLogout = playerLogout
                    )
                }

                composable(NavRoutes.PLAYER_ONBOARDING) {
                    PlayerOnboardingScreen(
                        user = user,
                        onCompleted = { updated ->
                            authViewModel.onUserUpdated(updated)
                            playerNavController.navigate(NavRoutes.PLAYER_HOME) {
                                popUpTo(NavRoutes.PLAYER_ONBOARDING) { inclusive = true }
                            }
                        },
                        onLogout = playerLogout
                    )
                }

                composable(NavRoutes.PLAYER_EDIT_PROFILE) {
                    PlayerEditProfileScreen(
                        user = user,
                        onSaved = { updated ->
                            authViewModel.onUserUpdated(updated)
                            playerNavController.popBackStack()
                        },
                        onBack = { playerNavController.popBackStack() }
                    )
                }

                composable(NavRoutes.PLAYER_HOME) {
                    PlayerDashboardScreen(
                        user = user,
                        navController = playerNavController,
                        onCoachClick = { coachId -> playerNavController.navigate(NavRoutes.playerBook(coachId)) },
                        onOpenBookings = { playerNavController.navigate(NavRoutes.PLAYER_BOOKINGS) { launchSingleTop = true } },
                        onOpenAcademies = { playerNavController.navigate(NavRoutes.PLAYER_ACADEMIES) { launchSingleTop = true } },
                        onOpenAcademy = { id -> playerNavController.navigate(NavRoutes.playerAcademy(id)) },
                        onLogout = {
                            authViewModel.logout()
                            rootNavController.navigate(NavRoutes.LOGIN) { popUpTo(0) { inclusive = true } }
                        }
                    )
                }

                composable(NavRoutes.PLAYER_SEARCH) {
                    SearchCoachScreen(
                        onBookCoach = { coach -> playerNavController.navigate(NavRoutes.playerBook(coach.uid)) },
                        onOpenAcademies = { playerNavController.navigate(NavRoutes.PLAYER_ACADEMIES) { launchSingleTop = true } },
                        onBack = { playerNavController.navigate(NavRoutes.PLAYER_HOME) { launchSingleTop = true } }
                    )
                }

                composable(NavRoutes.PLAYER_ACADEMIES) {
                    AcademiesScreen(
                        user = user,
                        navController = playerNavController,
                        onOpenAcademy = { id -> playerNavController.navigate(NavRoutes.playerAcademy(id)) }
                    )
                }

                composable(
                    route = NavRoutes.PLAYER_ACADEMY,
                    arguments = listOf(navArgument("academyId") { type = NavType.StringType })
                ) { backStack ->
                    AcademyDetailScreen(
                        academyId = backStack.arguments?.getString("academyId") ?: "",
                        user = user,
                        onBack = { playerNavController.popBackStack() },
                        onOpenBookings = { playerNavController.navigate(NavRoutes.PLAYER_BOOKINGS) { launchSingleTop = true } }
                    )
                }

                composable(NavRoutes.PLAYER_BOOKINGS) {
                    PlayerBookingsScreen(
                        user = user,
                        onBack = { playerNavController.popBackStack() },
                        onFindCoach = { playerNavController.navigate(NavRoutes.PLAYER_SEARCH) { launchSingleTop = true } },
                        onFindAcademy = { playerNavController.navigate(NavRoutes.PLAYER_ACADEMIES) { launchSingleTop = true } }
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
                            playerNavController.navigate(NavRoutes.PLAYER_BOOKINGS) {
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
                        onLogout = playerLogout,
                        onBack = { playerNavController.navigate(NavRoutes.PLAYER_HOME) { launchSingleTop = true } },
                        onEditProfile = { playerNavController.navigate(NavRoutes.PLAYER_EDIT_PROFILE) { launchSingleTop = true } }
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
        // ROLE ≠ VERIFICATION: every organisation enters through ORG_GATE, which routes by the
        // verification status stored on organisations/{uid} (never by the role alone).
        composable(NavRoutes.ORG_NAV) {
            val orgNavController = rememberNavController()
            val user = authState.user ?: return@composable
            val logout: () -> Unit = {
                authViewModel.logout()
                rootNavController.navigate(NavRoutes.LOGIN) { popUpTo(0) { inclusive = true } }
            }
            fun goHome() = orgNavController.navigate(NavRoutes.ORG_HOME) {
                popUpTo(orgNavController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
            val openVerification: (Boolean) -> Unit = { editable ->
                orgNavController.navigate(if (editable) NavRoutes.ORG_ONBOARDING else NavRoutes.ORG_VERIFICATION_STATUS) { launchSingleTop = true }
            }

            NavHost(navController = orgNavController, startDestination = NavRoutes.ORG_GATE) {

                composable(NavRoutes.ORG_GATE) {
                    OrgGateScreen(user = user, onRoute = { landing ->
                        val dest = when (landing) {
                            OrganisationLanding.ONBOARDING -> NavRoutes.ORG_ONBOARDING
                            OrganisationLanding.STATUS -> NavRoutes.ORG_VERIFICATION_STATUS
                            OrganisationLanding.DASHBOARD -> NavRoutes.ORG_HOME
                        }
                        // The dashboard is always underneath, so Back from onboarding/status goes home.
                        orgNavController.navigate(NavRoutes.ORG_HOME) { popUpTo(NavRoutes.ORG_GATE) { inclusive = true } }
                        if (dest != NavRoutes.ORG_HOME) orgNavController.navigate(dest)
                    })
                }

                composable(NavRoutes.ORG_ONBOARDING) {
                    OrganisationOnboardingScreen(
                        user = user,
                        onExit = { goHome() },
                        onSubmitted = {
                            orgNavController.navigate(NavRoutes.ORG_VERIFICATION_STATUS) {
                                popUpTo(NavRoutes.ORG_HOME) { inclusive = false }
                            }
                        }
                    )
                }

                composable(NavRoutes.ORG_VERIFICATION_STATUS) {
                    OrganisationVerificationStatusScreen(
                        user = user,
                        onEditVerification = { orgNavController.navigate(NavRoutes.ORG_ONBOARDING) { launchSingleTop = true } },
                        onOpenDashboard = { goHome() },
                        onLogout = logout
                    )
                }

                composable(NavRoutes.ORG_HOME) {
                    OrgDashboardScreen(
                        user = user,
                        navController = orgNavController,
                        onLogout = logout,
                        onCreateEvent = { orgNavController.navigate(NavRoutes.ORG_CREATE_EVENT) },
                        onOpenVerification = openVerification,
                        onOpenRequests = { orgNavController.navigate(NavRoutes.ORG_REQUESTS) { launchSingleTop = true } }
                    )
                }

                composable(NavRoutes.ORG_REQUESTS) {
                    OrgRequestsScreen(user = user, onBack = { orgNavController.popBackStack() })
                }

                composable(NavRoutes.ORG_EVENTS) {
                    OrgEventsScreen(
                        user = user,
                        navController = orgNavController,
                        onCreateEvent = { orgNavController.navigate(NavRoutes.ORG_CREATE_EVENT) },
                        onEditDraft = { id -> orgNavController.navigate(NavRoutes.orgEditDraft(id)) }
                    )
                }

                composable(NavRoutes.ORG_CREATE_EVENT) {
                    CreateEventScreen(
                        user = user,
                        onBack = { orgNavController.popBackStack() },
                        onEventCreated = { orgNavController.popBackStack() },
                        onOpenVerification = { openVerification(true) }
                    )
                }

                composable(
                    route = NavRoutes.ORG_EDIT_DRAFT,
                    arguments = listOf(navArgument("draftId") { type = NavType.StringType })
                ) { backStack ->
                    CreateEventScreen(
                        user = user,
                        draftId = backStack.arguments?.getString("draftId"),
                        onBack = { orgNavController.popBackStack() },
                        onEventCreated = { orgNavController.popBackStack() },
                        onOpenVerification = { openVerification(true) }
                    )
                }

                composable(NavRoutes.ORG_PROFILE) {
                    OrgProfileScreen(
                        user = user,
                        onBack = { orgNavController.navigate(NavRoutes.ORG_HOME) { launchSingleTop = true } },
                        onLogout = logout,
                        onOpenVerification = openVerification
                    )
                }
            }
        }
    }
}
