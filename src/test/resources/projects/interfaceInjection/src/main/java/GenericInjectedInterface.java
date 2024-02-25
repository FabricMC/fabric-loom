
public interface GenericInjectedInterface<T> {
	default T genericMethodThatDidNotExist() {
		return null;
	}
}
