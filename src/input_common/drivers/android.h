// SPDX-FileCopyrightText: Copyright 2024 yuzu Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

#pragma once

#include <set>
#include <jni.h>
#include "input_common/input_engine.h"

namespace InputCommon {

/**
 * A virtual controller that is always assigned to the game input
 */
class Android final : public InputEngine {
public:
    explicit Android(std::string input_engine_);

    /**
     * Registers controller number to accept new inputs
     * @param controller_number the controller number that will take this action
     */
    void RegisterController(jobject j_input_device);

    /**
     * Sets the status of all buttons bound with the key to pressed
     * @param controller_number the controller number that will take this action
     * @param button_id the id of the button
     * @param value indicates if the button is pressed or not
     */
    void SetButtonState(std::string guid, size_t port, int button_id, bool value);

    /**
     * Sets the status of a analog input to a specific player index
     * @param controller_number the controller number that will take this action
     * @param axis_id the id of the axis to move
     * @param x_value the position of the stick in the x axis
     * @param y_value the position of the stick in the y axis
     */
    void SetAxisPosition(std::string guid, size_t port, int axis_id, float value);

    /**
     * Sets the status of the motion sensor to a specific player index
     * @param controller_number the controller number that will take this action
     * @param delta_timestamp time passed since last reading
     * @param gyro_x,gyro_y,gyro_z the gyro sensor readings
     * @param accel_x,accel_y,accel_z the accelerometer reading
     */
    void SetMotionState(std::string guid, size_t port, u64 delta_timestamp, float gyro_x,
                        float gyro_y, float gyro_z, float accel_x, float accel_y, float accel_z);

    Common::Input::DriverResult SetVibration(
        [[maybe_unused]] const PadIdentifier& identifier,
        [[maybe_unused]] const Common::Input::VibrationStatus& vibration) override;

    bool IsVibrationEnabled([[maybe_unused]] const PadIdentifier& identifier) override;

    std::vector<Common::ParamPackage> GetInputDevices() const override;

    std::set<s32> GetDeviceAxes(JNIEnv* env, jobject& j_device) const;

    Common::ParamPackage BuildParamPackageForAnalog(PadIdentifier identifier, int axis_x,
                                                    int axis_y) const;

    Common::ParamPackage BuildAnalogParamPackageForButton(PadIdentifier identifier, s32 axis,
                                                          bool invert) const;

    Common::ParamPackage BuildButtonParamPackageForButton(PadIdentifier identifier,
                                                          s32 button) const;

    bool MatchVID(Common::UUID device, const std::vector<std::string>& vids) const;

    AnalogMapping GetAnalogMappingForDevice(const Common::ParamPackage& params) override;

    ButtonMapping GetButtonMappingForDevice(const Common::ParamPackage& params) override;

    Common::Input::ButtonNames GetUIName(
        [[maybe_unused]] const Common::ParamPackage& params) const override;

private:
    std::unordered_map<PadIdentifier, jobject> input_devices;

    /// Returns the correct identifier corresponding to the player index
    PadIdentifier GetIdentifier(const std::string& guid, size_t port) const;

    const s32 AXIS_X = 0;
    const s32 AXIS_Y = 1;
    const s32 AXIS_Z = 11;
    const s32 AXIS_RX = 12;
    const s32 AXIS_RY = 13;
    const s32 AXIS_RZ = 14;
    const s32 AXIS_HAT_X = 15;
    const s32 AXIS_HAT_Y = 16;
    const s32 AXIS_LTRIGGER = 17;
    const s32 AXIS_RTRIGGER = 18;

    const s32 KEYCODE_DPAD_UP = 19;
    const s32 KEYCODE_DPAD_DOWN = 20;
    const s32 KEYCODE_DPAD_LEFT = 21;
    const s32 KEYCODE_DPAD_RIGHT = 22;
    const s32 KEYCODE_BUTTON_A = 96;
    const s32 KEYCODE_BUTTON_B = 97;
    const s32 KEYCODE_BUTTON_X = 99;
    const s32 KEYCODE_BUTTON_Y = 100;
    const s32 KEYCODE_BUTTON_L1 = 102;
    const s32 KEYCODE_BUTTON_R1 = 103;
    const s32 KEYCODE_BUTTON_L2 = 104;
    const s32 KEYCODE_BUTTON_R2 = 105;
    const s32 KEYCODE_BUTTON_THUMBL = 106;
    const s32 KEYCODE_BUTTON_THUMBR = 107;
    const s32 KEYCODE_BUTTON_START = 108;
    const s32 KEYCODE_BUTTON_SELECT = 109;
    const std::vector<s32> keycode_ids{
        KEYCODE_DPAD_UP,       KEYCODE_DPAD_DOWN,     KEYCODE_DPAD_LEFT,    KEYCODE_DPAD_RIGHT,
        KEYCODE_BUTTON_A,      KEYCODE_BUTTON_B,      KEYCODE_BUTTON_X,     KEYCODE_BUTTON_Y,
        KEYCODE_BUTTON_L1,     KEYCODE_BUTTON_R1,     KEYCODE_BUTTON_L2,    KEYCODE_BUTTON_R2,
        KEYCODE_BUTTON_THUMBL, KEYCODE_BUTTON_THUMBR, KEYCODE_BUTTON_START, KEYCODE_BUTTON_SELECT,
    };

    const std::string sony_vid{"054c"};
    const std::string nintendo_vid{"057e"};
    const std::string razer_vid{"1532"};
    const std::string redmagic_vid{"3537"};
    const std::string backbone_labs_vid{"358a"};
    const std::vector<std::string> flipped_ab_vids{sony_vid, nintendo_vid, razer_vid, redmagic_vid,
                                                   backbone_labs_vid};
    const std::vector<std::string> flipped_xy_vids{sony_vid, razer_vid, redmagic_vid,
                                                   backbone_labs_vid};
};

} // namespace InputCommon
