/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2023 FabricMC
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

package net.fabricmc.loom.kotlin.remapping;

import java.util.List;

import kotlinx.metadata.KmAnnotation;
import kotlinx.metadata.KmProperty;
import kotlinx.metadata.internal.extensions.KmClassExtension;
import kotlinx.metadata.internal.extensions.KmConstructorExtension;
import kotlinx.metadata.internal.extensions.KmFunctionExtension;
import kotlinx.metadata.internal.extensions.KmPackageExtension;
import kotlinx.metadata.internal.extensions.KmPropertyExtension;
import kotlinx.metadata.internal.extensions.KmTypeExtension;
import kotlinx.metadata.internal.extensions.KmTypeParameterExtension;
import kotlinx.metadata.jvm.JvmFieldSignature;
import kotlinx.metadata.jvm.JvmMethodSignature;
import kotlinx.metadata.jvm.internal.JvmClassExtension;
import kotlinx.metadata.jvm.internal.JvmConstructorExtension;
import kotlinx.metadata.jvm.internal.JvmFunctionExtension;
import kotlinx.metadata.jvm.internal.JvmPackageExtension;
import kotlinx.metadata.jvm.internal.JvmPropertyExtension;
import kotlinx.metadata.jvm.internal.JvmTypeExtension;
import kotlinx.metadata.jvm.internal.JvmTypeParameterExtension;
import org.jetbrains.annotations.Nullable;

/*
 * This is a fun meme. All of these kotlin classes are marked as "internal" so Kotlin code cannot compile against them.
 * However, luckily for us the Java compiler has no idea about this, so they can compile against it :D
 *
 * This file contains Java wrappers around Kotlin classes, to used by Kotlin.
 */
public interface JvmExtensionWrapper {
	record Class(JvmClassExtension extension) implements JvmExtensionWrapper {
		@Nullable
		public static Class get(KmClassExtension classExtension) {
			if (classExtension instanceof JvmClassExtension jvmClassExtension) {
				return new Class(jvmClassExtension);
			}

			return null;
		}

		public List<KmProperty> getLocalDelegatedProperties() {
			return extension.getLocalDelegatedProperties();
		}

		@Nullable
		public String getModuleName() {
			return extension.getModuleName();
		}

		public void setModuleName(@Nullable String name) {
			extension.setModuleName(name);
		}

		@Nullable
		public String getAnonymousObjectOriginName() {
			return extension.getAnonymousObjectOriginName();
		}

		public void setAnonymousObjectOriginName(@Nullable String name) {
			extension.setAnonymousObjectOriginName(name);
		}

		public int getJvmFlags() {
			return extension.getJvmFlags();
		}

		public void setJvmFlags(int flags) {
			extension.setJvmFlags(flags);
		}
	}

	record Package(JvmPackageExtension extension) {
		@Nullable
		public static Package get(KmPackageExtension packageExtension) {
			if (packageExtension instanceof JvmPackageExtension jvmPackageExtension) {
				return new Package(jvmPackageExtension);
			}

			return null;
		}

		public List<KmProperty> getLocalDelegatedProperties() {
			return extension.getLocalDelegatedProperties();
		}

		@Nullable
		public String getModuleName() {
			return extension.getModuleName();
		}

		public void setModuleName(@Nullable String name) {
			extension.setModuleName(name);
		}
	}

	record Function(JvmFunctionExtension extension) {
		@Nullable
		public static Function get(KmFunctionExtension functionExtension) {
			if (functionExtension instanceof JvmFunctionExtension jvmFunctionExtension) {
				return new Function(jvmFunctionExtension);
			}

			return null;
		}

		@Nullable
		public JvmMethodSignature getSignature() {
			return extension.getSignature();
		}

		public void setSignature(@Nullable JvmMethodSignature signature) {
			extension.setSignature(signature);
		}

		@Nullable
		public String getLambdaClassOriginName() {
			return extension.getLambdaClassOriginName();
		}

		public void setLambdaClassOriginName(@Nullable String name) {
			extension.setLambdaClassOriginName(name);
		}
	}

	record Property(JvmPropertyExtension extension) {
		@Nullable
		public static Property get(KmPropertyExtension propertyExtension) {
			if (propertyExtension instanceof JvmPropertyExtension jvmPropertyExtension) {
				return new Property(jvmPropertyExtension);
			}

			return null;
		}

		public int getJvmFlags() {
			return extension.getJvmFlags();
		}

		public void setJvmFlags(int flags) {
			extension.setJvmFlags(flags);
		}

		@Nullable
		public JvmFieldSignature getFieldSignature() {
			return extension.getFieldSignature();
		}

		public void setFieldSignature(@Nullable JvmFieldSignature signature) {
			extension.setFieldSignature(signature);
		}

		@Nullable
		public JvmMethodSignature getGetterSignature() {
			return extension.getGetterSignature();
		}

		public void setGetterSignature(@Nullable JvmMethodSignature signature) {
			extension.setGetterSignature(signature);
		}

		@Nullable
		public JvmMethodSignature getSetterSignature() {
			return extension.getSetterSignature();
		}

		public void setSetterSignature(@Nullable JvmMethodSignature signature) {
			extension.setSetterSignature(signature);
		}

		@Nullable
		public JvmMethodSignature getSyntheticMethodForAnnotations() {
			return extension.getSyntheticMethodForAnnotations();
		}

		public void setSyntheticMethodForAnnotations(@Nullable JvmMethodSignature signature) {
			extension.setSyntheticMethodForAnnotations(signature);
		}

		@Nullable
		public JvmMethodSignature getSyntheticMethodForDelegate() {
			return extension.getSyntheticMethodForDelegate();
		}

		public void setSyntheticMethodForDelegate(@Nullable JvmMethodSignature signature) {
			extension.setSyntheticMethodForDelegate(signature);
		}
	}

	record Constructor(JvmConstructorExtension extension) {
		@Nullable
		public static Constructor get(KmConstructorExtension constructorExtension) {
			if (constructorExtension instanceof JvmConstructorExtension jvmConstructorExtension) {
				return new Constructor(jvmConstructorExtension);
			}

			return null;
		}

		@Nullable
		public JvmMethodSignature getSignature() {
			return extension.getSignature();
		}

		public void setSignature(@Nullable JvmMethodSignature signature) {
			extension.setSignature(signature);
		}
	}

	record TypeParameter(JvmTypeParameterExtension extension) {
		@Nullable
		public static TypeParameter get(KmTypeParameterExtension typeParameterExtension) {
			if (typeParameterExtension instanceof JvmTypeParameterExtension jvmTypeParameterExtension) {
				return new TypeParameter(jvmTypeParameterExtension);
			}

			return null;
		}

		public List<KmAnnotation> getAnnotations() {
			return extension.getAnnotations();
		}
	}

	record Type(JvmTypeExtension extension) {
		@Nullable
		public static Type get(KmTypeExtension typeExtension) {
			if (typeExtension instanceof JvmTypeExtension jvmTypeExtension) {
				return new Type(jvmTypeExtension);
			}

			return null;
		}

		public boolean isRaw() {
			return extension.isRaw();
		}

		public void setRaw(boolean raw) {
			extension.setRaw(raw);
		}

		public List<KmAnnotation> getAnnotations() {
			return extension.getAnnotations();
		}
	}
}
