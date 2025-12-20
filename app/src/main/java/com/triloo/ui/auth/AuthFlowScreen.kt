package com.triloo.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

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
