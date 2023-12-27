// SPDX-FileCopyrightText: 2023 yuzu Emulator Project
// SPDX-License-Identifier: GPL-2.0-or-later

package org.yuzu.yuzu_emu.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.navigation.NavigationBarView
import java.io.File
import java.io.FilenameFilter
import kotlinx.coroutines.launch
import org.yuzu.yuzu_emu.HomeNavigationDirections
import org.yuzu.yuzu_emu.NativeLibrary
import org.yuzu.yuzu_emu.R
import org.yuzu.yuzu_emu.activities.EmulationActivity
import org.yuzu.yuzu_emu.databinding.ActivityMainBinding
import org.yuzu.yuzu_emu.features.settings.model.Settings
import org.yuzu.yuzu_emu.fragments.AddGameFolderDialogFragment
import org.yuzu.yuzu_emu.fragments.IndeterminateProgressDialogFragment
import org.yuzu.yuzu_emu.fragments.MessageDialogFragment
import org.yuzu.yuzu_emu.model.AddonViewModel
import org.yuzu.yuzu_emu.model.GamesViewModel
import org.yuzu.yuzu_emu.model.HomeViewModel
import org.yuzu.yuzu_emu.model.TaskState
import org.yuzu.yuzu_emu.model.TaskViewModel
import org.yuzu.yuzu_emu.utils.*
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import org.yuzu.yuzu_emu.UpdateManager

class MainActivity : AppCompatActivity(), ThemeProvider {
    private lateinit var binding: ActivityMainBinding

    private val homeViewModel: HomeViewModel by viewModels()
    private val gamesViewModel: GamesViewModel by viewModels()
    private val taskViewModel: TaskViewModel by viewModels()
    private val addonViewModel: AddonViewModel by viewModels()

    override var themeId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !DirectoryInitialization.areDirectoriesReady }

        ThemeHelper.setTheme(this)

        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val updateManager = UpdateManager(this)
        updateManager.checkForUpdates()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        window.statusBarColor =
            ContextCompat.getColor(applicationContext, android.R.color.transparent)
        window.navigationBarColor =
            ContextCompat.getColor(applicationContext, android.R.color.transparent)

        binding.statusBarShade.setBackgroundColor(
            ThemeHelper.getColorWithOpacity(
                MaterialColors.getColor(
                    binding.root,
                    com.google.android.material.R.attr.colorSurface
                ),
                ThemeHelper.SYSTEM_BAR_ALPHA
            )
        )
        if (InsetsHelper.getSystemGestureType(applicationContext) !=
            InsetsHelper.GESTURE_NAVIGATION
        ) {
            binding.navigationBarShade.setBackgroundColor(
                ThemeHelper.getColorWithOpacity(
                    MaterialColors.getColor(
                        binding.root,
                        com.google.android.material.R.attr.colorSurface
                    ),
                    ThemeHelper.SYSTEM_BAR_ALPHA
                )
            )
        }

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.fragment_container) as NavHostFragment
        setUpNavigation(navHostFragment.navController)
        (binding.navigationView as NavigationBarView).setOnItemReselectedListener {
            when (it.itemId) {
                R.id.gamesFragment -> gamesViewModel.setShouldScrollToTop(true)
                R.id.searchFragment -> gamesViewModel.setSearchFocused(true)
                R.id.homeSettingsFragment -> {
                    val action = HomeNavigationDirections.actionGlobalSettingsActivity(
                        null,
                        Settings.MenuTag.SECTION_ROOT
                    )
                    navHostFragment.navController.navigate(action)
                }
            }
        }

        // Prevents navigation from being drawn for a short time on recreation if set to hidden
        if (!homeViewModel.navigationVisible.value.first) {
            binding.navigationView.visibility = View.INVISIBLE
            binding.statusBarShade.visibility = View.INVISIBLE
        }

        lifecycleScope.apply {
            launch {
                repeatOnLifecycle(Lifecycle.State.CREATED) {
                    homeViewModel.navigationVisible.collect { showNavigation(it.first, it.second) }
                }
            }
            launch {
                repeatOnLifecycle(Lifecycle.State.CREATED) {
                    homeViewModel.statusBarShadeVisible.collect { showStatusBarShade(it) }
                }
            }
            launch {
                repeatOnLifecycle(Lifecycle.State.CREATED) {
                    homeViewModel.contentToInstall.collect {
                        if (it != null) {
                            installContent(it)
                            homeViewModel.setContentToInstall(null)
                        }
                    }
                }
            }
        }

        // Dismiss previous notifications (should not happen unless a crash occurred)
        EmulationActivity.stopForegroundService(this)

        setInsets()
    }

    fun finishSetup(navController: NavController) {
        navController.navigate(R.id.action_firstTimeSetupFragment_to_gamesFragment)
        (binding.navigationView as NavigationBarView).setupWithNavController(navController)
        showNavigation(visible = true, animated = true)
    }

    private fun setUpNavigation(navController: NavController) {
        val firstTimeSetup = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            .getBoolean(Settings.PREF_FIRST_APP_LAUNCH, true)

        if (firstTimeSetup && !homeViewModel.navigatedToSetup) {
            navController.navigate(R.id.firstTimeSetupFragment)
            homeViewModel.navigatedToSetup = true
        } else {
            (binding.navigationView as NavigationBarView).setupWithNavController(navController)
        }
    }

    private fun showNavigation(visible: Boolean, animated: Boolean) {
        if (!animated) {
            if (visible) {
                binding.navigationView.visibility = View.VISIBLE
            } else {
                binding.navigationView.visibility = View.INVISIBLE
            }
            return
        }

        val smallLayout = resources.getBoolean(R.bool.small_layout)
        binding.navigationView.animate().apply {
            if (visible) {
                binding.navigationView.visibility = View.VISIBLE
                duration = 300
                interpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)

                if (smallLayout) {
                    binding.navigationView.translationY =
                        binding.navigationView.height.toFloat() * 2
                    translationY(0f)
                } else {
                    if (ViewCompat.getLayoutDirection(binding.navigationView) ==
                        ViewCompat.LAYOUT_DIRECTION_LTR
                    ) {
                        binding.navigationView.translationX =
                            binding.navigationView.width.toFloat() * -2
                        translationX(0f)
                    } else {
                        binding.navigationView.translationX =
                            binding.navigationView.width.toFloat() * 2
                        translationX(0f)
                    }
                }
            } else {
                duration = 300
                interpolator = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)

                if (smallLayout) {
                    translationY(binding.navigationView.height.toFloat() * 2)
                } else {
                    if (ViewCompat.getLayoutDirection(binding.navigationView) ==
                        ViewCompat.LAYOUT_DIRECTION_LTR
                    ) {
                        translationX(binding.navigationView.width.toFloat() * -2)
                    } else {
                        translationX(binding.navigationView.width.toFloat() * 2)
                    }
                }
            }
        }.withEndAction {
            if (!visible) {
                binding.navigationView.visibility = View.INVISIBLE
            }
        }.start()
    }

    private fun showStatusBarShade(visible: Boolean) {
        binding.statusBarShade.animate().apply {
            if (visible) {
                binding.statusBarShade.visibility = View.VISIBLE
                binding.statusBarShade.translationY = binding.statusBarShade.height.toFloat() * -2
                duration = 300
                translationY(0f)
                interpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
            } else {
                duration = 300
                translationY(binding.navigationView.height.toFloat() * -2)
                interpolator = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
            }
        }.withEndAction {
            if (!visible) {
                binding.statusBarShade.visibility = View.INVISIBLE
            }
        }.start()
    }

    override fun onResume() {
        ThemeHelper.setCorrectTheme(this)
        super.onResume()
    }

    override fun onDestroy() {
        EmulationActivity.stopForegroundService(this)
        super.onDestroy()
    }

    private fun setInsets() =
        ViewCompat.setOnApplyWindowInsetsListener(
            binding.root
        ) { _: View, windowInsets: WindowInsetsCompat ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val mlpStatusShade = binding.statusBarShade.layoutParams as MarginLayoutParams
            mlpStatusShade.height = insets.top
            binding.statusBarShade.layoutParams = mlpStatusShade

            // The only situation where we care to have a nav bar shade is when it's at the bottom
            // of the screen where scrolling list elements can go behind it.
            val mlpNavShade = binding.navigationBarShade.layoutParams as MarginLayoutParams
            mlpNavShade.height = insets.bottom
            binding.navigationBarShade.layoutParams = mlpNavShade

            windowInsets
        }

    override fun setTheme(resId: Int) {
        super.setTheme(resId)
        themeId = resId
    }

    val getGamesDirectory =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { result ->
            if (result != null) {
                processGamesDir(result)
            }
        }

    fun processGamesDir(result: Uri) {
        contentResolver.takePersistableUriPermission(
            result,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        val uriString = result.toString()
        val folder = gamesViewModel.folders.value.firstOrNull { it.uriString == uriString }
        if (folder != null) {
            Toast.makeText(
                applicationContext,
                R.string.folder_already_added,
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        AddGameFolderDialogFragment.newInstance(uriString)
            .show(supportFragmentManager, AddGameFolderDialogFragment.TAG)
    }

    val getProdKey =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { result ->
            if (result != null) {
                processKey(result)
            }
        }

    fun processKey(result: Uri): Boolean {
        if (FileUtil.getExtension(result) != "keys") {
            MessageDialogFragment.newInstance(
                this,
                titleId = R.string.reading_keys_failure,
                descriptionId = R.string.install_prod_keys_failure_extension_description
            ).show(supportFragmentManager, MessageDialogFragment.TAG)
            return false
        }

        contentResolver.takePersistableUriPermission(
            result,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        val dstPath = DirectoryInitialization.userDirectory + "/keys/"
        if (FileUtil.copyUriToInternalStorage(
                result,
                dstPath,
                "prod.keys"
            ) != null
        ) {
            if (NativeLibrary.reloadKeys()) {
                Toast.makeText(
                    applicationContext,
                    R.string.install_keys_success,
                    Toast.LENGTH_SHORT
                ).show()
                gamesViewModel.reloadGames(true)
                return true
            } else {
                MessageDialogFragment.newInstance(
                    this,
                    titleId = R.string.invalid_keys_error,
                    descriptionId = R.string.install_keys_failure_description,
                    helpLinkId = R.string.dumping_keys_quickstart_link
                ).show(supportFragmentManager, MessageDialogFragment.TAG)
                return false
            }
        }
        return false
    }

    val getFirmware =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { result ->
            if (result == null) {
                return@registerForActivityResult
            }

            val inputZip = contentResolver.openInputStream(result)
            if (inputZip == null) {
                Toast.makeText(
                    applicationContext,
                    getString(R.string.fatal_error),
                    Toast.LENGTH_LONG
                ).show()
                return@registerForActivityResult
            }

            val filterNCA = FilenameFilter { _, dirName -> dirName.endsWith(".nca") }

            val firmwarePath =
                File(DirectoryInitialization.userDirectory + "/nand/system/Contents/registered/")
            val cacheFirmwareDir = File("${cacheDir.path}/registered/")

            val task: () -> Any = {
                var messageToShow: Any
                try {
                    FileUtil.unzipToInternalStorage(BufferedInputStream(inputZip), cacheFirmwareDir)
                    val unfilteredNumOfFiles = cacheFirmwareDir.list()?.size ?: -1
                    val filteredNumOfFiles = cacheFirmwareDir.list(filterNCA)?.size ?: -2
                    messageToShow = if (unfilteredNumOfFiles != filteredNumOfFiles) {
                        MessageDialogFragment.newInstance(
                            this,
                            titleId = R.string.firmware_installed_failure,
                            descriptionId = R.string.firmware_installed_failure_description
                        )
                    } else {
                        firmwarePath.deleteRecursively()
                        cacheFirmwareDir.copyRecursively(firmwarePath, true)
                        NativeLibrary.initializeSystem(true)
                        getString(R.string.save_file_imported_success)
                    }
                } catch (e: Exception) {
                    messageToShow = getString(R.string.fatal_error)
                } finally {
                    cacheFirmwareDir.deleteRecursively()
                }
                messageToShow
            }

            IndeterminateProgressDialogFragment.newInstance(
                this,
                R.string.firmware_installing,
                task = task
            ).show(supportFragmentManager, IndeterminateProgressDialogFragment.TAG)
        }

    val getAmiiboKey =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { result ->
            if (result == null) {
                return@registerForActivityResult
            }

            if (FileUtil.getExtension(result) != "bin") {
                MessageDialogFragment.newInstance(
                    this,
                    titleId = R.string.reading_keys_failure,
                    descriptionId = R.string.install_amiibo_keys_failure_extension_description
                ).show(supportFragmentManager, MessageDialogFragment.TAG)
                return@registerForActivityResult
            }

            contentResolver.takePersistableUriPermission(
                result,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            val dstPath = DirectoryInitialization.userDirectory + "/keys/"
            if (FileUtil.copyUriToInternalStorage(
                    result,
                    dstPath,
                    "key_retail.bin"
                ) != null
            ) {
                if (NativeLibrary.reloadKeys()) {
                    Toast.makeText(
                        applicationContext,
                        R.string.install_keys_success,
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    MessageDialogFragment.newInstance(
                        this,
                        titleId = R.string.invalid_keys_error,
                        descriptionId = R.string.install_keys_failure_description,
                        helpLinkId = R.string.dumping_keys_quickstart_link
                    ).show(supportFragmentManager, MessageDialogFragment.TAG)
                }
            }
        }

    val installGameUpdate = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { documents: List<Uri> ->
        if (documents.isEmpty()) {
            return@registerForActivityResult
        }

        if (addonViewModel.game == null) {
            installContent(documents)
            return@registerForActivityResult
        }

        IndeterminateProgressDialogFragment.newInstance(
            this@MainActivity,
            R.string.verifying_content,
            false
        ) {
            var updatesMatchProgram = true
            for (document in documents) {
                val valid = NativeLibrary.doesUpdateMatchProgram(
                    addonViewModel.game!!.programId,
                    document.toString()
                )
                if (!valid) {
                    updatesMatchProgram = false
                    break
                }
            }

            if (updatesMatchProgram) {
                homeViewModel.setContentToInstall(documents)
            } else {
                MessageDialogFragment.newInstance(
                    this@MainActivity,
                    titleId = R.string.content_install_notice,
                    descriptionId = R.string.content_install_notice_description,
                    positiveAction = { homeViewModel.setContentToInstall(documents) }
                )
            }
        }.show(supportFragmentManager, IndeterminateProgressDialogFragment.TAG)
    }

    private fun installContent(documents: List<Uri>) {
        IndeterminateProgressDialogFragment.newInstance(
            this@MainActivity,
            R.string.installing_game_content
        ) {
            var installSuccess = 0
            var installOverwrite = 0
            var errorBaseGame = 0
            var errorExtension = 0
            var errorOther = 0
            documents.forEach {
                when (
                    NativeLibrary.installFileToNand(
                        it.toString(),
                        FileUtil.getExtension(it)
                    )
                ) {
                    NativeLibrary.InstallFileToNandResult.Success -> {
                        installSuccess += 1
                    }

                    NativeLibrary.InstallFileToNandResult.SuccessFileOverwritten -> {
                        installOverwrite += 1
                    }

                    NativeLibrary.InstallFileToNandResult.ErrorBaseGame -> {
                        errorBaseGame += 1
                    }

                    NativeLibrary.InstallFileToNandResult.ErrorFilenameExtension -> {
                        errorExtension += 1
                    }

                    else -> {
                        errorOther += 1
                    }
                }
            }

            addonViewModel.refreshAddons()

            val separator = System.getProperty("line.separator") ?: "\n"
            val installResult = StringBuilder()
            if (installSuccess > 0) {
                installResult.append(
                    getString(
                        R.string.install_game_content_success_install,
                        installSuccess
                    )
                )
                installResult.append(separator)
            }
            if (installOverwrite > 0) {
                installResult.append(
                    getString(
                        R.string.install_game_content_success_overwrite,
                        installOverwrite
                    )
                )
                installResult.append(separator)
            }
            val errorTotal: Int = errorBaseGame + errorExtension + errorOther
            if (errorTotal > 0) {
                installResult.append(separator)
                installResult.append(
                    getString(
                        R.string.install_game_content_failed_count,
                        errorTotal
                    )
                )
                installResult.append(separator)
                if (errorBaseGame > 0) {
                    installResult.append(separator)
                    installResult.append(
                        getString(R.string.install_game_content_failure_base)
                    )
                    installResult.append(separator)
                }
                if (errorExtension > 0) {
                    installResult.append(separator)
                    installResult.append(
                        getString(R.string.install_game_content_failure_file_extension)
                    )
                    installResult.append(separator)
                }
                if (errorOther > 0) {
                    installResult.append(
                        getString(R.string.install_game_content_failure_description)
                    )
                    installResult.append(separator)
                }
                return@newInstance MessageDialogFragment.newInstance(
                    this,
                    titleId = R.string.install_game_content_failure,
                    descriptionString = installResult.toString().trim(),
                    helpLinkId = R.string.install_game_content_help_link
                )
            } else {
                return@newInstance MessageDialogFragment.newInstance(
                    this,
                    titleId = R.string.install_game_content_success,
                    descriptionString = installResult.toString().trim()
                )
            }
        }.show(supportFragmentManager, IndeterminateProgressDialogFragment.TAG)
    }

    val exportUserData = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { result ->
        if (result == null) {
            return@registerForActivityResult
        }

        IndeterminateProgressDialogFragment.newInstance(
            this,
            R.string.exporting_user_data,
            true
        ) {
            val zipResult = FileUtil.zipFromInternalStorage(
                File(DirectoryInitialization.userDirectory!!),
                DirectoryInitialization.userDirectory!!,
                BufferedOutputStream(contentResolver.openOutputStream(result)),
                taskViewModel.cancelled
            )
            return@newInstance when (zipResult) {
                TaskState.Completed -> getString(R.string.user_data_export_success)
                TaskState.Failed -> R.string.export_failed
                TaskState.Cancelled -> R.string.user_data_export_cancelled
            }
        }.show(supportFragmentManager, IndeterminateProgressDialogFragment.TAG)
    }

    val importUserData =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { result ->
            if (result == null) {
                return@registerForActivityResult
            }

            IndeterminateProgressDialogFragment.newInstance(
                this,
                R.string.importing_user_data
            ) {
                val checkStream =
                    ZipInputStream(BufferedInputStream(contentResolver.openInputStream(result)))
                var isYuzuBackup = false
                checkStream.use { stream ->
                    var ze: ZipEntry? = null
                    while (stream.nextEntry?.also { ze = it } != null) {
                        val itemName = ze!!.name.trim()
                        if (itemName == "/config/config.ini" || itemName == "config/config.ini") {
                            isYuzuBackup = true
                            return@use
                        }
                    }
                }
                if (!isYuzuBackup) {
                    return@newInstance MessageDialogFragment.newInstance(
                        this,
                        titleId = R.string.invalid_yuzu_backup,
                        descriptionId = R.string.user_data_import_failed_description
                    )
                }

                // Clear existing user data
                NativeConfig.unloadGlobalConfig()
                File(DirectoryInitialization.userDirectory!!).deleteRecursively()

                // Copy archive to internal storage
                try {
                    FileUtil.unzipToInternalStorage(
                        BufferedInputStream(contentResolver.openInputStream(result)),
                        File(DirectoryInitialization.userDirectory!!)
                    )
                } catch (e: Exception) {
                    return@newInstance MessageDialogFragment.newInstance(
                        this,
                        titleId = R.string.import_failed,
                        descriptionId = R.string.user_data_import_failed_description
                    )
                }

                // Reinitialize relevant data
                NativeLibrary.initializeSystem(true)
                NativeConfig.initializeGlobalConfig()
                gamesViewModel.reloadGames(false)

                return@newInstance getString(R.string.user_data_import_success)
            }.show(supportFragmentManager, IndeterminateProgressDialogFragment.TAG)
        }
}
