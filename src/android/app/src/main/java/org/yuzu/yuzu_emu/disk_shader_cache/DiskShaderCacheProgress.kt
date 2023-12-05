// SPDX-FileCopyrightText: 2023 yuzu Emulator Project
// SPDX-License-Identifier: GPL-2.0-or-later

package org.yuzu.yuzu_emu.disk_shader_cache

import androidx.annotation.Keep
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.yuzu.yuzu_emu.NativeLibrary
import org.yuzu.yuzu_emu.R
import org.yuzu.yuzu_emu.activities.EmulationActivity
import org.yuzu.yuzu_emu.model.EmulationViewModel

@Keep
object DiskShaderCacheProgress {
    private lateinit var emulationViewModel: EmulationViewModel
    private lateinit var emulationActivity: EmulationActivity

    private fun prepareViewModel() {
        val activity = NativeLibrary.sEmulationActivity.get() as? EmulationActivity
            ?: throw IllegalStateException("EmulationActivity 不存在")
        emulationActivity = activity
        emulationViewModel = ViewModelProvider(emulationActivity)[EmulationViewModel::class.java]
    }

    @JvmStatic
    fun loadProgress(stage: Int, progress: Int, max: Int) {
        val activity = NativeLibrary.sEmulationActivity.get() as? EmulationActivity
            ?: return

        when (LoadCallbackStage.values()[stage]) {
            LoadCallbackStage.Prepare -> {
                prepareViewModel()
                showMessage(activity.getString(R.string.prepare_loading_message))
            }
            LoadCallbackStage.Build -> {
                GlobalScope.launch(Dispatchers.Main) {
                    updateProgressAsync(activity.getString(R.string.building_shaders), progress, max)
                }
            }
            LoadCallbackStage.Complete -> {}
        }
    }

    private suspend fun updateProgressAsync(title: String, progress: Int, max: Int) {
        withContext(Dispatchers.Main) {
            showMessage("$title：异步进度执行中")
        }

        // 模拟一些处理时间
        kotlinx.coroutines.delay(1000)

        withContext(Dispatchers.Main) {
            emulationViewModel.updateProgress(title, progress, max)
        }
    }

    private fun showMessage(message: String) {
        emulationActivity.showToast(message)
    }

    enum class LoadCallbackStage {
        Prepare, Build, Complete
    }
}
