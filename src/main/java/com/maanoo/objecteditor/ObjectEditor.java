package com.maanoo.objecteditor;

import java.util.HashMap;


public class ObjectEditor {

    public static void main(String[] args) {

        final HashMap<String, String> map = new HashMap<String, String>();
        map.put("key1", "value1");
        map.put("key2", "value2");

//        System.out.println(ClassInfo.of(map.getClass()));

        final ObjectEditorWindow w = new ObjectEditorWindow(map);
        final ObjectEditorWindow w2 = new ObjectEditorWindow(w);
    }

}
