/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

package net.fabricmc.loom.test.util

import org.jetbrains.annotations.Nullable
import spock.lang.Shared

import javax.imageio.ImageIO
import java.awt.Point
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.awt.event.InputEvent
import java.awt.image.BufferedImage
import java.util.concurrent.TimeoutException

trait RobotTrait {
	@Shared
	Robot robot = new Robot()

	void waitForAndClick(String name) {
		long startTime = System.currentTimeSeconds()
		def image = getImage(name)

		Thread.start {
			// Wait 120 seconds
			while (true) {
				def point = findOnScreen(image)

				if (point != null) {
					robot.mouseMove(point.x as int, point.y as int)
					robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
					robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
					return
				}

				def elapsed = System.currentTimeSeconds() - startTime
				//println("Unable to find image on screen after $elapsed seconds.")

				//ImageIO.write(screenCapture(), "png", new File("src/test/resources/images/test.png"))

				if (elapsed > 120) {
					break
				}

				Thread.sleep(1000)
			}

			// TODO ensure this fails the test
			throw new TimeoutException()
		}
	}

	@Nullable
	Point findOnScreen(BufferedImage image) {
		def screenCapture = screenCapture()

		for (sx in 0..<screenCapture.width) {
			for (sy in 0..<screenCapture.height) {
				if (isImageAt(screenCapture, image, sx, sy)) {
					return new Point(x: sx, y: sy)
				}
			}
		}

		return null
	}

	boolean isImageAt(BufferedImage main, BufferedImage child, int x, int y) {
		int w = child.getWidth()
		int h = child.getHeight()

		if (x + w > main.getWidth()) {
			return false
		}

		if (y + h > main.getHeight()) {
			return false
		}

		for (ix in 0..<w) {
			for (iy in 0..<h) {
				int mainRgb = main.getRGB(x + ix, y + iy)
				int childRgb = child.getRGB(ix, iy)

				if (mainRgb != childRgb) {
					return false
				}
			}
		}

		return true
	}

	BufferedImage screenCapture() {
		return robot.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()))
	}

	BufferedImage getImage(String name) {
		return ImageIO.read(new File("src/test/resources/images/${name}.png"))
	}
}
