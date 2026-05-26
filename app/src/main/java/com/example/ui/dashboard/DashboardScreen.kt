package com.example.ui.dashboard

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.TranscriptEntity
import com.example.ui.components.CustomIcons
import java.text.SimpleDateFormat
import java.util.*

// Dynamic high contrast colors for the Obsidian Mint Theme
private val BackgroundObsidian = Color(0xFF0F0F11)
private val SurfaceCardObsidian = Color(0xFF191A1E)
private val SurfaceBorderSlate = Color(0xFF2C2D35)
private val ColorMintTeal = Color(0xFF00E5C9)
private val ColorPulsingRed = Color(0xFFFF4B5C)
private val TextWhiteHighContrast = Color(0xFFF5F5F7)
private val TextGraySubtle = Color(0xFF9E9EAA)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // States
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val isSimulating by viewModel.isSimulating.collectAsStateWithLifecycle()
    val scenarioTitle by viewModel.simulationScenarioTitle.collectAsStateWithLifecycle()
    val liveAmplitudes by viewModel.liveAmplitudes.collectAsStateWithLifecycle()
    val recordingDuration by viewModel.recordingDurationSeconds.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val transcriptionOutput by viewModel.transcriptionOutput.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val fontSizeScale by viewModel.fontSizeScale.collectAsStateWithLifecycle()
    val activeQuickReply by viewModel.activeQuickReply.collectAsStateWithLifecycle()
    val transcripts by viewModel.transcripts.collectAsStateWithLifecycle()
    val isLocalKeyAvailable by viewModel.isLocalKeyAvailable.collectAsStateWithLifecycle()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var noteEditingId by remember { mutableStateOf<Int?>(null) }
    var tempNoteText by remember { mutableStateOf("") }

    // Audio Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startVoiceRecording()
        } else {
            // Error managed in viewmodel
            viewModel.startVoiceRecording() // will trigger error gracefully inside viewmodel
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundObsidian),
        containerColor = BackgroundObsidian,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = ColorMintTeal.copy(alpha = 0.15f),
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "سميع — Samiâ",
                            fontWeight = FontWeight.Bold,
                            color = TextWhiteHighContrast,
                            letterSpacing = 0.5.sp,
                            fontSize = 20.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "الإعدادات",
                            tint = ColorMintTeal
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = BackgroundObsidian,
                    titleContentColor = TextWhiteHighContrast
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {

            // SECTION 1: DHH live alerts / recording status & visualization
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCardObsidian),
                    border = BorderStroke(1.dp, SurfaceBorderSlate),
                    modifier = Modifier.fillMaxWidth().testTag("live_status_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Title status
                        val statusText = when {
                            isRecording -> "جاري الاستماع للمايكروفون الحقيقي..."
                            isSimulating -> "جاري محاكاة المحادثة: $scenarioTitle"
                            isProcessing -> "جاري تحليل دقة اللهجة التونسية بالذكاء الاصطناعي..."
                            else -> "اضغط ابدأ وتحدث بالتونسي (الدرجة)"
                        }
                        val statusColor = when {
                            isRecording -> ColorPulsingRed
                            isSimulating -> Color(0xFF00B4D8)
                            isProcessing -> ColorMintTeal
                            else -> TextGraySubtle
                        }

                        Text(
                            text = statusText,
                            color = statusColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Audio Waveform Animated canvas
                        LiveWaveformVisualizer(
                            amplitudes = liveAmplitudes,
                            isRecording = isRecording,
                            isSimulating = isSimulating,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Duration timer display with big micro button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // Duration counter
                            Text(
                                text = formatDuration(recordingDuration),
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRecording || isSimulating) TextWhiteHighContrast else TextGraySubtle
                                ),
                                modifier = Modifier.width(90.dp),
                                textAlign = TextAlign.Center
                            )

                            // Unified Record / Stop trigger button
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = if (isRecording || isSimulating) {
                                                listOf(ColorPulsingRed, ColorPulsingRed.copy(alpha = 0.7f))
                                            } else {
                                                listOf(ColorMintTeal, ColorMintTeal.copy(alpha = 0.7f))
                                            }
                                        )
                                    )
                                    .border(2.dp, TextWhiteHighContrast.copy(alpha = 0.15f), CircleShape)
                                    .clickable {
                                        if (isSimulating) {
                                            // Simulated scenarios run automatically for 4 seconds, do nothing standard or lets cancel
                                        } else if (isRecording) {
                                            viewModel.stopVoiceRecordingAndTranscribe()
                                        } else {
                                            // Request standard mic recording
                                            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                    .testTag("action_record_button")
                            ) {
                                val pulseScale by rememberInfiniteTransition(label = "").animateFloat(
                                    initialValue = 1f,
                                    targetValue = if (isRecording) 1.15f else 1.0f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(800, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = ""
                                )

                                Icon(
                                    imageVector = if (isRecording) CustomIcons.Stop else CustomIcons.Mic,
                                    contentDescription = if (isRecording) "إيقاف التسجيل" else "بدء التقاط الصوت",
                                    tint = Color.Black,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .animateContentSize()
                                )
                            }

                            // Extra assist note icon/indication
                            Box(
                                modifier = Modifier.width(90.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isProcessing) {
                                    CircularProgressIndicator(
                                        color = ColorMintTeal,
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(28.dp)
                                    )
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isLocalKeyAvailable || apiKey.isNotBlank()) Color(0xFF1B322D) else Color(0xFF332025),
                                        modifier = Modifier.padding(4.dp)
                                    ) {
                                        Text(
                                            text = if (isLocalKeyAvailable || apiKey.isNotBlank()) "متصل بالذكاء" else "محاكاة محلية",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isLocalKeyAvailable || apiKey.isNotBlank()) ColorMintTeal else ColorPulsingRed
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Error display if any
            if (errorMessage != null) {
                item {
                    Surface(
                        color = ColorPulsingRed.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, ColorPulsingRed.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = "تنبيه", tint = ColorPulsingRed)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = errorMessage!!,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = ColorPulsingRed,
                                    textDirection = TextDirection.Rtl
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.clearError() }) {
                                Icon(Icons.Default.Clear, contentDescription = "إخفاء", tint = ColorPulsingRed)
                            }
                        }
                    }
                }
            }

            // SECTION 2: Large Display Box of Captured Transcription text
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCardObsidian),
                    border = BorderStroke(2.dp, SurfaceBorderSlate),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transcription_output_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "النص الصوتي المباشر (التونسي)",
                                fontWeight = FontWeight.Bold,
                                color = ColorMintTeal,
                                fontSize = 15.sp
                            )

                            // Active quick annotations
                            if (isProcessing) {
                                Text(
                                    text = "جاري الكتابة بالدرجة...",
                                    fontSize = 12.sp,
                                    color = ColorMintTeal,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Big Transcription Output Letters Area
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp)
                                .background(Color(0xFF101013), shape = RoundedCornerShape(12.dp))
                                .border(1.dp, SurfaceBorderSlate, RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            if (transcriptionOutput.isBlank()) {
                                Text(
                                    text = "تكلم بالتونسي مثل برشة باهي أو استخدم المحاكيات الجاهزة بالأسفل للاختبار الفوري دون مجهود.\n\nسيظهر النص هنا بحروف واضحة وعلامات ترقيم ممتازة وملاحظات الأصوات المحيطة في أقواس.",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = TextGraySubtle,
                                        lineHeight = 26.sp,
                                        textAlign = TextAlign.Right,
                                        textDirection = TextDirection.Rtl
                                    ),
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            } else {
                                Text(
                                    text = transcriptionOutput,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        color = TextWhiteHighContrast,
                                        lineHeight = 34.sp * fontSizeScale,
                                        fontSize = 20.sp * fontSizeScale,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Right,
                                        textDirection = TextDirection.Rtl
                                    ),
                                    modifier = Modifier.fillMaxWidth().testTag("transcription_raw_text")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Controls for DHH accessibility: font resizing, copy, share
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Scale slider
                            Row(
                                modifier = Modifier.weight(1.3f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "حجم الخط",
                                    fontSize = 11.sp,
                                    color = TextGraySubtle,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Slider(
                                    value = fontSizeScale,
                                    onValueChange = { viewModel.setFontSizeScale(it) },
                                    valueRange = 0.8f..2.0f,
                                    steps = 5,
                                    modifier = Modifier.height(20.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = ColorMintTeal,
                                        activeTrackColor = ColorMintTeal,
                                        inactiveTrackColor = SurfaceBorderSlate
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Action buttons
                            Row(
                                modifier = Modifier.weight(0.7f),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        if (transcriptionOutput.isNotBlank()) {
                                            clipboardManager.setText(AnnotatedString(transcriptionOutput))
                                            ToastUtils.showToast(context, "تم نسخ النص بنجاح!")
                                        }
                                    },
                                    enabled = transcriptionOutput.isNotBlank(),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        disabledContainerColor = Color.Transparent,
                                        containerColor = SurfaceBorderSlate.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.testTag("copy_text_button")
                                ) {
                                    Icon(
                                        imageVector = CustomIcons.Copy,
                                        contentDescription = "نسخ النص",
                                        tint = if (transcriptionOutput.isNotBlank()) ColorMintTeal else TextGraySubtle,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                IconButton(
                                    onClick = {
                                        if (transcriptionOutput.isNotBlank()) {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, transcriptionOutput)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "مشاركة النص"))
                                        }
                                    },
                                    enabled = transcriptionOutput.isNotBlank(),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        disabledContainerColor = Color.Transparent,
                                        containerColor = SurfaceBorderSlate.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.testTag("share_text_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "مشاركة النص",
                                        tint = if (transcriptionOutput.isNotBlank()) ColorMintTeal else TextGraySubtle,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 3: Simulated Tunisian Arabic Scenarios Chips
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "محاكيات التونسي التجريبية (للاختبار الفوري السريع)",
                        fontWeight = FontWeight.Bold,
                        color = TextWhiteHighContrast,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                        style = TextStyle(
                            textAlign = TextAlign.Right,
                            textDirection = TextDirection.Rtl
                        )
                    )

                    val scenarios = listOf(
                        ScenarioItem(
                            "🛒 في المارشي",
                            "يا خويا عيشك برشة ناس ما فيبالهاش بالحكاية هذي توة",
                            "يا خويا عيشك، برشة ناس ما فيبالهاش بالحكاية هذي توّة.\n\nبربي قداش سوم هالحكّة زيت ودقلة النور؟\n\n[ضحك] باهي بربّي، شكون ينجم يعاوني في هذا؟ C'est urgent."
                        ),
                        ScenarioItem(
                            "☕ قهوة تونسية",
                            "باهي عيشك جيبلي قهوة إكسبريس واحدة تاي بالنعناع",
                            "أهلاً وسهلاً بيك يا صاحبي! شحوالك؟\n\n[توقف طويل] باهي عيشك، جيبلي قهوة إكسبريس وواحدة تاي بالنعناع مع الكشكوشة بربّي.\n\nMerci برشة يا غالي!"
                        ),
                        ScenarioItem(
                            "📍 باب البحر",
                            "بربي خويا الغالي، تنجم تدلني على ثنية باب البحر من هوني؟",
                            "بربّي خويا الغالي، تنجم تدلني على ثنية باب البحر من هوني؟\n\nراني ضعت والوقت زربني، C'est pas possible!\n\n[صوت في الخلفية: زامور سيارات]"
                        ),
                        ScenarioItem(
                            "🚑 مستعجلين",
                            "يا طبيب عيشك المدام مريضة برشة توجعها كرشها ساعتين",
                            "يا طبيب عيشك، المدام مريضة برشة وتوجعها كرشها توّة ساعتين!\n\n[صوت مرتفع/قلق] ومستعجلين علخر... بربّي عاونا."
                        )
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        scenarios.forEach { scenario ->
                            AssistChip(
                                onClick = {
                                    if (!isRecording && !isSimulating) {
                                        viewModel.runSimulatedScenario(
                                            scenario.title,
                                            scenario.promptSample,
                                            scenario.simulatedOutput
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = scenario.title,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp,
                                        color = if (isSimulating && scenarioTitle == scenario.title) Color.Black else ColorMintTeal
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (isSimulating && scenarioTitle == scenario.title) ColorMintTeal else SurfaceCardObsidian,
                                    labelColor = ColorMintTeal
                                ),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (isSimulating && scenarioTitle == scenario.title) ColorMintTeal else SurfaceBorderSlate
                                ),
                                enabled = !isRecording && !isSimulating,
                                modifier = Modifier.testTag("scenario_chip_${scenario.title}")
                            )
                        }
                    }
                }
            }

            // SECTION 4: Deaf Quick Assist Response Cards (الردود الفورية السريعة للأصم)
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "الردود السريعة الفورية للأصم (اضغط لعرض نص عملاق للناس)",
                        fontWeight = FontWeight.Bold,
                        color = TextWhiteHighContrast,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                        style = TextStyle(
                            textAlign = TextAlign.Right,
                            textDirection = TextDirection.Rtl
                        )
                    )

                    val quickReplies = listOf(
                        "أنا مسمعش مليح، بربي سجل صوتك في المايك هوني يكتب بالوقت.",
                        "سامحني راني أصم، تنجم تكتبلي جوابك هوني بربي عيشك؟",
                        "باهي برشة، يرحم والديك ومرسي عيشك!",
                        "دلّني على السيرفيس الصحيح وإلا الاستعجالات بربي، c'est urgent!"
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickReplies.forEach { reply ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, ColorMintTeal.copy(alpha = 0.3f)),
                                colors = CardDefaults.cardColors(containerColor = ColorMintTeal.copy(alpha = 0.05f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setActiveQuickReply(reply) }
                                    .testTag("quick_reply_card_${reply.take(15)}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "عرض",
                                        tint = ColorMintTeal,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = reply,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextWhiteHighContrast,
                                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                                        style = TextStyle(
                                            textAlign = TextAlign.Right,
                                            textDirection = TextDirection.Rtl
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 5: Search & Historical Transcripts Database (الأرشيف المحفوظ للأصم)
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (transcripts.isNotEmpty()) {
                            Text(
                                text = "مسح الأرشيف",
                                color = ColorPulsingRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable {
                                    viewModel.clearAllHistory()
                                    ToastUtils.showToast(context, "تم مسح جميع السجلات")
                                }
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        Text(
                            text = "السجلات السابقة والمحفوظات ${if (transcripts.isNotEmpty()) "(${transcripts.size})" else ""}",
                            fontWeight = FontWeight.Bold,
                            color = TextWhiteHighContrast,
                            fontSize = 14.sp
                        )
                    }

                    // Search input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.applySearchQuery(it) },
                        placeholder = {
                            Text(
                                "ابحث في الكلمات، الملاحظات، أو التواريخ...",
                                color = TextGraySubtle,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Right
                            )
                        },
                        leadingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { viewModel.applySearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "إلغاء", tint = TextGraySubtle)
                                }
                            }
                        },
                        trailingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "بحث", tint = ColorMintTeal)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("search_transcripts_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhiteHighContrast,
                            unfocusedTextColor = TextWhiteHighContrast,
                            focusedBorderColor = ColorMintTeal,
                            unfocusedBorderColor = SurfaceBorderSlate,
                            focusedContainerColor = Color(0xFF101013),
                            unfocusedContainerColor = Color(0xFF101013)
                        )
                    )
                }
            }

            // Historical List representation (We embed rows natively inside LazyColumn for absolute speed)
            if (transcripts.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = CustomIcons.History,
                            contentDescription = "السجل فارغ",
                            tint = TextGraySubtle.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "لا توجد سجلات محفوظة حالياً.\nسيتم حفظ التسجيلات التونسية وسيناريوهات المحاكاة تلقائياً لتتمكن من الرجوع إليها." else "لم يتم العثور على نتائج تطابق معيار البحث.",
                            fontSize = 13.sp,
                            color = TextGraySubtle,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }
            } else {
                items(transcripts, key = { it.id }) { transcript ->
                    TranscriptItemRow(
                        item = transcript,
                        isSelectedForNote = noteEditingId == transcript.id,
                        onBookmarkToggle = { viewModel.toggleBookmark(transcript.id, transcript.isBookmarked) },
                        onDeleteClick = {
                            viewModel.deleteTranscript(transcript)
                            ToastUtils.showToast(context, "تم حذف المقطع بنجاح")
                        },
                        onNoteClick = {
                            if (noteEditingId == transcript.id) {
                                noteEditingId = null
                            } else {
                                noteEditingId = transcript.id
                                tempNoteText = transcript.note
                            }
                        },
                        onNoteSave = {
                            viewModel.updateAnnotation(transcript.id, tempNoteText)
                            noteEditingId = null
                            ToastUtils.showToast(context, "تم حفظ الملاحظة!")
                        },
                        tempNoteValue = tempNoteText,
                        onTempNoteChange = { tempNoteText = it }
                    )
                }
            }
        }
    }

    // DIA 1: Fullscreen Quick Reply display for instant DHH flash card accessibility!
    if (activeQuickReply != null) {
        Dialog(
            onDismissRequest = { viewModel.setActiveQuickReply(null) },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BackgroundObsidian)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "تواصل خارجي",
                        tint = ColorMintTeal,
                        modifier = Modifier.size(80.dp)
                    )

                    Spacer(modifier = Modifier.height(36.dp))

                    Text(
                        text = activeQuickReply!!,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextWhiteHighContrast,
                            lineHeight = 56.sp,
                            textAlign = TextAlign.Center,
                            textDirection = TextDirection.Rtl
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, ColorMintTeal, RoundedCornerShape(16.dp))
                            .background(SurfaceCardObsidian, RoundedCornerShape(16.dp))
                            .padding(24.dp)
                            .testTag("fullscreen_quick_reply_text")
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    Button(
                        onClick = { viewModel.setActiveQuickReply(null) },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorMintTeal, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("close_fullscreen_reply")
                    ) {
                        Text("إغلاق والعودة", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // DIA 2: Configuration settings dialog
    if (showSettingsDialog) {
        var editingKey by remember { mutableStateOf(apiKey) }

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setCustomApiKey(editingKey)
                        showSettingsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorMintTeal, contentColor = Color.Black)
                ) {
                    Text("حفظ التغييرات", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("إلغاء", color = ColorMintTeal)
                }
            },
            title = {
                Text(
                    "إعدادات السمع والاتصال",
                    color = TextWhiteHighContrast,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "يتصل التطبيق بخدمة Gemini 3.5-Flash لتحويل الصوت إلى نص باللهجة التونسية (الدرجة). يمكنك إدخال مفتاح الـ API الخاص بك هوني لتشغيل دائم، وإلا فاستخدم المحاكيات المحلية المدمجة.",
                        fontSize = 12.sp,
                        color = TextGraySubtle,
                        style = TextStyle(
                            textAlign = TextAlign.Right,
                            textDirection = TextDirection.Rtl
                        )
                    )

                    OutlinedTextField(
                        value = editingKey,
                        onValueChange = { editingKey = it },
                        label = {
                            Text(
                                "مفتاح Gemini API الخاص بك",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Right
                            )
                        },
                        placeholder = { Text("AIzaSy...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_key_settings_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhiteHighContrast,
                            unfocusedTextColor = TextWhiteHighContrast,
                            focusedBorderColor = ColorMintTeal,
                            unfocusedBorderColor = SurfaceBorderSlate,
                            focusedContainerColor = Color.Black,
                            unfocusedContainerColor = Color.Black
                        )
                    )
                }
            },
            containerColor = SurfaceCardObsidian,
            shape = RoundedCornerShape(16.dp),
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
        )
    }
}

// Custom Waveform rendering
@Composable
fun LiveWaveformVisualizer(
    amplitudes: List<Float>,
    isRecording: Boolean,
    isSimulating: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(Color(0xFF101013), shape = RoundedCornerShape(12.dp))
            .border(1.dp, SurfaceBorderSlate, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!isRecording && !isSimulating) {
            Text(
                text = "التقاط موجات الصوت غير نشط",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = TextGraySubtle,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val barCount = amplitudes.size
                val barSpacing = 4.dp.toPx()
                val totalSpacing = barSpacing * (barCount - 1)
                val barWidth = (width - totalSpacing) / barCount

                amplitudes.forEachIndexed { index, amp ->
                    // Standard minimum thickness
                    val barHeight = (amp * height).coerceIn(6.dp.toPx(), height * 0.9f)
                    val x = index * (barWidth + barSpacing)
                    val y = (height - barHeight) / 2f

                    drawRoundRect(
                        color = if (isSimulating) Color(0xFF00B4D8) else ColorMintTeal,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }
            }
        }
    }
}

// Single Custom Transcript row representation
@Composable
fun TranscriptItemRow(
    item: TranscriptEntity,
    isSelectedForNote: Boolean,
    onBookmarkToggle: () -> Unit,
    onDeleteClick: () -> Unit,
    onNoteClick: () -> Unit,
    onNoteSave: () -> Unit,
    tempNoteValue: String,
    onTempNoteChange: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("transcript_item_row_${item.id}"),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, SurfaceBorderSlate),
        colors = CardDefaults.cardColors(containerColor = SurfaceCardObsidian)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Timestamp and favorite / bookmark star toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Actions (Bookmark, delete, note, copy)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBookmarkToggle,
                        modifier = Modifier.testTag("bookmark_toggle_${item.id}")
                    ) {
                        Icon(
                            imageVector = if (item.isBookmarked) Icons.Filled.Star else Icons.Outlined.Star,
                            contentDescription = "تثبيت السجل",
                            tint = if (item.isBookmarked) Color(0xFFF39C12) else TextGraySubtle,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(onClick = onNoteClick) {
                        Icon(
                            imageVector = CustomIcons.Note,
                            contentDescription = "إضافة ملاحظة",
                            tint = if (item.note.isNotBlank()) ColorMintTeal else TextGraySubtle,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(item.text))
                            ToastUtils.showToast(context, "تم النسخ للأرشيف!")
                        }
                    ) {
                        Icon(
                            imageVector = CustomIcons.Copy,
                            contentDescription = "نسخ السجل",
                            tint = TextGraySubtle,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.testTag("delete_item_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف السجل",
                            tint = ColorPulsingRed.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Time indicators
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatUnixTime(item.timestamp),
                        fontSize = 11.sp,
                        color = TextGraySubtle,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "المدة: ${formatDuration(item.durationMs / 1000)}",
                        fontSize = 10.sp,
                        color = TextGraySubtle
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Body text
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = TextWhiteHighContrast,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Right,
                    textDirection = TextDirection.Rtl
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Attached annotation note if present
            if (item.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = ColorMintTeal.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, ColorMintTeal.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "ملاحظتي: ${item.note}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ColorMintTeal,
                        modifier = Modifier.padding(8.dp),
                        style = TextStyle(
                            textAlign = TextAlign.Right,
                            textDirection = TextDirection.Rtl
                        )
                    )
                }
            }

            // Note edit box expansion block
            if (isSelectedForNote) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onNoteSave,
                        colors = ButtonDefaults.buttonColors(containerColor = ColorMintTeal, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text("حفظ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedTextField(
                        value = tempNoteValue,
                        onValueChange = onTempNoteChange,
                        placeholder = {
                            Text(
                                "مثال: مكالمة الطبيب، وصف لشارع...",
                                fontSize = 12.sp,
                                color = TextGraySubtle,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Right
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ColorMintTeal,
                            unfocusedBorderColor = SurfaceBorderSlate,
                            focusedContainerColor = Color.Black,
                            unfocusedContainerColor = Color.Black
                        )
                    )
                }
            }
        }
    }
}

// Time formattings
private fun formatUnixTime(timeMs: Long): String {
    val formatter = SimpleDateFormat("yyyy/MM/dd - HH:mm", Locale.getDefault())
    return formatter.format(Date(timeMs))
}

private fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}

// Scenario data structures
class ScenarioItem(
    val title: String,
    val promptSample: String,
    val simulatedOutput: String
)

// Single file inline Toast wrapper helper
object ToastUtils {
    fun showToast(context: Context, msg: String) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
    }
}
