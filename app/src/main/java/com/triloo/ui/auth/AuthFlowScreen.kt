package com.triloo.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.triloo.ui.PreviewData
import com.triloo.ui.theme.TrilooTheme

@Composable
fun AuthFlowScreen(
    onNavigateBack: () -> Unit,
    onAuthComplete: () -> Unit
) {
    var step by rememberSaveable { mutableStateOf(AuthStep.SignIn) }

    when (step) {
        AuthStep.SignIn -> SignInScreen(
            onNavigateBack = onNavigateBack,
            onSignInSuccess = onAuthComplete,
            onNavigateToSignUp = { step = AuthStep.SignUp },
            onNavigateToForgotPassword = { step = AuthStep.ForgotPassword }
        )
        AuthStep.SignUp -> SignUpScreen(
            onNavigateBack = { step = AuthStep.SignIn },
            onSignUpSuccess = onAuthComplete
        )
        AuthStep.ForgotPassword -> ForgotPasswordScreen(
            onNavigateBack = { step = AuthStep.SignIn }
        )
    }
}

private enum class AuthStep {
    SignIn,
    SignUp,
    ForgotPassword
}

@Preview(showBackground = true)
@Composable
private fun AuthFlowScreenPreview() {
    TrilooTheme {
        var step by rememberSaveable { mutableStateOf(AuthStep.SignIn) }
        when (step) {
            AuthStep.SignIn -> SignInContent(
                authState = PreviewData.authState,
                onNavigateBack = {},
                onSignInSuccess = {},
                onNavigateToSignUp = { step = AuthStep.SignUp },
                onNavigateToForgotPassword = { step = AuthStep.ForgotPassword },
                onSignIn = { _, _ -> },
                onClearError = {}
            )
            AuthStep.SignUp -> SignUpContent(
                authState = PreviewData.authState,
                onNavigateBack = { step = AuthStep.SignIn },
                onSignUpSuccess = {},
                onSignUp = { _, _, _ -> },
                onClearError = {}
            )
            AuthStep.ForgotPassword -> ForgotPasswordContent(
                authState = PreviewData.authState,
                onNavigateBack = { step = AuthStep.SignIn },
                onSendReset = { _, _ -> },
                onClearError = {}
            )
        }
    }
}
