package com.cashmemer.ui.members

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cashmemer.R
import com.cashmemer.core.data.CashMemerRepository
import com.cashmemer.core.model.Member
import com.cashmemer.ui.components.DangerIconButton
import com.cashmemer.ui.components.SectionCard
import com.cashmemer.ui.components.SectionTitle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** The member's picture, or a person outline when there isn't one. */
@Composable
private fun MemberAvatar(photoUri: String?, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (photoUri == null) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            AsyncImage(
                model = photoUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
    }
}

/**
 * Copies a picked image into the app's files directory and returns the new
 * location.
 *
 * Storing the gallery uri directly looks like it works and then quietly breaks:
 * the read permission is granted to this process only, so after a reboot every
 * member photo would vanish.
 */
private fun copyMemberPhoto(context: Context, source: Uri): String? = runCatching {
    val dir = File(context.filesDir, "members").apply { mkdirs() }
    val target = File(dir, "member-${System.currentTimeMillis()}.jpg")

    context.contentResolver.openInputStream(source)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    } ?: error("Could not read that image")

    Uri.fromFile(target).toString()
}.getOrNull()

class MembersViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CashMemerRepository.get(application)

    val members: StateFlow<List<Member>> = repository.observeMembers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(member: Member) {
        viewModelScope.launch { repository.saveMember(member) }
    }

    fun delete(member: Member) {
        viewModelScope.launch { repository.deleteMember(member) }
    }
}

@Composable
fun MembersScreen(viewModel: MembersViewModel = viewModel()) {
    val members by viewModel.members.collectAsState()
    var editing by remember { mutableStateOf<Member?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionTitle(stringResource(R.string.members_directory)) }

            items(members, key = { it.id }) { member ->
                SectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MemberAvatar(photoUri = member.photoUri, size = 56.dp)

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                        ) {
                            Text(member.name, style = MaterialTheme.typography.titleLarge)
                            Text(
                                member.phone,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        IconButton(onClick = { editing = member }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
                        }
                        DangerIconButton(
                            icon = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.delete_member),
                            onClick = { viewModel.delete(member) },
                        )
                    }
                }
            }

            if (members.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.members_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { editing = Member() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_member))
        }
    }

    editing?.let { member ->
        var name by remember(member.id) { mutableStateOf(member.name) }
        var phone by remember(member.id) { mutableStateOf(member.phone) }
        var email by remember(member.id) { mutableStateOf(member.email) }
        var address by remember(member.id) { mutableStateOf(member.address) }
        var photoUri by remember(member.id) { mutableStateOf(member.photoUri) }

        // The picture is copied into the app's own storage rather than stored
        // as a gallery uri, because that grant does not survive a reboot — the
        // photo would come back as a grey circle the next morning.
        val context = LocalContext.current
        val pickPhoto = rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { picked ->
            picked?.let { photoUri = copyMemberPhoto(context, it) ?: photoUri }
        }

        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(if (member.id == 0L) R.string.add_member else R.string.edit_member)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MemberAvatar(photoUri = photoUri, size = 64.dp)
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            TextButton(
                                onClick = {
                                    pickPhoto.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                }
                            ) {
                                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                                Text(
                                    text = stringResource(
                                        if (photoUri == null) R.string.add_photo
                                        else R.string.change_photo
                                    ),
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                            if (photoUri != null) {
                                TextButton(onClick = { photoUri = null }) {
                                    Text(
                                        text = stringResource(R.string.remove_photo),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.name)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text(stringResource(R.string.phone)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(stringResource(R.string.email)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text(stringResource(R.string.address)) },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.save(
                            member.copy(
                                name = name.trim(),
                                phone = phone.trim(),
                                email = email.trim(),
                                address = address.trim(),
                                photoUri = photoUri,
                            )
                        )
                        editing = null
                    },
                    enabled = name.isNotBlank(),
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
