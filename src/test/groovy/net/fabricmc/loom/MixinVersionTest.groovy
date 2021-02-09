package net.fabricmc.loom

import spock.lang.Specification

class MixinVersionTest extends Specification {

	def "Mixin version parsing"() {
		expect:
			LoomGradleExtension.parseMixinVersion(input) == output

		where:
			input					| output
			"0.7.11.10"				| "0.7"
			"0.8.2+build.24"		| "0.8"
			"0.9.1+mixin.0.8.2"		| "0.8"
			"0.9.1+mixin.0.8.10"	| "0.8"
			"1.12.3+mixin.0.8.2"	| "0.8"
			"1.16.3+mixin.1.0.2"	| "1.0"
			"1.16.3+mixin.1.10.0"	| "1.10"
	}
}
