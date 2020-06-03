package com.maanoo.objecteditor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;


public class ClassInfo {

    private static final HashMap<Class<?>, ClassInfo> classInfos = new HashMap<Class<?>, ClassInfo>();

    public static ClassInfo of(Class<?> c) {
        {
            final ClassInfo info = classInfos.get(c);
            if (info != null) return info;
        }
        final ClassInfo info = new ClassInfo(c);
        classInfos.put(c, info);
        return info;
    }

    // ===

    public final Class<?> c;

    private final ArrayList<Field> fields;
    private final ArrayList<MethodInfo> methods;

    private ClassInfo(Class<?> c) {
        this.c = c;

        fields = new ArrayList<Field>();
        methods = new ArrayList<MethodInfo>();

        load(c);
    }

    private void load(Class<?> c) {
        if (c == null) return;

        for (final Field field : c.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);

            fields.add(field);
        }

        for (final Method method : c.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())) continue;
            method.setAccessible(true);

            if (containsOverloadOf(method)) continue;

            methods.add(new MethodInfo(method));
        }

        load(c.getSuperclass());
    }

    private boolean containsOverloadOf(Method method) {
        for (final MethodInfo i : methods) {
            if (isOverload(method, i.method)) {
                return true;
            }
        }
        return false;
    }

    private boolean isOverload(Method method, Method overload) {

        if (overload.getParameterCount() != method.getParameterCount()
                || overload.getDeclaringClass() == method.getDeclaringClass()
                || !overload.getName().equals(method.getName()))
            return false;

        // TODO: opt, getParameters clones the array

        final Parameter[] params1 = overload.getParameters();
        final Parameter[] params2 = overload.getParameters();

        for (int i = 0; i < params1.length; i++) {
            if (!params1[i].equals(params2[i])) return false;
        }
        return true;
    }

    public Iterable<Field> getFields() {
        return fields;
    }

    public Iterable<MethodInfo> getMethods() {
        return methods;
    }

    // ===

    public static final class MethodInfo {

        public final Method method;

        public final Parameter[] parameters;
        public final Class<?>[] parametersTypes;
        public final Class<?> returnType;

        public MethodInfo(Method method) {
            this.method = method;

            parameters = method.getParameters();
            parametersTypes = method.getParameterTypes();
            returnType = method.getReturnType();
        }

        public Class<?> getDeclaringClass() {
            return method.getDeclaringClass();
        }

        public String getName() {
            return method.getName();
        }

        public int getParameterCount() {
            return parameters.length;
        }

        public interface ParameterProvider {
            Object get(Class<?> c, String name);
        }

        public Object invoke(Object object, ParameterProvider paramProvider) {

            final Object[] params = new Object[method.getParameterCount()];

            int index = 0;
            for (final Parameter input : method.getParameters()) {
                final Object param = paramProvider.get(input.getType(), input.getName());

                params[index++] = param;
            }

            try {
                return method.invoke(object, params);
            } catch (final Throwable ex) {
                return ex;
            }
        }

        public String getString() {
            final StringBuilder sb = new StringBuilder();

            sb.append(method.getName());

            sb.append("(");
            if (method.getParameterCount() > 0) {
                for (final Parameter i : parameters) {
                    sb.append(i.getType().getSimpleName());
                    sb.append(", ");
                }
                sb.setLength(sb.length() - ", ".length());
            }
            sb.append(")");

            if (method.getReturnType() != void.class) {
                sb.append(" : ").append(method.getReturnType().getSimpleName());
            }

            return sb.toString();
        }
    }

}
