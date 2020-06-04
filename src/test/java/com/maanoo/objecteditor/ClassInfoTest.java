package com.maanoo.objecteditor;

import static org.junit.Assert.*;

import java.lang.reflect.Method;
import java.util.HashMap;

import org.junit.Test;

import com.maanoo.objecteditor.ClassInfo.MethodInfo;


public class ClassInfoTest {

    @Test
    public void reflection() throws Exception {

        final HashMap<String, String> map = new HashMap<String, String>();

        assertNotNull(map.getClass());
        assertNotNull(map.getClass().getDeclaredFields());
        assertNotNull(map.getClass().getDeclaredMethods());

    }

    @Test
    public void reflectionInfo() throws Exception {

        final ClassInfo info = ClassInfo.of(HashMap.class);

        assertTrue(info.getFields().iterator().hasNext());
        assertTrue(info.getMethods().iterator().hasNext());
    }

    @Test
    public void reflectionInvokeMethod() throws Exception {

        final HashMap<String, String> map = new HashMap<String, String>();
        map.put("key", "value");

        final Class<?> c = map.getClass();
        final Method method = c.getMethod("clear");

        method.invoke(map);

        assertEquals(0, map.size());
    }

    @Test
    public void reflectionInvokePrivateMethod() throws Exception {

        final HashMap<String, String> map = new HashMap<String, String>();
        map.put("key", "value");

        final ClassInfo info = ClassInfo.of(map.getClass());

        MethodInfo method = null;
        for (final MethodInfo i : info.getMethods()) {
            if (i.getName().equals("reinitialize")) {
                method = i;
            }
        }

        assertNotNull(method);

        method.invoke(map, null);

        assertEquals(0, map.size());
    }

}
