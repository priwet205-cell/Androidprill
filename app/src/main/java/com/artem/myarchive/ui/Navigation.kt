package com.artem.myarchive.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.artem.myarchive.ui.viewers.FileViewerScreen
import com.artem.myarchive.ui.viewers.ImageViewerScreen

/** -1 в аргументах означает «нет» (корень / новая запись). */
const val NONE = -1L

@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "section/$NONE") {

        composable(
            "section/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { back ->
            val id = back.arguments!!.getLong("id")
            SectionScreen(
                sectionId = id.takeIf { it != NONE },
                onBack = { nav.popBackStack() },
                onOpenSection = { nav.navigate("section/$it") },
                onOpenEntry = { nav.navigate("entry/$it") },
                onAddEntry = { sectionId -> nav.navigate("edit/$sectionId/$NONE") },
                onOpenSettings = { nav.navigate("settings") },
            )
        }

        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }

        composable(
            "entry/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { back ->
            val id = back.arguments!!.getLong("id")
            EntryScreen(
                entryId = id,
                onBack = { nav.popBackStack() },
                onEdit = { sectionId -> nav.navigate("edit/$sectionId/$id") },
                onOpenImage = { index -> nav.navigate("image/$id/$index") },
                onOpenFile = { attachmentId -> nav.navigate("file/$attachmentId") },
            )
        }

        composable(
            "edit/{sectionId}/{entryId}",
            arguments = listOf(
                navArgument("sectionId") { type = NavType.LongType },
                navArgument("entryId") { type = NavType.LongType },
            ),
        ) { back ->
            val args = back.arguments!!
            EntryEditScreen(
                sectionId = args.getLong("sectionId"),
                entryId = args.getLong("entryId").takeIf { it != NONE },
                onDone = { nav.popBackStack() },
            )
        }

        composable(
            "image/{entryId}/{index}",
            arguments = listOf(
                navArgument("entryId") { type = NavType.LongType },
                navArgument("index") { type = NavType.IntType },
            ),
        ) { back ->
            val args = back.arguments!!
            ImageViewerScreen(
                entryId = args.getLong("entryId"),
                startIndex = args.getInt("index"),
                onBack = { nav.popBackStack() },
            )
        }

        composable(
            "file/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { back ->
            FileViewerScreen(
                attachmentId = back.arguments!!.getLong("id"),
                onBack = { nav.popBackStack() },
            )
        }
    }
}
