package net.fabricmc.loom.util.function;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Stream-like utilities for working with collections.
 *
 * @author Juuz
 */
public final class CollectionUtil {
	/**
	 * Finds the first element matching the predicate.
	 *
	 * @param collection the collection to be searched
	 * @param filter     the predicate to be matched
	 * @param <E>        the element type
	 * @return the first matching element, or empty if none match
	 */
	public static <E> Optional<E> find(Iterable<? extends E> collection, Predicate<? super E> filter) {
		for (E e : collection) {
			if (filter.test(e)) {
				return Optional.of(e);
			}
		}

		return Optional.empty();
	}

	/**
	 * Transforms the collection with a function.
	 *
	 * @param collection the source collection
	 * @param transform  the transformation function
	 * @param <A> the source type
	 * @param <B> the target type
	 * @return a mutable list with the transformed entries
	 */
	public static <A, B> List<B> map(Iterable<? extends A> collection, Function<? super A, ? extends B> transform) {
		ArrayList<B> result = new ArrayList<>();

		for (A a : collection) {
			result.add(transform.apply(a));
		}

		return result;
	}
}
