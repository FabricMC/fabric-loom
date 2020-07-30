package net.fabricmc.loom.util.srg;

/**
 * An exception that occurs when processing obfuscation mappings.
 *
 * @author Juuz
 */
public class MappingException extends RuntimeException {
	public MappingException(String message) {
		super(message);
	}
}
