package net.fabricmc.loom.util.proccessing;

import java.util.*;

//TODO is this format too verbose? Should something like tiny be used?
public class AccessManipulatorData {

	List<ClassData> classData;

	static class ClassData {
		Map<String, String> name;
		private List<AccessData> methods;
		private List<AccessData> fields;

		public void addName(String mapping, String name){
			if(this.name == null){
				this.name = new LinkedHashMap<>();
			}
			this.name.put(mapping, name);
		}

		public boolean isEqual(String name) {
			return this.name != null && this.name.containsValue(name);
		}

		public void addMethod(AccessData accessData){
			if(methods == null){
				methods = new ArrayList<>();
			}
			methods.add(accessData);
		}

		public void addField(AccessData accessData){
			if(fields == null){
				fields = new ArrayList<>();
			}
			fields.add(accessData);
		}

		public List<AccessData> getMethods() {
			if(methods == null){
				return Collections.emptyList();
			}
			return methods;
		}

		public List<AccessData> getFields() {
			if(fields == null){
				return Collections.emptyList();
			}
			return fields;
		}
	}

	static class AccessData {
		Map<String, String> name;
		Access access;

		public void addName(String mapping, String name){
			if(this.name == null){
				this.name = new LinkedHashMap<>();
			}
			this.name.put(mapping, name);
		}

		public boolean isEqual(String name) {
			return this.name != null && this.name.containsValue(name);
		}
	}

	enum Access {
		PUBLIC,
		PROTECTED,
		PRIVATE,
		DEAFULT
	}
}
