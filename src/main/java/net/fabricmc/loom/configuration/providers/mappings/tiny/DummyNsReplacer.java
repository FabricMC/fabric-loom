package net.fabricmc.loom.configuration.providers.mappings.tiny;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.fabricmc.mappingio.MappingUtil;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor;

final class DummyNsReplacer extends ForwardingMappingVisitor {
	private static final Map<String, String> REPLACEMENTS = Map.of(MappingUtil.NS_SOURCE_FALLBACK, "intermediary", MappingUtil.NS_TARGET_FALLBACK, "named");

	DummyNsReplacer(MappingVisitor next) {
		super(next);
	}

	@Override
	public void visitNamespaces(String srcNamespace, List<String> dstNamespaces) throws IOException {
		super.visitNamespaces(REPLACEMENTS.getOrDefault(srcNamespace, srcNamespace), replaceEach(dstNamespaces));
	}

	private static List<String> replaceEach(List<String> namespaces) {
		List<String> result = null;

		for (int i = 0; i < namespaces.size(); i++) {
			String ns = namespaces.get(i);
			String newNs = REPLACEMENTS.get(ns);

			if (newNs != null) {
				if (result == null) {
					result = new ArrayList<>(namespaces);
				}

				result.set(i, newNs);
			}
		}

		return result != null ? result : namespaces;
	}
}
