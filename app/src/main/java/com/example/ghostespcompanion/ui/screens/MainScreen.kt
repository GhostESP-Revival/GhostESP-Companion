package com.example.ghostespcompanion.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.example.ghostespcompanion.R
import com.example.ghostespcompanion.ui.theme.GhostESPTheme

/**
 * Main screen scaffold with minimalist neo-brutalist top app bar
 *
 * Clean white accents on deep black background.
 * Professional and modern aesthetic.
 *
 * @param onBack If non-null, a back arrow is shown in the top bar and calls this lambda.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                actions = actions,
                scrollBehavior = scrollBehavior,
                windowInsets = WindowInsets(0, 0, 0, 0),
 colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = floatingActionButton,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            content(paddingValues)
        }
    }
}

/**
 * Preview helper for screens - defaults to dark mode
 */
@Composable
fun ScreenPreview(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    GhostESPTheme(darkTheme = darkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            content()
        }
    }
}
