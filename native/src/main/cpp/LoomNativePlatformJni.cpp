/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#include "net_fabricmc_loom_util_nativeplatform_LoomNativePlatform.h"

#include "LoomNativePlatform.hpp"

#include <string>

namespace {
std::wstring asWstring(JNIEnv *env, jstring string) {
  const jchar *raw = env->GetStringChars(string, 0);
  jsize len = env->GetStringLength(string);

  std::wstring_view view(reinterpret_cast<const wchar_t *>(raw), len);
  std::wstring value{view.begin(), view.end()};

  env->ReleaseStringChars(string, raw);

  return value;
}
} // namespace

JNIEXPORT jint JNICALL
Java_net_fabricmc_loom_util_nativeplatform_LoomNativePlatform_getPidHoldingFileLock(
    JNIEnv *env, jclass, jstring path) {

  const auto wpath = asWstring(env, path);
  const auto pid = Loom::getPidHoldingFileLock(wpath);

  return -1;
}