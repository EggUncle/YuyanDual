# Input engine compatibility: static verification

Verified on 2026-10-22 while restoring the RP Duo adaptation from installed APK version 20261022.19. The APK identifies source commit `0e3cee330ec54ac88558cec84506b453adef0dca` and application ID `com.yuyan.pinyin.online.debug`.

## Identical native library

The extracted `lib/arm64-v8a/libyuyanime.so` is byte-for-byte identical to the fork's `app/src/main/jniLibs/arm64-v8a/libyuyanime.so`:

- Size: 4,364,536 bytes.
- SHA-256: `61e3e2eef53e9c3c294e82ab16bf3202ce51050873266d601ae7195184dd26f6`.

No native binary was patched or replaced.

## Package and date gate

Disassembly of `Java_com_yuyan_inputmethod_core_Rime_startupRime` (address `0x1d7580`) establishes the following control flow, rather than relying only on string matches:

1. The JNI call at `0x1d7684`–`0x1d76b4` resolves and calls `Context.getPackageName()`; the method-name and signature strings are at `0x15c120` and `0x15cf4d`.
2. Four `strcmp` calls at `0x1d76fc`, `0x1d7710`, `0x1d7724`, and `0x1d7738` compare that name with `com.yuyan.pinyin.online.release`, `com.yuyan.pinyin.offline.release`, `com.yuyan.pinyin.online.debug`, and `com.yuyan.pinyin.offline.debug`. The strings reside at `0x16736c`, `0x1673ac`, `0x1673ec`, and `0x16742c`. A match branches to the normal startup path.
3. For an unmatched name, the call at `0x1d7764` reads `CLOCK_REALTIME`. Instructions at `0x1d776c`–`0x1d7778` compare seconds with `0x69554880` (1,767,196,800), equivalent to 2025-12-31 16:00 UTC / 2026-01-01 00:00 Asia/Shanghai.
4. At or after that threshold an unmatched package returns via `0x1d77b8`, skipping the `Rime::startup(bool)` call at `0x1d77b4`. The visible keyboard can therefore remain usable while Chinese engine startup fails.

The restored fork uses `com.yuyan.pinyin.offline.debug` for debug builds and `com.yuyan.pinyin.offline.release` for release builds, both names present in the unchanged library. The debug package can coexist with the observed source APK's `online.debug` package; this does not guarantee coexistence with every other Yuyan build. An update within the same package still requires a matching signing certificate.

## Diagnostics and validation scope

`RimeEngine.selectSchema` now reports initialization failure once per process after the expected dictionary deployment version is present. The log and toast contain no typed text. This diagnostic does not repair a missing dictionary or bypass the native gate.

The source APK's bundled compatibility document has been restored with an explicit provenance note. Its original device-test claims describe that source build, not tests rerun against the restored fork. Static verification above establishes the binary behavior; current build and device-test results are recorded separately.

## Provenance limitation

The root GPL-3.0 license alone does not establish the licensing or source completeness of every bundled native library. This binary also contains a Chinese notice asserting Yuyan ownership and requiring written authorization for commercial use or distribution. This restoration introduces no new native engine and makes no broader licensing claim.
