package com.wetype.liquid.discovery

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

object MethodFinder {
    private val methodCache = ConcurrentHashMap<String, Method?>()
    private val fieldCache = ConcurrentHashMap<String, Field?>()
    private val constructorCache = ConcurrentHashMap<String, Constructor<*>?>()

    fun findMethodExact(clazz: Class<*>, methodName: String, vararg parameterTypes: Class<*>): Method? {
        val cacheKey = "${clazz.name}#$methodName(${parameterTypes.joinToString { it.name }})"
        return methodCache.computeIfAbsent(cacheKey) {
            try {
                var current: Class<*>? = clazz
                while (current != null && current != Any::class.java) {
                    try {
                        val m = current.getDeclaredMethod(methodName, *parameterTypes)
                        m.isAccessible = true
                        return@computeIfAbsent m
                    } catch (e: NoSuchMethodException) {
                        current = current.superclass
                    }
                }
                null
            } catch (t: Throwable) {
                null
            }
        }
    }

    fun findFirstMethodByParamTypes(clazz: Class<*>, vararg paramTypes: Class<*>): Method? {
        val cacheKey = "${clazz.name}#firstMethod(${paramTypes.joinToString { it.name }})"
        return methodCache.computeIfAbsent(cacheKey) {
            try {
                var current: Class<*>? = clazz
                while (current != null && current != Any::class.java) {
                    for (m in current.declaredMethods) {
                        val types = m.parameterTypes
                        if (types.size == paramTypes.size) {
                            var match = true
                            for (i in types.indices) {
                                if (!paramTypes[i].isAssignableFrom(types[i])) {
                                    match = false
                                    break
                                }
                            }
                            if (match) {
                                m.isAccessible = true
                                return@computeIfAbsent m
                            }
                        }
                    }
                    current = current.superclass
                }
                null
            } catch (t: Throwable) {
                null
            }
        }
    }

    fun findMethodsWithSignature(
        clazz: Class<*>,
        returnType: Class<*>? = null,
        paramTypes: Array<Class<*>>? = null,
        isStatic: Boolean? = null
    ): List<Method> {
        val result = mutableListOf<Method>()
        try {
            var current: Class<*>? = clazz
            while (current != null && current != Any::class.java) {
                for (m in current.declaredMethods) {
                    if (returnType != null && !returnType.isAssignableFrom(m.returnType)) continue
                    if (isStatic != null && Modifier.isStatic(m.modifiers) != isStatic) continue
                    if (paramTypes != null) {
                        val types = m.parameterTypes
                        if (types.size != paramTypes.size) continue
                        var match = true
                        for (i in types.indices) {
                            if (!paramTypes[i].isAssignableFrom(types[i])) {
                                match = false
                                break
                            }
                        }
                        if (!match) continue
                    }
                    m.isAccessible = true
                    result.add(m)
                }
                current = current.superclass
            }
        } catch (t: Throwable) {
            SafeHook.log(SafeHook.LogLevel.WARN, message = "Error finding methods in ${clazz.name}: ${t.message}")
        }
        return result
    }

    fun findFieldExact(clazz: Class<*>, fieldName: String): Field? {
        val cacheKey = "${clazz.name}#$fieldName"
        return fieldCache.computeIfAbsent(cacheKey) {
            try {
                var current: Class<*>? = clazz
                while (current != null && current != Any::class.java) {
                    try {
                        val f = current.getDeclaredField(fieldName)
                        f.isAccessible = true
                        return@computeIfAbsent f
                    } catch (e: NoSuchFieldException) {
                        current = current.superclass
                    }
                }
                null
            } catch (t: Throwable) {
                null
            }
        }
    }

    fun findFirstFieldByType(clazz: Class<*>, fieldType: Class<*>): Field? {
        val cacheKey = "${clazz.name}#firstFieldType(${fieldType.name})"
        return fieldCache.computeIfAbsent(cacheKey) {
            try {
                var current: Class<*>? = clazz
                while (current != null && current != Any::class.java) {
                    for (f in current.declaredFields) {
                        if (fieldType.isAssignableFrom(f.type)) {
                            f.isAccessible = true
                            return@computeIfAbsent f
                        }
                    }
                    current = current.superclass
                }
                null
            } catch (t: Throwable) {
                null
            }
        }
    }

    fun getFieldValue(instance: Any, fieldName: String): Any? {
        val field = findFieldExact(instance.javaClass, fieldName) ?: return null
        return try {
            field.get(instance)
        } catch (t: Throwable) {
            null
        }
    }

    fun setFieldValue(instance: Any, fieldName: String, value: Any?): Boolean {
        val field = findFieldExact(instance.javaClass, fieldName) ?: return false
        return try {
            field.set(instance, value)
            true
        } catch (t: Throwable) {
            false
        }
    }
}
