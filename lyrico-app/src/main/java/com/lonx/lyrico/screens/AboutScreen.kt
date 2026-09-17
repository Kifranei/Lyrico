package com.lonx.lyrico.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lonx.lyrico.App.Companion.OWNER_ID
import com.lonx.lyrico.App.Companion.REPO_NAME
import com.lonx.lyrico.App.Companion.TELEGRAM_GROUP_LINK
import com.lonx.lyrico.BuildConfig
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.AboutBgEffect
import com.lonx.lyrico.ui.components.about.aboutCardBlendColors
import com.lonx.lyrico.ui.components.about.aboutTitleBlendColors
import com.lonx.lyrico.ui.effect.BgEffectBackground
import com.lonx.lyrico.viewmodel.AboutViewModel
import com.lonx.lyrico.viewmodel.UiError
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.OpensourceLicenceDestination
import com.ramcosta.composedestinations.generated.destinations.UpdateDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 悬浮标题上下留白，以及滚动时标题向上收进顶栏的位移。 */
private val HeroTopPadding = 148.dp
private val HeroBottomPadding = 112.dp
private val HeroLift = 96.dp
private val HeroContentEstimate = 80.dp

/** 卡片毛玻璃参数。关掉流光时 backdrop 为 null，卡片退回不透明底色。 */
private data class AboutCardStyle(
    val backdrop: LayerBackdrop?,
    val blendColors: List<BlendColorEntry>,
    val blurRadius: Float
)

private val LocalAboutCardStyle = compositionLocalOf { AboutCardStyle(null, emptyList(), 0f) }

@Composable
@Destination<RootGraph>(route = "about")
fun AboutScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: AboutViewModel = koinViewModel()
    val bgEffect by viewModel.aboutBgEffect.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val contributors by viewModel.contributors.collectAsStateWithLifecycle()
    val loading by viewModel.loadingContributors.collectAsStateWithLifecycle()
    val error by viewModel.contributorsError.collectAsStateWithLifecycle()

    val errorText = when (val e = error) {
        null -> null
        UiError.LoadFailed -> stringResource(R.string.load_failed)
        is UiError.Message -> e.text
    }

    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()
    val density = LocalDensity.current

    // 占位项的高度，用它把滚动量归一化成 0..1 的淡出进度
    var heroSpacerHeightPx by remember { mutableIntStateOf(0) }
    // 先按标题 + 版本号的大致高度占位，测量完成后再修正，避免首帧列表跳动
    var heroContentHeight by remember { mutableStateOf(HeroContentEstimate) }

    // 0 = 标题完全展开；1 = 标题已滚走，顶栏接管
    val scrollProgress by remember {
        derivedStateOf {
            when {
                heroSpacerHeightPx <= 0 -> 0f
                lazyListState.firstVisibleItemIndex > 0 -> 1f
                else -> (lazyListState.firstVisibleItemScrollOffset.toFloat() / heroSpacerHeightPx)
                    .coerceIn(0f, 1f)
            }
        }
    }

    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val effectEnabled = bgEffect != AboutBgEffect.OFF
    // 没有流光就没有可模糊的背景，此时毛玻璃只会让卡片发灰
    val blurEnabled = remember(effectEnabled) { effectEnabled && isRenderEffectSupported() }
    val backdrop = rememberLayerBackdrop()
    val cardStyle = AboutCardStyle(
        backdrop = backdrop.takeIf { blurEnabled },
        blendColors = remember(isDark) { aboutCardBlendColors(isDark) },
        blurRadius = if (isDark) 72f else 64f
    )
    val titleBlend = remember(isDark) { aboutTitleBlendColors(isDark) }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.about_title),
                color = MiuixTheme.colorScheme.surface.copy(alpha = scrollProgress),
                titleColor = MiuixTheme.colorScheme.onSurface.copy(alpha = scrollProgress),
                navigationIcon = {
                    IconButton(onClick = { navigator.popBackStack() }) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                scrollBehavior = topAppBarScrollBehavior
            )
        }
    ) { paddingValues ->
        val topPadding = paddingValues.calculateTopPadding()
        val heroLiftPx = with(density) { HeroLift.toPx() }

        BgEffectBackground(
            dynamicBackground = effectEnabled,
            modifier = Modifier.fillMaxSize(),
            bgModifier = Modifier.layerBackdrop(backdrop),
            effectBackground = effectEnabled,
            isOs3 = bgEffect.isOs3,
            alpha = { 1f - scrollProgress }
        ) {
            // 悬浮标题：不在列表里，靠 graphicsLayer 跟随滚动淡出并上移
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = (1f - scrollProgress * 1.35f).coerceIn(0f, 1f)
                        translationY = -heroLiftPx * scrollProgress
                    }
                    .padding(top = topPadding + HeroTopPadding)
                    .onSizeChanged { size ->
                        heroContentHeight = with(density) { size.height.toDp() }
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    modifier = Modifier
                        .padding(bottom = 5.dp)
                        .then(
                            if (blurEnabled) {
                                Modifier.textureBlur(
                                    backdrop = backdrop,
                                    shape = RoundedCornerShape(16.dp),
                                    blurRadius = 150f,
                                    noiseCoefficient = BlurDefaults.NoiseCoefficient,
                                    colors = BlurColors(blendColors = titleBlend),
                                    contentBlendMode = BlendMode.DstIn,
                                    enabled = true
                                )
                            } else {
                                Modifier
                            }
                        ),
                    text = stringResource(R.string.app_name),
                    color = MiuixTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 35.sp
                )
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }

            CompositionLocalProvider(LocalAboutCardStyle provides cardStyle) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .scrollEndHaptic()
                        .overScrollVertical()
                        .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
                    overscrollEffect = null
                ) {
                    item(key = "hero_spacer") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(
                                    topPadding + HeroTopPadding + heroContentHeight + HeroBottomPadding
                                )
                                .onSizeChanged { heroSpacerHeightPx = it.height }
                        )
                    }

                    item(key = "app_info") {
                        SmallTitle(text = stringResource(R.string.about_app_info))
                        FrostedCard {
                            BasicComponent(
                                title = stringResource(R.string.about_app_version),
                                summary = "${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})"
                            )
                            ArrowPreference(
                                title = stringResource(R.string.about_project_url),
                                summary = stringResource(R.string.about_project_url_sub),
                                endActions = { LinkTag("GitHub") },
                                onClick = {
                                    viewModel.openBrowser(
                                        context,
                                        "https://github.com/$OWNER_ID/$REPO_NAME"
                                    )
                                }
                            )
                            ArrowPreference(
                                title = "Telegram",
                                summary = TELEGRAM_GROUP_LINK,
                                endActions = { LinkTag("Telegram") },
                                onClick = { viewModel.openBrowser(context, TELEGRAM_GROUP_LINK) }
                            )
                            ArrowPreference(
                                title = stringResource(R.string.title_opensource_licence),
                                onClick = { navigator.navigate(OpensourceLicenceDestination()) }
                            )
                            ArrowPreference(
                                title = stringResource(R.string.about_update_entry),
                                summary = stringResource(R.string.about_update_entry_summary),
                                onClick = { navigator.navigate(UpdateDestination()) }
                            )
                        }
                    }

                    item(key = "contributors_title") {
                        SmallTitle(text = stringResource(R.string.about_contributors))
                    }

                    when {
                        loading -> item(key = "contributors_loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(size = 32.dp)
                            }
                        }

                        errorText != null -> item(key = "contributors_error") {
                            FrostedCard {
                                BasicComponent(onClick = { viewModel.loadContributors() }) {
                                    Text(
                                        text = errorText,
                                        modifier = Modifier.padding(12.dp),
                                        color = MiuixTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        contributors.isEmpty() -> item(key = "contributors_empty") {
                            FrostedCard {
                                BasicComponent(
                                    title = stringResource(R.string.about_no_contributors)
                                )
                            }
                        }

                        else -> itemsIndexed(
                            items = contributors,
                            key = { _, contributor -> contributor.id }
                        ) { _, contributor ->
                            FrostedCard {
                                ArrowPreference(
                                    title = contributor.login,
                                    summary = stringResource(
                                        R.string.about_contribution_count,
                                        contributor.contributions
                                    ),
                                    startAction = {
                                        AsyncImage(
                                            model = contributor.avatar_url,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .padding(end = 16.dp)
                                                .size(36.dp)
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    },
                                    endActions = { LinkTag("GitHub") },
                                    onClick = {
                                        viewModel.openBrowser(context, contributor.html_url)
                                    }
                                )
                            }
                        }
                    }

                    item(key = "bottom_spacer") {
                        Box(
                            modifier = Modifier.height(
                                paddingValues.calculateBottomPadding() + 32.dp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkTag(text: String) {
    Text(
        text = text,
        fontSize = MiuixTheme.textStyles.body2.fontSize,
        color = MiuixTheme.colorScheme.onSurfaceVariantActions
    )
}

/**
 * 关于页的卡片：开启流光时用毛玻璃透出背景，否则退回普通卡片。
 */
@Composable
private fun FrostedCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val style = LocalAboutCardStyle.current
    val backdrop = style.backdrop

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .then(
                if (backdrop != null) {
                    Modifier.textureBlur(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(16.dp),
                        blurRadius = style.blurRadius,
                        noiseCoefficient = BlurDefaults.NoiseCoefficient,
                        colors = BlurColors(blendColors = style.blendColors),
                        enabled = true
                    )
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.defaultColors(
            color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
            contentColor = MiuixTheme.colorScheme.onSurface
        ),
        content = content
    )
}
