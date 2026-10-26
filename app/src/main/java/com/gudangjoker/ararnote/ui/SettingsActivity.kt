package com.gudangjoker.ararnote.ui

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gudangjoker.ararnote.R
import com.gudangjoker.ararnote.backup.BackupManager
import com.gudangjoker.ararnote.databinding.ActivitySettingsBinding
import com.gudangjoker.ararnote.security.PasswordManager
import kotlinx.coroutines.launch
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var passwordManager: PasswordManager
    private lateinit var backupManager: BackupManager

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

        binding.btnSettingPassword.setOnClickListener {
            handlePasswordSettingClick()
        }

        binding.btnSettingBackup.setOnClickListener {
            performBackup()
        }

        binding.btnSettingRestore.setOnClickListener {
            showRestoreSelectionDialog()
        }
    }

    private fun updatePasswordStatus() {
        if (passwordManager.isPasswordSet()) {
            binding.textPasswordStatus.text = "Master Password is set (tap to change or remove)"
        } else {
            binding.textPasswordStatus.text = getString(R.string.pref_master_password_summary)
        }
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
                    Toast.makeText(this, R.string.msg_password_removed, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.msg_wrong_password, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun performBackup() {
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

    private fun showRestoreSelectionDialog() {
        val backupDir = File(getExternalFilesDir(null), "backup")
        val files = backupDir.listFiles { _, name -> name.endsWith(".backup") }

        if (files.isNullOrEmpty()) {
            Toast.makeText(this, "No backup files found in storage", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(
                    this@SettingsActivity,
                    "Successfully restored $count notes",
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
