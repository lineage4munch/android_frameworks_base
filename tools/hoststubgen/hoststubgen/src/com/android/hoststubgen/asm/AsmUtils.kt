/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.hoststubgen.asm

import com.android.hoststubgen.ClassParseException
import com.android.hoststubgen.HostStubGenInternalException
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode


/** Name of the class initializer method. */
val CLASS_INITIALIZER_NAME = "<clinit>"

/** Descriptor of the class initializer method. */
val CLASS_INITIALIZER_DESC = "()V"

/**
 * Find any of [anyAnnotations] from the list of visible / invisible annotations.
 */
fun findAnyAnnotation(
        anyAnnotations: Set<String>,
        visibleAnnotations: List<AnnotationNode>?,
        invisibleAnnotations: List<AnnotationNode>?,
    ): AnnotationNode? {
    for (an in visibleAnnotations ?: emptyList()) {
        if (anyAnnotations.contains(an.desc)) {
            return an
        }
    }
    for (an in invisibleAnnotations ?: emptyList()) {
        if (anyAnnotations.contains(an.desc)) {
            return an
        }
    }
    return null
}

fun findAnnotationValueAsString(an: AnnotationNode, propertyName: String): String? {
    for (i in 0..(an.values?.size ?: 0) - 2 step 2) {
        val name = an.values[i]

        if (name != propertyName) {
            continue
        }
        val value = an.values[i + 1]
        if (value is String) {
            return value
        }
        throw ClassParseException(
                "The type of '$name' in annotation \"${an.desc}\" must be String" +
                        ", but is ${value?.javaClass?.canonicalName}")
    }
    return null
}

private val removeLastElement = """[./][^./]*$""".toRegex()

fun getPackageNameFromClassName(className: String): String {
    return className.replace(removeLastElement, "")
}

fun resolveClassName(className: String, packageName: String): String {
    if (className.contains('.') || className.contains('/')) {
        return className
    }
    return "$packageName.$className"
}

fun String.toJvmClassName(): String {
    return this.replace('.', '/')
}

fun String.toHumanReadableClassName(): String {
    return this.replace('/', '.')
}

fun String.toHumanReadableMethodName(): String {
    return this.replace('/', '.')
}

private val numericalInnerClassName = """.*\$\d+$""".toRegex()

fun isAnonymousInnerClass(cn: ClassNode): Boolean {
    // TODO: Is there a better way?
    return cn.name.matches(numericalInnerClassName)
}

/**
 * Take a class name. If it's a nested class, then return the name of its direct outer class name.
 * Otherwise, return null.
 */
fun getDirectOuterClassName(className: String): String? {
    val pos = className.indexOf('$')
    if (pos < 0) {
        return null
    }
    return className.substring(0, pos)
}

/**
 * Write bytecode to push all the method arguments to the stack.
 * The number of arguments and their type are taken from [methodDescriptor].
 */
fun writeByteCodeToPushArguments(methodDescriptor: String, writer: MethodVisitor) {
    var i = -1
    Type.getArgumentTypes(methodDescriptor).forEach { type ->
        i++

        // See https://en.wikipedia.org/wiki/List_of_Java_bytecode_instructions

        // Note, long and double will consume two local variable spaces, so the extra `i++`.
        when (type) {
            Type.VOID_TYPE -> throw HostStubGenInternalException("VOID_TYPE not expected")
            Type.BOOLEAN_TYPE, Type.INT_TYPE, Type.SHORT_TYPE, Type.CHAR_TYPE
                -> writer.visitVarInsn(Opcodes.ILOAD, i)
            Type.LONG_TYPE -> writer.visitVarInsn(Opcodes.LLOAD, i++)
            Type.FLOAT_TYPE -> writer.visitVarInsn(Opcodes.FLOAD, i)
            Type.DOUBLE_TYPE -> writer.visitVarInsn(Opcodes.DLOAD, i++)
            else -> writer.visitVarInsn(Opcodes.ALOAD, i)
        }
    }
}

/**
 * Write bytecode to "RETURN" that matches the method's return type, according to
 * [methodDescriptor].
 */
fun writeByteCodeToReturn(methodDescriptor: String, writer: MethodVisitor) {
    Type.getReturnType(methodDescriptor).let { type ->
        // See https://en.wikipedia.org/wiki/List_of_Java_bytecode_instructions
        when (type) {
            Type.VOID_TYPE -> writer.visitInsn(Opcodes.RETURN)
            Type.BOOLEAN_TYPE, Type.INT_TYPE, Type.SHORT_TYPE, Type.CHAR_TYPE
                -> writer.visitInsn(Opcodes.IRETURN)
            Type.LONG_TYPE -> writer.visitInsn(Opcodes.LRETURN)
            Type.FLOAT_TYPE -> writer.visitInsn(Opcodes.FRETURN)
            Type.DOUBLE_TYPE -> writer.visitInsn(Opcodes.DRETURN)
            else -> writer.visitInsn(Opcodes.ARETURN)
        }
    }
}

/**
 * Return the "visibility" modifier from an `access` integer.
 *
 * (see https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.1-200-E.1)
 */
fun getVisibilityModifier(access: Int): Int {
    return access and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PRIVATE or Opcodes.ACC_PROTECTED)
}

/**
 * Return true if an `access` integer is "private" or "package private".
 */
fun isVisibilityPrivateOrPackagePrivate(access: Int): Boolean {
    return when (getVisibilityModifier(access)) {
        0 -> true // Package private.
        Opcodes.ACC_PRIVATE -> true
        else -> false
    }
}
