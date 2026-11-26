package com.parapo.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parapo.auth.AuthGate
import com.parapo.storage.Profile
import com.parapo.storage.ProfileStore
import com.parapo.ui.components.PhotoCropDialog
import com.parapo.ui.components.ProfileAvatar
import com.parapo.ui.theme.ActionBlue
import com.parapo.ui.theme.DangerRed
import com.parapo.ui.theme.InkNavy
import com.parapo.ui.theme.MetaNavy
import com.parapo.ui.theme.ScreenBg
import com.parapo.ui.theme.SuccessGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ADDRESS_MAX = 120
private const val EMAIL_MAX = 60
private val FieldBorder = Color(0xFFD5DCE4)

/**
 * Profile (opened from the Home avatar): round photo, username, password, address and Gmail.
 * Picking a photo opens a circle cropper before it is saved.
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onUsernameChanged: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    val saved by ProfileStore.profile.collectAsState()
    val photo by ProfileStore.photo.collectAsState()

    var savedUsername by remember { mutableStateOf(AuthGate.getUsername(appContext)) }
    var username by remember { mutableStateOf(savedUsername) }
    var address by remember(saved) { mutableStateOf(saved.address) }
    var email by remember(saved) { mutableStateOf(saved.email) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var feedbackIsError by remember { mutableStateOf(false) }
    var cropSource by remember { mutableStateOf<Bitmap?>(null) }
    var showChangePassword by remember { mutableStateOf(false) }

    val usernameError = AuthGate.usernameError(username)
    val emailError = if (email.isBlank() || android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
        null
    } else {
        "Enter a valid email, like name@gmail.com"
    }
    val edited = username.trim() != savedUsername ||
        address.trim() != saved.address ||
        email.trim() != saved.email
    val canSave = edited && usernameError == null && emailError == null

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) { ProfileStore.loadForCrop(appContext, uri) }
                if (bmp != null) {
                    cropSource = bmp
                } else {
                    feedbackIsError = true
                    feedback = "Couldn't open that photo. Try another one."
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
    ) {
        // Header (white), same style as the other screens
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .statusBarsPadding()
                .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = InkNavy)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "My Profile",
                color = InkNavy,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (feedback != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = (if (feedbackIsError) DangerRed else SuccessGreen).copy(alpha = 0.14f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = feedback!!,
                            color = InkNavy,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { feedback = null }) {
                            Text("OK", color = ActionBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Photo card
            ProfileCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box {
                        ProfileAvatar(photo = photo, size = 112.dp)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(36.dp)
                                .background(Color.White, CircleShape)
                                .padding(3.dp)
                                .background(ActionBlue, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.CameraAlt,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(savedUsername, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = InkNavy)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (photo == null) "Upload photo" else "Change photo",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (photo != null) {
                        TextButton(onClick = {
                            ProfileStore.removePhoto(appContext)
                            feedbackIsError = false
                            feedback = "Profile photo removed"
                        }) {
                            Text("Remove photo", color = DangerRed, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Account info card: username, password, address, Gmail
            ProfileCard {
                Text("Account info", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = InkNavy)
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileField(
                        value = username,
                        onValueChange = { if (it.length <= AuthGate.USERNAME_MAX) username = it },
                        label = "Username",
                        icon = Icons.Filled.Person,
                        error = usernameError,
                        hint = "You use this name to log in"
                    )
                    // Password is never shown: tap Change to set a new one.
                    OutlinedTextField(
                        value = "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022",
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = ActionBlue) },
                        trailingIcon = {
                            TextButton(onClick = { showChangePassword = true }) {
                                Text("Change", color = ActionBlue, fontWeight = FontWeight.Bold)
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    ProfileField(
                        value = address,
                        onValueChange = { address = it.take(ADDRESS_MAX) },
                        label = "Address",
                        icon = Icons.Filled.LocationOn,
                        capitalization = KeyboardCapitalization.Sentences
                    )
                    ProfileField(
                        value = email,
                        onValueChange = { email = it.take(EMAIL_MAX) },
                        label = "Gmail",
                        icon = Icons.Filled.Email,
                        keyboardType = KeyboardType.Email,
                        error = emailError,
                        isLast = true,
                        onDone = { focus.clearFocus() }
                    )
                }
            }

            Button(
                onClick = {
                    focus.clearFocus()
                    var problem: String? = null
                    if (username.trim() != savedUsername) {
                        if (AuthGate.changeUsername(appContext, username)) {
                            savedUsername = username.trim()
                            onUsernameChanged(savedUsername)
                        } else {
                            problem = "Couldn't change the username."
                        }
                    }
                    ProfileStore.save(appContext, Profile(address = address.trim(), email = email.trim()))
                    feedbackIsError = problem != null
                    feedback = problem ?: "Profile saved"
                },
                enabled = canSave,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ActionBlue,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("Save changes", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    cropSource?.let { bmp ->
        PhotoCropDialog(
            source = bmp,
            onCancel = { cropSource = null },
            onConfirm = { cropped ->
                cropSource = null
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { ProfileStore.savePhoto(appContext, cropped) }
                    feedbackIsError = !ok
                    feedback = if (ok) "Profile photo updated" else "Couldn't save that photo. Try again."
                }
            }
        )
    }

    if (showChangePassword) {
        ChangePasswordDialog(
            onDismiss = { showChangePassword = false },
            onSubmit = { current, new, onError ->
                if (AuthGate.changePassword(appContext, current, new)) {
                    showChangePassword = false
                    feedbackIsError = false
                    feedback = "Password updated"
                } else {
                    onError("Current password is wrong.")
                }
            }
        )
    }
}

@Composable
private fun ProfileCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = InkNavy,
    unfocusedTextColor = InkNavy,
    focusedBorderColor = ActionBlue,
    unfocusedBorderColor = FieldBorder,
    focusedLabelColor = ActionBlue,
    unfocusedLabelColor = MetaNavy,
    cursorColor = ActionBlue,
    errorBorderColor = DangerRed,
    errorLabelColor = DangerRed,
    errorSupportingTextColor = DangerRed,
    focusedSupportingTextColor = MetaNavy,
    unfocusedSupportingTextColor = MetaNavy
)

@Composable
private fun ProfileField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    keyboardType: KeyboardType = KeyboardType.Text,
    error: String? = null,
    hint: String? = null,
    isLast: Boolean = false,
    onDone: () -> Unit = {}
) {
    val supporting = error ?: hint
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = ActionBlue) },
        isError = error != null,
        supportingText = if (supporting != null) {
            { Text(supporting) }
        } else null,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            keyboardType = keyboardType,
            imeAction = if (isLast) ImeAction.Done else ImeAction.Next
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onSubmit: (currentPassword: String, newPassword: String, onError: (String) -> Unit) -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { if (it.length <= AuthGate.PASSWORD_MAX) currentPassword = it },
                    label = { Text("Current password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { if (it.length <= AuthGate.PASSWORD_MAX) newPassword = it },
                    label = { Text("New password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { if (it.length <= AuthGate.PASSWORD_MAX) confirmPassword = it },
                    label = { Text("Confirm new password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                if (error != null) {
                    Text(error!!, color = DangerRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val problem = AuthGate.passwordError(newPassword)
                        ?: if (newPassword != confirmPassword) "New passwords don't match." else null
                    if (currentPassword.isEmpty()) {
                        error = "Enter your current password."
                    } else if (problem != null) {
                        error = problem
                    } else {
                        error = null
                        onSubmit(currentPassword, newPassword) { error = it }
                    }
                }
            ) { Text("Update") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

