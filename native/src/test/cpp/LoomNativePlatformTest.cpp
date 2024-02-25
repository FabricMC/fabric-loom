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

#include "LoomNativePlatform.hpp"
#include "Raii.hpp"

#define WIN32_LEAN_AND_MEAN
#include <windows.h>

#include <iostream>

namespace {

struct FileHandleRaiiTraits {
  using type = HANDLE;
  static constexpr auto invalid_value = INVALID_HANDLE_VALUE;
  static void close(type t) noexcept { ::CloseHandle(t); }
};
using FileHandle = Loom::RaiiWithInvalidValue<FileHandleRaiiTraits>;

void testGetPidHoldingFileLock() {
  std::cout << "testGetPidHoldingFileLock" << std::endl;

  std::filesystem::path file =
      std::filesystem::temp_directory_path() / "test.txt";
  // Hold a lock on the file
  FileHandle fileHandle{::CreateFileW(file.c_str(), GENERIC_READ, 0, NULL,
                                      OPEN_EXISTING, 0, NULL)};

  const auto pids = Loom::getPidHoldingFileLock(file);
  const auto currentPid = ::GetCurrentProcessId();

  if (std::find(pids.begin(), pids.end(), currentPid) == pids.end()) {
    throw std::runtime_error(
        "Current process not found in the list of processes holding the lock");
  }

  std::cout << "testGetPidHoldingFileLock passed" << std::endl;
}

void runTests() {
  try {
    testGetPidHoldingFileLock();
  } catch (const std::exception &e) {
    std::cerr << "Test failed: " << e.what() << std::endl;
    std::exit(1);
  }
}

} // namespace

int main() {
  runTests();
  return 0;
}