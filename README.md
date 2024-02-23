| Pull Request | Commit | Title | Author | Merged? |
|----|----|----|----|----|
| [12461](https://github.com/yuzu-emu/yuzu//pull/12461) | [`2831f5dc6`](https://github.com/yuzu-emu/yuzu//pull/12461/files) | Rework Nvdec and VIC to fix out-of-order videos, and speed up decoding. | [Kelebek1](https://github.com/Kelebek1/) | Yes |
| [12749](https://github.com/yuzu-emu/yuzu//pull/12749) | [`aad4b0d6f`](https://github.com/yuzu-emu/yuzu//pull/12749/files) | general: workarounds for SMMU syncing issues | [liamwhite](https://github.com/liamwhite/) | Yes |
| [13073](https://github.com/yuzu-emu/yuzu//pull/13073) | [`b5a17b501`](https://github.com/yuzu-emu/yuzu//pull/13073/files) | fsp: Migrate remaining interfaces to cmif serialization | [FearlessTobi](https://github.com/FearlessTobi/) | Yes |
| [13096](https://github.com/yuzu-emu/yuzu//pull/13096) | [`0a8759057`](https://github.com/yuzu-emu/yuzu//pull/13096/files) | texture_cache: use two-pass collection for costly load resources | [liamwhite](https://github.com/liamwhite/) | Yes |
| [13100](https://github.com/yuzu-emu/yuzu//pull/13100) | [`2c00599a5`](https://github.com/yuzu-emu/yuzu//pull/13100/files) | audio: move to new ipc | [liamwhite](https://github.com/liamwhite/) | Yes |
| [13115](https://github.com/yuzu-emu/yuzu//pull/13115) | [`89c2fd3d2`](https://github.com/yuzu-emu/yuzu//pull/13115/files) | olsc, pctl: move to new ipc | [liamwhite](https://github.com/liamwhite/) | Yes |
| [13116](https://github.com/yuzu-emu/yuzu//pull/13116) | [`54ed837ad`](https://github.com/yuzu-emu/yuzu//pull/13116/files) | android: Flip AB/XY for 8Bitdo controllers | [t895](https://github.com/t895/) | Yes |


End of merge log. You can find the original README.md below the break.

-----

<!--
SPDX-FileCopyrightText: 2018 yuzu Emulator Project
SPDX-License-Identifier: GPL-2.0-or-later
-->

<h1 align="center">
  <br>
  <a href="https://yuzu-emu.org/"><img src="https://raw.githubusercontent.com/yuzu-emu/yuzu-assets/master/icons/icon.png" alt="yuzu" width="200"></a>
  <br>
  <b>yuzu</b>
  <br>
</h1>

<h4 align="center"><b>yuzu</b> is the world's most popular, open-source, Nintendo Switch emulator — started by the creators of <a href="https://citra-emu.org" target="_blank">Citra</a>.
<br>
It is written in C++ with portability in mind, and we actively maintain builds for Windows, Linux and Android.
</h4>

<p align="center">
    <a href="https://dev.azure.com/yuzu-emu/yuzu/">
        <img src="https://dev.azure.com/yuzu-emu/yuzu/_apis/build/status/yuzu%20mainline?branchName=master"
            alt="Azure Mainline CI Build Status">
    </a>
    <a href="https://discord.com/invite/u77vRWY">
        <img src="https://img.shields.io/discord/398318088170242053?color=5865F2&label=yuzu&logo=discord&logoColor=white"
            alt="Discord">
    </a>
</p>

<p align="center">
  <a href="#compatibility">Compatibility</a> |
  <a href="#development">Development</a> |
  <a href="#building">Building</a> |
  <a href="#download">Download</a> |
  <a href="#support">Support</a> |
  <a href="#license">License</a>
</p>

## Compatibility

The emulator is capable of running most commercial games at full speed, provided you meet the [necessary hardware requirements](https://yuzu-emu.org/help/quickstart/#hardware-requirements).

For a full list of games yuzu supports, please visit our [Compatibility page](https://yuzu-emu.org/game/).

Check out our [website](https://yuzu-emu.org/) for the latest news on exciting features, monthly progress reports, and more!

## Development

Most of the development happens on GitHub. It's also where [our central repository](https://github.com/yuzu-emu/yuzu) is hosted. For development discussion, please join us on [Discord](https://discord.com/invite/u77vRWY).

If you want to contribute, please take a look at the [Contributor's Guide](https://github.com/yuzu-emu/yuzu/wiki/Contributing) and [Developer Information](https://github.com/yuzu-emu/yuzu/wiki/Developer-Information).
You can also contact any of the developers on Discord in order to know about the current state of the emulator.

If you want to contribute to the user interface translation project, please check out the [yuzu project on transifex](https://www.transifex.com/yuzu-emulator/yuzu). We centralize translation work there, and periodically upstream translations.

## Building

* __Windows__: [Windows Build](https://github.com/yuzu-emu/yuzu/wiki/Building-For-Windows)
* __Linux__: [Linux Build](https://github.com/yuzu-emu/yuzu/wiki/Building-For-Linux)

## Download

You can download the latest releases automatically via the installer on our [downloads](https://yuzu-emu.org/downloads/) page.


## Support

If you enjoy the project and want to support us financially, check out our Patreon!

<a href="https://www.patreon.com/yuzuteam">
    <img src="https://c5.patreon.com/external/logo/become_a_patron_button@2x.png" width="160">
</a>

Any donations received will go towards things like:
* Switch consoles to explore and reverse-engineer the hardware
* Switch games for testing, reverse-engineering, and implementing new features
* Web hosting and infrastructure setup
* Software licenses (e.g. Visual Studio, IDA Pro, etc.)
* Additional hardware (e.g. GPUs as-needed to improve rendering support, other peripherals to add support for, etc.)

If you wish to support us a different way, please join our [Discord](https://discord.gg/u77vRWY) and talk to bunnei. You may also contact: donations@yuzu-emu.org.

## License

yuzu is licensed under the GPLv3 (or any later version). Refer to the [LICENSE.txt](https://github.com/yuzu-emu/yuzu/blob/master/LICENSE.txt) file.
