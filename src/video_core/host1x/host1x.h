// SPDX-FileCopyrightText: 2021 yuzu Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

#pragma once

#include "common/common_types.h"

#include "common/address_space.h"
#include "video_core/cdma_pusher.h"
#include "video_core/host1x/gpu_device_memory_manager.h"
#include "video_core/host1x/syncpoint_manager.h"
#include "video_core/memory_manager.h"

namespace Core {
class System;
} // namespace Core

namespace Tegra::Host1x {
class Nvdec;

enum class ChannelType : u32 {
    MsEnc = 0,
    VIC = 1,
    GPU = 2,
    NvDec = 3,
    Display = 4,
    NvJpg = 5,
    TSec = 6,
    Max = 7,
};

class Host1x {
public:
    explicit Host1x(Core::System& system);
    ~Host1x();

    Core::System& System() {
        return system;
    }

    SyncpointManager& GetSyncpointManager() {
        return syncpoint_manager;
    }

    const SyncpointManager& GetSyncpointManager() const {
        return syncpoint_manager;
    }

    Tegra::MaxwellDeviceMemoryManager& MemoryManager() {
        return memory_manager;
    }

    const Tegra::MaxwellDeviceMemoryManager& MemoryManager() const {
        return memory_manager;
    }

    Tegra::MemoryManager& GMMU() {
        return gmmu_manager;
    }

    const Tegra::MemoryManager& GMMU() const {
        return gmmu_manager;
    }

    Common::FlatAllocator<u32, 0, 32>& Allocator() {
        return *allocator;
    }

    const Common::FlatAllocator<u32, 0, 32>& Allocator() const {
        return *allocator;
    }

    void StartDevice(s32 fd, ChannelType type, u32 syncpt);
    void StopDevice(s32 fd, ChannelType type);

    void PushEntries(s32 fd, ChCommandHeaderList&& entries) {
        auto it = devices.find(fd);
        if (it == devices.end()) {
            return;
        }
        it->second->PushEntries(std::move(entries));
    }

    Nvdec& GetLastNvdecDevice() {
        auto it = devices.find(last_nvdec_fd);
        ASSERT(it->second.get() != nullptr);
        return *reinterpret_cast<Nvdec*>(it->second.get());
    }

private:
    Core::System& system;
    SyncpointManager syncpoint_manager;
    Tegra::MaxwellDeviceMemoryManager memory_manager;
    Tegra::MemoryManager gmmu_manager;
    std::unique_ptr<Common::FlatAllocator<u32, 0, 32>> allocator;
    std::unordered_map<s32, std::unique_ptr<CDmaPusher>> devices;
    s32 last_nvdec_fd{};
};

} // namespace Tegra::Host1x
