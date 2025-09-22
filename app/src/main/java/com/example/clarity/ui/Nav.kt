package com.example.clarity.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.clarity.ui.campaign.CampaignScreen
import com.example.clarity.ui.comms.CommsScreen
import com.example.clarity.ui.done.DoneScreen
import com.example.clarity.ui.donor.DonorInfoScreen
import com.example.clarity.ui.gift.GiftScreen
import com.example.clarity.ui.login.LoginScreen
import com.example.clarity.ui.payment.PaymentScreen
import com.example.clarity.ui.signature.SignatureScreen
import com.example.clarity.ui.verify.SmsVerifyScreen
import com.example.clarity.data.SessionStore

object Route {
    const val Login = "login"
    const val Campaign = "campaign/{session}/{fundraiser}"
    fun campaign(session: String, fundraiser: String) = "campaign/$session/$fundraiser"
    const val Donor = "donor/{session}/{fundraiser}"
    fun donor(session: String, fundraiser: String) = "donor/$session/$fundraiser"
    const val Gift = "gift/{session}/{donor}"
    fun gift(session: String, donor: String) = "gift/$session/$donor"
    const val Verify = "verify/{session}/{donor}"
    fun verify(session: String, donor: String) = "verify/$session/$donor"
    const val Pay = "pay/{session}/{donor}"
    fun pay(session: String, donor: String) = "pay/$session/$donor"
    const val Comms = "comms/{session}/{donor}"
    fun comms(session: String, donor: String) = "comms/$session/$donor"
    const val Sign = "sign/{session}/{donor}"
    fun sign(session: String, donor: String) = "sign/$session/$donor"
    const val Done = "done"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Route.Login) {

        composable(Route.Login) {
            LoginScreen(
                onLoggedIn = { session, fundraiser ->
                    nav.navigate(Route.campaign(session, fundraiser))
                }
            )
        }

// Update the Campaign composable call in AppNav():

        composable(Route.Campaign,
            arguments = listOf(
                navArgument("session"){ type = NavType.StringType },
                navArgument("fundraiser"){ type = NavType.StringType }
            )
        ) { back ->
            val s = back.arguments!!.getString("session")!!
            val f = back.arguments!!.getString("fundraiser")!!
            CampaignScreen(
                sessionId = s,
                fundraiserId = f,
                onStartDonation = { nav.navigate(Route.donor(s, f)) },
                onLogout = {
                    // Clear session data
                    SessionStore.resetForNextDonor()
                    // Clear session/fundraiser data too
                    SessionStore.sessionId = null
                    SessionStore.fundraiserId = null
                    SessionStore.fundraiserDisplayName = null
                    SessionStore.charityId = null
                    SessionStore.charityName = "Your Charity"
                    SessionStore.charityLogoUrl = null
                    SessionStore.charityBlurb = null
                    SessionStore.brandPrimaryHex = null
                    SessionStore.campaign = null

                    // Navigate back to login and clear the back stack
                    nav.navigate(Route.Login) {
                        popUpTo(0) { inclusive = true }  // Clear entire back stack
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            Route.Donor,
            arguments = listOf(
                navArgument("session"){ type = NavType.StringType },
                navArgument("fundraiser"){ type = NavType.StringType }
            )
        ) { back ->
            val s = back.arguments!!.getString("session")!!
            val f = back.arguments!!.getString("fundraiser")!!

            // In the composable(Route.Donor) block, update call to DonorInfoScreen:
            DonorInfoScreen(
                sessionId = s,
                fundraiserId = f,
                onNext = { donorId, mobileE164, email, fullName, dobIso, address ->
                    // store donor details in the current backstack entry BEFORE navigating
                    nav.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set("donor_phone_e164", mobileE164)
                    nav.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set("donor_email", email)
                    nav.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set("donor_full_name", fullName)
                    nav.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set("donor_dob", dobIso)
                    nav.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set("donor_address", address)
                    nav.navigate(Route.gift(s, donorId))
                },
                onBack = { nav.popBackStack() }
            )


        }


        composable(
            Route.Gift,
            arguments = listOf(
                navArgument("session"){ type = NavType.StringType },
                navArgument("donor"){ type = NavType.StringType }
            )
        ) { back ->
            val s = back.arguments!!.getString("session")!!
            val d = back.arguments!!.getString("donor")!!

            val phoneE164 = nav.previousBackStackEntry
                ?.savedStateHandle
                ?.get<String>("donor_phone_e164")
                .orEmpty()

            GiftScreen(
                sessionId = s,
                donorId = d,
                mobileE164 = phoneE164,
                onGoToPay = { nav.navigate(Route.pay(s, d)) },
                onBackToDonor = {
                    // We stored fundraiserId at login; reuse it to return to donor screen.
                    val f = com.example.clarity.data.SessionStore.fundraiserId ?: return@GiftScreen
                    nav.navigate(Route.donor(s, f)) {
                        launchSingleTop = true
                    }
                }
            )
        }



        composable(
            Route.Verify,
            arguments = listOf(
                navArgument("session"){ type = NavType.StringType },
                navArgument("donor"){ type = NavType.StringType }
            )
        ) { back ->
            val s = back.arguments!!.getString("session")!!
            val d = back.arguments!!.getString("donor")!!

            SmsVerifyScreen(
                sessionId = s,
                donorId = d,
                onYes = {
                    // proceed to pay and remove Verify from backstack so back doesn’t return here
                    nav.navigate(Route.pay(s, d)) {
                        popUpTo(Route.Verify) { inclusive = true }
                    }
                },
                onNo = {
                    // just pop back to the Donor screen (pattern route is fine here)
                    nav.popBackStack(Route.Donor, inclusive = false)
                }
            )
        }


        composable(Route.Pay,
            arguments = listOf(
                navArgument("session"){ type = NavType.StringType },
                navArgument("donor"){ type = NavType.StringType }
            )
        ) { back ->
            val s = back.arguments!!.getString("session")!!
            val d = back.arguments!!.getString("donor")!!

            PaymentScreen(
                sessionId = s,
                donorId = d,
                onDone = { nav.navigate(Route.Done) },
                onBack = { nav.popBackStack() }
            )
        }

        composable(Route.Comms,
            arguments = listOf(
                navArgument("session"){ type = NavType.StringType },
                navArgument("donor"){ type = NavType.StringType }
            )
        ) { back ->
            val s = back.arguments!!.getString("session")!!
            val d = back.arguments!!.getString("donor")!!
            CommsScreen(s, d) { needsTerms ->
                if (needsTerms) nav.navigate(Route.sign(s, d)) else nav.navigate(Route.Done)
            }
        }

        composable(Route.Sign,
            arguments = listOf(
                navArgument("session"){ type = NavType.StringType },
                navArgument("donor"){ type = NavType.StringType }
            )
        ) { back ->
            val s = back.arguments!!.getString("session")!!
            val d = back.arguments!!.getString("donor")!!
            SignatureScreen(s, d) { nav.navigate(Route.Done) }
        }

        // inside AppNav(), in composable(Route.Done) { ... }
        composable(Route.Done) {
            DoneScreen(
                onStartNextDonation = {
                    // 1) Clear donor-specific cache
                    com.example.clarity.data.SessionStore.resetForNextDonor()

                    // 2) Clear any donor_* values left in back stack saved state (best-effort)
                    val keys = listOf(
                        "donor_phone_e164",
                        "donor_email",
                        "donor_full_name",
                        "donor_dob",
                        "donor_address"
                    )
                    // Try both previous and current entries (depending on your navigation path)
                    listOf(nav.previousBackStackEntry, nav.currentBackStackEntry).forEach { entry ->
                        keys.forEach { k -> entry?.savedStateHandle?.remove<String>(k) }
                    }

                    // 3) Navigate back to Campaign for the next donor
                    val session = com.example.clarity.data.SessionStore.sessionId
                    val fundraiser = com.example.clarity.data.SessionStore.fundraiserId
                    if (!session.isNullOrBlank() && !fundraiser.isNullOrBlank()) {
                        nav.navigate(Route.campaign(session, fundraiser)) {
                            popUpTo(Route.Login) { inclusive = false }
                            launchSingleTop = true
                        }
                    } else {
                        // Fallback if session/fundraiser somehow missing
                        nav.popBackStack(Route.Login, inclusive = false)
                    }
                }
            )
        }


    }
}
