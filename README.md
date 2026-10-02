# 🏆 Athlink — Sports Community Platform

A full-featured Android application built with **Kotlin**, **Jetpack Compose**, **MVVM**, **Hilt DI**, and **Firebase**.

---

## 📁 Project Structure

```
com.athlink.app/
├── AthlinkApp.kt              ← Hilt @HiltAndroidApp
├── MainActivity.kt            ← Entry point
│
├── data/
│   ├── model/
│   │   ├── User.kt            ← User + UserRole enum
│   │   ├── Coach.kt           ← Coach profile model
│   │   ├── Session.kt         ← Booking / session model
│   │   ├── EventMessage.kt    ← Event, Message, ChatThread
│   │   └── DummyData.kt       ← Sample data for all flows
│   ├── remote/
│   │   ├── FirebaseAuthSource.kt   ← Auth operations
│   │   └── FirestoreSource.kt      ← Firestore CRUD + realtime
│   └── repository/
│       ├── AuthRepository.kt
│       ├── CoachRepository.kt
│       ├── SessionRepository.kt
│       └── EventChatRepository.kt  ← EventRepository + ChatRepository
│
├── viewmodel/
│   ├── AuthViewModel.kt       ← Login / signup / logout state
│   ├── CoachViewModel.kt      ← Coach list, search, filter
│   ├── SessionViewModel.kt    ← Book, load, update sessions
│   ├── EventViewModel.kt      ← Create / load events
│   └── ChatViewModel.kt       ← Real-time messages
│
├── navigation/
│   ├── NavRoutes.kt           ← Route constants + helpers
│   └── AthlinkNavHost.kt      ← Root + nested NavHosts per role
│
├── ui/
│   ├── theme/
│   │   ├── Color.kt           ← Brand palette + gradient stops
│   │   ├── Type.kt            ← Typography scale
│   │   └── Theme.kt           ← Light/Dark MaterialTheme
│   │
│   ├── components/            ← Reusable UI primitives
│   │   ├── PrimaryButton.kt   ← Gradient button + outline variant
│   │   ├── AppTextField.kt    ← Branded OutlinedTextField
│   │   ├── CoachCard.kt       ← Full + compact coach cards
│   │   ├── SessionCard.kt     ← Session with status badge
│   │   └── BottomNavBar.kt    ← Role-specific nav bars
│   │
│   └── screens/
│       ├── auth/
│       │   ├── SplashScreen.kt
│       │   ├── LoginScreen.kt
│       │   └── SignupScreen.kt
│       ├── player/
│       │   ├── PlayerDashboard.kt
│       │   ├── SearchCoachScreen.kt
│       │   ├── BookSessionScreen.kt
│       │   ├── PlayerChatListScreen.kt
│       │   ├── ChatScreen.kt
│       │   └── PlayerProfileScreen.kt
│       ├── coach/
│       │   ├── CoachDashboardScreen.kt
│       │   ├── ManageSessionsScreen.kt
│       │   └── CoachProfileScreen.kt
│       └── organisation/
│           ├── OrgDashboard.kt
│           └── CreateEventScreen.kt
│
└── utils/
    └── AppModule.kt           ← Hilt @Module: Firebase instances
```

---

## 🚀 Setup Instructions

### 1. Firebase Setup
1. Go to [Firebase Console](https://console.firebase.google.com)
2. Create a new project named **Athlink**
3. Add an Android app with package name: `com.athlink.app`
4. Download `google-services.json` → place in `/app/`
5. Enable these Firebase services:
   - **Authentication** → Email/Password provider
   - **Firestore Database** → Start in test mode
   - **Storage** → Start in test mode

### 2. Firestore Security Rules (Development)
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

### 3. Build & Run
```bash
# Open in Android Studio Hedgehog or newer
# Sync Gradle → Run on emulator or device (API 26+)
```

---

## 🎨 Design System

| Token | Value |
|---|---|
| Primary | `#FF6B35` (Athlink Orange) |
| Gradient | `#FF6B35` → `#FF3B86` |
| Dark Base | `#1A1F4E` (Deep Blue) |
| Success | `#00C853` |
| Warning | `#FFB800` |
| Error | `#FF3B30` |

---

## 🧩 Navigation Flow

```
Splash
  └─▶ [if logged in]  → Role Dashboard
  └─▶ [if logged out] → Login
                          └─▶ Signup
                          
After login:
  Player     → PlayerNavGraph
    ├── Home (Dashboard)
    ├── Search Coaches
    ├── Book Session
    ├── Chat (list + thread)
    └── Profile
    
  Coach      → CoachNavGraph
    ├── Home (Dashboard + Earnings)
    ├── Manage Sessions (Accept/Reject)
    └── Profile
    
  Organisation → OrgNavGraph
    ├── Home (Events Dashboard)
    ├── Create Event
    └── Profile
```

---

## 📦 Key Dependencies

| Library | Purpose |
|---|---|
| Jetpack Compose BOM 2024.08 | UI framework |
| Navigation Compose 2.7 | Screen navigation |
| Hilt 2.51 | Dependency injection |
| Firebase Auth / Firestore / Storage | Backend |
| Coil 2.7 | Image loading |
| Accompanist | System UI controller |
| Coroutines + Flow | Async + reactive state |

---

## ✅ Features Implemented

- [x] Splash screen with animation
- [x] Email/password auth (Login + Signup)
- [x] Role selection (Player / Coach / Organisation)
- [x] Role-based navigation graph
- [x] Player dashboard with stats
- [x] Coach search with filters & chips
- [x] Session booking with date picker + time slots
- [x] Session status management (Accept / Reject)
- [x] Real-time chat with message bubbles
- [x] Coach dashboard with earnings overview
- [x] Organisation event creation & listing
- [x] Profile screens for all roles
- [x] Material 3 theming (Light + Dark)
- [x] Reusable component library
- [x] Firebase fallback to dummy data

---

## 🔧 Extending the Project

### Add a new screen
1. Create `ui/screens/<role>/MyNewScreen.kt`
2. Add route constant in `NavRoutes.kt`
3. Add `composable(NavRoutes.MY_ROUTE)` in `AthlinkNavHost.kt`
4. Add ViewModel state in `viewmodel/` if needed

### Add Firestore collection
1. Add model in `data/model/`
2. Add CRUD methods in `FirestoreSource.kt`
3. Add repository in `data/repository/`
4. Inject repository in ViewModel
