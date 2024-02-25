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

#pragma once

namespace Loom {
template <typename Traits> class RaiiWithInvalidValue {
public:
  using type = typename Traits::type;

private:
  type value;

public:
  RaiiWithInvalidValue() noexcept : value{Traits::invalid_value} {}

  explicit RaiiWithInvalidValue(type t) noexcept : value{t} {}

  ~RaiiWithInvalidValue() {
    if (is_valid()) {
      Traits::close(value);
    }
  }

  RaiiWithInvalidValue(const RaiiWithInvalidValue &) = delete;
  RaiiWithInvalidValue &operator=(const RaiiWithInvalidValue &) = delete;
  RaiiWithInvalidValue &operator=(RaiiWithInvalidValue &&other) = delete;
  RaiiWithInvalidValue(RaiiWithInvalidValue &&other) = delete;

  bool is_valid() const noexcept { return value != Traits::invalid_value; }

  explicit operator bool() const noexcept { return is_valid(); }

  type get() const noexcept { return value; }
};
} // namespace Loom