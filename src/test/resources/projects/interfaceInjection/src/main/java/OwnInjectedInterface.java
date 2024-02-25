
public interface OwnInjectedInterface<T> {
	default void anotherNewMethodThatDidNotExist() {
	}

	default T typedMethodThatDidNotExist() {
		return null;
	}
}
