package ru.otmetka.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.otmetka.android.ui.AccountScreen
import ru.otmetka.android.ui.AppViewModel
import ru.otmetka.android.ui.BatteryGuideScreen
import ru.otmetka.android.ui.ConsentScreen
import ru.otmetka.android.ui.DayScreen
import ru.otmetka.android.ui.LoginScreen
import ru.otmetka.android.ui.NewPlanScreen
import ru.otmetka.android.ui.OtmetkaTheme
import ru.otmetka.android.ui.PlanSettingsScreen
import ru.otmetka.android.ui.PlansScreen
import ru.otmetka.android.ui.QuestionsScreen
import ru.otmetka.android.ui.Screen

class MainActivity : ComponentActivity() {

    private val app: AppViewModel by viewModels { AppViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openPlanFrom(intent)
        setContent {
            OtmetkaTheme {
                val state by app.state.collectAsStateWithLifecycle()
                val topLevel = state.screen == Screen.Login || state.screen == Screen.Consent || state.screen == Screen.Plans
                BackHandler(enabled = !topLevel) { app.back() }
                Scaffold { innerPadding ->
                    val modifier = Modifier.padding(innerPadding).imePadding()
                    when (val screen = state.screen) {
                        Screen.Login -> LoginScreen(state, app, modifier)
                        Screen.Consent -> ConsentScreen(state, app, modifier)
                        Screen.Plans -> PlansScreen(app, modifier)
                        is Screen.Day -> DayScreen(screen.planId, app, modifier)
                        Screen.BatteryGuide -> BatteryGuideScreen(app, modifier)
                        is Screen.NewPlan -> NewPlanScreen(screen.formId, app, modifier)
                        is Screen.PlanSettings -> PlanSettingsScreen(screen.planId, app, modifier)
                        is Screen.Questions -> QuestionsScreen(screen.planId, screen.draft, app, modifier)
                        Screen.Account -> AccountScreen(app, modifier)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openPlanFrom(intent)
    }

    /** A tap on a reminder opens the day view of its plan. */
    private fun openPlanFrom(intent: Intent) {
        val planId = intent.getStringExtra(EXTRA_PLAN_ID) ?: return
        if (app.state.value.screen == Screen.Plans || app.state.value.screen is Screen.Day) {
            app.open(Screen.Day(planId))
        }
    }

    companion object {
        const val EXTRA_PLAN_ID = "planId"
    }
}
