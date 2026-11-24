package tw.edu.pu.csim.tcyang.digitalink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import tw.edu.pu.csim.tcyang.digitalink.ui.theme.DigitalInkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DigitalInkTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Doodle(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Doodle(modifier: Modifier = Modifier) {
    var recognizedText by remember {
        mutableStateOf("請在畫布區手寫，再按「開始辨識」按鈕")
    }

    // 建立一個路徑物件
    var path by remember { mutableStateOf(Path()) }

    // ML Kit 墨水與筆劃收集器
    // inkBuilder 負責收集「整頁的全部筆劃」
    // strokeBuilder 負責記錄「單一筆劃」（手指按下到抬起）
    var inkBuilder by remember {
        mutableStateOf(Ink.builder())
    }

    var strokeBuilder by remember {
        mutableStateOf<Ink.Stroke.Builder?>(null)
    }

    // 取得語言辨識碼 (繁體中文 zh-Hant)
    val modelIdentifier = remember {
        try {
            DigitalInkRecognitionModelIdentifier.fromLanguageTag("zh-Hant")
        } catch (e: Exception) {
            null
        }
    }

    // 建立語言模型物件
    val model = remember(modelIdentifier) {
        modelIdentifier?.let { DigitalInkRecognitionModel.builder(it).build() }
    }

    // 建立手寫辨識引擎
    val recognizer = remember(model) {
        model?.let {
            DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(it).build())
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            modifier = Modifier
                .size(300.dp)
                .border(2.dp, Color.Gray)
                .pointerInput(Unit) {
                    // 監聽拖曳手勢
                    detectDragGestures(
                        onDragStart = { offset -> /* 手指按下 */
                            val x = offset.x
                            val y = offset.y
                            val t = System.currentTimeMillis()

                            path.moveTo(x, y)

                            // ML Kit 記錄起點
                            strokeBuilder = Ink.Stroke.builder()
                            strokeBuilder?.addPoint(Ink.Point.create(x, y, t))
                        },
                        onDrag = { change, dragAmount -> /* 手指移動 */
                            // 消費拖曳事件，避免影響父層元件手勢
                            change.consume()

                            val x = change.position.x
                            val y = change.position.y
                            val t = System.currentTimeMillis()
                            path.lineTo(x, y)

                            // 複製出新的 Path 物件並指派回 path，強制 Compose 重繪
                            val tempPath = Path()
                            tempPath.addPath(path)
                            path = tempPath

                            // ML Kit 記錄移動點座標
                            strokeBuilder?.addPoint(Ink.Point.create(x, y, t))
                        },
                        onDragEnd = { /* 手指離開 */
                            // 將打包好的 Stroke 加入 inkBuilder
                            strokeBuilder?.build()?.let { stroke ->
                                inkBuilder.addStroke(stroke)
                            }

                            // 重置當前 Stroke Builder，準備下一筆劃
                            strokeBuilder = null
                        }
                    )
                }
        ) {
            // 繪製黑色路徑，透明度 1 (不透明)，線條粗細 10
            drawPath(path, Color.Black, 1f, Stroke(10f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row {
            // 開始辨識
            Button(
                onClick = {
                    val ink = inkBuilder.build() // 包含全部手寫軌跡的「欲辨識內容」

                    if (ink.strokes.isEmpty()) {
                        recognizedText = "請先書寫內容"
                        return@Button
                    }

                    recognizedText = "正在處理中..."

                    if (model != null) {
                        RemoteModelManager.getInstance()
                            .download(model, DownloadConditions.Builder().build())
                            .addOnSuccessListener {
                                recognizer?.recognize(ink)
                                    ?.addOnSuccessListener { result ->
                                        if (result.candidates.isNotEmpty()) {
                                            val topCandidate = result.candidates[0].text
                                            recognizedText = "辨識結果: $topCandidate"
                                        } else {
                                            recognizedText = "辨識結果: 無法辨識"
                                        }
                                    }
                                    ?.addOnFailureListener {
                                        recognizedText = "辨識失敗: ${it.message}"
                                    }
                            }
                            .addOnFailureListener { recognizedText = "模型下載失敗" }
                    } else {
                        recognizedText = "模型建立失敗"
                    }
                }
            ) {
                Text("開始辨識")
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 清除畫布
            Button(
                onClick = {
                    path = Path() // 指派新物件，立刻觸發 Canvas 重繪並清空畫面

                    // 清除 ML Kit 累積的筆畫資料
                    inkBuilder = Ink.builder()
                    strokeBuilder = null
                    recognizedText = "請在畫布區手寫，再按「開始辨識」按鈕"
                }
            ) {
                Text("清除")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(recognizedText)
    }
}

@Preview(showBackground = true)
@Composable
fun DoodlePreview() {
    DigitalInkTheme {
        Doodle()
    }
}