/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2026 FabricMC
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

package net.fabricmc.loom.util.problem;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.gradle.api.Action;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.ProblemReporter;

import net.fabricmc.loom.util.github.GithubActionsAnnotations;

/**
 * A wrapper for {@link ProblemReporter}. This class can manage multiple problems at a time
 * and report them as GitHub Actions checks.
 */
public final class LoomProblemReporter {
	private final ProblemReporter reporter;
	private final ProblemReportingOptions options;
	private final List<Problem> problems = new ArrayList<>();

	public LoomProblemReporter(ProblemReporter reporter, ProblemReportingOptions options) {
		this.reporter = reporter;
		this.options = options;
	}

	public LoomProblemReporter problem(ProblemId problemId, Action<? super Problem.Builder> action) {
		Problem.Builder builder = Problem.builder(problemId);
		action.execute(builder);
		problem(builder.build());
		return this;
	}

	public LoomProblemReporter problem(Problem problem) {
		problems.add(problem);
		return this;
	}

	public void report() {
		for (Problem problem : problems) {
			reporter.report(problem.id(), spec -> {
				if (problem.details() != null) {
					spec.details(problem.details());
				}

				if (problem.contextualLabel() != null) {
					spec.contextualLabel(problem.contextualLabel());
				}

				if (problem.solution() != null) {
					spec.solution(problem.solution());
				}

				if (problem.cause() != null) {
					spec.withException(problem.cause());
				}

				if (problem.fileLocation() != null) {
					String path = problem.fileLocation().file().toAbsolutePath().toString();
					int line = problem.fileLocation().startLine();
					int column = problem.fileLocation().startColumn();

					if (line > 0) {
						if (column > 0) {
							spec.lineInFileLocation(path, line, column);
						} else {
							spec.lineInFileLocation(path, line);
						}
					} else {
						spec.fileLocation(path);
					}
				}
			});

			if (options.getDisplayGithubActionsAnnotations().get()) {
				String message = Objects.requireNonNullElse(problem.details(), problem.message());

				if (problem.solution() != null) {
					message += "\n\nSolution: " + problem.solution();
				}

				GithubActionsAnnotations.Builder builder = switch (problem.severity()) {
				case ERROR -> GithubActionsAnnotations.error(message);
				case WARNING -> GithubActionsAnnotations.warning(message);
				case ADVICE -> GithubActionsAnnotations.notice(message);
				};

				if (!message.equals(problem.id().getDisplayName())) {
					// We have details in the body so we can put the short context-free display name in the title.
					builder.title(problem.id().getDisplayName());
				}

				if (problem.fileLocation() != null) {
					builder.file(problem.fileLocation());
				}

				builder.build().printToStdout();
			}
		}
	}

	/**
	 * Reports any issues and throws a {@link RuntimeException} if issues were found.
	 *
	 * @param contextProblem the problem used as the exception detail message when multiple issues are found
	 */
	public void reportAndThrow(ProblemId contextProblem) {
		reportAndThrow(contextProblem.getDisplayName());
	}

	/**
	 * Reports any issues and throws a {@link RuntimeException} if issues were found.
	 *
	 * @param messageForMultipleProblems the exception detail message used when multiple issues are found
	 */
	public void reportAndThrow(String messageForMultipleProblems) {
		if (problems.isEmpty()) {
			return;
		}

		report();

		if (problems.size() == 1) {
			Problem problem = problems.getFirst();
			throw new RuntimeException(problem.message(), problem.cause());
		} else {
			var messageBuilder = new StringBuilder(messageForMultipleProblems).append(':');
			List<Throwable> causes = new ArrayList<>();

			for (Problem problem : problems) {
				messageBuilder.append("\n - ").append(problem.message());

				if (problem.cause() != null) {
					causes.add(problem.cause());
				}
			}

			Throwable cause = !causes.isEmpty() ? causes.getFirst() : null;
			RuntimeException exception = new RuntimeException(messageBuilder.toString(), cause);

			for (int i = 1; i < causes.size(); i++) {
				exception.addSuppressed(causes.get(i));
			}

			throw exception;
		}
	}
}
