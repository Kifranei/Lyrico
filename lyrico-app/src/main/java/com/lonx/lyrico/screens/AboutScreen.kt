package com.lonx.lyrico.screens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lonx.lyrico.App.Companion.OWNER_ID
import com.lonx.lyrico.App.Companion.REPO_NAME
import com.lonx.lyrico.App.Companion.TELEGRAM_GROUP_LINK
import com.lonx.lyrico.BuildConfig
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.effect.BgEffectBackground
import com.lonx.lyrico.utils.UpdateEffect
import com.lonx.lyrico.viewmodel.AboutViewModel
import com.lonx.lyrico.viewmodel.UiError
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.OpensourceLicenceDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.isRenderEffectSupported
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.shapes.SmoothRoundedCornerShape
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
@Destination<RootGraph>(route = "about")
fun AboutScreen(
    navigator: DestinationsNavigator,
) {
    val viewModel: AboutViewModel = koinViewModel()
    val checkUpdateEnabled by viewModel.checkUpdateEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val updateEffect by viewModel.updateEffect.collectAsStateWithLifecycle(
        initialValue = UpdateEffect(R.string.about_check_update_default),
    )
    val contributors by viewModel.contributors.collectAsStateWithLifecycle()
    val loading by viewModel.loadingContributors.collectAsStateWithLifecycle()
    val error by viewModel.contributorsError.collectAsStateWithLifecycle()

    val errorText = when (val e = error) {
        null -> null
        UiError.LoadFailed -> stringResource(R.string.load_failed)
        is UiError.Message -> e.text
    }

    val scrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()
    var logoHeightPx by remember { mutableIntStateOf(0) }

    val scrollProgress by remember {
        derivedStateOf {
            if (logoHeightPx <= 0) 0f
            else {
                val index = lazyListState.firstVisibleItemIndex
                val offset = lazyListState.firstVisibleItemScrollOffset
                if (index > 0) 1f else (offset.toFloat() / logoHeightPx).coerceIn(0f, 1f)
            }
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.about_title),
                scrollBehavior = scrollBehavior,
                color = colorScheme.surface.copy(alpha = if (scrollProgress == 1f) 1f else 0f),
                titleColor = colorScheme.onSurface.copy(alpha = scrollProgress),
                defaultWindowInsetsPadding = false,
                navigationIcon = {
                    val layoutDirection = LocalLayoutDirection.current
                    IconButton(onClick = { navigator.popBackStack() }) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.action_back),
                            tint = colorScheme.onSurface,
                            modifier = Modifier.graphicsLayer {
                                scaleX = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
                            },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        AboutContent(
            padding = PaddingValues(top = innerPadding.calculateTopPadding()),
            scrollBehavior = scrollBehavior,
            scrollProgress = scrollProgress,
            lazyListState = lazyListState,
            onLogoHeightChanged = { logoHeightPx = it },
            checkUpdateEnabled = checkUpdateEnabled,
            updateEffect = updateEffect,
            onCheckUpdateEnabledChange = viewModel::setCheckUpdateEnabled,
            onCheckUpdate = viewModel::checkUpdate,
            onOpenGithub = { viewModel.openBrowser(context, "https://github.com/$OWNER_ID/$REPO_NAME") },
            onOpenTelegram = { viewModel.openBrowser(context, TELEGRAM_GROUP_LINK) },
            onOpenContributor = { url -> viewModel.openBrowser(context, url) },
            contributors = contributors,
            loading = loading,
            errorText = errorText,
            onOpenSource = { navigator.navigate(OpensourceLicenceDestination()) },
        )
    }
}

@Composable
private fun AboutContent(
    padding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    scrollProgress: Float,
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    onLogoHeightChanged: (Int) -> Unit,
    checkUpdateEnabled: Boolean,
    updateEffect: UpdateEffect,
    onCheckUpdateEnabledChange: (Boolean) -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenGithub: () -> Unit,
    onOpenTelegram: () -> Unit,
    onOpenSource: () -> Unit,
    onOpenContributor: (String) -> Unit,
    contributors: List<com.lonx.lyrico.data.dto.ContributorInfo>,
    loading: Boolean,
    errorText: String?,
) {
    val backdrop = rememberLayerBackdrop()
    val isDark = isSystemInDarkTheme()
    val blurEnable by remember { mutableStateOf(isRenderEffectSupported()) }
    val shaderSupported = remember { isRuntimeShaderSupported() }

    val density = LocalDensity.current
    var logoHeightDp by remember { mutableStateOf(300.dp) }
    var logoAreaY by remember { mutableFloatStateOf(0f) }
    var titleY by remember { mutableFloatStateOf(0f) }
    var versionY by remember { mutableFloatStateOf(0f) }
    var titleProgress by remember { mutableFloatStateOf(0f) }
    var versionProgress by remember { mutableFloatStateOf(0f) }
    var initialLogoAreaY by remember { mutableFloatStateOf(0f) }

    val titleBlend = remember(isDark) {
        if (isDark) {
            listOf(
                BlendColorEntry(Color(0xe6a1a1a1.toInt()), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af500.toInt()), BlurBlendMode.Lab),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xcc4a4a4a.toInt()), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xff4f4f4f.toInt()), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af200.toInt()), BlurBlendMode.Lab),
            )
        }
    }

    val cardBlendColors = remember(isDark) {
        if (isDark) {
            listOf(
                BlendColorEntry(Color(0x4DA9A9A9), BlurBlendMode.Luminosity),
                BlendColorEntry(Color(0x1A9C9C9C), BlurBlendMode.PlusDarker),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0x340034F9), BlurBlendMode.Overlay),
                BlendColorEntry(Color(0xB3FFFFFF.toInt()), BlurBlendMode.HardLight),
            )
        }
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .onEach { offset ->
                if (lazyListState.firstVisibleItemIndex > 0) {
                    if (titleProgress != 1f) titleProgress = 1f
                    if (versionProgress != 1f) versionProgress = 1f
                    return@onEach
                }
                if (initialLogoAreaY == 0f && logoAreaY > 0f) {
                    initialLogoAreaY = logoAreaY
                }
                val refLogoAreaY = if (initialLogoAreaY > 0f) initialLogoAreaY else logoAreaY
                val stage1TotalLength = refLogoAreaY - versionY
                val stage2TotalLength = versionY - titleY
                val versionDelay = stage1TotalLength * 0.5f
                versionProgress = ((offset.toFloat() - versionDelay) / (stage1TotalLength - versionDelay).coerceAtLeast(1f)).coerceIn(0f, 1f)
                titleProgress = ((offset.toFloat() - stage1TotalLength) / stage2TotalLength.coerceAtLeast(1f)).coerceIn(0f, 1f)
            }
            .collect { }
    }

    BgEffectBackground(
        dynamicBackground = shaderSupported,
        modifier = Modifier.fillMaxSize(),
        bgModifier = Modifier.layerBackdrop(backdrop),
        effectBackground = shaderSupported,
        alpha = { 1f - scrollProgress },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = padding.calculateTopPadding() + 120.dp)
                .onSizeChanged { size -> with(density) { logoHeightDp = size.height.toDp() } },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                modifier = Modifier
                    .padding(bottom = 5.dp)
                    .onGloballyPositioned { coordinates ->
                        if (titleY != 0f) return@onGloballyPositioned
                        val y = coordinates.positionInWindow().y
                        val size = coordinates.size
                        titleY = y + size.height
                    }
                    .graphicsLayer {
                        alpha = 1 - titleProgress
                        scaleX = 1 - (titleProgress * 0.05f)
                        scaleY = 1 - (titleProgress * 0.05f)
                    }
                    .textureBlur(
                        backdrop = backdrop,
                        shape = SmoothRoundedCornerShape(16.dp),
                        blurRadius = 150f,
                        noiseCoefficient = BlurDefaults.NoiseCoefficient,
                        colors = BlurColors(blendColors = titleBlend),
                        contentBlendMode = BlendMode.DstIn,
                        enabled = blurEnable,
                    ),
                text = stringResource(R.string.app_name),
                color = colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 35.sp,
            )
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = 1 - versionProgress
                        scaleX = 1 - (versionProgress * 0.05f)
                        scaleY = 1 - (versionProgress * 0.05f)
                    }
                    .onGloballyPositioned { coordinates ->
                        if (versionY != 0f) return@onGloballyPositioned
                        val y = coordinates.positionInWindow().y
                        val size = coordinates.size
                        versionY = y + size.height
                    },
                color = colorScheme.onSurfaceVariantSummary,
                text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(top = padding.calculateTopPadding()),
            overscrollEffect = null,
        ) {
            item(key = "logoSpacer") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(logoHeightDp + 218.dp)
                        .onSizeChanged { size -> onLogoHeightChanged(size.height) }
                        .onGloballyPositioned { coordinates ->
                            val y = coordinates.positionInWindow().y
                            val size = coordinates.size
                            logoAreaY = y + size.height
                        },
                )
            }

            item {
                SmallTitle(text = stringResource(R.string.about_app_info))
                FrostedCard(backdrop = backdrop, blurEnable = blurEnable, cardBlendColors = cardBlendColors) {
                    BasicComponent(
                        title = stringResource(R.string.about_app_version),
                        summary = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    )
                    ArrowPreference(
                        title = stringResource(R.string.about_project_url),
                        summary = stringResource(R.string.about_project_url_sub),
                        onClick = onOpenGithub,
                    )
                    ArrowPreference(
                        title = stringResource(R.string.about_telegram),
                        summary = TELEGRAM_GROUP_LINK,
                        onClick = onOpenTelegram,
                    )
                    ArrowPreference(
                        title = stringResource(R.string.title_opensource_licence),
                        onClick = onOpenSource,
                    )
                    SwitchPreference(
                        title = stringResource(R.string.about_auto_check_update),
                        summary = stringResource(R.string.about_auto_check_update_sub),
                        checked = checkUpdateEnabled,
                        onCheckedChange = onCheckUpdateEnabledChange,
                    )
                    ArrowPreference(
                        title = stringResource(R.string.about_check_update),
                        summary = stringResource(updateEffect.messageRes, *updateEffect.formatArgs.toTypedArray()),
                        onClick = onCheckUpdate,
                    )
                }
            }

            item {
                SmallTitle(text = stringResource(R.string.about_project_section))
                FrostedCard(backdrop = backdrop, blurEnable = blurEnable, cardBlendColors = cardBlendColors) {
                    ArrowPreference(
                        title = stringResource(R.string.app_name),
                        summary = "github.com/$OWNER_ID/$REPO_NAME",
                        onClick = onOpenGithub,
                    )
                    ArrowPreference(
                        title = stringResource(R.string.title_opensource_licence),
                        summary = stringResource(R.string.about_community_section),
                        onClick = onOpenSource,
                    )
                }
            }

            item {
                SmallTitle(text = stringResource(R.string.about_contributors))
            }

            when {
                loading -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(size = 32.dp)
                    }
                }

                errorText != null -> item {
                    FrostedCard(backdrop = backdrop, blurEnable = blurEnable, cardBlendColors = cardBlendColors) {
                        Text(
                            text = errorText,
                            modifier = Modifier.padding(16.dp),
                            color = colorScheme.onSurface,
                        )
                    }
                }

                contributors.isEmpty() -> item {
                    FrostedCard(backdrop = backdrop, blurEnable = blurEnable, cardBlendColors = cardBlendColors) {
                        BasicComponent(title = stringResource(R.string.about_no_contributors))
                    }
                }

                else -> item {
                    FrostedCard(backdrop = backdrop, blurEnable = blurEnable, cardBlendColors = cardBlendColors) {
                        contributors.forEachIndexed { index, contributor ->
                            ArrowPreference(
                                title = contributor.login,
                                summary = stringResource(R.string.about_contribution_count, contributor.contributions),
                                startAction = {
                                    AsyncImage(
                                        model = contributor.avatar_url,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .padding(end = 16.dp)
                                            .size(36.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop,
                                    )
                                },
                                onClick = { onOpenContributor(contributor.html_url) },
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun FrostedCard(
    backdrop: top.yukonga.miuix.kmp.blur.LayerBackdrop,
    blurEnable: Boolean,
    cardBlendColors: List<BlendColorEntry>,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .textureBlur(
                backdrop = backdrop,
                shape = SmoothRoundedCornerShape(16.dp),
                blurRadius = 60f,
                noiseCoefficient = BlurDefaults.NoiseCoefficient,
                colors = BlurColors(blendColors = cardBlendColors),
                enabled = blurEnable,
            ),
        colors = CardDefaults.defaultColors(
            if (blurEnable) Color.Transparent else colorScheme.surfaceContainer,
            Color.Transparent,
        ),
    ) {
        content()
    }
}
