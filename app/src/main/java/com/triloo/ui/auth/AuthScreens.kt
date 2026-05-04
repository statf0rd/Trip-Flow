package com.triloo.ui.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triloo.ui.PreviewData
import com.triloo.ui.components.TrilooButton
import com.triloo.ui.theme.*
import com.triloo.ui.theme.TrilooMotion
import com.triloo.ui.theme.TrilooTheme

/**
 * Экран входа.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(
    onNavigateBack: () -> Unit,
    onSignInSuccess: () -> Unit,
    onNavigateToSignUp: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val authState by viewModel.uiState.collectAsStateWithLifecycle()

    SignInContent(
        authState = authState,
        onNavigateBack = onNavigateBack,
        onSignInSuccess = onSignInSuccess,
        onNavigateToSignUp = onNavigateToSignUp,
        onNavigateToForgotPassword = onNavigateToForgotPassword,
        onSignIn = { email, password -> viewModel.signIn(email, password) },
        onClearError = viewModel::clearError
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInContent(
    authState: AuthUiState,
    onNavigateBack: () -> Unit,
    onSignInSuccess: () -> Unit,
    onNavigateToSignUp: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onSignIn: (String, String) -> Unit,
    onClearError: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(authState.currentUser?.id) {
        if (authState.currentUser != null) {
            onSignInSuccess()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "✈️",
                style = MaterialTheme.typography.displayLarge
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "С возвращением!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Войдите, чтобы продолжить",
                style = MaterialTheme.typography.bodyLarge,
                color = Slate600
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; onClearError() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Email,
                        contentDescription = null,
                        tint = Slate500
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                shape = TrilooShapes.Sm
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; onClearError() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Пароль") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = Slate500
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (passwordVisible) "Скрыть пароль" else "Показать пароль"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                singleLine = true,
                shape = TrilooShapes.Sm
            )
            
            TextButton(
                onClick = onNavigateToForgotPassword,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = "Забыли пароль?",
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            AnimatedVisibility(
                visible = authState.error != null,
                enter = TrilooMotion.enterExpand(),
                exit = TrilooMotion.exitShrink()
            ) {
                Surface(
                    shape = TrilooShapes.Sm,
                    color = ErrorLight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Error,
                            contentDescription = null,
                            tint = Error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = authState.error ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Error
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            TrilooButton(
                text = "Войти",
                onClick = { onSignIn(email.trim(), password) },
                enabled = email.isNotBlank() && password.isNotBlank(),
                isLoading = authState.isLoading,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            Row(
                modifier = Modifier.padding(vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Нет аккаунта?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate600
                )
                TextButton(onClick = onNavigateToSignUp) {
                    Text(
                        text = "Зарегистрироваться",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Экран регистрации.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    onNavigateBack: () -> Unit,
    onSignUpSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val authState by viewModel.uiState.collectAsStateWithLifecycle()
    SignUpContent(
        authState = authState,
        onNavigateBack = onNavigateBack,
        onSignUpSuccess = onSignUpSuccess,
        onSignUp = { name, email, password -> viewModel.signUp(name, email, password) },
        onClearError = viewModel::clearError
    )
}

/**
 * Экран восстановления пароля.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpContent(
    authState: AuthUiState,
    onNavigateBack: () -> Unit,
    onSignUpSuccess: () -> Unit,
    onSignUp: (String, String, String) -> Unit,
    onClearError: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(authState.currentUser?.id) {
        if (authState.currentUser != null) {
            onSignUpSuccess()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Создать аккаунт",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Присоединяйтесь к Triloo",
                style = MaterialTheme.typography.bodyLarge,
                color = Slate600
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; onClearError() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Имя") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = Slate500
                    )
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                shape = TrilooShapes.Sm
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; onClearError() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Email,
                        contentDescription = null,
                        tint = Slate500
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                shape = TrilooShapes.Sm
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; onClearError() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Пароль") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = Slate500
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = null
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                shape = TrilooShapes.Sm,
                supportingText = {
                    Text("Минимум 6 символов")
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; onClearError() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Подтвердите пароль") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = Slate500
                    )
                },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                singleLine = true,
                shape = TrilooShapes.Sm,
                isError = confirmPassword.isNotEmpty() && password != confirmPassword,
                supportingText = {
                    if (confirmPassword.isNotEmpty() && password != confirmPassword) {
                        Text("Пароли не совпадают", color = Error)
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            TrilooButton(
                text = "Зарегистрироваться",
                onClick = { onSignUp(name.trim(), email.trim(), password) },
                enabled = name.isNotBlank() && email.isNotBlank() && 
                         password.length >= 6 && password == confirmPassword,
                isLoading = authState.isLoading,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            authState.error?.let { error ->
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = TrilooShapes.Sm,
                    color = ErrorLight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Error,
                            contentDescription = null,
                            tint = Error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = Error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Регистрируясь, вы соглашаетесь с\nУсловиями использования и Политикой конфиденциальности",
                style = MaterialTheme.typography.bodySmall,
                color = Slate500,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Экран-заглушка для предпросмотра восстановления пароля.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    onNavigateBack: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val authState by viewModel.uiState.collectAsStateWithLifecycle()
    ForgotPasswordContent(
        authState = authState,
        onNavigateBack = onNavigateBack,
        onSendReset = { email, onSuccess -> viewModel.sendPasswordReset(email, onSuccess) },
        onClearError = viewModel::clearError
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ForgotPasswordContent(
    authState: AuthUiState,
    onNavigateBack: () -> Unit,
    onSendReset: (String, () -> Unit) -> Unit,
    onClearError: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var isSent by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            AnimatedContent(
                targetState = isSent,
                transitionSpec = {
                    (fadeIn(
                        animationSpec = tween(
                            durationMillis = TrilooMotion.durationMedium,
                            easing = TrilooMotion.easingEmphasized
                        )
                    ) + slideInVertically(
                        animationSpec = tween(
                            durationMillis = TrilooMotion.durationMedium,
                            easing = TrilooMotion.easingEmphasized
                        ),
                        initialOffsetY = { it / 8 }
                    )) with (fadeOut(
                        animationSpec = tween(
                            durationMillis = TrilooMotion.durationShort,
                            easing = TrilooMotion.easingExit
                        )
                    ) + slideOutVertically(
                        animationSpec = tween(
                            durationMillis = TrilooMotion.durationShort,
                            easing = TrilooMotion.easingExit
                        ),
                        targetOffsetY = { -it / 10 }
                    ))
                },
                label = "resetState"
            ) { sent ->
                if (sent) {
                    Icon(
                        imageVector = Icons.Rounded.MarkEmailRead,
                        contentDescription = null,
                        tint = TealSecondary,
                        modifier = Modifier.size(80.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Проверьте почту",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Мы отправили инструкции по восстановлению пароля на $email",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Slate600,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    TrilooButton(
                        text = "Вернуться к входу",
                        onClick = onNavigateBack,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.LockReset,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(80.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Восстановление пароля",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Введите email, и мы отправим инструкции по восстановлению",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Slate600,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; onClearError() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Email") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Email,
                                contentDescription = null,
                                tint = Slate500
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true,
                        shape = TrilooShapes.Sm
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    TrilooButton(
                        text = "Отправить",
                        onClick = { onSendReset(email.trim()) { isSent = true } },
                        enabled = email.isNotBlank(),
                        isLoading = authState.isLoading,
                        modifier = Modifier.fillMaxWidth()
                    )

                    authState.error?.let { error ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = Error
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SignInContentPreview() {
    TrilooTheme {
        SignInContent(
            authState = PreviewData.authState.copy(error = "Неверный пароль"),
            onNavigateBack = {},
            onSignInSuccess = {},
            onNavigateToSignUp = {},
            onNavigateToForgotPassword = {},
            onSignIn = { _, _ -> },
            onClearError = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SignUpContentPreview() {
    TrilooTheme {
        SignUpContent(
            authState = PreviewData.authState.copy(error = "Email уже используется"),
            onNavigateBack = {},
            onSignUpSuccess = {},
            onSignUp = { _, _, _ -> },
            onClearError = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ForgotPasswordContentPreview() {
    TrilooTheme {
        ForgotPasswordContent(
            authState = PreviewData.authState,
            onNavigateBack = {},
            onSendReset = { _, onSuccess -> onSuccess() },
            onClearError = {}
        )
    }
}
