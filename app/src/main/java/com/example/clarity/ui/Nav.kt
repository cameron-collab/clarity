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

        composable(Route.Campaign,
            arguments = listOf(
                navArgument("session"){ type = NavType.StringType },
                navArgument("fundraiser"){ type = NavType.StringType }
            )
        ) { back ->
            val s = back.arguments!!.getString("session")!!
            val f = back.arguments!!.getString("fundraiser")!!
            CampaignScreen(s, f) { nav.navigate(Route.donor(s, f)) }
        }

        composable(Route.Donor,
            arguments = listOf(
                navArgument("session"){ type = NavType.StringType },
                navArgument("fundraiser"){ type = NavType.StringType }
            )
        ) { back ->
            val s = back.arguments!!.getString("session")!!
            val f = back.arguments!!.getString("fundraiser")!!
            DonorInfoScreen(s, f) { donorId -> nav.navigate(Route.gift(s, donorId)) }
        }

        composable(Route.Gift,
            arguments = listOf(
                navArgument("session"){ type = NavType.StringType },
                navArgument("donor"){ type = NavType.StringType }
            )
        ) { back ->
            val s = back.arguments!!.getString("session")!!
            val d = back.arguments!!.getString("donor")!!
            GiftScreen(s, d,
                onMonthly = { nav.navigate(Route.verify(s, d)) },
                onOtg = { nav.navigate(Route.pay(s, d)) }
            )
        }

        composable(Route.Verify,
            arguments = listOf(
                navArgument("session"){ type = NavType.StringType },
                navArgument("donor"){ type = NavType.StringType }
            )
        ) { back ->
            val s = back.arguments!!.getString("session")!!
            val d = back.arguments!!.getString("donor")!!
            SmsVerifyScreen(s, d) { nav.navigate(Route.pay(s, d)) }
        }

        composable(Route.Pay,
            arguments = listOf(
                navArgument("session"){ type = NavType.StringType },
                navArgument("donor"){ type = NavType.StringType }
            )
        ) { back ->
            val s = back.arguments!!.getString("session")!!
            val d = back.arguments!!.getString("donor")!!
            PaymentScreen(s, d) { monthly ->
                if (monthly) nav.navigate(Route.comms(s, d))
                else nav.navigate(Route.comms(s, d))
            }
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

        composable(Route.Done) { DoneScreen() }
    }
}
