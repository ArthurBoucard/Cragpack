package app.cragpack.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import app.cragpack.data.AccountRepository
import app.cragpack.data.AppGraph
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import app.cragpack.feature.explainFailure
import app.cragpack.data.SupabaseException
import app.cragpack.designsystem.component.CtBackHandler
import app.cragpack.designsystem.component.CtBottomSheet
import app.cragpack.designsystem.component.CtButton
import app.cragpack.designsystem.component.CtButtonSize
import app.cragpack.designsystem.component.CtButtonStyle
import app.cragpack.designsystem.component.CtIconButton
import app.cragpack.designsystem.component.CtScreen
import app.cragpack.designsystem.component.CtText
import app.cragpack.designsystem.component.CtTextField
import app.cragpack.designsystem.component.CtaDock
import app.cragpack.designsystem.component.TopBar
import app.cragpack.designsystem.icon.LucideIcon
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.tokens.SizeTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Why the account prompt opened: the action it continues once signed in (tech spec §2). */
enum class AccountReason(val title: String) {
    Invite("Create an account to invite your crew"),
    Join("Create an account to join the trip"),
    Share("Create an account to share a link"),
    Sync("Create an account to sync your phones"),
}

/** The account, for the Google button inside sheets and screens (provided by App()). */
val LocalAccount = staticCompositionLocalOf<AccountRepository> { error("App() provides LocalAccount") }

/**
 * Account prompt (13): only before an action that needs others or the cloud (D4). ✕ = Not now.
 * Google signs in right here, then [onSignedIn] (by default the sheet just closes; Join keeps its pending join);
 * email opens Create account.
 * ponytail: Sign in with Apple needs a paid Apple developer account; the Terms / Privacy line waits for the policy (Phase 5).
 */
@Composable
fun BoxScope.AccountPromptSheet(reason: AccountReason?, onDismiss: () -> Unit, onEmail: () -> Unit, onSignedIn: () -> Unit = onDismiss) {
    CtBottomSheet(reason != null, reason?.title.orEmpty(), onDismiss) {
        CtText("Your trips on this phone come with you.", color = CragpackTheme.colors.ink2)
        GoogleButton(onSignedIn)
        CtButton("Continue with email", onEmail, style = CtButtonStyle.Ghost, size = CtButtonSize.Wide, icon = LucideIcon.Mail)
    }
}

/** "Continue with Google", where this phone has Google's sign-in (README: Google sign-in). Shows its own errors. */
@Composable
private fun GoogleButton(onSignedIn: () -> Unit) {
    val google = rememberGoogleSignIn() ?: return
    val account = LocalAccount.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // ponytail: plain button as in mock 13; Google's brand rules want their "G" logo, check before release.
    CtButton("Continue with Google", {
        scope.launch {
            busy = true
            error = null
            error = try {
                google.idToken()?.let { account.signInWithGoogle(it.token, it.nonce); onSignedIn() }
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: GoogleSignInUnavailable) {
                e.message
            } catch (e: Exception) {
                explainFailure(e, "sign in with Google")
            } finally {
                busy = false
            }
        }
    }, size = CtButtonSize.Wide, enabled = !busy)
    error?.let { CtText(it, color = CragpackTheme.colors.dangerInk) }
}

enum class AccountMode(val title: String, val action: String) {
    Create("Create account", "Create account"),
    SignIn("Sign in", "Sign in"),
    CheckEmail("Check your email", "Sign in"),
}

data class AccountForm(
    val mode: AccountMode = AccountMode.Create,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    /** What to do next, or what went wrong. */
    val message: String? = null,
    val isError: Boolean = false,
) {
    val canSubmit get() = !busy && email.isNotBlank() && password.isNotEmpty() && (mode != AccountMode.Create || name.isNotBlank())
}

class AccountViewModel(private val graph: AppGraph) : ViewModel() {
    var form by mutableStateOf(AccountForm())

    fun switchMode(mode: AccountMode) {
        form = form.copy(mode = mode, message = null)
    }

    /** True once signed in. */
    suspend fun submit(): Boolean {
        val f = form
        form = f.copy(busy = true, message = null)
        return try {
            val signedIn = when (f.mode) {
                AccountMode.Create -> graph.account.signUp(f.name, f.email, f.password)
                else -> true.also { graph.account.signIn(f.email, f.password) }
            }
            if (!signedIn) form = form.copy(mode = AccountMode.CheckEmail)
            signedIn
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            form = form.copy(message = explain(e), isError = true)
            false
        } finally {
            form = form.copy(busy = false)
        }
    }

    suspend fun resend() {
        form = runCatching { graph.account.resendConfirmation(form.email) }.fold(
            { form.copy(message = "Sent again. Check your spam folder too.", isError = false) },
            { form.copy(message = explain(it), isError = true) },
        )
    }

    private fun explain(e: Throwable) = when {
        e is SupabaseException && e.code == "email_not_confirmed" -> "Your email isn't confirmed yet. Tap the link we sent to ${form.email.trim()}."
        e is SupabaseException -> e.message.orEmpty()
        else -> "Can't reach Cragpack. Check your connection and try again."
    }
}

/**
 * Create account (14), with its two design gaps: "I have an account" (sign in) and, while Supabase waits
 * for the email to be confirmed, a Check your email step that signs in with what was just typed.
 * ponytail: the form lives in memory; if the phone kills the app while you're in your mail app,
 * you come back to an empty form and sign in from "I have an account".
 */
@Composable
fun AccountScreen(vm: AccountViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val form = vm.form
    val back = { if (form.mode == AccountMode.CheckEmail) vm.switchMode(AccountMode.Create) else onBack() }
    CtBackHandler(onBack = back)
    CtScreen(
        dock = {
            CtaDock(
                form.mode.action,
                LucideIcon.ArrowRight,
                enabled = form.canSubmit,
                onClick = { scope.launch { if (vm.submit()) onDone() } },
            )
        },
    ) { bottom ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = SizeTokens.edge),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TopBar(
                leading = { CtIconButton(LucideIcon.ChevronLeft, "Back", back) },
                center = { CtText(form.mode.title, style = CragpackTheme.type.title) },
            )
            if (form.mode == AccountMode.CheckEmail) {
                CtText("We sent a link to ${form.email.trim()}. Tap it to confirm your account, then come back here and sign in.")
                CtButton("Resend email", { scope.launch { vm.resend() } }, style = CtButtonStyle.Ghost, size = CtButtonSize.Sm)
            } else {
                Fields(vm, form)
                if (form.mode == AccountMode.SignIn) GoogleButton(onSignedIn = onDone) // design gap: "plus Apple and Google"
                val other = if (form.mode == AccountMode.Create) AccountMode.SignIn else AccountMode.Create
                CtButton(
                    if (other == AccountMode.SignIn) "I have an account" else "Create an account",
                    { vm.switchMode(other) },
                    style = CtButtonStyle.Ghost,
                    size = CtButtonSize.Sm,
                )
            }
            form.message?.let {
                CtText(it, Modifier.padding(horizontal = 8.dp), color = if (form.isError) CragpackTheme.colors.dangerInk else CragpackTheme.colors.ink2)
            }
            Spacer(Modifier.height(bottom.calculateBottomPadding()))
        }
    }
}

@Composable
private fun Fields(vm: AccountViewModel, form: AccountForm) {
    if (form.mode == AccountMode.Create) {
        CtTextField(
            form.name, { vm.form = form.copy(name = it) }, "Name",
            placeholder = "What your crew calls you",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
    }
    CtTextField(
        form.email, { vm.form = form.copy(email = it) }, "Email",
        placeholder = "you@example.com",
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
    )
    CtTextField(
        form.password, { vm.form = form.copy(password = it) }, "Password",
        placeholder = if (form.mode == AccountMode.Create) "At least 6 characters" else "",
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        visualTransformation = PasswordVisualTransformation(),
    )
}
