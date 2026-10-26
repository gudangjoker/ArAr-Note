package com.gudangjoker.ararnote.ui

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gudangjoker.ararnote.R
import com.gudangjoker.ararnote.backup.BackupManager
import com.gudangjoker.ararnote.databinding.ActivitySettingsBinding
import com.gudangjoker.ararnote.security.BiometricHelper
import com.gudangjoker.ararnote.security.PasswordManager
import com.gudangjoker.ararnote.widget.NoteWidgetProvider
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var passwordManager: PasswordManager
    private lateinit var backupManager: BackupManager

    // Storage Access Framework (SAF) Launcher for saving backup to Google Drive / Cloud / Local
    private val createBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            performBackupToUri(uri)
        }
    }

    // Storage Access Framework (SAF) Launcher for browsing and restoring backup from Google Drive / Cloud / Local
    private val openBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            confirmAndRestoreFromUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        passwordManager = PasswordManager(this)
        backupManager = BackupManager(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_settings)

        updatePasswordStatus()
        updateBiometricStatus()

        binding.btnSettingPassword.setOnClickListener {
            handlePasswordSettingClick()
        }

        binding.btnSettingBiometric.setOnClickListener {
            handleBiometricToggle()
        }

        binding.btnSettingBackup.setOnClickListener {
            showBackupOptionsDialog()
        }

        binding.btnSettingRestore.setOnClickListener {
            showRestoreOptionsDialog()
        }
    }

    private fun updatePasswordStatus() {
        if (passwordManager.isPasswordSet()) {
            binding.textPasswordStatus.text = "Master Password is set (tap to change or remove)"
        } else {
            binding.textPasswordStatus.text = getString(R.string.pref_master_password_summary)
        }
    }

    private fun updateBiometricStatus() {
        val isAvailable = BiometricHelper.isBiometricAvailable(this)
        if (!isAvailable) {
            binding.switchBiometric.isEnabled = false
            binding.switchBiometric.isChecked = false
            binding.textBiometricStatus.text = "Biometric sensor not available or not enrolled"
        } else if (!passwordManager.isPasswordSet()) {
            binding.switchBiometric.isEnabled = false
            binding.switchBiometric.isChecked = false
            binding.textBiometricStatus.text = "Set a Master Password first to enable"
        } else {
            binding.switchBiometric.isEnabled = true
            val isEnabled = passwordManager.isBiometricEnabled()
            binding.switchBiometric.isChecked = isEnabled
            binding.textBiometricStatus.text = if (isEnabled) {
                "Enabled (Unlock with Fingerprint or Face)"
            } else {
                "Disabled (Tap to enable biometric unlock)"
            }
        }
    }

    private fun handleBiometricToggle() {
        if (!BiometricHelper.isBiometricAvailable(this)) {
            Toast.makeText(this, "Biometrics not available or not enrolled on this device", Toast.LENGTH_SHORT).show()
            return
        }

        if (!passwordManager.isPasswordSet()) {
            Toast.makeText(this, "Please set a Master Password first", Toast.LENGTH_SHORT).show()
            showSetPasswordDialog()
            return
        }

        if (passwordManager.isBiometricEnabled()) {
            // Disable
            passwordManager.setBiometricEnabled(false, null)
            updateBiometricStatus()
            Toast.makeText(this, "Biometric unlock disabled", Toast.LENGTH_SHORT).show()
        } else {
            // Enable: prompt password first, then biometric confirmation
            promptPasswordForBiometricEnable()
        }
    }

    private fun promptPasswordForBiometricEnable() {
        val view = layoutInflater.inflate(R.layout.dialog_password, null)
        val editPassword = view.findViewById<EditText>(R.id.edit_password)

        AlertDialog.Builder(this)
            .setTitle("Confirm Master Password")
            .setView(view)
            .setPositiveButton(R.string.action_done) { _, _ ->
                val entered = editPassword.text.toString()
                if (passwordManager.verifyPassword(entered)) {
                    BiometricHelper.showBiometricPrompt(
                        activity = this,
                        title = "Confirm Biometric Unlock",
                        subtitle = "Touch sensor to link biometric with Master Password",
                        negativeButtonText = getString(R.string.action_cancel),
                        onSuccess = {
                            passwordManager.setBiometricEnabled(true, entered)
                            updateBiometricStatus()
                            Toast.makeText(this, "Biometric unlock enabled successfully", Toast.LENGTH_SHORT).show()
                        },
                        onFallbackToPassword = {},
                        onError = { err ->
                            Toast.makeText(this, err, Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    Toast.makeText(this, R.string.msg_wrong_password, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun handlePasswordSettingClick() {
        if (passwordManager.isPasswordSet()) {
            val options = arrayOf("Change Master Password", "Remove Master Password")
            AlertDialog.Builder(this)
                .setTitle(R.string.pref_master_password)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> promptOldPasswordForChange()
                        1 -> promptOldPasswordForRemoval()
                    }
                }
                .show()
        } else {
            showSetPasswordDialog()
        }
    }

    private fun showSetPasswordDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_password, null)
        val editPassword = view.findViewById<EditText>(R.id.edit_password)
        val editConfirm = view.findViewById<EditText>(R.id.edit_confirm_password)
        editConfirm.visibility = View.VISIBLE

        AlertDialog.Builder(this)
            .setTitle("Set Master Password")
            .setView(view)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val p1 = editPassword.text.toString()
                val p2 = editConfirm.text.toString()
                if (p1.isNotBlank() && p1 == p2) {
                    passwordManager.setPassword(p1)
                    updatePasswordStatus()
                    updateBiometricStatus()
                    Toast.makeText(this, R.string.msg_password_set, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun promptOldPasswordForChange() {
        val view = layoutInflater.inflate(R.layout.dialog_password, null)
        val editPassword = view.findViewById<EditText>(R.id.edit_password)

        AlertDialog.Builder(this)
            .setTitle("Verify Current Password")
            .setView(view)
            .setPositiveButton(R.string.action_done) { _, _ ->
                val entered = editPassword.text.toString()
                if (passwordManager.verifyPassword(entered)) {
                    showSetPasswordDialog()
                } else {
                    Toast.makeText(this, R.string.msg_wrong_password, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun promptOldPasswordForRemoval() {
        val view = layoutInflater.inflate(R.layout.dialog_password, null)
        val editPassword = view.findViewById<EditText>(R.id.edit_password)

        AlertDialog.Builder(this)
            .setTitle("Remove Master Password")
            .setView(view)
            .setPositiveButton(R.string.action_done) { _, _ ->
                val entered = editPassword.text.toString()
                if (passwordManager.verifyPassword(entered)) {
                    passwordManager.clearPassword()
                    updatePasswordStatus()
                    updateBiometricStatus()
                    Toast.makeText(this, R.string.msg_password_removed, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.msg_wrong_password, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showBackupOptionsDialog() {
        val options = arrayOf(
            getString(R.string.backup_option_saf),
            getString(R.string.backup_option_internal)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.pref_backup)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                        val fileName = "ArArNote_backup_${dateFormat.format(Date())}.backup"
                        createBackupLauncher.launch(fileName)
                    }
                    1 -> {
                        performInternalBackup()
                    }
                }
            }
            .show()
    }

    private fun performBackupToUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                val count = contentResolver.openOutputStream(uri)?.use { outputStream ->
                    backupManager.writeBackupToStream(outputStream)
                } ?: 0
                Toast.makeText(
                    this@SettingsActivity,
                    "${getString(R.string.msg_backup_gdrive_success)} ($count notes)",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, R.string.msg_backup_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performInternalBackup() {
        lifecycleScope.launch {
            try {
                val backupFile = backupManager.createBackup()
                Toast.makeText(
                    this@SettingsActivity,
                    "Backup saved: ${backupFile.name}",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, R.string.msg_backup_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showRestoreOptionsDialog() {
        val options = arrayOf(
            getString(R.string.restore_option_saf),
            getString(R.string.restore_option_internal)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.pref_restore)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        openBackupLauncher.launch(arrayOf("*/*"))
                    }
                    1 -> {
                        showInternalRestoreSelectionDialog()
                    }
                }
            }
            .show()
    }

    private fun confirmAndRestoreFromUri(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_restore_confirm_title)
            .setMessage(R.string.dialog_restore_confirm_msg)
            .setPositiveButton(R.string.action_restore) { _, _ ->
                performRestoreFromUri(uri)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun performRestoreFromUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                val count = contentResolver.openInputStream(uri)?.use { inputStream ->
                    backupManager.restoreBackup(inputStream)
                } ?: 0
                NoteWidgetProvider.notifyWidgets(this@SettingsActivity)
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.msg_restore_count, count),
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, R.string.msg_restore_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showInternalRestoreSelectionDialog() {
        val backupDir = File(getExternalFilesDir(null), "backup")
        val files = backupDir.listFiles { _, name -> name.endsWith(".backup") }

        if (files.isNullOrEmpty()) {
            Toast.makeText(this, "No backup files found in internal storage", Toast.LENGTH_SHORT).show()
            return
        }

        val fileNames = files.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select Backup to Restore")
            .setItems(fileNames) { _, which ->
                restoreFromFile(files[which])
            }
            .show()
    }

    private fun restoreFromFile(file: File) {
        lifecycleScope.launch {
            try {
                val count = backupManager.restoreBackup(file)
                NoteWidgetProvider.notifyWidgets(this@SettingsActivity)
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.msg_restore_count, count),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, R.string.msg_restore_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
