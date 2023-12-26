// SPDX-FileCopyrightText: Copyright 2023 yuzu Emulator Project
// SPDX-License-Identifier: GPL-2.0-or-later

#include "common/assert.h"
#include "common/heap_tracker.h"

namespace Common {

namespace {

constexpr size_t MaxResidentMapCount = 0x8000;

} // namespace

HeapTracker::HeapTracker(Common::HostMemory& buffer) : m_buffer(buffer) {}
HeapTracker::~HeapTracker() = default;

void HeapTracker::Map(size_t virtual_offset, size_t host_offset, size_t length,
                      MemoryPermission perm, bool is_separate_heap) {
    // When mapping other memory, map pages immediately.
    if (!is_separate_heap) {
        m_buffer.Map(virtual_offset, host_offset, length, perm, false);
        return;
    }

    {
        // We are mapping part of a separate heap.
        std::scoped_lock lk{m_lock};

        auto* map = new SeparateHeapMap{
            .vaddr = virtual_offset,
            .paddr = host_offset,
            .size = length,
            .map_id = m_next_map_id++,
            .tick = m_tick++,
            .perm = perm,
            .is_resident = false,
        };

        // Insert into mappings.
        m_mappings.insert(*map);
    }

    // Finally, map.
    this->DeferredMapSeparateHeap(virtual_offset);
}

void HeapTracker::Unmap(size_t virtual_offset, size_t size, bool is_separate_heap) {
    // If this is a separate heap...
    if (is_separate_heap) {
        std::scoped_lock lk{m_rebuild_lock, m_lock};

        const SeparateHeapMap key{
            .vaddr = virtual_offset,
            .size = size,
        };

        // Split at the boundaries of the region we are removing.
        this->SplitHeapMapLocked(virtual_offset);
        this->SplitHeapMapLocked(virtual_offset + size);

        // Erase all mappings in range.
        auto it = m_mappings.find(key);
        while (it != m_mappings.end() && it->vaddr < virtual_offset + size) {
            // Get pointer to item.
            SeparateHeapMap* const item = std::addressof(*it);

            if (item->is_resident) {
                // Unlink from resident tree.
                m_resident_mappings.erase(m_resident_mappings.iterator_to(*item));

                // Decrease reference count.
                const auto count_it = m_resident_map_counts.find(item->map_id);
                this->RemoveReferenceLocked(count_it, 1);
            }

            // Unlink from mapping tree and advance.
            it = m_mappings.erase(it);

            // Free the item.
            delete item;
        }
    }

    // Unmap pages.
    m_buffer.Unmap(virtual_offset, size, false);
}

void HeapTracker::Protect(size_t virtual_offset, size_t size, MemoryPermission perm) {
    // Ensure no rebuild occurs while reprotecting.
    std::shared_lock lk{m_rebuild_lock};

    // Split at the boundaries of the region we are reprotecting.
    this->SplitHeapMap(virtual_offset, size);

    // Declare tracking variables.
    VAddr cur = virtual_offset;
    VAddr end = virtual_offset + size;

    while (cur < end) {
        VAddr next = cur;
        bool should_protect = false;

        {
            std::scoped_lock lk2{m_lock};

            const SeparateHeapMap key{
                .vaddr = next,
            };

            // Try to get the next mapping corresponding to this address.
            const auto it = m_mappings.nfind_key(key);

            if (it == m_mappings.end()) {
                // There are no separate heap mappings remaining.
                next = end;
                should_protect = true;
            } else if (it->vaddr == cur) {
                // We are in range.
                // Update permission bits.
                it->perm = perm;

                // Determine next address and whether we should protect.
                next = cur + it->size;
                should_protect = it->is_resident;
            } else /* if (it->vaddr > cur) */ {
                // We weren't in range, but there is a block coming up that will be.
                next = it->vaddr;
                should_protect = true;
            }
        }

        // Clamp to end.
        next = std::min(next, end);

        // Reprotect, if we need to.
        if (should_protect) {
            m_buffer.Protect(cur, next - cur, perm);
        }

        // Advance.
        cur = next;
    }
}

bool HeapTracker::DeferredMapSeparateHeap(u8* fault_address) {
    if (m_buffer.IsInVirtualRange(fault_address)) {
        return this->DeferredMapSeparateHeap(fault_address - m_buffer.VirtualBasePointer());
    }

    return false;
}

bool HeapTracker::DeferredMapSeparateHeap(size_t virtual_offset) {
    std::scoped_lock lk{m_lock};

    while (this->IsEvictRequiredLocked()) {
        // Unlock before we rebuild to ensure proper lock ordering.
        m_lock.unlock();

        // Evict four maps.
        for (size_t i = 0; i < 4; /* ... */) {
            i += this->EvictSingleSeparateHeapMap();
        }

        // Lock again.
        m_lock.lock();
    }

    // Check to ensure this was a non-resident separate heap mapping.
    const auto it = this->GetNearestHeapMapLocked(virtual_offset);
    if (it == m_mappings.end()) {
        // Not in any separate heap.
        return false;
    }
    if (it->is_resident) {
        // Already mapped and shouldn't be considered again.
        return false;
    }

    // Map the area.
    m_buffer.Map(it->vaddr, it->paddr, it->size, it->perm, false);

    // This map is now resident.
    this->AddReferenceLocked(it->map_id, 1);
    it->is_resident = true;
    it->tick = m_tick++;

    // Insert into resident maps.
    m_resident_mappings.insert(*it);

    // We succeeded.
    return true;
}

bool HeapTracker::EvictSingleSeparateHeapMap() {
    std::scoped_lock lk{m_rebuild_lock, m_lock};

    ASSERT(!m_resident_mappings.empty());

    // Select the item with the lowest tick to evict.
    auto* const item = std::addressof(*m_resident_mappings.begin());
    auto it = m_mappings.iterator_to(*item);

    // Track the map ID.
    const size_t map_id = it->map_id;

    // Walk backwards until we find the first entry.
    while (it != m_mappings.begin()) {
        // If the previous element does not have the same map ID, stop.
        const auto prev = std::prev(it);
        if (prev->map_id != map_id) {
            break;
        }

        // Continue.
        it = prev;
    }

    // Track the begin and end address.
    const VAddr begin_vaddr = it->vaddr;
    VAddr end_vaddr = begin_vaddr;

    // Get the count iterator.
    const auto count_it = m_resident_map_counts.find(map_id);

    // Declare whether we have erased an underlying mapping.
    bool was_erased = false;

    // Unmark and merge everything in range.
    while (it != m_mappings.end() && it->map_id == map_id) {
        if (it->is_resident) {
            // Remove from resident tree.
            m_resident_mappings.erase(m_resident_mappings.iterator_to(*it));
            it->is_resident = false;

            // Remove reference count.
            was_erased |= this->RemoveReferenceLocked(count_it, 1);
        }

        // Update the end address.
        end_vaddr = it->vaddr + it->size;

        // Advance.
        it = this->MergeHeapMapForEvictLocked(it);
    }

    // Finally, unmap.
    ASSERT(end_vaddr >= begin_vaddr);
    m_buffer.Unmap(begin_vaddr, end_vaddr - begin_vaddr, false);

    // Return whether we actually removed a mapping.
    // This will be true if there were no holes, which is likely.
    return was_erased;
}

void HeapTracker::SplitHeapMap(VAddr offset, size_t size) {
    std::scoped_lock lk{m_lock};

    this->SplitHeapMapLocked(offset);
    this->SplitHeapMapLocked(offset + size);
}

void HeapTracker::SplitHeapMapLocked(VAddr offset) {
    const auto it = this->GetNearestHeapMapLocked(offset);
    if (it == m_mappings.end() || it->vaddr == offset) {
        // Not contained or no split required.
        return;
    }

    // Get the underlying item as the left.
    auto* const left = std::addressof(*it);

    // Cache the original size values.
    const size_t size = left->size;

    // Adjust the left map.
    const size_t left_size = offset - left->vaddr;
    left->size = left_size;

    // Create the new right map.
    auto* const right = new SeparateHeapMap{
        .vaddr = left->vaddr + left_size,
        .paddr = left->paddr + left_size,
        .size = size - left_size,
        .map_id = left->map_id,
        .tick = left->tick,
        .perm = left->perm,
        .is_resident = left->is_resident,
    };

    // Insert the new right map.
    m_mappings.insert(*right);

    // If the original map was not resident, we are done.
    if (!left->is_resident) {
        return;
    }

    // Update reference count.
    this->AddReferenceLocked(left->map_id, 1);

    // Insert right into resident map.
    m_resident_mappings.insert(*right);
}

HeapTracker::AddrTree::iterator HeapTracker::MergeHeapMapForEvictLocked(AddrTree::iterator it) {
    if (it == m_mappings.end()) {
        // Not contained.
        return it;
    }

    if (it == m_mappings.begin()) {
        // Nothing to merge with.
        return std::next(it);
    }

    // Get the left and right items.
    auto* const right = std::addressof(*it);
    auto* const left = std::addressof(*std::prev(it));

    if (left->vaddr + left->size != right->vaddr) {
        // Virtual range not contiguous, cannot merge.
        return std::next(it);
    }

    if (left->paddr + left->size != right->paddr) {
        // Physical range not contiguous, cannot merge.
        return std::next(it);
    }

    if (left->perm != right->perm) {
        // Permissions mismatch, cannot merge.
        return std::next(it);
    }

    if (left->map_id != right->map_id) {
        // Map ID mismatch, cannot merge.
        return std::next(it);
    }

    // Merge size to the left.
    left->size += right->size;

    // Erase the right element.
    const auto next_it = m_mappings.erase(it);

    // Free the right element.
    delete right;

    // Return the iterator to the next position.
    return next_it;
}

HeapTracker::AddrTree::iterator HeapTracker::GetNearestHeapMapLocked(VAddr offset) {
    const SeparateHeapMap key{
        .vaddr = offset,
    };

    return m_mappings.find(key);
}

void HeapTracker::AddReferenceLocked(size_t map_id, size_t inc) {
    m_resident_map_counts[map_id]++;
}

bool HeapTracker::RemoveReferenceLocked(MapCountTree::iterator it, size_t dec) {
    ASSERT(it != m_resident_map_counts.end());

    const auto new_value = it->second -= dec;
    ASSERT(new_value >= 0);

    if (new_value <= 0) {
        m_resident_map_counts.erase(it);
        return true;
    }

    return false;
}

bool HeapTracker::IsEvictRequiredLocked() {
    return m_resident_map_counts.size() > MaxResidentMapCount;
}

} // namespace Common
