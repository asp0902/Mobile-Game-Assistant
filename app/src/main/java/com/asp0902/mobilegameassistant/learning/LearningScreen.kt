package com.asp0902.mobilegameassistant.learning

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LearningScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { LearnedKnowledgeRepository(context) }
    val scope = rememberCoroutineScope()
    val vault = remember(context) { LearningTokenVault(context) }
    var showUpdate by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var tokenSaved by remember { mutableStateOf(vault.hasToken()) }
    val version by produceState("자료 버전 확인 중", repository) {
        value = withContext(Dispatchers.IO) { LearningFiles.status(context) }
    }
    val loaded by produceState<Result<LearnedSnapshot>?>(null, repository) { value = repository.load() }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var showConversation by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val snapshot = loaded?.getOrNull()
    val entries = if (showConversation) snapshot?.conversation.orEmpty() else snapshot?.entries.orEmpty()
    val selected = entries.firstOrNull { it.id == selectedId }
    val detail by produceState<Result<String>?>(null, selected) {
        value = null
        selected?.let { value = repository.text(it) }
    }
    val portrait by produceState<ImageBitmap?>(null, selected?.portrait) {
        value = null
        selected?.portrait?.let { asset ->
            value = withContext(Dispatchers.IO) {
                runCatching { LearningFiles.open(context, asset).use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
            }
        }
    }
    BackHandler { if (showUpdate) { showUpdate = false; token = "" } else if (selectedId != null) selectedId = null else onBack() }
    Scaffold(containerColor = Color(0xFFF1EBDE)) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { if (showUpdate) { showUpdate = false; token = "" } else if (selectedId != null) selectedId = null else onBack() }) { Text("뒤로") }
            Text("저장된 학습 · ${snapshot?.date ?: "불러오는 중"}", style = MaterialTheme.typography.titleLarge)
            Text("과거 기록과 미실험 제안을 분리합니다. 현재 계정 자동 확인이나 성공 보장이 아닙니다.")
            if (showUpdate) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Text("비공개 GitHub: asp0902/Mobile-Game-Assistant\n$version") }
                    item { Text("이 저장소만 허용한 fine-grained 토큰(Contents: Read-only)을 입력하세요. APK에는 토큰을 넣지 않으며 Android Keystore로 기기에 암호화 보관합니다.") }
                    item {
                        OutlinedTextField(value = token, onValueChange = { token = it },
                            label = { Text(if (tokenSaved) "저장된 토큰 교체" else "GitHub 읽기 전용 토큰") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = !busy && token.isNotBlank(), onClick = {
                                val entered = token; token = ""; busy = true
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) { vault.save(entered) }
                                        tokenSaved = true; message = "토큰을 기기에 암호화 저장했습니다."
                                    } catch (error: CancellationException) { throw error }
                                    catch (_: Exception) { message = "토큰 저장 실패. 형식과 기기 보안 저장소를 확인하세요." }
                                    finally { busy = false }
                                }
                            }) { Text("토큰 저장") }
                            Button(enabled = !busy && tokenSaved, onClick = {
                                busy = true
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) { vault.forget() }
                                        tokenSaved = false; message = "토큰 삭제 완료. 저장된 자료는 오프라인에서 유지됩니다."
                                    } catch (error: CancellationException) { throw error }
                                    catch (_: Exception) { message = "토큰 삭제 실패" }
                                    finally { busy = false }
                                }
                            }) { Text("토큰 삭제") }
                        }
                    }
                    item {
                        Button(enabled = !busy && tokenSaved, modifier = Modifier.fillMaxWidth(), onClick = {
                            busy = true; message = "다운로드·형식·SHA-256 검증 중. 기존 자료를 유지합니다."
                            scope.launch {
                                try { message = LearningFiles.update(context) }
                                catch (error: CancellationException) { throw error }
                                catch (_: Exception) { message = "업데이트 실패: 기존 자료 유지. 토큰 권한·만료, 네트워크, 저장 공간, 자료 형식 또는 필요한 APK 버전을 확인하세요." }
                                finally { busy = false }
                            }
                        }) { Text(if (busy) "처리 중" else "학습 자료 수동 업데이트") }
                    }
                    item { Text(message) }
                    item { Text("JSON·초상·지원된 추천 규칙만 갱신합니다. 성공 후 트래킹을 중지하고 앱을 완전히 종료한 뒤 재실행하세요. 새 인식 방식과 실행 코드에는 새 APK가 필요합니다.") }
                }
            } else if (loaded?.isFailure == true) {
                Text("학습 자료 읽기 실패: ${loaded?.exceptionOrNull()?.message}")
                Button(onClick = { showUpdate = true }) { Text("원격 업데이트") }
            } else if (selected != null) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Text(selected.title, style = MaterialTheme.typography.titleMedium) }
                    item {
                        portrait?.let { Image(it, contentDescription = selected.title, modifier = Modifier.size(100.dp)) }
                        if (selected.portrait != null && portrait == null) Text("초상화: CHECK")
                    }
                    item {
                        SelectionContainer {
                            Text(detail?.getOrNull() ?: if (detail?.isFailure == true) "본문 읽기 실패: ${detail?.exceptionOrNull()?.message}" else "불러오는 중")
                        }
                    }
                }
            } else {
                Button(onClick = { showUpdate = true }, modifier = Modifier.fillMaxWidth()) { Text("원격 업데이트 · $version") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showConversation = false; query = "" }) { Text("정리 자료") }
                    Button(onClick = { showConversation = true; query = "" }) { Text("대화 원문") }
                }
                if (showConversation) Text("당시 발언 원문입니다. 오래된 수치·오답은 정리 자료의 정정 사항보다 우선하지 않습니다. 검색은 제목 기준입니다.")
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("자료 검색") }, modifier = Modifier.fillMaxWidth())
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(entries.filter { query.isBlank() || it.title.contains(query, true) || it.text.contains(query, true) }, key = { it.id }) { entry ->
                        Button(onClick = { selectedId = entry.id }, modifier = Modifier.fillMaxWidth()) { Text(entry.title) }
                    }
                }
            }
        }
    }
}
