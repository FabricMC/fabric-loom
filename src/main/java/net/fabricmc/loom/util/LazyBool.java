package net.fabricmc.loom.util;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * A lazily computed boolean value.
 */
public final class LazyBool implements BooleanSupplier {
	private BooleanSupplier supplier;
	private Boolean value;

	public LazyBool(BooleanSupplier supplier) {
		this.supplier = Objects.requireNonNull(supplier, "supplier");
	}

	@Override
	public boolean getAsBoolean() {
		if (value == null) {
			value = supplier.getAsBoolean();
			supplier = null; // Release the supplier
		}

		return value;
	}
}
